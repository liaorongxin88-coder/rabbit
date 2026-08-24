# Flutter App 文档

`app/` 是面向兔场业务用户的 Flutter Android 客户端。当前路由覆盖登录、首页、兔场、笼位、兔只、批次、批量出库、NFC、数据面板、个人中心、设置和软件内升级。

## 先读

- 工程规则：`../../app/.rule`
- 源码说明：[../../app/README.md](../../app/README.md)
- 总体架构：[../project/architecture.md](../project/architecture.md)
- 测试与真机验收：[../project/testing.md](../project/testing.md)

## 工程边界

源码按 `config`、`data`、`domain`、`routing` 和 `ui` 分层。网络请求从 UI 进入 repository，再由 `data/services/network/client.dart` 访问后端。路由集中在 `lib/src/routing/routes.dart`，异步状态由 Riverpod provider、controller 或 view model 管理。

业务请求使用普通用户 JWT。兔场范围请求还必须携带 `X-House-Id`，客户端入口权限不能代替后端校验。环境、Android flavor 和构建模式是三套独立概念，具体规则以 `app/.rule` 和源码 README 为准。

## 流程与设计资料

- [兔场、笼位和兔只流程](modules/rabbit-management-flow.md)
- [手机号认证和兔场进入流程](modules/auth-phone-wechat-flow.md)
- [窗口状态转移](modules/window-state-transitions.md)
- [界面排布板](design/app-interface-layout-board.excalidraw)
- [导航树](design/app-navigation-tree.excalidraw)
- [完整可见状态机](design/app-complete-state-machine.excalidraw)

批量出库和母兔生产流程的跨端规格位于 [features/](../features/README.md)。

## 常用命令

```bash
cd app
./rabbit bootstrap
./rabbit check
./rabbit apk dev --debug
```

涉及 Android 构建、真机安装或发布包时，构建成功不能替代设备安装、前台 Activity、进程和日志验证。
