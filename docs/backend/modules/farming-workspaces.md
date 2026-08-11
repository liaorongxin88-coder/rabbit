# 多养殖业务工作空间

## 决策

后端当前继续采用单体部署，但按模块化单体演进。`workspace` 表示当前账号直接有权访问的
养殖业务空间，不等同于 Maven 多模块，也不等同于独立微服务。

首期不新增通用工作空间表，也不重命名 `rabbit_houses`、`house_users` 或现有
`house_id`。这些字段已经参与大量生产查询、权限校验和客户端协议，直接迁移会同时扩大
数据、权限和多端兼容风险。当前通过适配器把兔场投影成通用工作空间：

```text
global account
  -> enabled direct house membership
       -> farming workspace (RABBIT:<houseId>)
            -> cages / rabbits / batches / records
```

`GET /api/workspaces` 返回当前账号实际可访问的业务模块和工作空间。兔场仍通过
`X-House-Id` 进入，原有 `/api/houses` 接口保持不变。接口不接收额外归属筛选参数，任何
客户端参数都不能扩大 `house_users` 得出的授权结果。

零兔场账号调用该接口是合法行为：模块目录仍可返回，`workspaces` 为空。客户端据此进入
创建或加入兔场流程，而不是清除登录态。

响应中的 `capabilities` 表示模块支持的功能集合，`role` 和 `permissions` 才表示当前账号在
该空间内实际获得的角色和动作权限。客户端不能因为模块支持某项能力就直接开放写操作。

工作空间视图包含：

- `workspaceKey`：例如 `RABBIT:1001`。
- `resourceId`：现有 `houseId`。
- `name`、`businessType`、`businessName`。
- `capabilities`、`role`、`permissions`。
- `scopeHeader=X-House-Id`。

所有权来自当前账号的 `house_users.role`，支持多位共同 `OWNER`，工作空间视图不维护单一
所有者字段。

## 当前代码边界

`modules/workspace` 只负责通用核心，具体养殖模块通过依赖倒置接入：

- `workspace.model`：跨模块使用的模块定义、工作空间视图和能力词汇。
- `workspace.spi.FarmingWorkspaceProvider`：每种养殖业务注册模块元数据并返回用户可见空间。
- `FarmingWorkspaceService`：聚合模块、拒绝重复模块编码并排序已授权工作空间。
- `FarmingWorkspaceController`：提供只读 `/api/workspaces` 入口。
- `house.workspace.RabbitHouseWorkspaceProvider`：把现有兔场映射为 `RABBIT:<houseId>`。

依赖方向固定为：

```text
workspace.controller -> workspace.service -> workspace.spi + workspace.model
house.workspace --------------------------> workspace.spi + workspace.model
```

`workspace` 通用核心不得导入 `house`、`rabbit` 或任何未来的物种模块。具体模块只允许使用
`workspace.model` 和 `workspace.spi`，不得调用 workspace 的 controller/service 内部。
`FarmingModuleArchitectureTest` 会分析生产字节码并在测试阶段阻止边界回退。

模块注册只描述业务能力，不包含 MQTT、NFC 或硬件能力。设备逻辑后续只能通过已经解析并
授权的工作空间上下文调用设备端口。

## 可复用与专属逻辑

可以沉到通用核心的能力：

| 能力 | 通用边界 |
| --- | --- |
| 身份与访问 | 全局账号、直接工作空间成员、状态和角色 |
| 工作空间治理 | 列表、成员关系、角色到动作权限、数据作用域 |
| 基础经营 | 库存、饲料、健康/治疗、称重、销售/出库 |
| 平台能力 | 审计、写请求幂等、事件、报表、通知端口 |

必须保留在具体养殖模块的逻辑：

| 领域 | 原因 |
| --- | --- |
| 场地拓扑 | 兔笼、禽舍、栏位、池塘的约束不同 |
| 养殖对象 | 个体、群体、批次的身份和聚合方式不同 |
| 生命周期 | 繁殖、育肥、产蛋、水产等状态机不可共用一套状态字段 |
| 生产指标 | 胎次、产蛋率、料肉比等口径和校验不同 |

不要建立包含所有物种字段的 `Animal` 大表，也不要用一套字符串状态机承载所有养殖流程。
共享模块提供接口和基础值对象，具体模块维护自己的表、状态转换和术语。

## 权限演进

现阶段授权链路为：

```text
enabled account -> enabled workspace -> enabled direct membership -> role -> action permission
```

`RabbitHouseWorkspaceProvider` 复用 `HouseService.listMyHouses`，因此账号停用、兔场停用、成员
停用和跨兔场隔离规则必须保持一致。现有 `rabbit:*` 权限码继续作为兔养殖生产权限。

第二个养殖模块落地时再引入通用 `workspace:*` 治理权限；生产动作继续使用模块前缀，例如
`rabbit:*`、`poultry:*`。平台管理员只管理账号、空间状态和治理，不因平台身份自动取得生产
写权限。

## 演进路线

1. 当前阶段：保持单个 Spring Boot 应用，建立 workspace 契约和兔场适配器。
2. 第二种养殖业务：新增独立包、表、权限前缀并实现 `FarmingWorkspaceProvider`。
3. 数据模型稳定后：按真实重复需求评估 `farming_workspaces`、`workspace_members` 等通用表，并通过双读、回填、切换、清理迁移。
4. 编译边界需要加强时：再将单个 Maven 工程拆成核心、具体物种模块和应用装配模块。
5. 只有在独立扩缩容、发布节奏、故障隔离或团队所有权出现明确压力时，才评估微服务。

在第二种业务出现前，不应为未来假设增加新的账号与兔场中间层。当前的直接成员关系和模块化
单体能保持最短授权链，也便于审计每个工作空间的真实访问来源。
