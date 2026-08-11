# Admin 文档

Admin 包含相互隔离的平台管理端和业务工作台，源码位于 `admin/`。平台管理端面向平台管理员，
业务工作台面向拥有兔场成员关系的普通业务账号。

## 先读

- 源码入口：[../../admin/README.md](../../admin/README.md)
- 工程规则：`../../admin/.rules`
- 视觉规则：`../../admin/DESIGN.md`
- 架构概览：[../common/architecture.md](../common/architecture.md)
- 测试验证：[../common/testing.md](../common/testing.md)

## 模块索引

- [modules/platform-admin.md](modules/platform-admin.md)：平台账号、兔场、业务用户、工作台和请求边界。

## 常用命令

```bash
pnpm --dir admin lint
pnpm --dir admin build
pnpm --dir admin dev
```

开发默认平台管理员账号为 `admin / admin123456`，生产环境必须覆盖或关闭 bootstrap。
