# Backend 文档

后端是 Rabbit 的业务事实来源，负责 REST API、权限隔离、Flyway 迁移、审计、提醒扫描、平台管理 API 和硬件网关适配。

## 先读

- 源码入口：[../../backend/README.md](../../backend/README.md)
- 公共架构：[../common/architecture.md](../common/architecture.md)
- 本地开发：[../common/development.md](../common/development.md)
- 测试验证：[../common/testing.md](../common/testing.md)
- 运维部署：[../common/operations.md](../common/operations.md)

## 模块索引

- [modules/domain-modules.md](modules/domain-modules.md)：业务模块职责和源码落点。
- [modules/api-and-permissions.md](modules/api-and-permissions.md)：普通业务 API、平台 API、鉴权和兔舍权限。
- [modules/data-and-migrations.md](modules/data-and-migrations.md)：数据库迁移、schema 参考、种子数据和数据隔离。

## 常用命令

```bash
mvn --file backend/pom.xml -DskipTests package
mvn --file backend/pom.xml -Pe2e verify
docker compose up -d --build --no-deps backend
```

数据库结构变更必须新增 Flyway 迁移，不要直接修改 `schema.sql` 作为唯一变更。
