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

## 常用命令

```bash
cd app
./rabbit check
./rabbit apk dev --debug
```

默认 Android 模拟器后端地址为 `http://10.0.2.2:8080`。Android Studio 可直接使用项目中的
共享运行配置。
