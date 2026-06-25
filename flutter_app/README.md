# Rabbit Flutter App

`flutter_app/` 是 Rabbit 当前移动端重构方向，面向 Android 客户端。它使用真实后端 API，不是静态原型。

## 当前范围

- 登录 / 注册
- 会话恢复
- 兔舍列表、创建和选择
- 首页四栏导航
- 今日预警
- 兔舍详情
- 笼位管理
- 兔只查看和编辑
- 从具体笼位进入新增兔只
- 未迁移模块的占位入口

NFC、后台通知、离线待提交和全量批次操作页尚未迁移，除非任务明确要求，不要顺手改后端契约或恢复原生 Android。

## 技术栈

- Flutter
- flutter_riverpod
- go_router
- dio
- flutter_secure_storage
- shared_preferences
- uuid

## 本地运行

```bash
cd flutter_app
flutter pub get
flutter run
```

Android 模拟器默认后端地址：`http://10.0.2.2:8080`。

真机调试时，将后端地址改为电脑局域网 IP，并保证手机和电脑在同一网络。

## 验证

```bash
cd flutter_app
flutter analyze
flutter test
flutter build apk --debug
```

默认要求：

- Flutter 代码改动至少跑 `flutter analyze`。
- model、repository、provider 或业务逻辑改动跑 `flutter test`。
- Android 构建配置或依赖改动跑 `flutter build apk --debug`。

## 目录

- `lib/src/config/`：应用配置。
- `lib/src/data/services/`：HTTP、会话存储等底层服务。
- `lib/src/data/repositories/`：面向功能的后端数据访问。
- `lib/src/domain/models/`：纯数据模型。
- `lib/src/routing/`：go_router 路由和守卫。
- `lib/src/ui/`：页面、组件、主题和 view model。

更详细的架构说明见 `lib/src/README.md`。

## 关键约定

- Android package id 保持 `com.rabbit.app.flutter`。
- 路由集中在 `lib/src/routing/router.dart`。
- API 请求通过 repository 和 `data/services/api_client.dart`，页面不直接创建 `Dio`。
- 登录后请求带 `Authorization: Bearer <token>`。
- 兔舍域请求带 `X-House-Id: <houseId>`。
- `ApiResponse.code != 0` 必须作为业务失败展示。
- 视觉 token 集中在 `lib/src/ui/core/themes/app_theme.dart`。
- 新增兔只必须从具体笼位入口发起，不能做全局无上下文新增。

## 规则入口

- `flutter_app/.rule`：当前 Flutter 工程规则。
- `flutter_app/AGENTS.md`、`flutter_app/CLAUDE.md`：Agent 兼容入口，内容应指回 `.rule`。

更多项目文档见 [../docs/flutter_app/README.md](../docs/flutter_app/README.md) 和 [../docs/README.md](../docs/README.md)。
