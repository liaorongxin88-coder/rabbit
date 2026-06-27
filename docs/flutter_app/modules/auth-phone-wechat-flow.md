# 手机号和微信登录交互

Flutter 客户端需要把“认证身份”和“可进入业务”分开。只有拿到已绑定手机号的正式 token 才能进入首页、兔舍、数据面板和我的页面。

## 入口布局

登录页建议按优先级展示：

1. `本机号码一键登录`：主按钮。
2. `微信快捷登录`：次按钮。
3. `账号密码登录`：折叠或底部入口，用于兼容旧账号。

页面文案需要明确：手机号用于账号绑定和找回，不绑定手机号无法使用。

## 状态机

```text
打开 App
  -> 无 session: 登录页
  -> 有正式 token: 首页
  -> 有 bindingToken: 绑定手机号页

手机号一键登录成功
  -> 正式 token
  -> 首页

微信快捷登录成功且已绑定手机号
  -> 正式 token
  -> 首页

微信快捷登录成功但未绑定手机号
  -> bindingToken
  -> 绑定手机号页

账号密码登录成功但未绑定手机号
  -> bindingToken
  -> 绑定手机号页
```

## 路由

建议新增：

- `/login`
- `/bind-phone`

路由守卫：

- 没有正式 token 时访问业务页，跳转 `/login`。
- 只有 `bindingToken` 时访问业务页，跳转 `/bind-phone`。
- 已有正式 token 时访问 `/login` 或 `/bind-phone`，跳转首页。

## Session 存储

正式 session：

- `token`
- `userId`
- `userName`
- `phoneBound=true`
- `maskedPhone`
- `currentHouseId`

绑定 session：

- `bindingToken`
- `provider`
- `maskedProviderName`
- 过期时间

正式 token 存 `flutter_secure_storage`。`bindingToken` 也应存安全存储，但 TTL 很短，绑定成功或退出登录必须清除。

## 接口处理

### 手机号一键登录

客户端调用运营商或聚合 SDK 拿到 `accessToken` 后：

```http
POST /api/auth/phone-one-tap-login
```

成功后保存正式 session 并进入首页。

### 微信快捷登录

客户端调用微信 SDK 拿到 `code` 后：

```http
POST /api/auth/wechat-quick-login
```

响应如果有 `token`：

- 保存正式 session。
- 进入首页。

响应如果有 `bindingRequired=true`：

- 保存 `bindingToken`。
- 跳转 `/bind-phone`。

### 绑定手机号

绑定页优先使用本机号码一键认证：

```http
POST /api/auth/bind-phone
Authorization: Bearer <bindingToken>
```

成功后清除 `bindingToken`，保存正式 session，进入首页。

如果一键认证不可用，后续可展示短信验证码备用流程。短信流程也必须拿到正式 token 后才能进入业务。

## 错误态

| 错误 | UI 处理 |
| --- | --- |
| 手机号一键认证不可用 | 展示短信验证码备用入口 |
| 微信取消授权 | 留在登录页，提示用户已取消 |
| `bindingToken` 过期 | 清除绑定态，回登录页重新认证 |
| 手机号已绑定其它活跃账号 | 展示冲突说明，不自动覆盖 |
| `MERGE_REQUIRED` | 展示“账号需要合并处理”，提供联系客服或后续确认入口 |
| 网络失败 | 保持当前页，允许重试 |

## 页面约束

- 不要让未绑定手机号的用户进入首页或任何业务 tab。
- 不要把微信 openid 或 unionid 展示给普通用户。
- 手机号只展示脱敏值，例如 `138****8000`。
- 登录页保持工具型，不做营销页。
- 主按钮使用当前 App 主蓝或绿色体系，错误和冲突才使用红色。
