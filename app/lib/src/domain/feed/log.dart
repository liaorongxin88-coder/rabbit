import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';

enum FeedAllocationPhase {
  breeding('BREEDING', '繁殖期'),
  fattening('FATTENING', '育肥期'),
  unassigned('UNASSIGNED', '未归属');

  const FeedAllocationPhase(this.wire, this.label);

  final String wire;
  final String label;

  static FeedAllocationPhase parse(Object? value) => switch (value) {
        'BREEDING' => FeedAllocationPhase.breeding,
        'FATTENING' => FeedAllocationPhase.fattening,
        'UNASSIGNED' => FeedAllocationPhase.unassigned,
        _ => throw FormatException('无法识别投喂阶段：$value'),
      };
}

class FeedAllocationGroup {
  const FeedAllocationGroup({
    required this.batchId,
    required this.phase,
    required this.rabbitCount,
  });

  final int? batchId;
  final FeedAllocationPhase phase;
  final int rabbitCount;

  String get key => '${batchId ?? 'unassigned'}:${phase.wire}';

  factory FeedAllocationGroup.fromJson(Map<String, dynamic> json) {
    final rabbitCount = json['rabbitCount'];
    if (rabbitCount is! num || rabbitCount <= 0) {
      throw const FormatException('投喂归属兔只数格式不正确');
    }
    final rawBatchId = json['batchId'];
    return FeedAllocationGroup(
      batchId: rawBatchId == null
          ? null
          : rawBatchId is num && rawBatchId > 0
              ? rawBatchId.toInt()
              : throw const FormatException('投喂归属批次格式不正确'),
      phase: FeedAllocationPhase.parse(json['phase']),
      rabbitCount: rabbitCount.toInt(),
    );
  }
}

class FeedAllocationPreview {
  const FeedAllocationPreview(this.groups);

  final List<FeedAllocationGroup> groups;

  factory FeedAllocationPreview.fromJson(Map<String, dynamic> json) {
    final rawGroups = json['groups'];
    if (rawGroups is! List) throw const FormatException('投喂归属预览格式不正确');
    return FeedAllocationPreview(
      rawGroups.map((item) {
        if (item is! Map) throw const FormatException('投喂归属预览格式不正确');
        return FeedAllocationGroup.fromJson(Map<String, dynamic>.from(item));
      }).toList(growable: false),
    );
  }
}

class FeedBatchAllocation {
  const FeedBatchAllocation({
    required this.batchId,
    required this.phase,
    required this.amountKg,
  });

  final int? batchId;
  final FeedAllocationPhase phase;
  final double amountKg;

  Map<String, Object?> toJson() => {
        'batchId': batchId,
        'phase': phase.wire,
        'amountKg': amountKg,
      };
}

String? validateFeedAllocations(
  double amount,
  List<FeedBatchAllocation> allocations,
) {
  if (!amount.isFinite || amount <= 0) return '请输入大于 0 的投喂数量';
  if (!_hasAtMostDecimals(amount, 2)) return '投喂总量最多保留两位小数';
  if (allocations.isEmpty) return '请先预览批次与阶段归属';
  if (allocations
      .any((item) => !item.amountKg.isFinite || item.amountKg <= 0)) {
    return '请填写每个归属分组的投喂量';
  }
  if (allocations.any((item) => !_hasAtMostDecimals(item.amountKg, 2))) {
    return '分组投喂量最多保留两位小数';
  }
  final totalCents = allocations.fold<int>(
    0,
    (sum, item) => sum + (item.amountKg * 100).round(),
  );
  return totalCents == (amount * 100).round() ? null : '分组投喂量合计必须等于投喂总量';
}

class FeedLogDraft {
  const FeedLogDraft({
    required this.rabbitIds,
    required this.feedTime,
    required this.requestId,
    required this.amount,
    required this.allocations,
    this.feedType,
    this.itemId,
    this.unit = 'kg',
    this.remark,
  });

  final List<int> rabbitIds;
  final DateTime feedTime;
  final String requestId;
  final double amount;
  final List<FeedBatchAllocation> allocations;
  final String? feedType;
  final int? itemId;
  final String unit;
  final String? remark;

  Map<String, Object> toJson() => {
        'rabbitIds': rabbitIds,
        'feedTime': farmDateTimeToIso(feedTime),
        'requestId': requestId,
        'amount': amount,
        'unit': unit,
        'allocations': allocations.map((item) => item.toJson()).toList(),
        if (feedType?.trim().isNotEmpty == true) 'feedType': feedType!.trim(),
        if (itemId != null) 'itemId': itemId!,
        if (remark?.trim().isNotEmpty == true) 'remark': remark!.trim(),
      };
}

bool _hasAtMostDecimals(double value, int places) {
  final scale = switch (places) { 2 => 100, 3 => 1000, _ => 1 };
  return ((value * scale).round() - value * scale).abs() < 0.000001;
}
