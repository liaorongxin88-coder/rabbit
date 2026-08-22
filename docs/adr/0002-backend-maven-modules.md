# ADR 0002: 后端 Maven 模块边界

- 状态：接受
- 日期：2026-08-23

## 背景

后端原先是单个 Maven 工程、单个 Spring Boot 进程和单个 MySQL 数据库。业务包之间存在大量事务内协作：出库会同时修改销售、兔只、批次、笼位、任务和历史，繁殖、批次和兔只也直接共享状态机及 Mapper。按业务包直接拆成进程会立即引入分布式事务、跨服务权限和数据同步问题。

当前需要先建立可执行的源码所有权和编译边界，同时保持现有部署、HTTP 契约、数据库事务和客户端地址不变。

## 决策

1. `backend/` 保持一个 Maven reactor，并拆为五个子模块：
   - `rabbit-platform`：通用响应、缓存、工具和请求幂等。
   - `rabbit-access`：认证、兔场、工作空间和业务权限。
   - `rabbit-production`：生产、现场记录、库存、销售、设置、文件和硬件适配。
   - `rabbit-reporting`：报表、审计和平台管理。
   - `rabbit-boot`：启动类、运行配置、Flyway 和整体验证测试。
2. 依赖方向固定为：

   ```text
   rabbit-boot -> rabbit-reporting -> rabbit-production -> rabbit-access -> rabbit-platform
        |                 |                  |                 |
        +-----------------+------------------+-----------------+
   ```

   `rabbit-boot` 直接组装四个库模块。`rabbit-reporting` 可以读取 production 和 access，production 可以调用 access，低层模块不能反向依赖高层模块。
3. 只有 `rabbit-boot` 使用 Spring Boot Maven 插件生成可执行 JAR。部署仍只有一个进程和一个镜像，客户端继续访问原有 `/api/**`。
4. 所有 Flyway 迁移、`schema.sql` 和 `seed_demo.sql` 集中在 `rabbit-boot`。现有 V1 至 V36 迁移只移动目录，不拆分、不改写。
5. Mapper XML 跟随所属 Maven 模块发布，MyBatis 继续使用 `classpath*:mapper/**/*.xml` 聚合加载。Java 包名和 Mapper namespace 保持不变。
6. 建场流程通过 `house.spi.HouseInitializer` 调用 production 初始化器。当前初始化器仍在同一数据库事务内复制兔场设置并批量建笼，不引入消息或远程调用。
7. 笼位和 NFC 查询路由由 production 控制器承接，URL 与请求契约不变。平台管理员守卫归 reporting，密钥校验归 access，消除低层模块对高层模块的反向编译依赖。
8. ArchUnit 检查 Maven 模块对应的包依赖方向。生产域内部的 `rabbit`、`batch`、`repro`、`cage`、`outbound` 和 `sale` 仍允许直接协作，后续按稳定 API 或 SPI 逐步收窄。

## 未采用的方案

- 立即拆成微服务：现有事务和状态机无法在不增加 Saga、Outbox、幂等消费及补偿机制的情况下跨进程运行。
- 每个业务包一个 Maven 模块：`rabbit`、`batch` 和 `repro` 存在双向 Mapper、Entity 和 Service 引用，会形成 Maven 循环依赖。
- 拆成多个 Git 仓库：当前客户端、OpenAPI、迁移、镜像和部署仍按一个发布单元演进，拆仓库会增加合同同步成本。
- 在多个模块中分发 Flyway 版本：单数据库下容易造成版本冲突和迁移顺序不清，且无法表达现有跨域外键变更。

## 结果

根命令 `mvn --file backend/pom.xml test` 和 `mvn --file backend/pom.xml -Pe2e verify` 保持有效。Docker 仍以 `backend/` 为构建上下文，但只复制 `rabbit-boot/target/rabbit-backend.jar`。

这次拆分只建立进程内模块边界，不表示服务已经独立。后续先引入事件合同、事务 Outbox、幂等消费者和查询投影，再评估抽取 reporting 或 access。只有出现独立发布、扩容、故障隔离或团队所有权需求时，才拆成独立进程或 Git 仓库。
