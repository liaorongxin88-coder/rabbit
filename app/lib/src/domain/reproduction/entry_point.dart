/// 录入母兔时可选的入轨阶段，以及该阶段必须补录的事实。
///
/// 由服务端的 `GET /api/repro/entry-points` 下发。客户端刻意不自己维护这张表：
/// 「待摸胎要配种日、待分笼要分娩日与活仔数」这类规则的真相在服务端的 EntryPoint
/// 表里，抄一份必然漂移，用户会遇到「填完才 400」（飞书 recvsrnEJ8bKrk）。
class ReproEntryPoint {
  const ReproEntryPoint({
    required this.stage,
    required this.stageLabel,
    required this.requiredFacts,
  });

  final String stage;
  final String stageLabel;
  final List<ReproRequiredFact> requiredFacts;

  bool requires(String fact) => requiredFacts.any((item) => item.fact == fact);

  /// 配种日既可能以 MATING_DATE 出现，也可能以 GESTATION_ANCHOR（配种日或预产期）出现。
  /// 客户端只提供配种日一个入口，因此两者都落到同一个字段上。
  bool get needsMatingDate =>
      requires('MATING_DATE') || requires('GESTATION_ANCHOR');

  static ReproEntryPoint fromJson(Map<String, dynamic> json) {
    return ReproEntryPoint(
      stage: json['stage']?.toString() ?? '',
      stageLabel: json['stageLabel']?.toString() ?? '',
      requiredFacts: [
        for (final raw in (json['requiredFacts'] as List? ?? const []))
          ReproRequiredFact.fromJson(Map<String, dynamic>.from(raw as Map)),
      ],
    );
  }
}

class ReproRequiredFact {
  const ReproRequiredFact({required this.fact, required this.label});

  final String fact;
  final String label;

  static ReproRequiredFact fromJson(Map<String, dynamic> json) {
    return ReproRequiredFact(
      fact: json['fact']?.toString() ?? '',
      label: json['label']?.toString() ?? '',
    );
  }
}
