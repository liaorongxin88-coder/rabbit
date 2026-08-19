import 'package:rabbit_flutter/src/domain/models/event_item.dart';

class ReminderPreference {
  const ReminderPreference({
    required this.id,
    required this.houseId,
    required this.enabled,
    required this.advanceDays,
    required this.notifyOverdue,
    required this.taskTypes,
  });

  final int id;
  final int houseId;
  final bool enabled;
  final int advanceDays;
  final bool notifyOverdue;
  final Set<String> taskTypes;

  static const supportedTypes = <String, String>{
    'ESTRUS': '催情',
    'MATING': '配种',
    'PALPATION': '摸胎',
    'PREPARTUM': '备产',
    'DELIVERY': '分娩',
    'WEANING': '分笼',
    'SALE_READY': '出售',
    'REPLACEMENT_MATURE': '后备成熟',
    'CUSTOM': '治疗复查与自定义',
  };

  static const defaults = ReminderPreference(
    id: 0,
    houseId: 0,
    enabled: true,
    advanceDays: 0,
    notifyOverdue: true,
    taskTypes: {'ALL'},
  );

  bool get includesAll => taskTypes.contains('ALL');

  DateTime dueBefore(DateTime now) {
    final today = DateTime(now.year, now.month, now.day);
    return today.add(Duration(days: advanceDays + 1)).subtract(
          const Duration(milliseconds: 1),
        );
  }

  bool includes(EventItem event) {
    if (!enabled) return false;
    if (!notifyOverdue && event.isOverdue) return false;
    if (includesAll) return true;
    return taskTypes.contains(_typeOf(event));
  }

  Map<String, dynamic> toUpdateJson({required String requestId}) {
    final selected = taskTypes.toList()..sort();
    return {
      'enabled': enabled,
      'advanceDays': advanceDays,
      'notifyOverdue': notifyOverdue,
      'taskTypes': selected.isEmpty ? const ['ALL'] : selected,
      'requestId': requestId,
    };
  }

  ReminderPreference copyWith({
    bool? enabled,
    int? advanceDays,
    bool? notifyOverdue,
    Set<String>? taskTypes,
  }) {
    return ReminderPreference(
      id: id,
      houseId: houseId,
      enabled: enabled ?? this.enabled,
      advanceDays: advanceDays ?? this.advanceDays,
      notifyOverdue: notifyOverdue ?? this.notifyOverdue,
      taskTypes: taskTypes ?? this.taskTypes,
    );
  }

  static ReminderPreference fromJson(Map<String, dynamic> json) {
    final rawTypes = json['taskTypes'];
    final types = rawTypes is List
        ? rawTypes
            .map((value) => value.toString().trim().toUpperCase())
            .where((value) => value.isNotEmpty)
            .toSet()
        : <String>{'ALL'};
    return ReminderPreference(
      id: _int(json['id']),
      houseId: _int(json['houseId']),
      enabled: json['enabled'] != false,
      advanceDays: _int(json['advanceDays']).clamp(0, 30).toInt(),
      notifyOverdue: json['notifyOverdue'] != false,
      taskTypes: types.isEmpty ? const {'ALL'} : types,
    );
  }

  static String _typeOf(EventItem event) {
    final value = event.eventType;
    if (value.contains('催情')) return 'ESTRUS';
    if (value.contains('配种')) return 'MATING';
    if (value.contains('摸胎')) return 'PALPATION';
    if (value.contains('备产')) return 'PREPARTUM';
    if (value.contains('分娩') || value.contains('生产')) return 'DELIVERY';
    if (value.contains('断奶') || value.contains('分笼')) return 'WEANING';
    if (value.contains('出售')) return 'SALE_READY';
    if (value.contains('后备')) return 'REPLACEMENT_MATURE';
    return 'CUSTOM';
  }

  static int _int(Object? value) {
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}
