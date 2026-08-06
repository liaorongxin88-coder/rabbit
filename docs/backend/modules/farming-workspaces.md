# 多养殖业务工作空间

## 决策

后端当前继续采用单体部署，但按模块化单体演进。`workspace` 在本项目中首先表示商户下的
养殖业务工作空间，不等同于 Maven 多模块，也不等同于独立微服务。

首期不新增通用工作空间表，也不重命名 `rabbit_houses`、`house_users` 或现有 `house_id`。
这些字段已经参与大量生产查询、权限校验和客户端协议，直接迁移会同时扩大数据、权限和
多端兼容风险。当前通过适配器把兔场投影成通用工作空间：

```text
merchant tenant
  -> farming workspace (RABBIT:<houseId>)
       -> rabbit house / cages / rabbits / batches / records
```

`GET /api/workspaces` 返回当前账号实际可访问的业务模块和工作空间。兔场仍通过
`X-House-Id` 进入，原有 `/api/houses` 接口保持不变。可选的 `merchantId` 参数只会收窄
已授权结果，不会通过商户编号扩大可见范围。

响应中的 `capabilities` 表示模块支持的功能集合，`role` 和 `permissions` 才表示当前账号在
该空间内实际获得的角色和动作权限。客户端必须按 `permissions` 控制交互，不能因为模块
支持某项能力就直接开放写操作。

## 当前代码边界

`modules/workspace` 只负责通用核心，具体养殖模块通过依赖倒置接入：

- `workspace.model`：跨模块使用的模块定义、工作空间视图和能力词汇。
- `workspace.spi.FarmingWorkspaceProvider`：每种养殖业务注册模块元数据并返回用户可见空间。
- `FarmingWorkspaceService`：聚合模块、拒绝重复模块编码并在授权结果内筛选商户。
- `FarmingWorkspaceController`：提供只读的 `/api/workspaces` 入口。
- `house.workspace.RabbitHouseWorkspaceProvider`：兔场侧适配器，把现有兔场映射为 `RABBIT:<houseId>`。

依赖方向固定为：

```text
workspace.controller -> workspace.service -> workspace.spi + workspace.model
house.workspace --------------------------> workspace.spi + workspace.model
```

`workspace` 通用核心不得导入 `house`、`rabbit` 或任何未来的物种模块。具体模块只允许使用
`workspace.model` 和 `workspace.spi`，不得调用 workspace 的 controller/service 内部。
`FarmingModuleArchitectureTest` 会分析生产字节码并在测试阶段阻止上述边界回退。

模块注册只描述业务能力，不包含 MQTT、NFC 或硬件能力。设备逻辑继续 pending，后续只能
通过已经解析并授权的工作空间上下文调用设备端口。

## 可复用与专属逻辑

可以沉到通用核心的能力：

| 能力 | 通用边界 |
| --- | --- |
| 身份与租户 | 全局账号、商户成员、状态、平台策略 |
| 工作空间访问 | 成员关系、角色到动作权限、数据作用域 |
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

现阶段沿用已验证的授权链路：

```text
global account -> enabled merchant membership -> visible rabbit house -> house role -> action permission
```

`RabbitHouseWorkspaceProvider` 复用 `HouseService.listMyHouses`，因此商户停用、成员停用和兔场成员
隔离规则仍然有效。现有 `rabbit:*` 权限码保持兼容。

第二个养殖模块落地时再引入通用的 `workspace:*` 治理权限，用于空间列表、资料和成员管理；
生产动作继续使用模块前缀，例如 `rabbit:*`、`poultry:*`。平台管理员只管理租户、模块开通
和治理策略，默认不直接取得生产数据权限。

## 演进路线

1. 当前阶段：保持单个 Spring Boot 应用，建立 workspace 契约和兔场适配器，旧接口零迁移。
2. 第二种养殖业务：新增独立包、表、权限前缀并实现 `workspace.spi.FarmingWorkspaceProvider`，用实际需求校验共享边界。
3. 数据模型稳定后：引入 `farming_workspaces`、`workspace_members` 等通用表，并通过双读、回填、切换、清理四步迁移现有兔场数据。
4. 编译边界需要加强时：再将单个 Maven 工程拆成 `farming-core`、`farming-rabbit`、具体物种模块和 `farming-app`。
5. 只有在独立扩缩容、发布节奏、故障隔离或团队所有权出现明确压力时，才评估拆成微服务。

在第 2 阶段之前拆 Maven workspace 只会移动目录，不能证明抽象正确；在第 5 阶段之前拆
微服务则会提前引入分布式事务、跨服务授权和运维成本。因此当前的模块化单体是更稳妥的
发展路径。
