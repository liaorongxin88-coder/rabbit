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
./rabbit bootstrap
./rabbit run dev
```

`./rabbit` 是跨机器、跨开发环节的统一入口；`make bootstrap`、`make run` 等目标是它的可选快捷别名。首次运行或升级 Flutter、Android Studio、JDK 后执行 `./rabbit bootstrap`。它会动态查找 Flutter、JDK 21 和 Android SDK，配置 Flutter，并生成只在本机生效的 Android Studio `GRADLE_LOCAL_JAVA_HOME` 配置。然后在 Android Studio 的 `Settings > Build, Execution, Deployment > Build Tools > Gradle` 中将 Gradle JDK 选为 `GRADLE_LOCAL_JAVA_HOME`。

解析优先级为当前 shell 的 `RABBIT_*` 显式覆盖、本机 `config/env/toolchain.local.env`、标准工具链环境变量、项目 FVM/Android `local.properties`、`PATH` 和各系统常见安装位置。自动发现失败时：

```bash
cp config/env/toolchain.local.env.example config/env/toolchain.local.env
# 只填写当前机器无法自动发现的 Flutter/JDK/Android SDK 路径
./rabbit bootstrap
```

`toolchain.local.env` 只配置本地构建工具链且不会提交；App 后端地址仍由下面的 `dev/test/prod` 编译期配置管理。

## 快捷开发命令

```bash
./rabbit doctor                  # 输出实际解析到的工具链与 Flutter doctor
./rabbit deps                    # flutter pub get
./rabbit run dev -d <device-id>  # 开发环境运行
./rabbit analyze                 # 静态检查
./rabbit test                    # test 环境单元/组件测试
./rabbit check                   # analyze + test
./rabbit verify                  # check + dev debug APK
./rabbit apk test --release      # 测试环境 release APK
./rabbit release aab             # 正式 AAB
```

使用 Make 时，对应入口为 `make doctor`、`make check`、`make verify`、`make run ENV=dev DEVICE=<device-id>`、`make apk ENV=test MODE=release` 和 `make aab`。所有 Android 相关入口都会在执行时重新解析 JDK 21 和 Android SDK，不依赖某台机器提交的绝对路径。

Android Studio 打开 `flutter_app/` 后，Run/Debug Configurations 里使用：

- `Rabbit Dev`：本地开发，`dev` flavor，默认后端 `http://10.0.2.2:8080`。
- `Rabbit Test`：测试环境，Android `staging` flavor，读取 `config/env/test.env`。
- `Rabbit Release`：正式环境，Android `prod` flavor，读取本地 `config/env/prod.env`。
- `Rabbit Tests`：运行 `test/` 目录下的 Flutter widget/unit tests。

真机调试时，将 `config/env/dev.env` 里的后端地址改为电脑局域网 IP，并保证手机和电脑在同一网络。不要把本机私有地址提交到 `test.env` 或正式环境配置。

## 环境配置与打包

后端地址通过 Flutter 编译期变量 `RABBIT_API_BASE_URL` 注入，配置文件放在 `config/env/`：

- `config/env/dev.env`：本地开发环境，默认 `http://10.0.2.2:8080`，提交默认值。
- `config/env/test.env`：测试环境，默认 `http://10.0.2.2:8080`，提交默认值，后续可改为测试服务地址。
- `config/env/prod.env`：正式环境，本地创建，不提交。先从 `config/env/prod.env.example` 复制再填写真实地址。
- `config/env/release.env`：旧脚本兼容配置，本地创建，不提交。

配置读取优先级：

1. `--dart-define` / `--dart-define-from-file` 注入的 `RABBIT_API_BASE_URL`。
2. APK asset 中的 `config/env/dev.env` 或 `config/env/test.env`。
3. 代码兜底默认值。

`RABBIT_CARRIER_AUTH_ENABLED` 控制一键登录入口，所有已提交环境默认 `false`。只有当前 flavor
已经在阿里云登记包名和签名、官方 Android AAR 已接入且后端号码认证也已启用时才设为
`true`。一键登录只允许连接 HTTPS 后端；使用 HTTP 的本地 `dev` / `test` 环境即使误开开关
也不会查询运营商能力、显示入口或发送认证凭证。交互授权的超时由未来接入的原生 SDK
adapter 在用户操作或网络认证阶段报告，Flutter 不对整个授权页面设置固定墙钟超时。
SDK 不可用、用户取消或授权失败时继续使用短信验证码登录。

