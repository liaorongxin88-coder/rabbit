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
cp .env.example .env
# 将所有 change-me 占位值替换为分别生成的稳定随机值，例如 openssl rand -hex 32
# 需要真实短信或一键登录时，再分别补齐对应阿里云参数并开启开关
docker compose up -d --build
```

根目录的 `.env` 由 Docker Compose 自动加载，并已被 Git 忽略。常驻测试应保持
`APP_JWT_SECRET`、`APP_ADMIN_JWT_SECRET`、`APP_PHONE_HASH_SECRET`、
`APP_SMS_CODE_SECRET` 和 `APP_PHONE_ONE_TAP_TOKEN_HASH_SECRET` 稳定；其中应用 JWT 与管理
JWT 密钥必须不同。Compose 会拒绝缺少前三个必需密钥的配置，后端也会拒绝空值、短值和
`change-me` 等公开占位值。`APP_SMS_CODE_SECRET` 仅在 `APP_SMS_ENABLED=true` 时必需。
手机号摘要密钥变化会断开已有手机号身份，短信和一键登录摘要密钥变化会使尚在有效期内的记录
失效。
短信和号码认证使用两套独立 RAM 凭证；不要让号码认证配置回退到
`ALIBABA_CLOUD_ACCESS_KEY_*`。需要从局域网设备访问后端时，将
`BACKEND_BIND_ADDRESS` 设置为 `0.0.0.0`；完成测试后恢复为 `127.0.0.1`。
局域网直连和默认部署保持 `APP_FORWARD_HEADERS_STRATEGY=none`，避免客户端伪造
`X-Forwarded-For` 绕过匿名限流。只有后端仅能被可信反向代理访问、且代理会覆盖客户端提供的
转发头时，才可将它改为 `framework`。

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
set -a
source ../.env
set +a
mvn spring-boot:run
```

直接启动同样不会回退到仓库内置密钥；至少需要提供彼此不同的 `APP_JWT_SECRET`、
`APP_ADMIN_JWT_SECRET` 和稳定的 `APP_PHONE_HASH_SECRET`。

默认配置在 `backend/src/main/resources/application.yml`，生产或多人环境用环境变量覆盖数据库、JWT 和管理员 bootstrap 配置。

常用打包命令：

```bash
cd backend
mvn -DskipTests package
java -jar target/rabbit-backend-0.0.1-SNAPSHOT.jar
```

## Flutter Android 客户端

```bash
cd app
./rabbit bootstrap
./rabbit run dev
```

`./rabbit` 会按当前环境、本机忽略配置、项目配置和常见安装位置动态解析 Flutter、JDK 21 与 Android SDK，不需要在仓库中写死机器路径。运行 `./rabbit doctor` 可查看实际使用的工具链。

常用检查：

```bash
cd app
./rabbit check
./rabbit apk dev --debug
```

默认 Android 模拟器访问本机后端地址为 `http://10.0.2.2:8080`。真机调试时改为电脑局域网 IP，并保证手机和电脑在同一网络。

一键登录的后端换号链路可在本地用测试 Provider 做自动化验证，但真实取号不能靠模拟器完成。
它还需要在阿里云控制台登记当前 flavor 的包名和签名、导入官方 Android AAR，并在带 SIM、
开启蜂窝数据且关闭 VPN 的 Android 真机上验证。客户端与后端开关都默认关闭，短信登录始终保留。

Flutter 代码结构和规则见 `app/.rule`、[../flutter_app/README.md](../flutter_app/README.md) 与 [../../app/README.md](../../app/README.md)。

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
