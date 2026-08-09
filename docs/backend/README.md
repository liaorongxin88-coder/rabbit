# 后端文档

后端是 Rabbit 的业务事实来源，负责 REST API、兔场数据隔离、权限、Flyway 迁移、审计、
提醒扫描、平台管理 API 和硬件网关适配。

## 源码入口

- 应用入口：`../../backend/src/main/java/com/rabbit/app/RabbitApplication.java`
- 业务模块：`../../backend/src/main/java/com/rabbit/app/modules/`
- 配置：`../../backend/src/main/resources/application.yml`
- Mapper XML：`../../backend/src/main/resources/mapper/`
- Flyway：`../../backend/src/main/resources/db/migration/`

## 先读

- [modules/domain-modules.md](modules/domain-modules.md)：模块职责和业务边界。
- [modules/api-and-permissions.md](modules/api-and-permissions.md)：普通业务 API、平台 API、鉴权和兔场权限。
- [modules/direct-house-access.md](modules/direct-house-access.md)：全局账号、直接兔场成员关系、状态和共同所有者。
- [modules/farming-workspaces.md](modules/farming-workspaces.md)：多养殖业务工作空间、复用边界和模块化演进路线。
- [modules/data-and-migrations.md](modules/data-and-migrations.md)：Flyway、结构参考和 `house_id` 隔离。
- [modules/auth-phone-wechat.md](modules/auth-phone-wechat.md)：手机号短信、一键登录后端换号、零兔场状态，以及尚未实现的微信绑定契约。
- [sms-auth.md](sms-auth.md)：阿里云短信参数、手机号验证码登录和本地启用方式。

## 验证

```bash
mvn --file backend/pom.xml test
mvn --file backend/pom.xml -Pe2e verify
```

涉及迁移时还应在全新数据库和已有历史数据库上分别验证。涉及权限时至少覆盖零兔场账号、
共同 `OWNER`、成员停用、兔场停用和跨兔场越权。
