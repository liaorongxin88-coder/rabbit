# 测试和验证

按改动范围选择验证，不需要每次都跑全量检查。

## 后端构建

```bash
cd backend
mvn -DskipTests package
```

涉及业务逻辑、权限、迁移、MyBatis mapper 或 API 契约时，应优先跑后端测试或 E2E。

## 后端 API E2E

准备 MySQL 测试库：

```bash
mysql -uroot -e "create database if not exists rabbit_app_e2e default character set utf8mb4 collate utf8mb4_general_ci;"
```

运行：

```bash
cd backend
mvn -Pe2e verify
```

可覆盖测试库连接：

```bash
E2E_DATASOURCE_URL='jdbc:mysql://localhost:3306/rabbit_app_e2e?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' \
E2E_DATASOURCE_USERNAME=root \
E2E_DATASOURCE_PASSWORD=rabbit_root \
mvn -Pe2e verify
```

注意：E2E 会清空 `rabbit_app_e2e`，不要指向开发库或生产库。

## 接口演示回归

后端启动后：

```powershell
.\tools\demo_flow.ps1 -BaseUrl "http://localhost:8080"
.\tools\demo_flow_full.ps1 -BaseUrl "http://localhost:8080"
```

这些脚本适合验证核心业务链路：注册/登录、兔舍、笼位、兔只、批次、事件提醒和繁殖性能。

## Flutter 验证

```bash
cd flutter_app
flutter analyze
flutter test
flutter build apk --debug
```

默认规则：

- Flutter UI 或状态改动至少跑 `flutter analyze`。
- model、repository、provider 或业务逻辑改动跑 `flutter test`。
- Android 构建配置、依赖或 manifest 改动跑 `flutter build apk --debug`。

如果 Flutter SDK cache 权限导致命令失败，先修复本机 Flutter SDK/cache 权限，再判断项目代码是否有问题。

## Flutter Android 设备 E2E

批量出库 Android 测试使用真实 Flutter Dev APK、Android 模拟器、`rabbit_app` 后端和每轮隔离 fixture。runner 会在没有设备时启动第一个可用 AVD，注入测试数据，执行只读权限、人体工学、提前出售、并发冲突恢复和成功提交，并对销售单、兔只状态和请求状态做数据库断言：

前置条件是 `http://127.0.0.1:8080` 后端和 `rabbit-mysql-1` 已运行且指向本地开发库 `rabbit_app`。预检不通过时 runner 会在注入 fixture 和启动模拟器之前退出。

```bash
cd flutter_app
./scripts/android_e2e.sh
```

runner 固定要求 JDK 21。macOS/Homebrew 默认使用 `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`；其他环境通过 `RABBIT_ANDROID_E2E_JAVA_HOME` 指定，避免 Android Studio JBR 升级后破坏 Gradle 8.4 构建。

常用矩阵参数：

```bash
RABBIT_ANDROID_E2E_AVD=Medium_Phone \
RABBIT_ANDROID_E2E_TEXT_SCALE=1.0 \
RABBIT_ANDROID_E2E_PROFILE=visual-baseline \
./scripts/android_e2e.sh

RABBIT_ANDROID_E2E_AVD=Medium_Phone \
RABBIT_ANDROID_E2E_TEXT_SCALE=2.0 \
RABBIT_ANDROID_E2E_PROFILE=accessibility-stress \
./scripts/android_e2e.sh
```

`visual-baseline` 使用 100% 系统字体，作为设计还原、日常回归和对外交付截图；runner 会拒绝用其他字号伪装成该档位，并在每张截图前断言系统字号与 App 有效字号仍和测试配置一致。`accessibility-stress` 使用 200% 系统字体，并验证 App 的人体工学上限为 150%。压力档只验证可达性、换行和溢出边界，不能作为视觉还原或交付截图基准。

截图、截图清单、fixture 标识、设备物理尺寸、测试档位和数据库断言保存在 `flutter_app/build/android-e2e/<run_id>/`。脚本会验证 7 张业务流程截图完整存在，可修改模拟器字体比例并在结束时恢复；实体机默认不修改系统设置，只有显式设置 `RABBIT_ANDROID_E2E_ALLOW_DEVICE_SETTINGS=1` 才执行字体矩阵。真机 NFC、TalkBack、左右手持机误触和疲劳仍需人工验收。

## Admin 验证

```bash
pnpm --dir admin lint
pnpm --dir admin build
```

涉及登录、请求层或路由守卫时，建议启动本地 dev server 并验证平台登录：

```bash
pnpm --dir admin dev --host 127.0.0.1
curl -s -H 'Content-Type: application/json' \
  -d '{"userName":"admin","password":"admin123456"}' \
  http://127.0.0.1:5173/api/admin/auth/login
```

涉及布局、表格、弹窗和响应式时，还需要浏览器检查桌面和窄屏宽度，确认没有控制台错误、横向溢出、文本重叠或按钮不可达。

## 文档改动

纯文档改动不要求跑应用构建。建议检查：

```bash
rg -n "file:///|d:/rabbit|TODO|TBD" README.md E2E_TESTING.md CONTRIBUTING.md backend/README.md flutter_app/README.md admin/README.md docs
```

如果文档涉及命令或路径，优先用当前仓库实际文件验证路径仍存在。

## 历史 Android Instrumentation

历史原生 Android 冒烟测试曾使用：

```bash
cd android
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.baseUrl=http://10.0.2.2:8080
```

当前仓库维护重心已转向 `flutter_app/`。除非恢复原生 Android 目录，否则该命令只作为历史记录保留。
