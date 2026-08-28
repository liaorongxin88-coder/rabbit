class FeedLogDraft {
  const FeedLogDraft({
    required this.rabbitIds,
    required this.feedTime,
    required this.requestId,
    required this.amount,
    this.feedType,
    this.itemId,
    this.unit,
    this.remark,
  });

  final List<int> rabbitIds;
  final DateTime feedTime;
  final String requestId;
  final double amount;
  final String? feedType;
  final int? itemId;
  final String? unit;
  final String? remark;

  Map<String, Object> toJson() => {
        'rabbitIds': rabbitIds,
        'feedTime': feedTime.toIso8601String(),
        'requestId': requestId,
        'amount': amount,
        if (feedType?.trim().isNotEmpty == true) 'feedType': feedType!.trim(),
        if (itemId != null) 'itemId': itemId!,
        if (unit?.trim().isNotEmpty == true) 'unit': unit!.trim(),
        if (remark?.trim().isNotEmpty == true) 'remark': remark!.trim(),
      };
}
