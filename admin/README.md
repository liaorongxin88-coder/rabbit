# Rabbit Admin

Rabbit Admin 包含两个相互隔离的操作界面：面向平台管理员的平台管理端，以及 `/workspace/**` 下供业务用户使用的兔场工作台。两者共用视觉组件，但使用独立会话、请求客户端和权限边界。

## 当前范围

- 平台管理员登录和退出
- 兔场列表、创建、编辑、启用和停用
- 业务用户查询，以及脱敏手机号、账号状态和可访问兔场数量展示
- 用户与兔场的直接成员关系管理；一个用户可加入多个兔场，每个兔场至少保留一位启用的 OWNER，并允许多位共同 OWNER
- 平台侧只读查看兔场的笼位、兔只、生产、成员和近期审计摘要
- 业务用户登录和退出，并在 `/workspace/**` 选择有权访问的兔场
- 按当前兔场角色管理笼位、兔只、生产批次和兔场成员

手机号是业务用户的认证身份，不是兔场属性。手机号登录先解析到用户，再通过兔场成员关系授权；所有兔场范围业务请求都必须携带 `X-House-Id`，并由后端重新校验成员权限。

不在第一版范围内：计费套餐、公开自助注册、客服代运营、跨兔场生产数据编辑。

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

## 线上部署

Admin 前端按静态资源部署。脚本会先执行生产构建，再通过 SSH 和 rsync 将 `admin/dist/` 同步到服务器指定路径。

首次配置：

```bash
cp admin/.env.deploy.example admin/.env.deploy
```

编辑 `admin/.env.deploy`：

```bash
DEPLOY_HOST=your.server.example.com
DEPLOY_PATH=/var/www/rabbit-admin
DEPLOY_USER=root
DEPLOY_PORT=22
```

一键部署：

```bash
pnpm --dir admin deploy
```

也可以不写 `.env.deploy`，直接通过环境变量部署：

```bash
DEPLOY_HOST=your.server.example.com DEPLOY_PATH=/var/www/rabbit-admin pnpm --dir admin deploy
```

默认构建会使用同源 `/api`。如果 admin 静态站和后端 API 不在同一域名下，部署时设置：

```bash
DEPLOY_API_BASE_URL=https://api.example.com pnpm --dir admin deploy
```

当前线上配置为 `DEPLOY_API_BASE_URL=https://api.dzht.top`，管理端访问域名为 `https://admin.dzht.top`。后端跨域白名单只通过 `APP_CORS_ALLOWED_ORIGINS` 配置，默认包含 `https://admin.dzht.top`；`rabbit.host.dzht.top` 仅作为主机连接域名，不承载应用服务。

服务器 Nginx 静态目录应指向 `DEPLOY_PATH`，并为前端路由配置 `try_files $uri $uri/ /index.html;`。如果使用同源 `/api`，还需要在同一个 server block 中把 `/api/` 反向代理到后端服务。

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
- `src/api/`：平台管理和业务工作台 API 方法。
- `src/lib/request.ts`：隔离的平台/业务 alova 实例、token 注入、`X-House-Id` 注入、响应解包和错误处理。
- `src/lib/auth.ts`：相互隔离的平台管理员与业务用户会话存储，以及当前兔场选择。
- `src/lib/workspace.ts`：业务工作台上下文与当前兔场契约。
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