不要在 Android Studio Run Configuration 的 `Environment variables` 里设置 `RABBIT_API_BASE_URL`，Flutter Android App 运行时不会按这种方式读取它。修改 `config/env/*.env` 后需要 Stop 当前 App 并重新 Run，不能只 hot reload。

推荐用统一入口打包：

```bash
./rabbit apk dev --debug
./rabbit apk test --release
./rabbit apk prod --release
```

正式发布和包体分析：

```bash
./rabbit release aab
./rabbit release apk
./rabbit release size
```

release buildType 启用 R8 和资源收缩。`build_release.sh` 会保存本地 Dart 符号；正式签名凭据仅通过 `RABBIT_ANDROID_*` 环境变量注入，不提交密钥。未提供签名变量时产物使用 debug 签名，只能用于本地 release 验证。
发布脚本优先读取 `config/env/prod.env`；尚未迁移的本地环境会临时兼容 `config/env/release.env` 并输出提示。

也可以直接传给 Flutter：

```bash
flutter build apk --debug --flavor dev --dart-define-from-file=config/env/dev.env
flutter build apk --release --flavor staging --dart-define-from-file=config/env/test.env
flutter build apk --release --flavor prod --dart-define-from-file=config/env/prod.env
```

`dev` / `test` / `prod` 是脚本层环境名；Android flavor 为 `dev` / `staging` / `prod`。旧的 `release` 环境名只作为兼容别名。`--debug` / `--profile` / `--release` 是 Flutter 构建模式。

Android 包名：

- `dev`：`com.rabbit.app.flutter.dev`
- `staging`：`com.rabbit.app.flutter.test`
- `prod`：`com.rabbit.app.flutter`

因此 Dev/Test/Release 可以在同一台设备上并存，便于 Android Studio 调试和正式包回归。

`pubspec.yaml` 已设置 `flutter.default-flavor: dev`，未显式指定 flavor 的 Flutter 工具默认走开发 flavor；正式包仍必须显式使用脚本层 `prod` 环境和正式环境文件。

## 验证

```bash
cd flutter_app
./rabbit check
./rabbit apk dev --debug
```

默认要求：

- Flutter 代码改动至少跑 `./rabbit analyze`。
- model、repository、provider 或业务逻辑改动跑 `./rabbit test`。
- Android 构建配置或依赖改动跑 `./rabbit apk dev --debug`。

## 目录

- `lib/src/config/`：应用配置和静态应用文案。
- `lib/src/data/services/<capability>/`：按网络、认证、NFC、本地存储等技术能力归档的底层服务。
- `lib/src/data/repositories/<business>/`：按认证、兔舍、笼位、兔只、批次、繁育等业务归档的后端数据访问。
- `lib/src/domain/<business>/`：按业务归档的纯数据模型。
- `lib/src/routing/`：go_router 路由和守卫。
- `lib/src/ui/<feature>/`：按功能归档，并用 `screens/`、`sheets/`、`widgets/`、`view_models/` 区分界面职责。

更详细的架构说明见 `lib/src/README.md`。

## 关键约定

- Android package id 保持 `com.rabbit.app.flutter`。
- Dev/Staging flavor 使用 `.dev` / `.test` 后缀，正式 `prod` flavor 保持 `com.rabbit.app.flutter`。
- 路由集中在 `lib/src/routing/routes.dart`。
- API 请求通过 repository 和 `data/services/network/client.dart`，页面不直接创建 `Dio`。
- 登录后请求带 `Authorization: Bearer <token>`。
- 兔舍域请求带 `X-House-Id: <houseId>`。
- `ApiResponse.code != 0` 必须作为业务失败展示。
- 视觉 token 集中在 `lib/src/ui/core/theme.dart`。
- 新增兔只必须从具体笼位入口发起，不能做全局无上下文新增。

## 规则入口

- `flutter_app/.rule`：当前 Flutter 工程规则。
- `flutter_app/AGENTS.md`、`flutter_app/CLAUDE.md`：Agent 兼容入口，内容应指回 `.rule`。

更多项目文档见 [../docs/flutter_app/README.md](../docs/flutter_app/README.md) 和 [../docs/README.md](../docs/README.md)。
