# 架构概览

## 系统边界

Rabbit 当前由三块主要应用组成：

- `backend/`：所有业务数据、权限、审计、提醒、迁移和平台管理 API 的来源。
- `flutter_app/`：当前移动端重构方向，面向兔场业务用户。
- `admin/`：平台运营控制台，面向 SaaS 平台管理员。

`tools/` 只放演示和回归脚本，`docs/` 只放当前维护文档。

## 后端

后端使用 Spring Boot + MyBatis + MySQL。源码按业务模块放在 `backend/src/main/java/com/rabbit/app/modules/`：

- `auth`：普通业务用户注册、登录、微信登录兼容。
- `house`：兔舍、成员和权限。
- `cage`：笼位维护和笼位概览。
- `rabbit`：兔只录入、编辑、状态、异常、离场和后备。
- `batch`：催情、配种、摸胎、备产、分娩、断奶、出售和繁殖性能。
- `event`：周期事件、提醒扫描、确认闭环和扫描日志。
- `feed`、`treatment`、`weight`、`inventory`、`sale`：现场记录和经营数据。
- `nfc`：NFC 标签绑定和目标解析。
- `audit`：接口审计。
- `dedup`：写请求幂等去重。
- `hardware`：硬件联动网关，默认 noop。
- `merchant`：全局账号与商户的成员关系、商户角色和兔场策略。
- `workspace`：多养殖业务工作空间契约和模块注册；当前由兔场模块提供 `RABBIT` 适配。
- `admin`：平台管理员、商户、商户成员权限和平台侧概览。
- `report`、`setting`：报表和全局设置。

数据库结构由 Flyway 管理，迁移目录为 `backend/src/main/resources/db/migration/`。`db/schema.sql` 只作为当前结构参考，不作为常规初始化入口。

当前保持单个 Spring Boot 模块化单体。业务层级是“全局账号 -> 商户租户 -> 养殖工作空间 ->
具体养殖模块”。现有兔场通过 `RABBIT:<houseId>` 暴露为工作空间，但仍使用原表和
`X-House-Id`，不进行破坏性迁移。第二种养殖业务应注册独立 workspace provider，并保留
自己的场地拓扑和生命周期状态机。详细决策见
[多养殖业务工作空间](../backend/modules/farming-workspaces.md)。

跨模块依赖使用 workspace 的 `model` 和 `spi` 契约。兔养殖适配器属于 `house.workspace`，
通用 workspace 核心不依赖任何具体养殖模块；该方向由后端架构测试强制执行。

## 权限与数据隔离

普通业务 API：

- 登录后使用 `Authorization: Bearer <token>`。
- 兔舍域请求必须带 `X-House-Id: <houseId>`。
- `GET /api/workspaces` 提供当前账号可见的养殖业务模块和空间，现有兔场键为 `RABBIT:<houseId>`。
- 权限分为 `view`、`edit`、`control`。
- 账号是全局身份，通过 `merchant_users` 加入一个或多个商户；`sys_user.merchant_id` 仅作为旧客户端的默认商户兼容字段。
- 兔场角色分为 `OWNER`、`MANAGER`、`STAFF`、`VIEWER`，并向旧客户端映射为原有权限字段。
- 查询和写入必须按兔舍隔离；没有直接 `house_id` 的从表需要通过父表关联过滤。

平台管理 API：

- 使用 `/api/admin/**`。
- 使用平台管理员 JWT。
- 不发送 `X-House-Id`。
- 可管理商户成员角色和兔场治理策略，不直接编辑商户生产数据。

## Flutter 客户端

Flutter Android 客户端位于 `flutter_app/`，包名为 `com.rabbit.app.flutter`，避免覆盖历史原生 Android App。

目录职责：

- `lib/src/config/`：运行时配置。
- `lib/src/data/services/`：HTTP、会话存储等底层服务。
- `lib/src/data/repositories/`：面向功能的 API 封装和数据组织。
- `lib/src/domain/models/`：跨层复用的数据模型。
- `lib/src/routing/`：go_router 路由表和导航守卫。
- `lib/src/ui/`：页面、组件、主题和 view model。

兔舍管理当前采用多级流程：

```text
兔舍列表 -> 兔舍详情 -> 笼位管理 / 兔只管理
```

新增兔只必须从具体笼位进入，保持笼位上下文；兔只列表页主要用于查看和编辑。

## 平台管理后台

Admin 位于 `admin/`，是 React + TypeScript + Vite 应用。它只服务平台管理员，不是商户业务后台。

关键边界：

- 请求统一走 `src/lib/request.ts` 和 `src/api/`。
- 平台登录使用 `POST /api/admin/auth/login`。
- 商户业务数据以只读概览为主。
- 可写操作集中在商户创建、编辑、启停、成员角色，以及兔场治理策略。

UI 和工程规则分别见 `admin/DESIGN.md` 与 `admin/.rules`。

## 历史 Android 客户端

历史原生 Android 客户端不再作为当前维护入口。后续移动端功能默认落在 `flutter_app/`，除非任务明确要求恢复或修改原生 Android。
