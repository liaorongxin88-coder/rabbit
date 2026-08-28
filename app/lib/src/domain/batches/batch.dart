import 'package:intl/intl.dart';

class Batch {
  const Batch({
    required this.id,
    required this.houseId,
    required this.batchCode,
    required this.status,
    required this.startDate,
    required this.endDate,
    required this.remark,
    this.pendingCompletion = false,
  });

  final int id;
  final int houseId;
  final String batchCode;
  final String status;
  final DateTime? startDate;
  final DateTime? endDate;
  final String remark;

  /// 批次内已无活跃成员，服务端提示可由人员手动结束。
  final bool pendingCompletion;

  String get title => batchCode.isEmpty ? '批次 #$id' : batchCode;

  String get dateLabel {
    final start = startDate;
    final end = endDate;
    final format = DateFormat('MM-dd');
    if (start == null && end == null) {
      return '日期未设置';
    }
    if (start != null && end != null) {
      return '${format.format(start)} - ${format.format(end)}';
    }
    if (start != null) {
      return '开始 ${format.format(start)}';
    }
    return '结束 ${format.format(end!)}';
  }

  static Batch fromJson(Map<String, dynamic> json) {
    return Batch(
      id: _intValue(json['id']),
      houseId: _intValue(json['houseId']),
      batchCode: json['batchCode'] as String? ?? '',
      status: json['status'] as String? ?? '',
      startDate: _parseDate(json['startDate']),
      endDate: _parseDate(json['endDate']),
      remark: json['remark'] as String? ?? '',
      pendingCompletion: _boolValue(json['pendingCompletion']),
    );
  }

  static DateTime? _parseDate(Object? value) {
    if (value is String && value.isNotEmpty) {
      return DateTime.tryParse(value);
    }
    return null;
  }

  static bool _boolValue(Object? value) {
    if (value is bool) return value;
    if (value is num) return value != 0;
    if (value is String) return value.toLowerCase() == 'true' || value == '1';
    return false;
  }

  static int _intValue(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    if (value is String) {
      return int.tryParse(value) ?? 0;
    }
    return 0;
  }
}

List<Batch> productionIntakeBatches(
  Iterable<Batch> batches, {
  required int houseId,
}) {
  final result = batches.where((batch) {
    if (batch.id <= 0 || batch.houseId != houseId) {
      return false;
    }
    final status = batch.status.trim().toUpperCase();
    return status != '已完成' && status != 'COMPLETED';
  }).toList();
  result.sort((left, right) => right.id.compareTo(left.id));
  return result;
}
