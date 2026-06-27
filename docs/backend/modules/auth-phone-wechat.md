# 手机号和微信登录设计

目标：手机号是 Rabbit App 的用户绑定锚点。无论用户通过手机号一键登录、微信快捷登录、旧用户名密码登录，还是历史 openid 登录，最终都必须绑定并验证手机号后才能使用业务功能。

## 设计原则

- 手机号是业务用户的唯一绑定主键，同一个手机号只能对应一个 `sys_user`。
- 微信、密码、运营商一键登录 token 都只是登录凭证，不是业务用户主键。
- 未绑定手机号的身份只能拿到短期 `bindingToken`，不能拿正式业务 JWT。
- 正式业务 JWT 只发给已绑定手机号的用户。
- 所有 `/api/**` 业务接口继续使用正式 JWT；绑定流程只走 `/api/auth/**`。
- 旧 openid-only 用户必须在下一次登录时绑定手机号后才能继续使用。

## 登录状态

| 状态 | 含义 | 可访问范围 |
| --- | --- | --- |
| `ANONYMOUS` | 未认证 | `/api/auth/*` |
| `PRE_AUTH` | 已验证某种外部凭证，但手机号未绑定 | 仅绑定手机号、取消登录 |
| `AUTHENTICATED` | 已绑定手机号并持有正式 JWT | 全部授权业务接口 |
| `MERGE_REQUIRED` | 快捷身份和手机号命中不同用户且都有业务数据 | 只读冲突提示，需用户确认或客服处理 |

## 推荐数据模型

在现有 `sys_user` 基础上新增手机号绑定字段：

```sql
alter table sys_user
  add column phone_country_code varchar(8) null after openid,
  add column phone_hash varchar(128) null after phone_country_code,
  add column phone_cipher varchar(512) null after phone_hash,
  add column phone_masked varchar(32) null after phone_cipher,
  add column phone_bound_time datetime null after phone_masked,
  add unique key uk_sys_user_phone_hash (phone_hash);
```

建议新增统一身份表，逐步替代 `sys_user.openid` 单字段绑定：

```sql
create table if not exists auth_identities (
  id bigint primary key auto_increment,
  user_id bigint not null,
  provider varchar(32) not null,
  provider_subject varchar(191) not null,
  provider_app varchar(64),
  verified_time datetime not null default current_timestamp,
  create_time datetime not null default current_timestamp,
  update_time datetime not null default current_timestamp on update current_timestamp,
  unique key uk_auth_identity_provider_subject (provider, provider_subject),
  key idx_auth_identity_user (user_id),
  constraint fk_auth_identity_user foreign key (user_id) references sys_user (user_id)
) engine=InnoDB default charset=utf8mb4;
```

`provider` 建议值：

- `PHONE_ONE_TAP`
- `PHONE_SMS`
- `WECHAT_OPENID`
- `WECHAT_UNIONID`
- `PASSWORD`

手机号存储建议：

- `phone_hash`：用于唯一约束和查询，使用服务端 pepper 后的 SHA-256/HMAC。
- `phone_cipher`：用于必要展示或客服核对，使用应用级加密。
- `phone_masked`：用于前端展示，例如 `138****8000`。
- 不在日志、审计详情、异常消息中输出完整手机号。

## Token 设计

### `bindingToken`

短期 token，仅用于绑定手机号。

建议：

- TTL：5 到 10 分钟。
- claims 包含 `purpose=bind_phone`。
- claims 包含已验证身份：`provider`、`providerSubject`、可选 `legacyUserId`。
- 不被 `JwtAuthenticationFilter` 识别为业务登录态。

### 正式业务 JWT

正式 token 只在手机号已绑定后生成：

- claims 包含 `userId`。
- 可选包含 `phoneBound=true`，便于以后做快速拒绝。
- 现有业务接口只接受正式 token。

## API 设计

### 手机号一键登录/注册

```http
POST /api/auth/phone-one-tap-login
```

请求：

```json
{
  "provider": "carrier",
  "accessToken": "carrier-token-from-sdk",
  "requestId": "uuid"
}
```

流程：

1. 后端调用运营商或聚合认证服务校验 `accessToken`。
2. 拿到可信手机号并标准化。
3. 按 `phone_hash` 查用户。
4. 用户不存在则创建用户并写入手机号绑定字段。
5. 写入或更新 `auth_identities(provider=PHONE_ONE_TAP)`。
6. 返回正式业务 JWT。

响应：

```json
{
  "token": "jwt",
  "userId": 10001,
  "userName": "u_10001",
  "phoneBound": true,
  "maskedPhone": "138****8000"
}
```

### 微信快捷登录

```http
POST /api/auth/wechat-quick-login
```

