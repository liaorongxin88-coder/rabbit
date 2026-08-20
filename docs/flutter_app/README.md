# Flutter App 文档

Flutter Android 客户端是当前移动端入口，源码位于 `app/`。

## 先读

- 源码入口：[../../app/README.md](../../app/README.md)
- 工程规则：`../../app/.rule`
- 架构概览：[../common/architecture.md](../common/architecture.md)
- 测试验证：[../common/testing.md](../common/testing.md)

## 模块索引

- [modules/rabbit-management-flow.md](modules/rabbit-management-flow.md)：兔场、笼位、兔只的多级流程和新增兔只规则。
- [modules/auth-phone-wechat-flow.md](modules/auth-phone-wechat-flow.md)：手机号短信、一键登录客户端边界、零兔场、精确邀请和首次设置密码，以及微信绑定规划。
- [modules/window-state-transitions.md](modules/window-state-transitions.md)：App 窗口状态转移图、Navigator 分层和 Riverpod 状态派生树。
- [design/app-interface-layout-board.excalidraw](design/app-interface-layout-board.excalidraw)：可编辑的 App 手机窗口、路由连线和 Sheet/Dialog 拖拽排布板。
- [design/app-navigation-tree.excalidraw](design/app-navigation-tree.excalidraw)：页面层级、跨页面核心流程和临时覆盖层的清晰树形关系板。
- [design/app-complete-state-machine.excalidraw](design/app-complete-state-machine.excalidraw)：首选的 App 可见窗口状态机派生全图，包含 Provider、Router、页面异步态、Outbound 和 NFC 状态机。

## 常用命令

```bash
cd app
./rabbit check
./rabbit apk dev --debug
```

默认 Android 模拟器后端地址为 `http://10.0.2.2:8080`。Android Studio 可直接使用项目中的
共享运行配置。
