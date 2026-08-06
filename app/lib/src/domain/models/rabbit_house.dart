class RabbitHouse {
  const RabbitHouse({
    required this.id,
    required this.name,
    required this.remark,
    required this.layoutRows,
    required this.layoutCols,
    required this.layoutLayers,
  });

  final int id;
  final String name;
  final String remark;
  final int layoutRows;
  final int layoutCols;
  final int layoutLayers;

  String get layoutLabel {
    final values = [layoutRows, layoutCols, layoutLayers];
    if (values.every((value) => value <= 0)) {
      return '布局未设置';
    }
    return '$layoutRows 排 · $layoutCols 列 · $layoutLayers 层';
  }

  static RabbitHouse fromJson(Map<String, dynamic> json) {
    return RabbitHouse(
      id: _intValue(json['id']),
      name: json['name'] as String? ?? '未命名兔舍',
      remark: json['remark'] as String? ?? '',
      layoutRows: _intValue(json['layoutRows']),
      layoutCols: _intValue(json['layoutCols']),
      layoutLayers: _intValue(json['layoutLayers']),
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
}
