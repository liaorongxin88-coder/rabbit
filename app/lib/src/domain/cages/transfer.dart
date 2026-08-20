/// 换笼位结果。
///
/// 三种结局对用户是完全不同的事实：搬进空笼、并入商品兔笼、两笼对调。
/// 提示文案必须区分，否则对调时用户不知道另一只兔去了哪里。
class CageTransferResult {
  const CageTransferResult({
    required this.mode,
    required this.rabbitId,
    required this.fromCageId,
    required this.toCageId,
    required this.swappedRabbitId,
  });

  final String mode;
  final int rabbitId;
  final int? fromCageId;
  final int? toCageId;
  final int? swappedRabbitId;

  bool get isSwap => mode == 'SWAP';

  static CageTransferResult fromJson(Map<String, dynamic> json) {
    return CageTransferResult(
      mode: json['mode'] as String? ?? '',
      rabbitId: _intValue(json['rabbitId']),
      fromCageId: _nullableIntValue(json['fromCageId']),
      toCageId: _nullableIntValue(json['toCageId']),
      swappedRabbitId: _nullableIntValue(json['swappedRabbitId']),
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
    return _intValue(value);
  }
}
