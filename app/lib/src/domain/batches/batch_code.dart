import 'package:intl/intl.dart';

const maxBatchCodeLength = 100;

final _defaultCodeFormat = DateFormat('yyyyMMdd-HHmm');
const _farmUtcOffset = Duration(hours: 8);
const _defaultHouseName = '兔舍';
const _batchTimestampLength = 13;

/// 新建批次时预填的编号，格式 `东一舍-20260220-1530`。
String defaultBatchCode(String houseName, DateTime date) {
  final timestamp = _defaultCodeFormat.format(
    date.toUtc().add(_farmUtcOffset),
  );
  const maxHouseRunes = maxBatchCodeLength - _batchTimestampLength - 1;
  final normalized = _normalizeHouseName(houseName);
  final runes = normalized.runes.toList(growable: false);
  final safeHouseName = String.fromCharCodes(runes.take(maxHouseRunes));
  return '$safeHouseName-$timestamp';
}

String _normalizeHouseName(String value) {
  final normalized =
      value.trim().replaceAll(RegExp(r'[\s\-_/\u2013\u2014]+'), '-');
  final withoutEdges = normalized.replaceAll(RegExp(r'^-+|-+$'), '');
  return withoutEdges.isEmpty ? _defaultHouseName : withoutEdges;
}
