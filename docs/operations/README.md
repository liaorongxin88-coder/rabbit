# 部署与发布

本目录描述本地 Compose、运行时配置、CI/CD、发布制品、生产部署和回滚边界。

- [deployment.md](deployment.md)：Compose、环境变量、Flyway、健康检查和日常运维。
- [ci-cd.md](ci-cd.md)：GitHub Actions、SemVer、GHCR、制品、生产审批和部署证据。
- [ADR 0001](../adr/0001-ci-cd-and-service-delivery.md)：CI/CD 与服务交付边界的决策记录。

发布结论必须区分质量门禁、制品构建、远端容器切换、数据库迁移、公开探测和客户端安装。任何一层的成功都不能替代其它层的证据。
