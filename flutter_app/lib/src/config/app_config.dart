class AppConfig {
  static const defaultBaseUrl = String.fromEnvironment(
    'RABBIT_API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );
}
