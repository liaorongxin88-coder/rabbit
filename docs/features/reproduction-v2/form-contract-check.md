# 生产流程人工表单完整度检查

需求来源：飞书 Base 记录 `recvss4qXnDEIX`「生产流程中人工操作的表单分别必须字段」。

## 字段归属

母兔、操作人、批次和周期不由客户端重复填写：服务端从已加锁的周期、JWT 登录态和
`X-House-Id` 推导并写入 `repro_events`。这样既满足留痕要求，也避免客户端篡改关联对象。
客户端必须提交执行时间和动作事实；执行时间支持日期与时分，默认当前时间，也允许补录过去。

| 动作 | 客户端必填 | 服务端派生/校验 | 未执行 |
| --- | --- | --- | --- |
| 催情 | 执行时间 | 母兔、人员、批次、周期 | 下次提醒时间 |
| 配种 | 执行时间、配种方式；体配还必须选择种公兔 | 人工授精仍提供公兔选择但允许不关联；一旦选择就校验公兔资格并记录系谱 | 下次提醒时间 |
| 摸胎 | 执行时间、摸胎结论 | 不确定时还必须选择复查时间 | 下次提醒时间 |
| 备产 | 执行时间 | 母兔、人员、批次、周期 | 下次提醒时间 |
| 接产 | 执行时间、总产仔数、活仔数、留仔数 | `0 <= 留仔 <= 活仔 <= 总产仔` | 下次提醒时间 |
| 难产 | 执行时间、难产详情、至少一张图片 | 三项仔数固定为 0；图片必须属于当前兔舍 | 下次提醒时间 |
| 留崽数调整 | 执行时间、调整后留崽数；增加时还需来源母兔 | 原值、新值、来源母兔、人员、批次和周期写入追加事件 | 不适用 |
| 流产 | 执行时间、死胎数、流产详情、至少一张图片 | 发生阶段从周期读取；图片必须属于当前兔舍 | 不支持推迟 |
| 分笼 | 执行时间、断奶数量 | 公母数同时为 0 或相加等于断奶数；手工分配时校验笼位容量 | 下次提醒时间 |
| 商品兔留后备 | 目标商品兔 | 返回 `replacementRecordId` 和目标笼位；人员与执行时间由服务端记录 | 不适用 |

## 图片链路

- `POST /api/business-files/images` 接收 `multipart/form-data`，单张最大 5 MB。
- 只接受实际内容为 JPEG、PNG、WebP 或 HEIC 的文件，不能只靠伪造 Content-Type 绕过。
- 每个动作最多 6 张；文件按兔舍隔离，相同内容在同一兔舍内按 SHA-256 去重。
- 图片内容和元数据保存在 `business_files`，动作只在 `biz_attachments` 与事件 payload 中保存
  `fileId` 引用。
- 读取图片仍要求业务 JWT、`X-House-Id` 和兔舍查看权限。

## 留崽数调整

`POST /api/repro/cycles/{cycleId}/kept-kits-adjustments` 只允许哺乳中的窝。增加留崽数必须
选择另一只种母兔作为来源；减少时不能伪填来源母兔。接口在同一事务内更新
`litters.kept_kits/current_nursing/foster_in/foster_out` 并追加
`KEPT_KITS_ADJUSTED` 事件，重复 `requestId` 返回首次结果。

## 契约保护

- 单只和批量动作请求都要求 `occurredAt`；App 即使调用方不显式传值也会发送当前时间。
- 配种方式始终必填；体配必须选择种公兔，人工授精的种公兔为可选。
- `POSTPONE` 必须选择今天或未来的提醒时间，且不会推进阶段或清除待办。
- 难产与流产在图片、详情或数量缺失时由服务端返回中文 400，不能绕过界面提交。
- 商品兔留后备接口通过 `replacement_records.request_id` 支持结果级幂等回查。

## 验证

- 后端：`ReproRequiredFieldsIT`、`ReproMatingEligibilityIT`、`ReproDeliveryIT`。
- Flutter：`mating_test.dart`、`parturition_test.dart`、`abortion_test.dart`、
  `form_contract_test.dart`、`required_images_test.dart`。
