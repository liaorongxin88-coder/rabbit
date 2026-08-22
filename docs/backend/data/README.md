# 后端数据结构资料

本目录保存数据库结构快照和关系图。它们用于追溯某个迁移版本，不替代 Flyway 迁移，也不能直接代表当前数据库。

- [schema-v32.md](snapshots/schema-v32.md)：Flyway V32 的业务域、数据表和关联关系快照。
- [database-erd-v24.svg](snapshots/database-erd-v24.svg)：Flyway V24 实体关系图。
- [database-relationships-v24.svg](snapshots/database-relationships-v24.svg)：Flyway V24 跨域关系和报表血缘图。

V24 ERD 的 SVG 元数据记录了 Graphviz 15.1.1，但原始 `.dot` 文件未保留；关系图的生成源也未保留。因此这两张图不能可靠再生成，只能作为历史快照。V32 Markdown 的来源是 Flyway V1 至 V32 和当时的实体类核对结果。

结构变更必须新增 `backend/rabbit-boot/src/main/resources/db/migration/` 下的迁移文件。需要发布新快照时，新增带 Flyway 版本号的文件，不覆盖旧快照。迁移规则见 [数据库和迁移](../modules/data-and-migrations.md)。
