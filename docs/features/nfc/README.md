# NFC 标签与碰一下选择

- 状态：写标签能力已交付；剩余优化项和四个选择入口待业务确认
- 核对日期：2026-09-02
- 适用范围：Backend、Flutter App

本专题记录兔笼 NFC 标签的载荷格式、初始化写入流程，以及各业务表单里「碰一下选择」的接入方式。

## 文档

- [design.md](design.md)：载荷与绑定模型、初始化会话能力、采集独占窗口的问题诊断、统一选择器设计和待确认项。
- [interaction.md](interaction.md)：七条交互性质的落法、共享选择器构件、四个入口的具体交互和初始化交互。

需求来源是飞书「鸿兔项目开发 需求收集与管理」表中的伞状需求 `recvqgPNkRN69z`（NFC功能实现）及其 13 条子需求。

当前行为以 `backend/rabbit-production/src/main/java/com/rabbit/app/modules/nfc/`、
`app/lib/src/data/services/nfc/`、`app/lib/src/ui/nfc/` 和相关回归测试为准。
