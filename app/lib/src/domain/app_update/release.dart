class AppRelease {
  const AppRelease({
    required this.buildNumber,
    required this.versionName,
    required this.downloadUrl,
    required this.sha256,
    required this.apkSizeBytes,
    required this.releaseNotes,
    required this.forceUpdate,
  });

  final int buildNumber;
  final String versionName;
  final Uri downloadUrl;
  final String sha256;
  final int apkSizeBytes;
  final String releaseNotes;
  final bool forceUpdate;

  factory AppRelease.fromJson(Map<String, dynamic> json) {
    final buildNumber = _positiveInt(json['buildNumber']);
    final versionName = _requiredString(json['versionName']);
    final downloadUrlValue = _requiredString(json['downloadUrl']);
    final downloadUrl =
        downloadUrlValue == null ? null : Uri.tryParse(downloadUrlValue);
    final sha256 = _requiredString(json['sha256'])?.toLowerCase();
    final apkSizeBytes = _positiveInt(json['apkSizeBytes']);
    if (buildNumber == null ||
        versionName == null ||
        downloadUrl == null ||
        downloadUrl.scheme.toLowerCase() != 'https' ||
        !downloadUrl.hasAuthority ||
        downloadUrl.host.isEmpty ||
        sha256 == null ||
        !RegExp(r'^[0-9a-f]{64}$').hasMatch(sha256) ||
        apkSizeBytes == null) {
      throw const FormatException('升级信息格式不正确');
    }
    return AppRelease(
      buildNumber: buildNumber,
      versionName: versionName,
      downloadUrl: downloadUrl,
      sha256: sha256,
      apkSizeBytes: apkSizeBytes,
      releaseNotes: _optionalString(json['releaseNotes']),
      forceUpdate: json['forceUpdate'] == true,
    );
  }
}

class AppUpdateCheck {
  const AppUpdateCheck._({
    required this.currentBuild,
    required this.release,
  });

  const AppUpdateCheck.upToDate({required int currentBuild})
      : this._(currentBuild: currentBuild, release: null);

  const AppUpdateCheck.available({
    required int currentBuild,
    required AppRelease release,
  }) : this._(currentBuild: currentBuild, release: release);

  final int currentBuild;
  final AppRelease? release;

  bool get updateAvailable => release != null;

  factory AppUpdateCheck.fromJson(Map<String, dynamic> json) {
    final currentBuild = _positiveInt(json['currentBuild']);
    if (currentBuild == null) {
      throw const FormatException('升级检查响应缺少当前构建号');
    }
    if (json['updateAvailable'] != true) {
      return AppUpdateCheck.upToDate(currentBuild: currentBuild);
    }
    final release = AppRelease.fromJson(json);
    if (!isNewerBuild(currentBuild, release.buildNumber)) {
      throw const FormatException('升级构建号必须高于当前构建号');
    }
    return AppUpdateCheck.available(
        currentBuild: currentBuild, release: release);
  }
}

bool isNewerBuild(int currentBuild, int candidateBuild) {
  return currentBuild > 0 && candidateBuild > currentBuild;
}

int? _positiveInt(Object? value) {
  if (value is int) {
    return value > 0 ? value : null;
  }
  if (value is num && value == value.roundToDouble()) {
    final parsed = value.toInt();
    return parsed > 0 ? parsed : null;
  }
  if (value is String) {
    final parsed = int.tryParse(value.trim());
    return parsed != null && parsed > 0 ? parsed : null;
  }
  return null;
}

String? _requiredString(Object? value) {
  if (value is! String || value.trim().isEmpty) {
    return null;
  }
  return value.trim();
}

String _optionalString(Object? value) {
  return value is String ? value.trim() : '';
}
