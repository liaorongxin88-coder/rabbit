# 手机号认证和兔场进入流程

Flutter 客户端必须把账号认证与兔场授权分开。手机号已经验证但没有兔场是有效登录态，不能
清除 JWT 或退回登录页。

## 当前实现状态

当前 App 已接入：

- 手机号验证码登录：`POST /api/auth/sms/code`、`POST /api/auth/sms/login`。
- 手机号账号的 `phoneBound`、`maskedPhone` 和 `hasPassword` 会话字段。
- 零兔场、单兔场和多兔场的进入逻辑。
- 精确手机号邀请：`POST /api/house-invitations`。
- 首次设置密码与后续修改密码：`PUT /api/auth/password`。
- 登录态手机号绑定与更换：`PUT /api/auth/phone`。
- 登录页短信找回密码：`POST /api/auth/sms/reset-password`。
- 供应商无关的一键登录能力探测、Kotlin 通道和后端 token 交换。
- 用户名密码登录兼容。

当前 App 还**没有导入阿里云官方号码认证 AAR**，原生 adapter 因此报告不可用，所有已提交
环境的 `RABBIT_CARRIER_AUTH_ENABLED` 也默认 `false`。这意味着调用链和回退逻辑可做本地
自动化测试，但现有 APK 不会展示一个无法完成真实取号的一键登录按钮。微信快捷登录前的
`bindingToken`、短期 token 绑定路由和账号合并流程仍未实现。

## 当前状态机

```text
打开 App
  -> 无正式 session: 登录页
  -> 有正式 token: 拉取 /api/auth/me 和 /api/houses
       -> houses 为空: 显示零兔场页
       -> houses 只有一个: 自动选中该兔场
       -> houses 有多个: 保留仍有效的最近兔场，否则要求选择

手机号短信登录成功
  -> 保存正式 token 和账号资料
  -> 重新拉取兔场列表
  -> 不自动创建占位兔场

运营商一键登录成功
  -> 原生授权页返回一次性 token
  -> POST /api/auth/phone-one-tap-login
  -> 仅保存后端返回的正式 session
  -> 重新拉取兔场列表
```

精确手机号邀请只会在目标账号下一次可信手机号登录时生效，因此短信登录成功后必须重新拉取
兔场列表，不能使用登录前的缓存决定路由。

## 当前 Session 与兔场选择

正式 session 包含：

- `token`
- `userId`
- `userName`
- `phoneBound`
- `maskedPhone`
- `hasPassword`
- `permissions`
- `currentHouseId`：可空的本地偏好，不是授权事实

规则：

- 正式 token 存 `flutter_secure_storage`。
- 零兔场时清除 `currentHouseId`，但保留正式 session。
- 单兔场时自动选择唯一兔场。
- 多兔场时只保留仍存在于最新列表中的 `currentHouseId`；否则清除并要求选择。
- 每个兔场域请求发送 `X-House-Id`；客户端选择不能替代后端授权。
- `SUSPENDED` 或 `ORPHANED` 兔场不能作为当前兔场进入生产页面。

## 当前手机号验证码登录

```http
POST /api/auth/sms/code
POST /api/auth/sms/login
```

Flutter 当前对两个请求显式发送 `purpose=LOGIN_OR_REGISTER`。后端仍接受缺省 purpose，以兼容
尚未升级的客户端；重置密码等流程必须使用各自 purpose，不能复用登录验证码。

短信登录响应中的 `hasPassword=false` 表示该手机号账号尚未设置可用密码，不影响正式 JWT、
零兔场状态或手机号邀请匹配。

## 当前零兔场页面

手机号首次登录且没有有效邀请时，`GET /api/houses` 返回空数组。页面提供：

- 创建兔场；成功后创建者成为第一位 `OWNER`。
- 等待兔场所有者按当前手机号邀请，并在收到邀请后重新使用手机号登录。

登录只能证明账号身份，不能替用户决定兔场名称、布局或所有权，因此不能自动创建空兔场。

## 当前精确手机号邀请

兔场成员页只向有 `rabbit:house-members:add` 权限的 `OWNER` 展示邀请入口：

```http
POST /api/house-invitations
X-House-Id: <currentHouseId>

{
  "phone": "13800138000",
  "role": "STAFF",
  "requestId": "uuid"
}
```

- 成功响应始终是 `{"status":"SUBMITTED","role":"STAFF"}`。
- UI 不得根据响应判断手机号是否已经注册，也不展示 `JOINED` 或账号存在状态。
- 无论目标账号是否存在，成员关系都在该手机号下一次可信登录时建立；提交邀请后可提示“已提交”。
- 邀请角色不包含 `OWNER`；共同所有权只能在成员加入后由现有 `OWNER` 明确授予。
- 不提供手机号模糊搜索，也不展示完整手机号。

