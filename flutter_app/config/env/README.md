# 环境配置

此目录包含两类配置：

- `dev.env`、`test.env`、`release.env`：注入 APK 的 App 编译期配置。
- `toolchain.local.env`：可选的本机构建工具链覆盖，不注入 APK且不提交。

本地 JDK 通常可自动发现。只有自动发现失败时，才从 `toolchain.local.env.example` 复制并填写 JDK 21 路径，然后运行 `./scripts/setup_android_env.sh`。环境变量 `RABBIT_JAVA_HOME` 的优先级高于本地文件。

Flutter 客户端通过编译期变量读取后端地址：

```dart
String.fromEnvironment('RABBIT_API_BASE_URL')
```

因此配置文件必须在 `flutter run` 或 `flutter build` 时传入，不能只设置 shell 环境变量。

为兼容 Android Studio 没有正确传递 `--dart-define-from-file` 的情况，`dev.env` 和 `test.env` 也会作为 Flutter asset 打进 APK。读取优先级是：

1. `--dart-define` / `--dart-define-from-file` 注入的 `RABBIT_API_BASE_URL`。
2. APK asset 中的 `config/env/dev.env` 或 `config/env/test.env`。
3. 代码兜底默认值。

Android Studio Run Configuration 里的 `Environment variables` 不等同于 Flutter `--dart-define`，不要用它配置后端地址。修改 env 文件后需要 Stop 当前 App 并重新 Run。

## 当前文件

- `dev.env`：本地开发配置，默认指向 Android 模拟器访问宿主机的地址 `http://10.0.2.2:8080`。
- `test.env`：测试环境配置，默认暂时也指向 `http://10.0.2.2:8080`；有独立测试后端后改这里。
- `release.env.example`：正式环境模板。复制为 `release.env` 后填写真实正式后端地址。
- `release.env`：正式环境本地配置，不提交。

环境文件还应声明 `RABBIT_BUILD_ENV`，用于排查当前包来自哪个环境：

```properties
RABBIT_BUILD_ENV=dev
RABBIT_API_BASE_URL=http://10.0.2.2:8080
```

## Android flavor

Android 工程提供三个环境 flavor：

- `dev`：包名 `com.rabbit.app.flutter.dev`，应用名 `智能兔管家 Dev`。
- `staging`：对应脚本层 `test` 环境，包名 `com.rabbit.app.flutter.test`，应用名 `智能兔管家 Test`。
- `releaseEnv`：对应脚本层 `release` 环境，包名 `com.rabbit.app.flutter`，应用名 `智能兔管家`。

这三个包可以同时安装在同一台设备上。Android Studio 的共享 Run/Debug Configurations 已经放在 `.run/`：

- `Rabbit Dev`
- `Rabbit Test`
- `Rabbit Release`
- `Rabbit Tests`

`Rabbit Release` 读取 `config/env/release.env`。

`pubspec.yaml` 已设置 `flutter.default-flavor: dev`，未显式指定 flavor 的 Flutter 工具默认走开发 flavor。正式构建必须显式使用脚本层 `release` 环境和正式环境文件。Android flavor 不能直接命名为 `release`，因为它会和 Flutter/Android 的 `--release` build type 冲突，所以内部 flavor 名为 `releaseEnv`。

## 命令

开发环境调试包：

```bash
./scripts/build_apk.sh dev --debug
```

测试环境 release 包：

```bash
./scripts/build_apk.sh test --release
```

正式环境打包：

```bash
cp config/env/release.env.example config/env/release.env
# 编辑 config/env/release.env
./scripts/build_apk.sh release --release
```

也可以直接使用 Flutter 参数：

```bash
flutter build apk --debug --flavor dev --dart-define-from-file=config/env/dev.env
flutter build apk --release --flavor staging --dart-define-from-file=config/env/test.env
flutter build apk --release --flavor releaseEnv --dart-define-from-file=config/env/release.env
```

`dev` / `test` / `release` 是脚本层环境名；Android flavor 为 `dev` / `staging` / `releaseEnv`。`--debug` / `--profile` / `--release` 表示 Flutter 构建模式。三者不要混为一谈。
