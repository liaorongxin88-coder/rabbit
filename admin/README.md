# Rabbit Admin

Rabbit Admin 是 SaaS 平台管理控制台，面向平台管理员，不是商户业务后台。

## 当前范围

- 平台管理员登录和退出
- 商户列表、创建、编辑、启用/停用
- 绑定和解绑商户业务用户
- 只读查看商户概览、兔舍、笼位、兔只和近期审计记录

不在第一版范围内：计费套餐、商户自助注册、客服代运营、跨商户生产数据编辑。

## 技术栈

- React 19
- TypeScript
- Vite
- Tailwind CSS
- Radix UI
- shadcn-style 源码组件
- alova
- lucide-react
- sonner
- oxlint

## 本地运行

```bash
pnpm --dir admin install
pnpm --dir admin dev
```

默认开发代理访问 `http://127.0.0.1:8080` 的后端。需要指向其它后端时设置 `VITE_API_BASE_URL`。

## 登录

后端开发默认会 bootstrap 平台管理员：

- 用户名：`admin`
- 密码：`admin123456`

生产环境必须覆盖 `APP_ADMIN_BOOTSTRAP_USERNAME`、`APP_ADMIN_BOOTSTRAP_PASSWORD`，或关闭 bootstrap 后用安全流程创建账号。

## 目录

- `src/App.tsx`：路由、鉴权壳和会话级导航。
- `src/pages/`：页面级组件。
- `src/components/`：业务组件和布局组件。
- `src/components/ui/`：通用 UI primitive。
- `src/api/`：平台管理 API 方法。
- `src/lib/request.ts`：alova 实例、token 注入、响应解包和错误处理。
- `src/lib/auth.ts`：平台管理员会话存储。
- `src/types/`：API 类型。
- `src/index.css`：Tailwind、语义 token 和全局动效类。

## 规则

- 工程规则：`admin/.rules`
- 视觉和交互规则：`admin/DESIGN.md`
- Agent 兼容入口：`admin/AGENTS.md`、`admin/CLAUDE.md`

涉及 UI、布局、文案、动效、表格、弹窗或响应式改动时，先读 `DESIGN.md`。

## 验证

```bash
pnpm --dir admin lint
pnpm --dir admin build
```

涉及登录或请求层时，建议启动 dev server 后验证：

```bash
curl -s -H 'Content-Type: application/json' \
  -d '{"userName":"admin","password":"admin123456"}' \
  http://127.0.0.1:5173/api/admin/auth/login
```

更多验证说明见 [../docs/admin/README.md](../docs/admin/README.md) 和 [../docs/common/testing.md](../docs/common/testing.md)。
