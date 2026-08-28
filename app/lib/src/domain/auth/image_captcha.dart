class ImageCaptcha {
  const ImageCaptcha({
    required this.captchaId,
    required this.imageBase64,
    required this.expiresInSeconds,
  });

  final String captchaId;
  final String imageBase64;
  final int expiresInSeconds;

  static ImageCaptcha fromJson(Map<String, dynamic> json) {
    return ImageCaptcha(
      captchaId: json['captchaId'] as String? ?? '',
      imageBase64: json['imageBase64'] as String? ?? '',
      expiresInSeconds: _intValue(json['expiresInSeconds']),
    );
  }

  bool get isValid =>
      captchaId.isNotEmpty && imageBase64.isNotEmpty && expiresInSeconds > 0;

  static int _intValue(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }
}
