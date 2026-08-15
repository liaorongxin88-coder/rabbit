class Cage {
  /// 与后端 `app.cage.commodity-capacity` 默认值一致。
  static const commodityCapacity = 10;

  const Cage({
    required this.id,
    required this.houseId,
    required this.cageNumber,
    this.rowCode = 'LEGACY',
    this.layerIndex,
    this.positionIndex,
    this.breedingOccupantGender,
    required this.status,
    required this.rabbitCount,
    required this.isEnabled,
  });

  final int id;
  final int houseId;
  final String cageNumber;
  final String rowCode;
  final int? layerIndex;
  final int? positionIndex;
  final String? breedingOccupantGender;
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
        if (isDoeBreedingCage) {
          return '种母兔';
        }
        if (isBuckBreedingCage) {
          return '种公兔';
        }
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

  /// 种兔笼、后备兔笼仅允许存放 1 只；商品兔笼最多 [commodityCapacity] 只。
  bool get acceptsMoreRabbits {
    if (!isEnabled) {
      return false;
    }
    if (status == '1' || status == '2') {
      return rabbitCount < 1;
    }
    if (status == '3') {
      return rabbitCount < commodityCapacity;
    }
    return true;
  }

  String? get entryBlockedReason {
    if (!isEnabled) {
      return '笼位已停用';
    }
    if ((status == '1' || status == '2') && rabbitCount >= 1) {
      return '该笼位已有兔子，不能再录入';
    }
    if (status == '3' && rabbitCount >= commodityCapacity) {
      return '该商品兔笼已满（最多 $commodityCapacity 只）';
    }
    return null;
  }

  /// 笼位用途是否匹配兔子类型。
  bool acceptsRabbitType(String rabbitType) {
    if (status == '0') {
      return true;
    }
    return status == _cageStatusForRabbitType(rabbitType);
  }

  bool get isCommodityCage => status == '0' || status == '3';

  /// Only the server's actual active breeding-rabbit lookup classifies a
  /// breeding cage by sex. Do not infer this from cage status or occupancy.
  bool get isDoeBreedingCage => breedingOccupantGender == '0';

  bool get isBuckBreedingCage => breedingOccupantGender == '1';

  int get commodityRemainingCapacity {
    if (!isCommodityCage) {
      return 0;
    }
    return (commodityCapacity - rabbitCount).clamp(0, commodityCapacity);
  }

  bool canAcceptCommodityCount(int count) {
    if (!isEnabled || count <= 0) {
      return false;
    }
    return commodityRemainingCapacity >= count;
  }

  /// 目标笼位能否接收该兔子（含单兔笼容量限制）。
  bool canAcceptRabbit(String rabbitType, {int? exceptRabbitCageId}) {
    if (!isEnabled || !acceptsRabbitType(rabbitType)) {
      return false;
    }
    if (exceptRabbitCageId != null && id == exceptRabbitCageId) {
      return true;
    }
    if (status == '1' || status == '2') {
      return rabbitCount < 1;
    }
    if (status == '3') {
      return rabbitCount < commodityCapacity;
    }
    return true;
  }

  static String _cageStatusForRabbitType(String type) {
    switch (type) {
      case '0':
        return '1';
      case '1':
        return '2';
      case '2':
        return '3';
      default:
        return '';
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
      rowCode: json['rowCode'] as String? ?? 'LEGACY',
      layerIndex: _nullableIntValue(json['layerIndex']),
      positionIndex: _nullableIntValue(json['positionIndex']),
      breedingOccupantGender: _optionalString(
        json['breedingOccupantGender'],
      ),
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

  static int? _nullableIntValue(Object? value) {
    if (value == null) return null;
    final parsed = _intValue(value);
    return parsed > 0 ? parsed : null;
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
