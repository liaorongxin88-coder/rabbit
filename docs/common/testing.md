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
