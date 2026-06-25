# 业务设计基准

## 基准资料

业务原始设计资料保留在 `docs/archive/legacy/`：

- [../archive/legacy/养兔管理系统完整技术文档.docx](../archive/legacy/养兔管理系统完整技术文档.docx)
- [../archive/legacy/养兔管理系统完整技术文档.extracted.md](../archive/legacy/养兔管理系统完整技术文档.extracted.md)
- [../archive/legacy/养兔管理系统完整技术文档.extracted.json](../archive/legacy/养兔管理系统完整技术文档.extracted.json)
- [../archive/legacy/README.md](../archive/legacy/README.md)

维护时优先阅读抽取版 Markdown，遇到格式、表格或语义不清时再回到 Word 原件。

## 当前实现范围

当前实现覆盖或保留的主要业务：

- 多兔舍、成员和权限隔离。
- 笼位维护、兔只录入、兔只状态流转和后备管理。
- 批次流程：催情、配种、摸胎、备产、分娩、断奶、出售、转后备。
- 事件提醒：定时扫描、提醒状态、确认闭环和扫描日志。
- 投喂、用药、异常、称重、库存、销售和报表。
- NFC 标签绑定和解析。
- 审计、TraceId、幂等和 CSV 导出。
- 平台管理员、商户管理和商户概览。

## 实现对齐说明

- 微信登录以 `sys_user.openid` 作为稳定身份，`/api/auth/wechat-login` 兼容传 `openid` 或 `code`。
- 核心生产和状态主记录直接冗余 `house_id`，包括 `pregnancy_check_records`、`parturition_records`、`prepartum_records`、`weaning_records`、`rabbit_status_history`。
- 明细表仍通过父表关联归属兔舍，例如 `weaning_record_allocations` 通过 `weaning_records` 关联，`sale_order_items` 通过 `sale_orders` 关联。
- 当前数据库演进以 Flyway 迁移为准，抽取版文档中的表结构仅作业务语义参考。
- Flutter 客户端中的兔舍管理已拆为多级流程，新增兔只从具体笼位进入。

## 使用原则

做业务变更时按以下顺序判断：

1. 当前代码和 Flyway 迁移代表真实运行状态。
2. `docs/` 和子项目 README 代表当前维护约定。
3. 抽取版技术文档代表业务语义和历史设计意图。
4. Word 原件用于核对抽取版遗漏或格式问题。

如果业务文档和代码冲突，不要只改文档或只改代码。先确认当前产品期望，再同步实现、迁移和文档。
