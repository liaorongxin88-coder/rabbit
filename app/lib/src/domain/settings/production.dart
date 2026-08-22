class GlobalSetting {
  const GlobalSetting({
    required this.id,
    required this.userId,
    required this.houseId,
    required this.aphrodisiacDays,
    required this.palpationDays,
    required this.prepartumDays,
    required this.weaningDays,
    required this.postpartumDays,
    required this.adaptationDays,
    required this.growingDays,
    required this.fatteningDays,
    required this.saleDays,
    required this.replacementDays,
    required this.remark,
  });

  final int id;
  final int userId;
  final int houseId;
  final int aphrodisiacDays;
  final int palpationDays;
  final int prepartumDays;
  final int weaningDays;
  final int postpartumDays;
  final int adaptationDays;
  final int growingDays;
  final int fatteningDays;
  final int saleDays;
  final int replacementDays;
  final String remark;

  static GlobalSetting defaults() {
    return const GlobalSetting(
      id: 0,
      userId: 0,
      houseId: 0,
      aphrodisiacDays: 2,
      palpationDays: 12,
      prepartumDays: 15,
      weaningDays: 30,
      postpartumDays: 10,
      adaptationDays: 3,
      growingDays: 18,
      fatteningDays: 12,
      saleDays: 33,
      replacementDays: 90,
      remark: '',
    );
  }

  static GlobalSetting fromJson(Map<String, dynamic> json) {
    return GlobalSetting(
      id: _intValue(json['id']),
      userId: _intValue(json['userId']),
      houseId: _intValue(json['houseId']),
      aphrodisiacDays: _intValue(json['aphrodisiacDays'], fallback: 2),
      palpationDays: _intValue(json['palpationDays'], fallback: 12),
      prepartumDays: _intValue(json['prepartumDays'], fallback: 15),
      weaningDays: _intValue(json['weaningDays'], fallback: 30),
      postpartumDays: _intValue(json['postpartumDays'], fallback: 10),
      adaptationDays: _intValue(json['adaptationDays'], fallback: 3),
      growingDays: _intValue(json['growingDays'], fallback: 18),
      fatteningDays: _intValue(json['fatteningDays'], fallback: 12),
      saleDays: _intValue(json['saleDays'], fallback: 33),
      replacementDays: _intValue(json['replacementDays'], fallback: 90),
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
      'adaptationDays': adaptationDays,
      'growingDays': growingDays,
      'fatteningDays': fatteningDays,
      'saleDays': commodityMaturityDays,
      'replacementDays': replacementDays,
      'remark': remark,
      'requestId': requestId,
    };
  }

  int get commodityMaturityDays => adaptationDays + growingDays + fatteningDays;

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

class HouseSettingState {
  const HouseSettingState({
    required this.setting,
    required this.customized,
  });

  final GlobalSetting setting;
  final bool customized;

  static HouseSettingState fromJson(Map<String, dynamic> json) {
    final settingJson = json['setting'];
    return HouseSettingState(
      setting: settingJson is Map
          ? GlobalSetting.fromJson(Map<String, dynamic>.from(settingJson))
          : GlobalSetting.defaults(),
      customized: json['customized'] == true,
    );
  }
}
