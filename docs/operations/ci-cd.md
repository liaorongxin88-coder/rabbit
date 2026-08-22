# CI/CD 与发布

## 交付模型

Rabbit 使用 GitHub Actions 执行质量门禁和制品发布。后端当前仍是模块化单体，发布单位是
一个后端镜像；可复用镜像工作流允许以后按真实服务边界增加镜像，不要求现在拆分代码。

| 触发方式 | 执行内容 | 产物或外部影响 |
| --- | --- | --- |
| Pull Request | 后端单测与 E2E、Admin、Flutter、工作流和文档检查、后端容器探测 | 无外部发布 |
| 推送 `main` | 与 PR 相同，并上传 QA Admin 包和测试环境 APK | Actions 临时制品，保留 14 天 |
| 推送注解标签 `vMAJOR.MINOR.PATCH` | 重跑质量门禁，构建并签名发布制品 | GHCR 镜像、GitHub Release、provenance |
| 发布完成且 `BACKEND_AUTO_DEPLOY=true` | 通过 `production` environment 审批后部署镜像 digest | 生产后端切换与部署证据 |

QA APK 使用 debug 签名，只能用于测试设备。正式 AAB 只有在启用 App 发布并提供完整签名
凭据后才会生成。

## 工作流

- [../../.github/workflows/ci.yml](../../.github/workflows/ci.yml)：PR 和 `main` 入口。
- [../../.github/workflows/release.yml](../../.github/workflows/release.yml)：SemVer 发布与可选生产部署。
- [../../.github/workflows/_quality-gates.yml](../../.github/workflows/_quality-gates.yml)：三端质量门禁。
- [../../.github/workflows/_build-service-image.yml](../../.github/workflows/_build-service-image.yml)：服务镜像、SBOM 和 provenance。

`main` 的分支保护至少应要求这些检查通过：

- Workflow and documentation lint
- Backend unit tests
- Backend MySQL and Valkey integration tests
- Admin lint, tests, and build
- Flutter checks and QA APK
- Backend container smoke

## 发布前配置

### 仓库变量

| 变量 | 用途 | 默认行为 |
| --- | --- | --- |
| `ADMIN_PROD_API_BASE_URL` | Admin 构建时的 API 地址 | 为空时使用前端同源 `/api` |
| `APP_RELEASE_ENABLED` | 是否构建正式签名 AAB | 未设为 `true` 时跳过 |
| `APP_PROD_API_BASE_URL` | App 正式 API 地址 | 启用 App 发布后必填，必须是 HTTPS |
| `APP_BUILD_NUMBER_OFFSET` | GitHub run number 的版本号偏移 | 默认 `4003` |
| `BACKEND_AUTO_DEPLOY` | 是否在标签发布后进入生产部署 | 未设为 `true` 时不连接生产 |

`BACKEND_AUTO_DEPLOY` 必须是仓库级变量，因为工作流要在进入 `production` environment 前
判断是否创建部署任务。

### production environment 变量

| 变量 | 用途 | 示例 |
| --- | --- | --- |
| `DEPLOY_HOST` | SSH 主机 | `rabbit.host.dzht.top` |
| `DEPLOY_USER` | SSH 用户 | `root`，后续应迁移到专用用户 |
| `DEPLOY_PORT` | SSH 端口 | `22` |
| `DEPLOY_PATH` | 远端 Compose 目录 | `/opt/rabbit` |
| `DEPLOY_BACKUP_PATH` | MySQL 备份目录 | `/var/backups/rabbit` |
| `BACKEND_PUBLIC_URL` | 部署后的 HTTPS 探测地址 | `https://rabbit.host.dzht.top` |
| `BACKEND_ROLLBACK_MODE` | 失败处理 | 默认 `manual`，确认迁移兼容后可设 `code` |

### production environment 密钥

