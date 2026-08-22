# Admin 文档

`admin/` 是 React 管理端，包含两个隔离的操作界面：平台管理端使用平台管理员身份，`/workspace/**` 业务工作台使用普通业务账号和所选兔场上下文。

## 先读

- 工程规则：`../../admin/.rules`
- 视觉和交互规则：[../../admin/DESIGN.md](../../admin/DESIGN.md)
- 源码说明：[../../admin/README.md](../../admin/README.md)
- 总体架构：[../project/architecture.md](../project/architecture.md)
- 测试与浏览器验收：[../project/testing.md](../project/testing.md)

## 身份与请求边界

平台端使用 `/api/admin/**`、独立 JWT 和独立会话，不发送 `X-House-Id`。业务工作台使用 `/api/**`、普通用户 JWT，并为兔场范围请求发送当前 `X-House-Id`。两个界面的 token、路由守卫、请求客户端和退出流程不能混用。

平台端负责账号、兔场、成员关系和只读业务概览。业务生产写入只能通过拥有对应兔场权限的业务账号完成。

## 模块资料

- [平台管理端与业务工作台](modules/platform-admin.md)

路由树以 `admin/src/App.tsx` 为准，请求边界以 `admin/src/lib/request.ts` 为准，长期视觉规则以 `admin/DESIGN.md` 为准。

## 常用命令

```bash
pnpm --dir admin lint
pnpm --dir admin test
pnpm --dir admin build
pnpm --dir admin dev
```

可见界面改动还要在桌面和窄屏视口检查控制台错误、横向溢出、文字遮挡、弹窗位置和操作可达性。
