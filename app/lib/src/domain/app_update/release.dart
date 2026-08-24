class AppPackageIdentity {
  const AppPackageIdentity({
    required this.versionName,
    required this.versionCode,
    required this.channel,
    required this.packageName,
  });

  final String versionName;
  final int versionCode;
  final String channel;
  final String packageName;
}

class AppUpdateException implements Exception {
  const AppUpdateException(this.message);

  final String message;

  @override
  String toString() => message;
}

class AppUpdateCheck {
  const AppUpdateCheck({
    required this.hasUpdate,
    required this.forceUpdate,
    this.id,
    this.channel,
    this.versionName,
    this.versionCode,
    this.releaseNotes,
    this.sizeBytes,
    this.sha256,
    this.downloadPath,
  });

  static const none = AppUpdateCheck(
    hasUpdate: false,
    forceUpdate: false,
  );

  final bool hasUpdate;
  final bool forceUpdate;
  final String? id;
  final String? channel;
  final String? versionName;
  final int? versionCode;
  final String? releaseNotes;
  final int? sizeBytes;
  final String? sha256;
  final String? downloadPath;

  static AppUpdateCheck fromJson(Object? data) {
    if (data is! Map) {
      return none;
    }
    final hasUpdate = data['hasUpdate'] == true;
    if (!hasUpdate) {
      return none;
    }
    return AppUpdateCheck(
      hasUpdate: true,
      forceUpdate: data['forceUpdate'] == true,
      id: _string(data['id']),
      channel: _string(data['channel']),
      versionName: _string(data['versionName']),
      versionCode: _int(data['versionCode']),
      releaseNotes: _string(data['releaseNotes']),
      sizeBytes: _int(data['sizeBytes']),
      sha256: _string(data['sha256']),
      downloadPath: _string(data['downloadPath']),
    );
  }

  static String? _string(Object? value) {
    if (value is String && value.trim().isNotEmpty) {
      return value.trim();
    }
    return null;
  }

  static int? _int(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    if (value is String) {
      return int.tryParse(value);
    }
    return null;
  }
}
