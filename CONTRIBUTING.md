# 贡献指南（Contributing）

本项目目标是“现场可用 + 可持续演进”。为了避免数据一致性、权限隔离、迁移回放等问题，请严格遵守本规范。

---

## 开发流程

### 分支策略

推荐：
- `main`：可随时部署的稳定分支
- `feat/<topic>`：新功能分支（例如 `feat/audit-export`）
- `fix/<topic>`：修复分支（例如 `fix/weaning-idempotent`）
- `chore/<topic>`：工程化/脚手架（例如 `chore/docker`）

合并方式：
- 建议使用 PR 合并到 `main`
- 小修复允许直接 push，但仍需遵循下面的提交与自测要求

---

## 提交信息规范（强制）

建议采用 Conventional Commits：

格式：

```text
<type>(<scope>): <subject>
```

推荐 type：
- `feat`：新功能
- `fix`：Bug 修复
- `refactor`：重构（不改变外部行为）
- `perf`：性能优化
- `docs`：文档
- `test`：测试
- `chore`：构建/脚本/工程化

推荐 scope（按模块）：
- `backend`
- `flutter`
- `admin`
- `db`
- `docker`
- `docs`
- `tools`

示例：
- `feat(backend): export audit logs csv`
- `fix(flutter): keep rabbit creation tied to cage context`
- `feat(admin): add merchant status filters`
- `chore(db): add flyway migration for indexes`

禁止：
- `update`
- `fix bug`
- 无意义/不包含范围的描述

---

## 自测要求（强制）

### 后端

- 至少保证构建通过：

```bash
cd backend
mvn -DskipTests package
```

若涉及 API 变更，建议用接口脚本回归：
- `tools/demo_flow.ps1`
- `tools/demo_flow_full.ps1`

### Flutter Android 客户端

```bash
cd flutter_app
./rabbit check
./rabbit apk dev --debug
```

若只改 UI，可至少运行 `./rabbit analyze`；若改 model、repository、provider 或业务逻辑，应运行 `./rabbit test`。

### 平台管理后台

```bash
pnpm --dir admin lint
pnpm --dir admin build
```

若改了登录、请求层、布局、弹窗、表格或响应式行为，需要在浏览器中做一次人工验证。

---

## 数据库变更规范（强制）

### 只允许通过 Flyway 迁移演进

- 任何表结构变更、索引变更，必须新增迁移文件到：
  - `backend/src/main/resources/db/migration/`
- 迁移文件必须可在“全新库”与“已有库”上执行

### 迁移内容约束

- 禁止在迁移里写入任何敏感信息（密码/密钥/token）
- 禁止依赖 MySQL 客户端交互式语法（例如 `DELIMITER` + 存储过程），以免 Flyway 执行不一致

---

## 权限与数据隔离（强制）

- 任何涉及业务数据的 API 必须校验权限（view/edit/control）
- 任何查询/写入必须确保 `house_id` 过滤正确
- 涉及批次从表且没有 `house_id` 的，需要 join 批次表按 `b.house_id` 过滤

---

## 幂等与可恢复（强制）

- 写接口尽量支持 `requestId` 幂等
- Android 写操作失败要考虑落入 PendingOps（可重试）

---

## 安全（强制）

- 禁止提交 `.env`、密钥、token、生产连接串
- 禁止在日志中打印 Authorization token / 密码
