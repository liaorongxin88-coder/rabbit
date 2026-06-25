# API 和权限

## 普通业务 API

普通业务 API 使用用户 JWT：

- `Authorization: Bearer <token>`
- 兔舍域请求带 `X-House-Id: <houseId>`

权限模型：

- `view`：可查看。
- `edit`：可做生产业务写入。
- `control`：可做成员管理、审计、硬件和维护类操作。

查询和写入必须按兔舍隔离。没有直接 `house_id` 的从表，需要通过父表关联过滤。

## 平台管理 API

平台 API 使用 `/api/admin/**`：

- 登录接口：`POST /api/admin/auth/login`
- 使用平台管理员 JWT。
- 不发送 `X-House-Id`。
- 不复用普通业务登录态。

第一版平台管理边界：

- 可管理商户、商户状态和商户业务用户绑定。
- 可查看商户概览。
- 不直接编辑兔舍、笼位、兔只、投喂、用药、繁殖或销售数据。

## 幂等

写接口优先支持 `requestId`，由客户端生成。后端通过 `dedup` 模块避免重复提交造成重复数据。

## 常用接口

认证和兔舍：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/wechat-login`
- `GET /api/houses`
- `POST /api/houses`
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
