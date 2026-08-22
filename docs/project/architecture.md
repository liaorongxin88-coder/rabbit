# 总体架构

## 系统组成

Rabbit 由一个后端和两个客户端组成：

- `backend/`：Spring Boot 模块化单体，负责业务规则、权限、数据持久化、审计、提醒和平台管理 API。
- `app/`：Flutter Android 客户端，面向兔场业务用户和现场操作。
- `admin/`：React Web 应用，包含平台管理端和业务工作台。

仓库根目录还包含 Compose、CI、部署脚本和项目文档。`rabbit-master/` 是独立的历史或参考工程，不属于当前三个维护子项目的运行链路。

## 运行时数据流

```text
Flutter App ───────────────┐
                          ├─ 普通业务 API /api/** ──┐
Admin 业务工作台 ─────────┘                         │
                                                    ├─ Backend ── MySQL
Admin 平台管理端 ── 平台 API /api/admin/** ─────────┘      └─ Redis/Valkey（按配置启用）
```

普通业务 API 使用业务用户 JWT。兔场范围请求还要携带 `X-House-Id`，后端根据账号、兔场、成员状态、角色和权限重新授权。平台 API 使用独立的平台管理员 JWT，不进入兔场上下文。

客户端可以根据权限隐藏入口，但不能替代后端授权。所有业务数据查询和写入都必须在服务端完成兔场隔离。

## 身份和兔场隔离

Rabbit 使用“账号直接加入兔场”的模型：

```text
sys_user
  -> house_users
       -> rabbit_houses
            -> cages / rabbits / batches / repro / records
```

- `sys_user` 是业务用户身份和账号状态的来源。
- `house_users` 是账号与兔场之间的授权关系，保存角色、成员状态和权限来源。
- `rabbit_houses` 是业务数据隔离边界。产品文案使用“兔场”，兼容表名、接口字段和请求头仍使用 `house`。
- 同一账号可以加入多个兔场，同一兔场可以有多位共同 `OWNER`。
- 账号、兔场和成员关系都有效时，后端才继续检查角色和动作权限。

手机号认证、密码登录和运营商一键登录最终都解析为同一个业务账号。首次完成可信手机号认证的账号可以暂时没有兔场，此时会获得正式会话，但只能执行账号级操作、创建兔场或等待邀请生效。

## 后端边界

后端是 `backend/` 下的 Maven 多模块工程，由 `rabbit-boot` 组装成一个 Spring Boot 进程：

- `rabbit-platform`：通用响应、缓存、工具和请求幂等。
- `rabbit-access`：`auth`、`house`、`workspace` 和业务权限。
- `rabbit-production`：`cage`、`rabbit`、`batch`、`repro`、`event`、`outbound`、现场记录、库存、销售、设置、文件与硬件适配。
- `rabbit-reporting`：`report`、`audit` 和平台管理。
- `rabbit-boot`：启动类、运行配置、Flyway、MyBatis 装配和整体验证测试。

模块内默认依赖方向是：

```text
controller -> service -> mapper -> MySQL
```

Controller 处理 HTTP 契约和认证上下文，Service 负责业务规则和事务，Mapper 负责持久化。共享基础设施不能反向依赖具体业务模块。架构测试负责检查分层和跨模块依赖。

数据库由 Flyway 演进。`backend/rabbit-boot/src/main/resources/db/migration/` 是结构变更的权威来源，`schema.sql` 和 `docs/backend/data/` 只用于阅读和核对。

## Flutter App 边界

Flutter 源码按 `config`、`data`、`domain`、`routing` 和 `ui` 分层：

```text
UI -> repository -> service/network -> Backend
        │
        └─ domain models
```

路由集中在 `app/lib/src/routing/routes.dart`。会话、当前兔场、环境配置和页面异步状态分别由专属 service、repository 和 Riverpod controller 管理。页面不能直接创建网络客户端，也不能自行信任本地保存的兔场选择。

移动端工程规则见 [App 文档](../app/README.md) 和 `app/.rule`。

## Admin 边界

Admin 的两套身份共用组件和视觉系统，但不共用会话或请求上下文：

- 平台管理端路由负责平台账号、业务用户、兔场和只读概览。
- `/workspace/**` 路由面向业务用户，并使用当前可访问兔场作为工作上下文。

路由树在 `admin/src/App.tsx`，平台和业务请求客户端在 `admin/src/lib/request.ts`，会话隔离在 `admin/src/lib/auth.ts`。工程与交互边界见 [Admin 文档](../admin/README.md)、`admin/.rules` 和 `admin/DESIGN.md`。

## 跨端契约

以下规则需要后端和客户端一起维护：

- 普通业务 JWT 与平台管理员 JWT 分离。
- 兔场范围请求必须携带并校验 `X-House-Id`。
- 后端统一响应中 `code != 0` 属于业务失败。
- 写请求通过 `requestId` 和服务端去重逻辑防止重复提交。
- 数据库结构变化先增加 Flyway 迁移，再调整 Mapper、服务和客户端契约。
- 权限入口、错误码、状态机和字段语义变化需要同步回归后端与实际消费者。

跨子项目业务规格见 [features/](../features/README.md)。

## 本地和交付边界

根 `docker-compose.yml` 默认启动 MySQL 和 backend，Redis 与 Valkey 通过 profile 选择启用。Admin 开发服务器和 Flutter App 在 Compose 外运行，通过 HTTP 访问后端。

CI、镜像、生产部署和回滚由 [operations/](../operations/README.md) 记录。单元测试、构建成功、本地容器、真机安装和生产部署是不同证据层级，交付报告必须分别说明。