| 密钥 | 用途 |
| --- | --- |
| `DEPLOY_SSH_KEY` | 部署账号私钥 |
| `DEPLOY_KNOWN_HOSTS` | 已人工核对指纹的完整 known_hosts 行 |
| `DEPLOY_GHCR_USERNAME` | 远端拉取私有 GHCR 镜像的账号 |
| `DEPLOY_GHCR_TOKEN` | 仅有 `read:packages` 权限的 GHCR token |
| `RABBIT_ANDROID_KEYSTORE_BASE64` | 正式 Android keystore 的 Base64 内容 |
| `RABBIT_ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `RABBIT_ANDROID_KEY_ALIAS` | 正式签名 alias |
| `RABBIT_ANDROID_KEY_PASSWORD` | 正式签名 key 密码 |

生产环境应配置 required reviewers。`DEPLOY_KNOWN_HOSTS` 必须通过独立渠道核对主机指纹，
不能把一次未经验证的 `ssh-keyscan` 输出直接当作信任依据。

## 发布步骤

1. 确认目标提交已经进入 `origin/main`，并且 `main` CI 通过。
2. 选择未使用的 SemVer，创建指向该提交的注解标签。
3. 推送标签。发布工作流会再次验证标签类型、目标 SHA 和 `origin/main` 祖先关系。
4. 工作流构建 GHCR 多架构镜像，并记录源码 SHA、镜像 digest、SBOM 和 provenance。
5. Admin 包与可选 AAB 写入 GitHub Release。部署任务只消费已发布的镜像 digest。

如果 GHCR 已存在同名 SemVer 镜像，工作流会失败，不会覆盖。失败后的修复版本必须使用新的
SemVer，不能通过重跑改写已经发布的镜像。

示例：

```bash
git tag -a v1.0.0 <main-commit-sha> -m "release: v1.0.0"
git push origin v1.0.0
```

标签推送是生产发布授权。推送前必须确认版本唯一、提交正确且迁移兼容。

## 后端部署和证据

部署脚本要求远端已经存在受运维管理的 `docker-compose.yml` 和 `.env`。CI 只上传镜像覆盖
文件和激活脚本，不替换生产环境变量或基础设施配置。生效的覆盖文件会保存在
`DEPLOY_PATH/.deploy/compose.backend-image.yml`，供后续部署和人工巡检复用。

激活顺序：

1. 验证 Compose、MySQL 健康和目标镜像 digest。
2. 使用 `mysqldump --single-transaction` 创建压缩备份并校验文件。
3. 记录旧容器、旧镜像引用、旧镜像 ID、源码 SHA 和备份路径。
4. 拉取目标 digest，仅重建 backend 服务。
5. 轮询容器健康，验证实际运行镜像、Flyway 版本、Valkey/Redis 和公开未登录探测。
6. 将证据保存为 Actions artifact，保留 90 天。

工作流成功只证明这次 GitHub 运行。生产交付报告还应引用实际部署 artifact，并核对远端
容器和镜像 ID，不能用本地容器或单次公共 HTTP 成功代替。

## 回滚与前向修复

默认 `BACKEND_ROLLBACK_MODE=manual`。部署失败时，脚本保留数据库备份、旧镜像和失败现场，
由值班人员先判断本次 Flyway 迁移是否向后兼容：

- 兼容：可把 `BACKEND_ROLLBACK_MODE` 临时设为 `code`，或手动用记录的旧镜像恢复 backend。
- 不兼容：不得只回滚代码，应修复迁移或发布前向修复版本；必要时按已验证流程恢复数据库。

数据库恢复会覆盖部署后的写入，必须单独审批。流水线不会自动执行数据库恢复。

## 增加微服务

出现真实拆分条件后，每个新服务应完成以下内容：

1. 独立 Dockerfile、构建上下文和健康检查。
2. 明确 API、鉴权、租户上下文、数据所有权和迁移负责人。
3. 在发布工作流中调用 `_build-service-image.yml`，使用独立 GHCR 镜像名。
4. 增加该服务的容器探测、部署适配器、备份或状态迁移方案。
5. 生产激活继续使用 digest，并保存每个服务的独立部署证据。

不要让多个“微服务”继续直接写同一组业务表，也不要在没有故障隔离或独立发布需求时复制
当前单体进程。

## 本地复现

```bash
# 快速质量门禁
./scripts/ci/check.sh

# 需要本地 MySQL 和 Valkey 的后端 E2E
./scripts/ci/backend-e2e.sh

# 构建后端镜像、Admin 和测试 APK
./scripts/ci/build.sh

# 工作流、Compose、脚本和文档预检
./scripts/ci/release-preflight.sh
```

项目 harness 也可以运行相同入口：

```bash
node /absolute/path/to/project-harness.js check .
node /absolute/path/to/project-harness.js gates . --profile full
node /absolute/path/to/project-harness.js evidence . --profile release
```
