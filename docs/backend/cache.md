# 后端缓存设计

## 目标与边界

业务代码只依赖项目接口，不依赖 Redis、Valkey 或 Lettuce API。第一版支持 `none`、
`redis`、`valkey` 三种 provider，其中 Redis 和 Valkey 复用同一个 RESP 协议适配器：

```text
业务服务 -> ApplicationCache -> JsonApplicationCache -> CacheBackend
                                                    -> Lettuce -> Redis / Valkey
                         -> NoopApplicationCache (provider=none)

短信服务 -> SmsVerificationStore -> LettuceSmsVerificationStore -> CacheBackend
                               -> UnavailableSmsVerificationStore (provider=none)
```

`ApplicationCache` 仅用于能够从 MySQL 或其它权威来源重新构建的读取结果。其连接失败、命令
失败或值损坏时按未命中处理。鉴权、权限、写请求幂等和分布式锁不属于这个 best-effort 接口
的适用范围。

短信验证码使用独立的 `SmsVerificationStore` 安全状态接口。它通过 Lua 原子执行预约、滑动窗口
限流、发送激活、错误次数和单次消费；任何缓存异常都失败关闭并返回 503，绝不按未命中后继续
认证。`APP_SMS_ENABLED=true` 时，provider 必须是 `redis` 或 `valkey`。

第一版采用同步 API 和单节点连接，不支持 Sentinel、Cluster、分布式锁、批量删除和防击穿
协调。短信原子脚本涉及多个 key，因此不能直接切换到 Cluster。引入这些能力时应扩展项目接口
和 key slot 设计，不能让业务模块直接使用客户端 API。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `APP_CACHE_PROVIDER` | `none` | `none`、`redis` 或 `valkey` |
| `APP_CACHE_HOST` | `127.0.0.1` | 缓存服务地址 |
| `APP_CACHE_PORT` | `6379` | 缓存服务端口 |
| `APP_CACHE_USERNAME` | 空 | ACL 用户名，可选 |
| `APP_CACHE_PASSWORD` | 空 | 密码；填写用户名时必须填写密码 |
| `APP_CACHE_DATABASE` | `0` | 逻辑数据库编号 |
| `APP_CACHE_SSL_ENABLED` | `false` | 是否启用 TLS |
| `APP_CACHE_CONNECT_TIMEOUT` | `2s` | 建立连接超时 |
| `APP_CACHE_COMMAND_TIMEOUT` | `1s` | 单条命令超时 |
| `APP_CACHE_KEY_PREFIX` | `rabbit:cache:v1` | 全局 key 前缀，格式变化时升级版本 |

生产环境应使用 ACL、密码和 TLS，并禁止将缓存端口暴露到公网。密码只放在未提交的根目录
`.env` 或部署平台的 Secret 中。

## 本地启动

先从 `.env.example` 创建根目录 `.env`，并完成其中必需的应用密钥配置。

使用 Redis：

```env
APP_CACHE_PROVIDER=redis
APP_CACHE_HOST=redis
```

```bash
docker compose --profile redis up -d --build
```

使用 Valkey：

```env
COMPOSE_PROFILES=valkey
APP_CACHE_PROVIDER=valkey
APP_CACHE_HOST=valkey
```

```bash
docker compose up -d --build
```

也可以不设置 `COMPOSE_PROFILES`，继续使用 `docker compose --profile valkey up -d --build`。
切换 provider 后需重建 backend 容器。开发用 Redis/Valkey 分别在宿主机回环地址的 `16380`、
`16381` 端口提供集成测试入口，不会监听局域网地址；服务没有持久化卷，重启会使当时尚未消费
的短信验证码失效，客户端需要重新获取。

## 业务接入

缓存 key 由命名空间和结构化片段组成。涉及租户数据时必须包含 `houseId`，还应包含会影响结果
的查询维度或数据版本，避免跨租户读取和旧格式碰撞。

```java
private static final Duration SUMMARY_TTL = Duration.ofMinutes(5);

CacheKey key = CacheKey.of("dashboard-summary", houseId, year);
return applicationCache.getOrLoad(
        key,
        DashboardSummary.class,
        SUMMARY_TTL,
        () -> dashboardMapper.selectSummary(houseId, year)
);
```

更新权威数据后，应在事务成功提交后调用 `evict`。如果无法精确失效，应使用较短 TTL 或把
数据版本放进 key；不要使用 `KEYS` 扫描删除。`getOrLoad` 不提供单飞或分布式防击穿，同一个
key 在并发未命中时可能执行多次 loader，因此 loader 必须是可安全重复的只读操作。
