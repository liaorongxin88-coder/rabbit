# Flutter 兔场管理流程

当前 Flutter 客户端采用多级兔场管理流程：

```text
兔场列表 -> 兔场详情 -> 笼位管理 / 兔只管理
```

## 路由

- `/houses`
- `/houses/:houseId`
- `/houses/:houseId/cages`
- `/houses/:houseId/rabbits`

## 新增兔只

新增兔只必须从具体笼位进入：

```text
兔场详情 -> 笼位管理 -> 选择具体笼位 -> 录入兔只
```

不要在兔只列表页新增全局无上下文的“新增兔只”入口。兔只列表页主要用于查看和编辑已有兔只。

## 主题和交互

- 正常兔只交互使用主蓝、田野绿和辅助 amber。
- 红色只用于错误或破坏性状态。
- 视觉 token 放在 `app/lib/src/ui/core/themes/app_theme.dart`。
- 长兔场名、用户名和事件名称必须做省略或换行约束，不能撑破移动端布局。

## 代码落点

- 路由：`app/lib/src/routing/router.dart`
- 兔场页面：`app/lib/src/ui/houses/widgets/`
- 笼位管理：`app/lib/src/ui/cages/widgets/cage_management_section.dart`
- 兔只录入：`app/lib/src/ui/rabbits/widgets/rabbit_entry_flow.dart`
- 后端访问：`app/lib/src/data/repositories/`
