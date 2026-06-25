class GlobalSetting {
  const GlobalSetting({
    required this.id,
    required this.houseId,
    required this.aphrodisiacDays,
    required this.palpationDays,
    required this.prepartumDays,
    required this.weaningDays,
    required this.postpartumDays,
    required this.saleDays,
    required this.replacementDays,
    required this.remark,
  });

  final int id;
  final int houseId;
  final int aphrodisiacDays;
  final int palpationDays;
  final int prepartumDays;
  final int weaningDays;
  final int postpartumDays;
  final int saleDays;
  final int replacementDays;
  final String remark;

  static GlobalSetting defaultsForHouse(int houseId) {
    return GlobalSetting(
      id: 0,
      houseId: houseId,
      aphrodisiacDays: 2,
      palpationDays: 12,
      prepartumDays: 3,
      weaningDays: 25,
      postpartumDays: 10,
      saleDays: 30,
      replacementDays: 45,
      remark: '',
    );
  }

  static GlobalSetting fromJson(Map<String, dynamic> json, {int? houseId}) {
    return GlobalSetting(
      id: _intValue(json['id']),
      houseId: _intValue(json['houseId'], fallback: houseId ?? 0),
      aphrodisiacDays: _intValue(json['aphrodisiacDays'], fallback: 2),
      palpationDays: _intValue(json['palpationDays'], fallback: 12),
      prepartumDays: _intValue(json['prepartumDays'], fallback: 3),
      weaningDays: _intValue(json['weaningDays'], fallback: 25),
      postpartumDays: _intValue(json['postpartumDays'], fallback: 10),
      saleDays: _intValue(json['saleDays'], fallback: 30),
      replacementDays: _intValue(json['replacementDays'], fallback: 45),
      remark: json['remark'] as String? ?? '',
    );
  }

  Map<String, dynamic> toUpdateJson({required String requestId}) {
    return {
      'aphrodisiacDays': aphrodisiacDays,
      'palpationDays': palpationDays,
      'prepartumDays': prepartumDays,
      'weaningDays': weaningDays,
      'postpartumDays': postpartumDays,
      'saleDays': saleDays,
      'replacementDays': replacementDays,
      'remark': remark,
      'requestId': requestId,
    };
  }

  static int _intValue(Object? value, {int fallback = 0}) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    if (value is String) {
      return int.tryParse(value) ?? fallback;
    }
    return fallback;
  }
}
