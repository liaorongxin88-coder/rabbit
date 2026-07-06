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
./scripts/run_flutter.sh dev
```

Android Studio 打开 `flutter_app/` 后，Run/Debug Configurations 里使用：

- `Rabbit Dev`：本地开发，`dev` flavor，默认后端 `http://10.0.2.2:8080`。
- `Rabbit Test`：测试环境，Android `staging` flavor，读取 `config/env/test.env`。
- `Rabbit Release`：正式环境，Android `releaseEnv` flavor，读取本地 `config/env/release.env`。
- `Rabbit Tests`：运行 `test/` 目录下的 Flutter widget/unit tests。

真机调试时，将 `config/env/dev.env` 里的后端地址改为电脑局域网 IP，并保证手机和电脑在同一网络。不要把本机私有地址提交到 `test.env` 或正式环境配置。

## 环境配置与打包

后端地址通过 Flutter 编译期变量 `RABBIT_API_BASE_URL` 注入，配置文件放在 `config/env/`：

- `config/env/dev.env`：本地开发环境，默认 `http://10.0.2.2:8080`，提交默认值。
- `config/env/test.env`：测试环境，默认 `http://10.0.2.2:8080`，提交默认值，后续可改为测试服务地址。
- `config/env/release.env`：正式环境，本地创建，不提交。先从 `config/env/release.env.example` 复制再填写真实地址。

配置读取优先级：

1. `--dart-define` / `--dart-define-from-file` 注入的 `RABBIT_API_BASE_URL`。
2. APK asset 中的 `config/env/dev.env` 或 `config/env/test.env`。
3. 代码兜底默认值。

不要在 Android Studio Run Configuration 的 `Environment variables` 里设置 `RABBIT_API_BASE_URL`，Flutter Android App 运行时不会按这种方式读取它。修改 `config/env/*.env` 后需要 Stop 当前 App 并重新 Run，不能只 hot reload。

推荐用脚本打包：

```bash
./scripts/build_apk.sh dev --debug
./scripts/build_apk.sh test --release
./scripts/build_apk.sh release --release
```

也可以直接传给 Flutter：

```bash
flutter build apk --debug --flavor dev --dart-define-from-file=config/env/dev.env
flutter build apk --release --flavor staging --dart-define-from-file=config/env/test.env
flutter build apk --release --flavor releaseEnv --dart-define-from-file=config/env/release.env
```

`dev` / `test` / `release` 是脚本层环境名；Android flavor 为 `dev` / `staging` / `releaseEnv`。`--debug` / `--profile` / `--release` 是 Flutter 构建模式。测试环境也可以打 release APK。Android flavor 不能直接命名为 `release`，因为它会和 Flutter/Android 的 `--release` build type 冲突。

Android 包名：

- `dev`：`com.rabbit.app.flutter.dev`
- `staging`：`com.rabbit.app.flutter.test`
- `releaseEnv`：`com.rabbit.app.flutter`

因此 Dev/Test/Release 可以在同一台设备上并存，便于 Android Studio 调试和正式包回归。

`pubspec.yaml` 已设置 `flutter.default-flavor: dev`，未显式指定 flavor 的 Flutter 工具默认走开发 flavor；正式包仍必须显式使用脚本层 `release` 环境和正式环境文件。

## 验证

```bash
cd flutter_app
flutter analyze
./scripts/test_flutter.sh
./scripts/build_apk.sh dev --debug
```

默认要求：

- Flutter 代码改动至少跑 `flutter analyze`。
- model、repository、provider 或业务逻辑改动跑 `./scripts/test_flutter.sh`。
- Android 构建配置或依赖改动跑 `./scripts/build_apk.sh dev --debug`。

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
- Dev/Staging flavor 使用 `.dev` / `.test` 后缀，正式 `releaseEnv` flavor 保持 `com.rabbit.app.flutter`。
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
