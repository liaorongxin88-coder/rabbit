# 部署和运维

自动构建、SemVer 发布、GHCR 镜像和生产环境审批见 [CI/CD 与发布](ci-cd.md)。本文件负责 Compose、运行时配置、数据库迁移和日常检查。

## 本地 Compose

根 `docker-compose.yml` 默认启动 MySQL 和 backend。Redis 与 Valkey 是可选 profile：

```bash
cp .env.example .env
# 替换全部 change-me 占位值
docker compose up -d --build
```

默认网络边界：

- backend 绑定到 `127.0.0.1:8080`，可用 `BACKEND_BIND_ADDRESS` 调整。
- MySQL 只暴露在 Compose 网络中，不映射宿主机端口。
- Redis 和 Valkey 只有启用对应 profile 时才启动，宿主机端口默认只绑定回环地址。
- MySQL 数据保存在 `rabbit_mysql_data` volume。

只更新 backend 并保留数据库：

```bash
docker compose up -d --build --no-deps backend
```

生产发布启用自动部署后，CI 使用 [compose.backend-image.yml](../../deploy/compose.backend-image.yml) 覆盖 backend 镜像，并以已经验证的 digest 执行 `--no-build` 激活。生产主机不会重新构建镜像。

## 配置来源

本地配置模板是根目录 `.env.example`，后端默认值和映射位于 `backend/rabbit-boot/src/main/resources/application.yml` 与 `docker-compose.yml`。文档不复制完整变量清单，新增配置时要同步这三个位置。

部署至少要提供：

- 数据库连接信息。
- 彼此不同的 `APP_JWT_SECRET` 和 `APP_ADMIN_JWT_SECRET`。
- 独立稳定的 `APP_PHONE_HASH_SECRET`。
- NFC 活跃 key id 和签名 key 集合。
- 生产环境的管理员 bootstrap 策略。

启用短信或运营商一键登录时，还要配置各自的开关、摘要密钥、限流参数和独立的阿里云凭证。短信认证依赖 Redis 或 Valkey，具体见 [短信登录配置](../backend/sms-auth.md) 和 [缓存设计](../backend/cache.md)。

不要提交 `.env`、签名文件、私钥、token 或生产连接串。稳定摘要密钥不能在普通重启中变化，否则现有身份或有效期内记录可能失效。

## 数据库迁移

- Flyway 迁移目录：`backend/rabbit-boot/src/main/resources/db/migration/`。
- backend 启动时自动执行迁移。
- `backend/rabbit-boot/src/main/resources/db/schema.sql` 只用于结构参考。
- `backend/rabbit-boot/src/main/resources/db/seed_demo.sql` 只用于演示或排障，不应直接导入生产库。

每次结构或索引变更都要新增迁移。迁移必须能在全新数据库和已有数据库上执行，且不能包含密码、token 或生产连接信息。大表回填、约束收紧和不可逆清理需要单独的备份、停写、抽检和失败处置方案。

## 运行检查

先检查 Compose 服务、日志和镜像：

```bash
docker compose ps
docker compose images
docker compose logs --tail=200 backend
```

再检查 HTTP 可达性：

```bash
curl -i http://127.0.0.1:8080/api/houses
```

未登录请求可能以 HTTP `200` 加业务 `code=401` 返回。如果后续改为标准 HTTP 状态，也可能是 HTTP `401` 加业务 `code=401`。探测脚本要同时检查 HTTP 层和业务响应，不能只匹配一个状态码。

缓存启用时还要在对应容器内执行 `PING`。生产部署需要核对实际 backend 容器 ID、镜像 ID 或 digest、重启次数、Flyway 版本和公开 HTTPS 探测结果。

## 备份和恢复

- 发布前使用一致性方式备份 MySQL，并校验备份文件可读。
- 记录备份路径、旧容器、旧镜像引用和目标镜像 digest。
- 数据库恢复会覆盖部署后的写入，必须单独审批并在恢复前确认影响范围。
- Flyway 迁移不向后兼容时，不能只回滚 backend 镜像，应发布前向修复或执行经过验证的数据库恢复流程。

生产部署和回滚的自动化顺序见 [CI/CD 与发布](ci-cd.md)。

## 时间和安全

- 后端、数据库和容器统一使用 `Asia/Shanghai`，避免提醒和到期日跨时区偏移。
- 反向代理只有在覆盖客户端转发头且 backend 不可直达时，才启用 `APP_FORWARD_HEADERS_STRATEGY=framework`。
- 生产环境要关闭或替换默认管理员 bootstrap 凭据。
- 日志、截图、测试报告和 Actions artifact 都不能暴露密钥或认证 token。
