# 平台管理后台

## 产品边界

Admin 面向平台管理员，不是商户业务后台。

当前范围：

- 平台管理员登录和退出。
- 商户列表、创建、编辑、启用和停用；创建商户时同步创建初始业务账号。
- 在商户下新增业务账号；每个业务账号只属于一个商户。
- 只读查看商户概览、兔舍、笼位、兔只和近期审计记录。

不在第一版范围内：

- 计费套餐。
- 商户自助注册。
- 客服代运营或业务数据代编辑。
- 跨商户生产数据写入。

## API 边界

- 平台登录：`POST /api/admin/auth/login`
- 平台请求：`/api/admin/**`
- 请求层：`admin/src/lib/request.ts`
- API 方法：`admin/src/api/`

Admin 请求必须使用平台管理员 JWT，不发送 `X-House-Id`，不复用普通业务登录。

## UI 和工程规则

- 工程规则：`admin/.rules`
- 视觉和交互规则：`admin/DESIGN.md`
- 页面放在 `admin/src/pages/`
- 业务组件放在 `admin/src/components/`
- 通用 primitive 放在 `admin/src/components/ui/`

涉及 UI、布局、文案、动效、表格、弹窗或响应式改动时，先读 `admin/DESIGN.md`。

## 验证

```bash
pnpm --dir admin lint
pnpm --dir admin build
```

登录或请求层改动建议再用本地 dev server 验证 `/api/admin/auth/login`。
