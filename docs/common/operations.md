# 部署和运维

## Docker 部署

仓库根目录提供 `docker-compose.yml`，包含 MySQL 和 backend：

```bash
docker compose up -d --build
```

服务：

- backend 镜像：`rabbit-backend:java21`
- backend 端口：`8080`
- MySQL 端口：`3306`
- MySQL volume：`rabbit_mysql_data`

只更新后端容器并保留 MySQL：

```bash
docker compose up -d --build --no-deps backend
```

## 关键环境变量

后端常用配置：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_JWT_SECRET`
- `APP_ADMIN_JWT_SECRET`
- `APP_ADMIN_BOOTSTRAP_ENABLED`
- `APP_ADMIN_BOOTSTRAP_USERNAME`
- `APP_ADMIN_BOOTSTRAP_PASSWORD`
- `APP_PHONE_HASH_SECRET`
- `APP_SMS_ENABLED`
- `APP_SMS_CODE_SECRET`
- `ALIBABA_CLOUD_ACCESS_KEY_ID`
- `ALIBABA_CLOUD_ACCESS_KEY_SECRET`
- `APP_SMS_SIGN_NAME`
- `APP_SMS_TEMPLATE_CODE`
- `APP_SMS_TEMPLATE_PARAM_NAME`
- `JAVA_OPTS`

生产必须替换所有默认 secret 和默认管理员密码。开启短信登录前还必须配置独立
RAM AccessKey、审核通过的短信签名和验证码模板，具体见
[../backend/sms-auth.md](../backend/sms-auth.md)。

## 数据库迁移

- 迁移脚本目录：`backend/src/main/resources/db/migration/`
- 后端启动时由 Flyway 自动执行。
- `backend/src/main/resources/db/schema.sql` 是结构参考，不是常规部署入口。
- `backend/src/main/resources/db/seed_demo.sql` 只用于演示或排障，不应导入生产库。

数据库变更要求：

- 新增、修改表结构或索引必须新增 Flyway 迁移。
- 迁移必须能在全新库和已有库上回放。
- 迁移不能写入密码、token 或生产连接信息。

## 健康检查

容器级检查：

```bash
docker ps
docker inspect rabbit-backend
docker logs rabbit-backend
```

HTTP 探测：

```bash
curl -i http://localhost:8080/api/houses
```

未登录环境下返回 `401` 属于服务可达的正常结果。

## 安全和备份

- 定期备份 MySQL，并演练恢复。
- 不在日志、截图或提交中暴露 JWT、数据库密码、硬件 token。
- 统一使用 `Asia/Shanghai`，避免提醒 due 计算跨时区。
- 平台管理员 bootstrap 只适合开发或首次初始化，生产环境应关闭或覆盖默认密码。

## 升级注意事项

- 后端升级前确认 Flyway 迁移可执行。
- 索引、约束或大表迁移建议低峰执行。
- Flutter 或 Admin 客户端升级前确认后端 API 契约未破坏。
- 如果 Docker Compose 在本机环境表现异常，可分别用 `docker ps`、`docker inspect`、`docker logs` 和 live HTTP probe 判断真实状态。
