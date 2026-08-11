# API 和权限

## 普通业务 API

普通业务 API 使用用户 JWT：

- `Authorization: Bearer <token>`
- 兔场域请求带 `X-House-Id: <houseId>`

当前已实现的短信登录、一键登录后端换号、零兔场、精确手机号邀请和首次设置密码，以及尚未
实现的微信手机号绑定适配契约见 [auth-phone-wechat.md](auth-phone-wechat.md)。

权限采用“作用域 + 动作权限码”，接口通过 `@RequiresPermission` 声明所需权限：

- 账号域：`account:*`、`workspaces:list`、`rabbit:houses:list`、`rabbit:houses:add`，只要求有效业务登录态。
- 兔场域：`rabbit:*`，同时校验账号状态、兔场状态、`house_users` 成员状态和兔场角色。
- 平台域：`platform:*`，只接受独立的平台管理员登录态。

例如 `rabbit:rabbits:list`、`rabbit:rabbits:add`、`rabbit:house-members:list`、
`platform:farms:list` 和 `platform:users:list`。`GET /api/houses/permission` 及工作空间响应会
返回当前兔场的 `permissions`；客户端应按权限码决定操作入口，后端仍对每次请求执行最终
校验。

账号与兔场的直接成员关系、状态和共同 `OWNER` 约束见
[direct-house-access.md](direct-house-access.md)。旧 `view`、`edit`、`control` 与
`isAdmin` 只用于兼容历史客户端，不再作为新接口授权主模型。

查询和写入必须按兔场隔离。没有直接 `house_id` 的从表，需要通过父表关联过滤。不能仅依赖
前端保存的当前兔场，也不能只在控制器检查一次后让 Mapper 查询跨越数据边界。

## 零兔场账号

完成手机号验证但尚未创建或加入兔场的账号是有效登录态：

- 可以访问 `/api/auth/me`、账号设置、`GET /api/houses`、`POST /api/houses` 和
  `GET /api/workspaces`。
- `GET /api/houses` 返回空数组，`GET /api/workspaces` 返回空工作空间集合。
- 不能访问任何需要 `X-House-Id` 的生产接口。
- 创建兔场后自动成为第一位 `OWNER`；存在精确手机号邀请时，在可信手机号登录后建立成员关系。

JWT 只表达账号身份，不内嵌当前兔场授权。成员角色或状态改变后，下一次请求立即按数据库中
的直接成员关系重新判断。

## 状态检查

兔场域授权必须同时满足：

```text
sys_user.status = ENABLED
rabbit_houses.status = ENABLED
rabbit_houses.is_deleted = false
house_users.status = ENABLED
house_users.role grants requested permission
```

`SUSPENDED` 或 `ORPHANED` 兔场不允许普通生产访问。停用某条成员关系只影响该账号在该兔场
的访问，不影响它加入的其他兔场。

## 平台管理 API

平台 API 使用 `/api/admin/**`：

- 登录接口：`POST /api/admin/auth/login`
- 使用平台管理员 JWT。
- 不发送 `X-House-Id`。
- 不复用普通业务登录态。

平台管理边界：

- 管理业务账号和账号状态。
- 管理兔场状态、查看兔场成员和处理 `ORPHANED` 恢复。
- 查看兔场生产概览。
- 不直接编辑笼位、兔只、投喂、用药、繁殖或销售数据。

平台接口和权限码以账号与兔场为资源，例如 `platform:users:*`、`platform:farms:*` 和
`platform:accounts:*`。

## 幂等

写接口优先支持 `requestId`，由客户端生成。后端通过 `dedup` 模块或资源级唯一键避免重复
提交。`requestId` 冲突且请求业务键不一致时返回冲突，不能把新请求误判成旧请求成功。

## 常用接口

认证和账号：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/sms/code`
- `POST /api/auth/sms/login`
- `POST /api/auth/sms/reset-password`
- `POST /api/auth/phone-one-tap-login`
- `POST /api/auth/wechat-login`
- `GET /api/auth/me`
- `PUT /api/auth/phone`（`account:phone:edit`）
- `PUT /api/auth/password`

以下是后续规划接口，当前不可调用：

- `POST /api/auth/wechat-quick-login`
- `POST /api/auth/bind-phone`

兔场和成员：

- `GET /api/houses`
- `POST /api/houses`
- `GET /api/houses/permission`
- `GET /api/workspaces`
- `GET /api/house-members`
- `PUT /api/house-members/{userId}`
- `DELETE /api/house-members/{userId}`
- `POST /api/house-members/leave`
- `POST /api/house-invitations`

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
- `GET /api/admin/users`
- `GET /api/admin/farms`
- `GET /api/admin/accounts`