请求：

```json
{
  "code": "wechat-code"
}
```

流程：

1. 后端用 `code` 换取 `openid`，有条件时也拿 `unionid`。
2. 查询 `auth_identities(WECHAT_UNIONID/WECHAT_OPENID)` 或兼容查询 `sys_user.openid`。
3. 如果命中用户且已绑定手机号，返回正式业务 JWT。
4. 如果未命中或命中用户未绑定手机号，返回 `bindingRequired=true` 和 `bindingToken`。
5. App 跳转手机号绑定页。

响应：已绑定手机号

```json
{
  "token": "jwt",
  "userId": 10001,
  "userName": "u_10001",
  "phoneBound": true,
  "maskedPhone": "138****8000"
}
```

响应：需要绑定手机号

```json
{
  "bindingRequired": true,
  "bindingToken": "short-lived-token",
  "provider": "WECHAT_OPENID",
  "maskedProviderName": "微信"
}
```

### 绑定手机号

```http
POST /api/auth/bind-phone
Authorization: Bearer <bindingToken>
```

请求使用手机号一键认证结果：

```json
{
  "phoneAuthProvider": "carrier",
  "phoneAccessToken": "carrier-token-from-sdk",
  "requestId": "uuid"
}
```

如果一键认证不可用，后续可补短信验证码版本：

```json
{
  "phone": "13800138000",
  "smsCode": "123456",
  "requestId": "uuid"
}
```

流程：

1. 校验 `bindingToken` 只能用于手机号绑定。
2. 校验手机号来源可信。
3. 按 `phone_hash` 查询现有用户。
4. 如果手机号无用户：
   - 若 `bindingToken` 带 `legacyUserId`，给该用户绑定手机号。
   - 否则创建新用户并绑定手机号。
5. 如果手机号已有用户：
   - 将当前微信身份绑定到这个手机号用户。
   - 如果 `legacyUserId` 也有业务数据，返回 `MERGE_REQUIRED`，不要静默合并。
6. 返回正式业务 JWT。

## 旧登录兼容

现有接口建议保留但改变返回语义：

- `POST /api/auth/register`：新注册必须携带并验证手机号；否则返回 400。
- `POST /api/auth/login`：用户名密码正确但用户未绑定手机号时，返回 `bindingRequired=true` 和 `bindingToken`，不返回正式 JWT。
- `POST /api/auth/wechat-login`：迁移为 `wechat-quick-login` 的兼容别名，不再为未绑定手机号的 openid 直接创建正式用户。

## 后端拦截规则

实施时需要确保：

- `/api/auth/**` 允许匿名访问，但绑定接口只接受 `bindingToken`。
- `JwtAuthenticationFilter` 不接受 `purpose=bind_phone` 的 token。
- `HouseGuardInterceptor` 和业务权限逻辑继续只读取正式 `AuthContext.userId`。
- 如果保留旧正式 JWT，一次性迁移前可在 Filter 中校验用户 `phone_bound_time`，未绑定则返回 403 和 `PHONE_BINDING_REQUIRED`。

## 冲突处理

| 场景 | 处理 |
| --- | --- |
| 微信首次登录，手机号无用户 | 创建用户，绑定手机号和微信 |
| 微信首次登录，手机号已有用户 | 将微信身份绑定到该手机号用户 |
| 旧 openid 用户无手机号，绑定新手机号 | 给旧用户补手机号 |
| 旧 openid 用户有业务数据，手机号也已有业务用户 | 返回 `MERGE_REQUIRED`，人工或确认合并 |
| 一个手机号尝试绑定多个用户 | 拒绝，以手机号用户为准 |
| 一个微信身份尝试换绑其它手机号 | 拒绝或进入客服换绑流程 |

## 审计和安全

- 记录登录方式、绑定方式、绑定结果、失败原因和 traceId。
- 审计中只写 masked phone，不写完整手机号。
- 运营商 token、微信 code、短信验证码不得写入日志。
- 绑定手机号、换绑手机号和冲突处理都需要限流。
- 手机号绑定接口必须支持 `requestId` 幂等。

## 实施顺序

1. 新增 `sys_user` 手机号字段和 `auth_identities` 表。
2. 新增 `bindingToken` 生成/校验能力。
3. 新增手机号认证适配接口，先抽象 `PhoneAuthProvider`，具体供应商后接入。
4. 改造微信登录：未绑定手机号时只返回绑定态。
5. 改造用户名密码登录：未绑定手机号时只返回绑定态。
6. Flutter 增加手机号一键登录、微信快捷登录和绑定手机号页面。
7. 迁移旧 openid 用户，按首次登录触发绑定，不批量造手机号。
