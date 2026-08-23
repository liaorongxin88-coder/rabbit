const maxBatchCodeLength = 100;

String defaultBatchCode(String houseName, DateTime date) {
  final localDate = date.isUtc ? date.toLocal() : date;
  final timestamp = '${localDate.year.toString().padLeft(4, '0')}'
      '${localDate.month.toString().padLeft(2, '0')}'
      '${localDate.day.toString().padLeft(2, '0')}'
      '${localDate.hour.toString().padLeft(2, '0')}'
      '${localDate.minute.toString().padLeft(2, '0')}'
      '${localDate.second.toString().padLeft(2, '0')}'
      '${localDate.millisecond.toString().padLeft(3, '0')}';
  final suffix = '-批次-$timestamp';
  final prefix = houseName.trim();
  final maxPrefixLength = maxBatchCodeLength - suffix.length;
  final shortenedPrefix = prefix.length > maxPrefixLength
      ? prefix.substring(0, maxPrefixLength)
      : prefix;
  return '$shortenedPrefix$suffix';
}
