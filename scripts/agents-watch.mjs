#!/usr/bin/env node
/**
 * 一次看全所有并发 subagent 的状态，替代逐个调用 get_subagent_result。
 *
 * subagent 的输出是 JSONL，每行一条消息（user / assistant / toolResult）。
 * assistant 行里带 model、usage、timestamp、cwd，足够还原「跑在哪个模型上、
 * 多久没动静、在改哪些文件、最后说了什么」。
 *
 * 用法：
 *   node scripts/agents-watch.mjs              # 全部 agent
 *   node scripts/agents-watch.mjs --full       # 附最后一段完整输出
 *   node scripts/agents-watch.mjs --idle 300   # 只列静默超过 300 秒的
 */
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { execSync } from "node:child_process";

const ROOT = path.resolve(import.meta.dirname, "..");

/**
 * JSONL 里的 cwd 是会话原始目录，不是 agent 实际的 worktree。
 * 真实位置只能从 git worktree list 反查 —— 目录名里嵌了 agentId。
 */
function worktreeMap() {
  const map = new Map();
  let out = "";
  try {
    out = execSync("git worktree list --porcelain", {
      cwd: ROOT,
      encoding: "utf8",
    });
  } catch {
    return map;
  }
  let cur = null;
  for (const line of out.split("\n")) {
    if (line.startsWith("worktree ")) cur = { dir: line.slice(9) };
    else if (line.startsWith("HEAD ") && cur) cur.head = line.slice(5, 12);
    else if (line.startsWith("branch ") && cur)
      cur.branch = line.slice(7).replace("refs/heads/", "");
    else if (line === "" && cur) {
      const m = cur.dir.match(/pi-agent-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{3})/);
      if (m) map.set(m[1], cur);
      cur = null;
    }
  }
  if (cur) {
    const m = cur.dir.match(/pi-agent-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{3})/);
    if (m) map.set(m[1], cur);
  }
  return map;
}

/** worktree 里已提交多少、还有多少没提交 */
function worktreeProgress(dir, baseRef) {
  try {
    const dirty = execSync("git status --porcelain --untracked-files=all", {
      cwd: dir,
      encoding: "utf8",
    })
      .split("\n")
      .filter(Boolean).length;
    let commits = 0;
    try {
      commits = Number(
        execSync(`git rev-list --count ${baseRef}..HEAD`, {
          cwd: dir,
          encoding: "utf8",
        }).trim(),
      );
    } catch {
      /* base 不可达时忽略 */
    }
    return { dirty, commits };
  } catch {
    return null;
  }
}

const argv = process.argv.slice(2);
const FULL = argv.includes("--full");
const IDLE_MIN = (() => {
  const i = argv.indexOf("--idle");
  return i >= 0 ? Number(argv[i + 1]) : 0;
})();

/**
 * 找 subagent 输出目录。
 *
 * os.tmpdir() 不可靠 —— 沙箱、定时任务、交互 shell 三种环境下 TMPDIR 可能不同，
 * 直接用会在部分调用方式下报「没找到目录」。多源搜，并允许环境变量覆盖。
 */
function candidateRoots() {
  const roots = new Set();
  if (process.env.PI_SUBAGENT_ROOT) roots.add(process.env.PI_SUBAGENT_ROOT);
  roots.add(os.tmpdir());
  for (const v of [process.env.TMPDIR, "/tmp"]) if (v) roots.add(v);
  // macOS 的 per-user 临时目录：/var/folders/xx/yyy/T
  for (const a of safeLs("/var/folders")) {
    for (const b of safeLs(path.join("/var/folders", a))) {
      const t = path.join("/var/folders", a, b, "T");
      if (fs.existsSync(t)) roots.add(t);
    }
  }
  return [...roots];
}

function findTaskDirs() {
  const bases = [];
  for (const root of candidateRoots()) {
    for (const d of safeLs(root)) {
      if (d.startsWith("pi-subagents-")) bases.push(path.join(root, d));
    }
  }
  const dirs = [];
  for (const b of bases) {
    for (const proj of safeLs(b)) {
      for (const sess of safeLs(path.join(b, proj))) {
        const t = path.join(b, proj, sess, "tasks");
        // 多个候选根（os.tmpdir()、TMPDIR、/var/folders 扫描）会解析到同一目录，
        // 不去重会让每个 agent 重复打印。按 realpath 去重。
        if (!fs.existsSync(t)) continue;
        let real = t;
        try {
          real = fs.realpathSync(t);
        } catch {
          /* 读不到就用原路径 */
        }
        if (!dirs.includes(real)) dirs.push(real);
      }
    }
  }
  return dirs;
}

function safeLs(p) {
  try {
    return fs.readdirSync(p);
  } catch {
    return [];
  }
}

function short(s, n) {
  if (!s) return "";
  const one = String(s).replace(/\s+/g, " ").trim();
  return one.length > n ? one.slice(0, n) + "…" : one;
}

