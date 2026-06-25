# Admin 文档

Admin 是 Rabbit SaaS 平台管理控制台，源码位于 `admin/`。

## 先读

- 源码入口：[../../admin/README.md](../../admin/README.md)
- 工程规则：`../../admin/.rules`
- 视觉规则：`../../admin/DESIGN.md`
- 架构概览：[../common/architecture.md](../common/architecture.md)
- 测试验证：[../common/testing.md](../common/testing.md)

## 模块索引

- [modules/platform-admin.md](modules/platform-admin.md)：平台管理员、商户管理、只读业务边界和请求规则。

## 常用命令

```bash
pnpm --dir admin lint
pnpm --dir admin build
pnpm --dir admin dev
```

开发默认平台管理员账号为 `admin / admin123456`，生产环境必须覆盖或关闭 bootstrap。
