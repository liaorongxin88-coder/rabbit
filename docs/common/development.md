# 本地开发

## 环境要求

- Docker Desktop 或 Docker Engine
- JDK 21
- Maven 3.9+
- Flutter SDK
- Android Studio 和 Android SDK
- Node.js、pnpm

macOS + Homebrew 后端开发建议固定 JDK 21：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

## 一键启动后端环境

仓库根目录：

```bash
docker compose up -d --build
```

默认启动 MySQL 和 backend：

- Backend: `http://localhost:8080`
- MySQL: `localhost:3306`
- 数据库: `rabbit_app`
- MySQL root 密码: `rabbit_root`

只刷新后端容器且保留 MySQL 数据：

```bash
docker compose up -d --build --no-deps backend
```

## 后端本地运行

```bash
cd backend
mvn spring-boot:run
```

默认配置在 `backend/src/main/resources/application.yml`，生产或多人环境用环境变量覆盖数据库、JWT 和管理员 bootstrap 配置。

常用打包命令：

```bash
cd backend
mvn -DskipTests package
java -jar target/rabbit-backend-0.0.1-SNAPSHOT.jar
```

## Flutter Android 客户端

```bash
cd flutter_app
./rabbit bootstrap
./rabbit run dev
```

`./rabbit` 会按当前环境、本机忽略配置、项目配置和常见安装位置动态解析 Flutter、JDK 21 与 Android SDK，不需要在仓库中写死机器路径。运行 `./rabbit doctor` 可查看实际使用的工具链。

常用检查：

```bash
cd flutter_app
./rabbit check
./rabbit apk dev --debug
```

默认 Android 模拟器访问本机后端地址为 `http://10.0.2.2:8080`。真机调试时改为电脑局域网 IP，并保证手机和电脑在同一网络。

Flutter 代码结构和规则见 `flutter_app/.rule`、[../flutter_app/README.md](../flutter_app/README.md) 与 [../../flutter_app/README.md](../../flutter_app/README.md)。

## 平台管理后台

```bash
pnpm --dir admin install
pnpm --dir admin dev
```

Admin dev server 默认通过 Vite proxy 访问本机 backend。若需要指定其它后端，使用 `VITE_API_BASE_URL`。

默认平台管理员由后端 bootstrap 创建：

- 用户名: `admin`
- 密码: `admin123456`

生产环境必须覆盖 `APP_ADMIN_BOOTSTRAP_USERNAME`、`APP_ADMIN_BOOTSTRAP_PASSWORD`，或设置 `APP_ADMIN_BOOTSTRAP_ENABLED=false` 后通过安全流程创建账号。

Admin 工程规则见 `admin/.rules`、[../admin/README.md](../admin/README.md) 与 [../../admin/README.md](../../admin/README.md)。

## 演示脚本

后端启动后可运行接口演示脚本：

```powershell
.\tools\demo_flow.ps1 -BaseUrl "http://localhost:8080"
.\tools\demo_flow_full.ps1 -BaseUrl "http://localhost:8080"
```

脚本会通过 API 创建演示数据，比直接导入 SQL 更接近真实业务路径。