function parse(file) {
  const lines = fs.readFileSync(file, "utf8").split("\n").filter(Boolean);
  const out = {
    id: path.basename(file, ".output"),
    lines: lines.length,
    model: null,
    cwd: null,
    lastTs: null,
    tools: {},
    files: new Set(),
    lastText: "",
    firstPrompt: "",
    tokens: 0,
    stopReason: null,
  };
  for (const l of lines) {
    let o;
    try {
      o = JSON.parse(l);
    } catch {
      continue;
    }
    const m = o.message;
    if (!m) continue;
    if (o.type === "user" && !out.firstPrompt) {
      const c = typeof m.content === "string" ? m.content : "";
      const head = c.split("\n").find((x) => x.trim().length > 8) || c;
      out.firstPrompt = short(head, 60);
    }
    if (o.type !== "assistant") continue;
    if (m.model) out.model = m.model;
    if (o.cwd) out.cwd = o.cwd;
    if (o.timestamp) out.lastTs = o.timestamp;
    if (m.stopReason) out.stopReason = m.stopReason;
    const u = m.usage || {};
    const t =
      (u.inputTokens || u.input_tokens || 0) +
      (u.outputTokens || u.output_tokens || 0);
    if (t > out.tokens) out.tokens = t;
    if (!Array.isArray(m.content)) continue;
    for (const b of m.content) {
      if (b.type === "toolCall") {
        out.tools[b.name] = (out.tools[b.name] || 0) + 1;
        const inp = b.input || b.args || {};
        const p = inp.path || inp.file_path || inp.filePath;
        if (p && typeof p === "string")
          out.files.add(p.replace(/^.*\/(rabbit[^/]*\/)?/, ""));
      } else if (b.type === "text" && b.text) {
        out.lastText = b.text;
      }
    }
  }
  return out;
}

const dirs = findTaskDirs();
if (!dirs.length) {
  console.log("没找到 subagent 输出目录");
  process.exit(0);
}

const WT = worktreeMap();
const now = Date.now();
const rows = [];
for (const d of dirs) {
  for (const f of safeLs(d)) {
    if (!f.endsWith(".output")) continue;
    const full = path.join(d, f);
    const st = fs.statSync(full);
    // 只看今天还在动的，历史会话跳过
    if (now - st.mtimeMs > 12 * 3600 * 1000) continue;
    const r = parse(full);
    r.mtime = st.mtimeMs;
    r.idle = Math.round((now - st.mtimeMs) / 1000);
    r.size = st.size;
    r.wt = WT.get(r.id) || null;
    // 只看本项目的 agent：要么有本仓的 worktree，要么 cwd 落在本仓内
    if (!r.wt && !(r.cwd || "").startsWith(ROOT)) continue;
    if (r.wt) r.progress = worktreeProgress(r.wt.dir, "main");
    rows.push(r);
  }
}

rows.sort((a, b) => b.mtime - a.mtime);
const shown = rows.filter((r) => r.idle >= IDLE_MIN);

if (!shown.length) {
  console.log(
    IDLE_MIN ? `没有静默超过 ${IDLE_MIN} 秒的 agent` : "没有活跃 agent",
  );
  process.exit(0);
}

const fmtIdle = (s) =>
  s < 90
    ? `${s}s`
    : s < 5400
      ? `${Math.round(s / 60)}m`
      : `${(s / 3600).toFixed(1)}h`;

console.log("");
for (const r of shown) {
  const topTools = Object.entries(r.tools)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([k, v]) => `${k}×${v}`)
    .join(" ");
  const totalTools = Object.values(r.tools).reduce((a, b) => a + b, 0);
  const state = r.idle > 600 ? "静默" : "活跃";

  console.log(`── ${r.id}  [${state} ${fmtIdle(r.idle)}]`);
  console.log(`   任务  ${r.firstPrompt}`);
  console.log(
    `   模型  ${r.model || "?"}    工具 ${totalTools} 次    消息 ${r.lines} 条`,
  );
  if (r.wt) {
    const p = r.progress;
    const prog = p
      ? `已提交 ${p.commits}    未提交 ${p.dirty} 个文件`
      : "状态不可读";
    console.log(
      `   分支  ${r.wt.head} ${r.wt.branch ? `(${r.wt.branch})` : "(detached)"}    ${prog}`,
    );
  } else {
    console.log("   分支  无独立 worktree（在主检出中作业）");
  }
  if (topTools) console.log(`   高频  ${topTools}`);
  if (r.files.size) {
    const fl = [...r.files].slice(0, 6).join("  ");
    console.log(
      `   触及  ${fl}${r.files.size > 6 ? `  (共 ${r.files.size})` : ""}`,
    );
  }
  if (r.stopReason) console.log(`   停因  ${r.stopReason}`);
  const txt = FULL ? r.lastText : short(r.lastText, 160);
  if (txt) console.log(`   近况  ${txt}`);
  console.log("");
}

console.log(
  `共 ${shown.length} 个 agent。--full 看完整输出，--idle <秒> 只看静默的。`,
);
