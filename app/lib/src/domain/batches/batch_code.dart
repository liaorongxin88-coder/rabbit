import 'package:intl/intl.dart';

const maxBatchCodeLength = 100;

final _defaultCodeFormat = DateFormat('yyyyMMdd-HHmm');

/// 新建批次时预填的编号，格式 `批次-20260220-1530`，固定 16 个字符。
///
/// 这个编号会出现在提醒卡片上，和周期号、日期挤在同一行，所以必须短到不被截断。
/// 提醒卡片自己已经单独显示了兔舍名，编号里不必再带一遍。
///
/// 只精确到分钟。同一兔舍在同一分钟内建两个批次才会撞名，而这只是个预填草稿，
/// 输入框里可以直接改掉。
String defaultBatchCode(DateTime date) {
  final local = date.isUtc ? date.toLocal() : date;
  return '批次-${_defaultCodeFormat.format(local)}';
}
