class Rabbit {
  const Rabbit({
    required this.id,
    required this.houseId,
    required this.cageId,
    required this.motherId,
    required this.type,
    required this.gender,
    required this.breed,
    required this.arrivalMethod,
    required this.arrivalDate,
    required this.weight,
    required this.isActive,
    this.growthStage,
    this.reproductiveStage,
    this.currentStage,
    this.currentCycleId,
    this.stageEnteredAt,
  });

  final int id;
  final int houseId;
  final int cageId;
  final int? motherId;
  final String type;
  final String gender;
  final String breed;
  final String arrivalMethod;
  final DateTime? arrivalDate;
  final double? weight;
  final bool isActive;
  final String? growthStage;
  final String? reproductiveStage;

  /// 生产阶段投影。种母兔的阶段只由生产流程状态机写，展示以它为准；
  /// [reproductiveStage] 是旧词汇，只对种公兔、后备兔仍有意义。
  final String? currentStage;
  final int? currentCycleId;
  final DateTime? stageEnteredAt;

  String get typeLabel {
    switch (type) {
      case '0':
        if (gender == '1') {
          return '种公兔';
        }
        if (gender == '0') {
          return '种母兔';
        }
        return '种兔';
      case '1':
        return '后备兔';
      case '2':
        return '商品兔';
      default:
        return type.isEmpty ? '未分类' : type;
    }
  }

  String get genderLabel {
    switch (gender) {
      case '0':
        return '母';
      case '1':
        return '公';
      default:
        return gender.isEmpty ? '未知' : gender;
    }
  }

  String get weightLabel {
    final value = weight;
    if (value == null || value <= 0) {
      return '未称重';
    }
    return '${value.toStringAsFixed(1)} kg';
  }

  static Rabbit fromJson(Map<String, dynamic> json) {
    return Rabbit(
      id: _intValue(json['id']),
      houseId: _intValue(json['houseId']),
      cageId: _intValue(json['cageId']),
      motherId: _nullableIntValue(json['motherId']),
      type: json['type'] as String? ?? '',
      gender: json['gender'] as String? ?? '',
      breed: json['breed'] as String? ?? '',
      arrivalMethod: json['arrivalMethod'] as String? ?? '',
      arrivalDate: _dateTimeValue(json['arrivalDate']),
      weight: _doubleValue(json['weight']),
      isActive: _boolValue(json['isActive'], fallback: true),
      growthStage: _optionalString(json['growthStage']),
      reproductiveStage: _optionalString(json['reproductiveStage']),
      currentStage: _optionalString(json['currentStage']),
      currentCycleId: _nullableIntValue(json['currentCycleId']),
      stageEnteredAt: _dateTimeValue(json['stageEnteredAt']),
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

  static int? _nullableIntValue(Object? value) {
    if (value == null) {
      return null;
    }
    final parsed = _intValue(value);
    return parsed <= 0 ? null : parsed;
  }

  static double? _doubleValue(Object? value) {
    if (value is num) {
      return value.toDouble();
    }
    if (value is String) {
      return double.tryParse(value);
    }
    return null;
  }

  static DateTime? _dateTimeValue(Object? value) {
    if (value is int) {
      return DateTime.fromMillisecondsSinceEpoch(value);
    }
    if (value is num) {
      return DateTime.fromMillisecondsSinceEpoch(value.toInt());
    }
    if (value is String && value.trim().isNotEmpty) {
      return DateTime.tryParse(value);
    }
    return null;
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

  static String? _optionalString(Object? value) {
    if (value is! String) {
      return null;
    }
    final normalized = value.trim();
    return normalized.isEmpty ? null : normalized;
  }
}
