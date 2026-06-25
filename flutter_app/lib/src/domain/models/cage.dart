class Cage {
  const Cage({
    required this.id,
    required this.houseId,
    required this.cageNumber,
    required this.status,
    required this.rabbitCount,
    required this.isEnabled,
  });

  final int id;
  final int houseId;
  final String cageNumber;
  final String status;
  final int rabbitCount;
  final bool isEnabled;

  String get label {
    final name = cageNumber.isEmpty ? '#$id' : cageNumber;
    return '$name · $rabbitCount 只';
  }

  String get usageLabel {
    switch (status) {
      case '0':
        return '空闲';
      case '1':
        return '种兔';
      case '2':
        return '后备兔';
      case '3':
        return '商品兔';
      default:
        return status.isEmpty ? '空闲' : status;
    }
  }

  String get preferredRabbitType {
    switch (status) {
      case '1':
        return '0';
      case '2':
        return '1';
      case '3':
        return '2';
      default:
        return '0';
    }
  }

  static Cage fromJson(Map<String, dynamic> json) {
    return Cage(
      id: _intValue(json['id']),
      houseId: _intValue(json['houseId']),
      cageNumber: json['cageNumber'] as String? ??
          json['code'] as String? ??
          json['cageNo'] as String? ??
          '',
      status: json['status'] as String? ?? '',
      rabbitCount: _intValue(json['rabbitCount']),
      isEnabled: _boolValue(json['isEnabled'], fallback: true),
    );
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

  static bool _boolValue(Object? value, {required bool fallback}) {
    if (value is bool) {
      return value;
    }
    if (value is num) {
      return value != 0;
    }
    if (value is String) {
      final normalized = value.toLowerCase();
      if (normalized == 'true' || normalized == '1') {
        return true;
      }
      if (normalized == 'false' || normalized == '0') {
        return false;
      }
    }
    return fallback;
  }
}
