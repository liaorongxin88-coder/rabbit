# API 和权限

## 普通业务 API

普通业务 API 使用用户 JWT：

- `Authorization: Bearer <token>`
- 兔舍域请求带 `X-House-Id: <houseId>`

正式业务 JWT 只应发给已绑定手机号的用户。手机号一键登录、微信快捷登录和旧登录兼容流程见 [auth-phone-wechat.md](auth-phone-wechat.md)。

权限采用“作用域 + 动作权限码”，接口通过 `@RequiresPermission` 声明所需权限：

- 业务账号域：`account:*`、`rabbit:houses:list`，只要求有效业务登录态。
- 商户域：`merchant:*`，同时校验商户状态、商户成员状态和商户角色。
- 兔场域：`rabbit:*`，同时校验商户租户边界、兔场成员关系和兔场角色。
- 平台域：`platform:*`，只接受独立的平台管理员登录态。

例如 `rabbit:rabbits:list`、`rabbit:rabbits:add`、`rabbit:house-members:list`、
`merchant:members:edit` 和 `platform:accounts:list`。后端登录态、商户成员列表和
`GET /api/houses/permission` 会返回当前作用域的 `permissions`；管理端和 App 应按权限码
决定操作入口是否可用，后端仍对每次请求执行最终校验。

账号、商户和兔场使用分层授权。商户成员关系与兔场角色的完整定义见 [merchant-house-access.md](merchant-house-access.md)。旧的 `view` / `edit` / `control` 与 `isAdmin` 只用于兼容历史客户端，不再作为新接口授权的主模型。兔场成员管理只允许兔场 `OWNER` 或商户 `OWNER`，`MANAGER` 不能转让所有权或管理成员。

查询和写入必须按兔舍隔离。没有直接 `house_id` 的从表，需要通过父表关联过滤。

## 平台管理 API

平台 API 使用 `/api/admin/**`：

- 登录接口：`POST /api/admin/auth/login`
- 使用平台管理员 JWT。
- 不发送 `X-House-Id`。
- 不复用普通业务登录态。

第一版平台管理边界：

- 可管理商户、商户状态、商户成员角色，并在商户下创建业务账号。
- 可配置商户建场、兔场成员管理权限和容量上限。
- 可查看商户概览。
- 不直接编辑兔舍、笼位、兔只、投喂、用药、繁殖或销售数据。

## 幂等

写接口优先支持 `requestId`，由客户端生成。后端通过 `dedup` 模块避免重复提交造成重复数据。

## 常用接口

认证和兔舍：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/wechat-login`
- `POST /api/auth/phone-one-tap-login`
- `POST /api/auth/wechat-quick-login`
- `POST /api/auth/bind-phone`
- `GET /api/houses`
- `POST /api/houses`
- `GET /api/merchant-memberships`
- `GET /api/merchant-memberships/{merchantId}/members`
- `GET /api/house-members`

生产业务：

- `GET /api/cages`
- `POST /api/rabbits`
- `GET /api/rabbits`
- `GET /api/batches`
- `POST /api/batches`
- `GET /api/events`
- `POST /api/events/ack`
- `POST /api/maintenance/events/scan`

平台管理：

- `POST /api/admin/auth/login`
- `GET /api/admin/merchants`
- `POST /api/admin/merchants`
- `PUT /api/admin/merchants/{id}`
- `GET /api/admin/merchants/{id}`
- `GET /api/admin/merchants/{id}/accounts`
- `POST /api/admin/merchants/{id}/accounts`
- `GET /api/admin/merchants/{id}/house-policy`
- `PUT /api/admin/merchants/{id}/house-policy`
- `PUT /api/admin/merchants/{id}/accounts/{userId}/membership`
- `GET /api/admin/accounts/merchant-accounts`
- `PUT /api/admin/accounts/merchant-accounts/{userId}`
