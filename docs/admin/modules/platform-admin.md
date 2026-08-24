# 平台管理端与业务工作台

## 产品边界

Admin 包含两个相互隔离的界面：

- 平台管理端：使用平台管理员身份管理业务账号、兔场状态和平台概览。
- 业务工作台：使用普通业务账号选择自己直接加入的兔场并处理生产业务。

当前范围：

- 平台管理员登录和退出。
- 业务账号列表、脱敏手机号、账号状态和可访问兔场数量。
- 兔场列表、创建、编辑、启用、停用和只读生产概览。
- `house_users` 直接成员关系、成员角色、成员状态和共同 `OWNER`。
- 业务账号登录后在 `/workspace/**` 选择有权访问的兔场。
- 按当前兔场权限处理笼位、兔只、生产批次和成员。
- 上传、发布和撤回 Flutter APK，让已安装的手机端在软件内升级。

不在当前范围：

- 计费套餐和公开自助购买。
- 客服代替兔场成员编辑生产数据。
- 平台身份绕过 `house_users` 执行跨兔场生产写入。

## 身份与请求边界

平台管理端：

- 登录：`POST /api/admin/auth/login`
- 请求：`/api/admin/**`
- 使用独立平台 JWT。
- 不发送 `X-House-Id`。
- 不复用普通业务登录态。

业务工作台：

- 使用普通业务 JWT。
- 从 `GET /api/houses` 取得当前账号直接可访问的兔场。
- 所有兔场范围请求发送 `X-House-Id`。
- 后端每次重新校验账号、兔场、成员状态和角色。

手机号是账号身份，不是兔场属性。平台和工作台只展示脱敏手机号，不提供手机号模糊枚举。
共同 `OWNER` 以多条有效 `house_users.role=OWNER` 表示，界面不能假定每个兔场只有一个
所有者。

## 平台兔场写入契约

- `POST /api/admin/farms` 创建兔场。请求包含 `name`、`layoutRows`、`layoutCols`、
  `layoutLayers`、可选 `remark`、`requestId`，并且必须且只能传
  `ownerUserId` 或 `ownerPhone` 之一。
- `ownerUserId` 必须指向启用中的业务用户。`ownerPhone` 会按现有手机号身份规则解析；
  未注册手机号只预置不可使用密码登录的业务身份，完成短信或一键登录验证后会进入同一身份。
- 创建在同一事务中写入兔场、初始 `OWNER` 成员关系和初始笼位，并按平台管理员与
  `requestId` 幂等；同一 `requestId` 复用于不同载荷会返回冲突。
- `PUT /api/admin/farms/{farmId}` 只更新 `name` 和 `remark`。布局、笼位、兔只和生产数据
  不通过平台兔场资料接口修改。
- `PUT /api/admin/farms/{farmId}/status` 独立启用或停用兔场；启用前必须存在至少一名有效
  `OWNER`。
- `POST /api/admin/app-releases` 上传某一渠道的 APK 草稿。发布后，
  `GET /api/app/updates/check` 对低于该内部版本号的客户端返回下载地址。
- 公开下载只提供该渠道当前最新已发布包；历史已发布版本仍参与强制更新判断，但不能用旧 ID 下载。
- `PUT /api/admin/app-releases/{id}` 可改更新说明和强制更新。`GET /api/admin/app-releases/{id}/apk` 供管理员核对安装包。

## UI 和工程规则

- 工程规则：`admin/.rules`
- 视觉和交互规则：`admin/DESIGN.md`
- 页面放在 `admin/src/pages/`
- 业务组件放在 `admin/src/components/`
- 通用 primitive 放在 `admin/src/components/ui/`
- 平台与业务会话必须使用不同的存储键和请求实例。

涉及 UI、布局、文案、动效、表格、弹窗或响应式改动时，先读 `admin/DESIGN.md`。

## 验证

```bash
pnpm --dir admin lint
pnpm --dir admin build
```

登录或请求层改动还应验证平台 JWT 不会进入业务请求、业务 JWT 不会进入平台请求，以及
切换兔场后 `X-House-Id` 与可见权限同步更新。
