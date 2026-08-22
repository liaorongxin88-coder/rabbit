# 后端文档

`backend/` 是 Rabbit 的业务事实来源。它负责业务 API、兔场隔离、权限校验、生产流程、审计、提醒、平台管理 API、数据库迁移和外部能力适配。

## 阅读顺序

1. [业务模块](modules/domain-modules.md)：模块职责和边界。
2. [API 和权限](modules/api-and-permissions.md)：业务 API、平台 API、鉴权和幂等。
3. [账号与兔场权限模型](modules/direct-house-access.md)：账号、成员、角色和共同所有者。
4. [数据库和迁移](modules/data-and-migrations.md)：Flyway、结构约束和兔场隔离。
5. 根据任务继续阅读认证、缓存、工作空间或数据结构专题。

## 模块与专题

- [手机号认证与后续适配](modules/auth-phone-wechat.md)
- [短信登录配置](sms-auth.md)
- [缓存设计](cache.md)
- [多养殖业务工作空间](modules/farming-workspaces.md)
- [数据结构资料](data/README.md)

跨端的批量出库和母兔生产流程资料位于 [features/](../features/README.md)。这些专题说明需求和实施背景，当前数据库结构仍以 Flyway 迁移为准。

## 源码入口

- 应用入口：`../../backend/rabbit-boot/src/main/java/com/rabbit/app/RabbitBackendApplication.java`
- 业务模块：`../../backend/rabbit-access/`、`../../backend/rabbit-production/`、`../../backend/rabbit-reporting/`
- 配置：`../../backend/rabbit-boot/src/main/resources/application.yml`
- Mapper XML：各业务模块的 `src/main/resources/mapper/`
- Flyway：`../../backend/rabbit-boot/src/main/resources/db/migration/`
- Checkstyle：`../../backend/config/checkstyle/checkstyle.xml`

后端是 Spring Boot 3.5、Java 21、MyBatis、MySQL 和 Flyway 组成的模块化单体。模块内默认依赖方向是 `controller -> service -> mapper`，架构测试负责约束跨层和跨模块依赖。

## 验证

```bash
mvn --file backend/pom.xml checkstyle:check
mvn --file backend/pom.xml test
mvn --file backend/pom.xml -Pe2e verify
```

迁移改动需要同时验证全新数据库和已有数据库。权限改动至少覆盖零兔场账号、共同 `OWNER`、成员停用、兔场停用和跨兔场访问。完整测试入口见 [项目测试文档](../project/testing.md)。
