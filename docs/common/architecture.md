# 架构概览

## 系统边界

Rabbit 当前由三块主要应用组成：

- `backend/`：所有业务数据、权限、审计、提醒、迁移和平台管理 API 的来源。
- `app/`：Flutter Android 客户端，面向兔场业务用户。
- `admin/`：平台运营控制台，面向平台管理员。

`tools/` 只放演示和回归脚本，`docs/` 只放当前维护文档。

## 后端

后端使用 Spring Boot + MyBatis + MySQL。源码按业务模块放在
`backend/src/main/java/com/rabbit/app/modules/`：

- `auth`：业务账号、手机号短信登录和微信登录兼容。
- `house`：兔场、成员、精确手机号邀请和权限。
- `cage`：笼位维护和笼位概览。
- `rabbit`：兔只录入、编辑、状态、异常、离场和后备。
- `batch`：催情、配种、摸胎、备产、分娩、断奶、出售和繁殖性能。
- `event`：周期事件、提醒扫描、确认闭环和扫描日志。
- `feed`、`treatment`、`weight`、`inventory`、`sale`：现场记录和经营数据。
- `nfc`：NFC 标签绑定和目标解析。
- `audit`：接口审计。
- `dedup`：写请求幂等去重。
- `hardware`：硬件联动网关，默认 noop。
- `workspace`：多养殖业务工作空间契约和模块注册；当前由兔场模块提供 `RABBIT` 适配。
- `admin`：平台管理员、业务账号、兔场和平台侧概览。
- `report`、`setting`：报表和账号级设置。

数据库结构由 Flyway 管理，迁移目录为
`backend/src/main/resources/db/migration/`。`db/schema.sql` 只作为当前结构参考，
不作为常规初始化入口。应用启动代码不得执行 DDL 或静默修补表结构；历史数据库兼容也必须
通过幂等 Flyway 迁移完成。

模块内请求依赖方向为：

```text
controller -> service -> mapper
```

Controller 负责 HTTP 参数、认证上下文和响应适配，不得直接访问 Mapper。通用基础设施包
（例如 `cache`）不得反向依赖具体业务模块；业务专用适配器放在对应模块的
`infrastructure` 包中。`FarmingModuleArchitectureTest` 在测试阶段强制这些规则。

批次模块对 Controller 保留稳定的 `BatchService` 命令门面，实际事务分别由生命周期、
繁育、分娩、断奶、催情和出售服务承担。跨流程复用的批次校验、状态历史和自动完结规则
集中在模块内部协作者中；查询由 `BatchQueryService` 等只读服务承担。

## 领域模型

Rabbit 采用“全局账号直接加入兔场”的模型：

```text
sys_user
  -> house_users
       -> rabbit_houses
            -> cages / rabbits / batches / records
```

- `sys_user` 是认证身份和账号状态的来源，不拥有隐式业务空间。
- `house_users` 是账号与兔场之间唯一的授权关系，保存角色和成员状态。
- `rabbit_houses` 是业务数据隔离边界。产品文案使用“兔场”，兼容 API、表名和请求头继续使用 `house`。
- 生产数据继续通过 `house_id` 归属兔场；没有直接 `house_id` 的明细通过父记录关联校验。
- 同一账号可以加入多个兔场，同一兔场可以有多位共同 `OWNER`。

账号、兔场和成员都必须处于可用状态，访问才成立：

```text
enabled account -> enabled house -> enabled house membership -> role -> permission
```

兔场角色为 `OWNER`、`MANAGER`、`STAFF`、`VIEWER`。所有权以
`house_users.role=OWNER` 为准，允许多位共同所有者；降级、移除或退出时必须至少保留一位
有效 `OWNER`。

## 认证与零兔场状态

手机号是移动端账号的稳定绑定锚点。当前已实现短信验证码认证和运营商一键登录后端换号；
官方 Android SDK 仍待控制台与真机接入。两种方式都落到同一个 `phone_hash` 账号，不能另建
一套账号身份。

手机号首次验证成功时只创建 `sys_user`，不会自动创建兔场。该账号已经完成认证，可以获得
正式 JWT，但 `GET /api/houses` 允许返回空列表。客户端此时进入创建或加入兔场流程；只有建立
有效 `house_users` 关系后，才能访问对应兔场的生产数据。

按手机号发出的精确邀请会保存号码 HMAC 和脱敏值，不保存明文。无论目标账号是否已存在，
邀请接口都只返回 `SUBMITTED`；成员关系在该手机号下一次完成可信短信登录时建立，以免通过
邀请响应枚举账号。

手机号账号默认 `hasPassword=false`。用户首次设置登录密码时不要求旧密码；设置成功后
`hasPassword=true`，以后修改必须校验旧密码。

## 权限与数据隔离

普通业务 API：

- 登录后使用 `Authorization: Bearer <token>`。
- 兔场域请求必须带 `X-House-Id: <houseId>`。
- `GET /api/houses` 和 `GET /api/workspaces` 只返回当前账号直接加入且状态有效的兔场。
- 账号级接口不要求当前兔场，因此零兔场账号仍可维护资料、设置密码、创建兔场和接受邀请。
- 兔场域权限使用 `rabbit:*` 动作权限码；客户端按返回的 `permissions` 控制入口，后端仍执行最终校验。
- 旧 `view`、`edit`、`control`、`isAdmin` 仅用于兼容历史客户端。

平台管理 API：

- 使用 `/api/admin/**` 和独立的平台管理员 JWT。
- 不发送 `X-House-Id`。
- 可管理账号与兔场状态、处理无有效所有者的兔场并查看平台概览。
- 默认不直接编辑兔场生产数据。

## 工作空间

当前保持单个 Spring Boot 模块化单体。现有兔场通过 `RABBIT:<houseId>` 投影为工作空间，
仍使用原表、原业务状态机和 `X-House-Id`，不新增通用工作空间表。

跨模块依赖使用 workspace 的 `model` 和 `spi` 契约。兔养殖适配器属于
`house.workspace`，通用 workspace 核心不依赖任何具体养殖模块；详细决策见
[多养殖业务工作空间](../backend/modules/farming-workspaces.md)。

## Flutter 客户端

Flutter Android 客户端位于 `app/`。关键客户端状态包括：

- 正式 session：JWT、账号资料、`phoneBound`、`maskedPhone` 和 `hasPassword`。
- 当前兔场：客户端偏好，可以为空；每次使用前必须确认仍在可访问兔场列表中。
- 零兔场：进入创建或加入兔场页面，不把空列表当成登录失败。

## 平台管理后台

Admin 位于 `admin/`，是 React + TypeScript + Vite 应用。它只服务平台管理员，不是兔场
生产操作端。平台侧以账号和兔场为导航对象，可查看状态与只读概览；生产写入仍由持有有效
兔场成员关系的业务账号完成。
