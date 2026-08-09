class ApiException implements Exception {
  const ApiException(
    this.message, {
    this.statusCode,
    this.businessCode,
  });

  final String message;
  final int? statusCode;
  final int? businessCode;

  bool get invalidatesSession {
    if (statusCode == 401 || businessCode == 401) {
      return true;
    }
    return message.trim() == '账号已停用' &&
        (statusCode == 403 || businessCode == 403);
  }

  @override
  String toString() => message;
}
