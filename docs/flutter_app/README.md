# Flutter App 文档

Flutter Android 客户端是当前移动端重构方向，源码位于 `flutter_app/`。

## 先读

- 源码入口：[../../flutter_app/README.md](../../flutter_app/README.md)
- 工程规则：`../../flutter_app/.rule`
- 架构概览：[../common/architecture.md](../common/architecture.md)
- 测试验证：[../common/testing.md](../common/testing.md)

## 模块索引

- [modules/rabbit-management-flow.md](modules/rabbit-management-flow.md)：兔舍、笼位、兔只的多级流程和新增兔只规则。

## 常用命令

```bash
cd flutter_app
flutter analyze
flutter test
flutter build apk --debug
```

默认 Android 模拟器后端地址为 `http://10.0.2.2:8080`。
