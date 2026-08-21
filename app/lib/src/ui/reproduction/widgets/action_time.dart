import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

String formatActionTime(DateTime value) =>
    DateFormat('yyyy-MM-dd HH:mm').format(value);

Future<DateTime?> pickActionTime({
  required BuildContext context,
  required DateTime current,
  required String helpText,
}) async {
  final now = DateTime.now();
  final firstDate = DateTime(2020);
  final initialDate = current.isBefore(firstDate)
      ? firstDate
      : current.isAfter(now)
          ? now
          : current;
  final date = await showDatePicker(
    context: context,
    initialDate: initialDate,
    firstDate: firstDate,
    lastDate: now,
    helpText: helpText,
    cancelText: '取消',
    confirmText: '下一步',
  );
  if (date == null || !context.mounted) {
    return null;
  }
  final time = await showTimePicker(
    context: context,
    initialTime: TimeOfDay.fromDateTime(
      _sameDay(date, current) ? current : date,
    ),
    helpText: '选择执行时间',
    cancelText: '取消',
    confirmText: '确定',
  );
  if (time == null) {
    return null;
  }
  final result =
      DateTime(date.year, date.month, date.day, time.hour, time.minute);
  return result.isAfter(now) ? now : result;
}

bool _sameDay(DateTime left, DateTime right) =>
    left.year == right.year &&
    left.month == right.month &&
    left.day == right.day;