## 当前设置与修改密码

账号设置页根据 `hasPassword` 改变表单：

- `false`：显示“设置登录密码”，不要求旧密码。
- `true`：显示“修改密码”，要求并校验旧密码。

成功后刷新 `/api/auth/me` 和本地 session，使 `hasPassword=true`。设置密码不会创建新账号，
也不会改变当前账号的任何兔场成员关系。

登录页“忘记密码”使用 `RESET_PASSWORD` purpose 获取验证码，再提交
`POST /api/auth/sms/reset-password`。登录验证码不能用于重置密码。

## 当前手机号绑定与更换

账号设置页显示脱敏后的当前手机号，并使用 `BIND_PHONE` purpose 验证新手机号：

- 未绑定手机号：验证新手机号后直接绑定。
- 已绑定且 `hasPassword=true`：验证当前密码和新手机号验证码后更换。
- 已绑定且 `hasPassword=false`：输入原手机号，以 `VERIFY_CURRENT_PHONE` purpose 验证原手机号，
  再验证新手机号后更换。
- 成功后刷新 `/api/auth/me`；手机号已属于其他账号时保留当前绑定并展示冲突错误。

三个验证码用途与 `LOGIN_OR_REGISTER` 相互隔离。客户端只展示脱敏手机号，不从后端读取或保存
完整的当前手机号。

## 当前兔场状态与共同 OWNER

- 同一兔场允许多位共同 `OWNER`，成员页按多条 `role=OWNER` 展示。
- 客户端不能假定只有一位所有者，也不能在提升新 `OWNER` 后降级其他所有者。
- 后端拒绝最后一位有效 `OWNER` 的降级、停用、移除或退出时，客户端保留原列表并展示错误。
- 账号、兔场或成员关系失效时，清除失效的 `currentHouseId` 并刷新列表。

## 当前一键登录客户端边界

Flutter 已提供 `CarrierAuthService`、MethodChannel、Kotlin `CarrierAuthAdapter`、后端 repository
调用和登录页入口。入口只有同时满足以下条件才显示：

- `RABBIT_CARRIER_AUTH_ENABLED=true`。
- 原生 adapter 报告当前设备与 SDK 可用。
- 用户在拉起授权前已确认 App 用户协议和隐私政策。

短期 token 只作为函数局部值提交给 `POST /api/auth/phone-one-tap-login`，不写 secure storage、
SharedPreferences 或日志。成功后只保存后端正式 JWT；用户取消、SDK 不可用、超时或后端失败
都保留短信验证码入口。重复点击只能启动一次授权流程；Flutter 不限制用户阅读授权页的时间，
网络认证超时由未来接入的原生 adapter 在 SDK 对应阶段报告。

当前原生实现是明确的 unavailable adapter，不包含测试手机号或生产 Fake。接入真实阿里云
adapter 时还需：

1. 从控制台下载并固定官方 Android AAR 版本与校验值。
2. 分别登记 `com.rabbit.app.flutter.dev`、`com.rabbit.app.flutter.test`、
   `com.rabbit.app.flutter` 及其实际签名。
3. 在用户确认 Rabbit 隐私政策之后才初始化 SDK，并使用官方授权页，不仿制或绕过授权确认。
4. 用带有效 SIM、开启蜂窝数据且关闭 VPN 的真机验证移动、联通、电信及失败回退。

模拟器和本地 Fake 只能验证应用层状态机，不能作为真实取号、授权页合规或三网验收证据。

## 后续适配：微信快捷登录与绑定手机号（未实现）

当前没有以下能力或接口：

- `POST /api/auth/wechat-quick-login`
- `bindingToken`
- 微信短期 token 专用的绑定接口
- `/bind-phone` 路由
- `MERGE_REQUIRED` 账号冲突处理

后续实现时，微信 code 验证成功但手机号未绑定的账号只能获得短期 `bindingToken`；绑定页需
通过短信验证码或运营商服务端换号取得可信手机号。绑定成功后清除短期 token，保存正式
session，重新拉取兔场列表。两个已有账号都存在兔场关系时必须进入显式冲突处理，不能静默
覆盖或迁移成员关系。

## 隐私约束

手机号只展示脱敏值，例如 `138****8000`。App 不展示或记录完整手机号、openid、unionid、
运营商 token、微信 code、短信验证码或账号内部生成的用户名。
