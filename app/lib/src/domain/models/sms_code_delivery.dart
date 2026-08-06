class SmsCodeDelivery {
  const SmsCodeDelivery({
    required this.expiresInSeconds,
    required this.retryAfterSeconds,
  });

  final int expiresInSeconds;
  final int retryAfterSeconds;

  factory SmsCodeDelivery.fromJson(Map<String, dynamic> json) {
    return SmsCodeDelivery(
      expiresInSeconds: _intValue(json['expiresInSeconds']),
      retryAfterSeconds: _intValue(json['retryAfterSeconds']),
    );
  }

  static int _intValue(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}
