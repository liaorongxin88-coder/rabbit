class ApiException implements Exception {
  const ApiException(
    this.message, {
    this.statusCode,
    this.businessCode,
  });

  final String message;
  final int? statusCode;
  final int? businessCode;

  @override
  String toString() => message;
}
