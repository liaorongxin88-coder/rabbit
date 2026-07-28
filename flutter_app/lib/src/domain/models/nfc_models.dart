class NfcPayloadTarget {
  const NfcPayloadTarget({
    required this.houseId,
    required this.cageId,
    required this.keyId,
    required this.payload,
  });

  final int houseId;
  final int cageId;
  final int keyId;
  final String payload;

  static NfcPayloadTarget parse(String payload) {
    final parts = payload.trim().split('.');
    if (parts.length != 5 || parts.first.toLowerCase() != 'r1') {
      throw const FormatException('NFC标签协议不受支持');
    }
    final houseId = int.tryParse(parts[1], radix: 36) ?? 0;
    final cageId = int.tryParse(parts[2], radix: 36) ?? 0;
    final keyId = int.tryParse(parts[3], radix: 36) ?? 0;
    if (houseId <= 0 || cageId <= 0 || keyId <= 0 || parts[4].isEmpty) {
      throw const FormatException('NFC标签内容不完整');
    }
    return NfcPayloadTarget(
      houseId: houseId,
      cageId: cageId,
      keyId: keyId,
      payload: payload.trim(),
    );
  }
}

class NfcLaunchEvent {
  const NfcLaunchEvent({
    required this.payload,
    required this.tagUid,
    required this.receivedAt,
  });

  final String payload;
  final String tagUid;
  final int receivedAt;

  Map<String, dynamic> toJson() => {
        'payload': payload,
        'tagUid': tagUid,
        'receivedAt': receivedAt,
      };

  static NfcLaunchEvent? fromJson(Object? value) {
    if (value is! Map) {
      return null;
    }
    final map = Map<String, dynamic>.from(value);
    final payload = map['payload'] as String? ?? '';
    if (payload.isEmpty) {
      return null;
    }
    return NfcLaunchEvent(
      payload: payload,
      tagUid: map['tagUid'] as String? ?? '',
      receivedAt: _intValue(map['receivedAt']),
    );
  }
}

class NfcCageQueueItem {
  const NfcCageQueueItem({
    required this.cageId,
    required this.cageNumber,
    required this.bindingStatus,
    required this.tagUid,
    required this.payload,
  });

  final int cageId;
  final String cageNumber;
  final String bindingStatus;
  final String? tagUid;
  final String payload;

  bool get isBound => bindingStatus == 'BOUND';
  bool get hasConflict => bindingStatus == 'CONFLICT';

  Map<String, dynamic> toJson() => {
        'cageId': cageId,
        'cageNumber': cageNumber,
        'bindingStatus': bindingStatus,
        'tagUid': tagUid,
        'payload': payload,
      };

  static NfcCageQueueItem fromJson(Map<String, dynamic> json) {
    return NfcCageQueueItem(
      cageId: _intValue(json['cageId']),
      cageNumber: json['cageNumber'] as String? ?? '',
      bindingStatus: json['bindingStatus'] as String? ?? 'UNBOUND',
      tagUid: json['tagUid'] as String?,
      payload: json['payload'] as String? ?? '',
    );
  }
}

class NfcCageBinding {
  const NfcCageBinding({
    required this.houseId,
    required this.cageId,
    required this.cageNumber,
    required this.tagUid,
    required this.bindingStatus,
  });

  final int houseId;
  final int cageId;
  final String cageNumber;
  final String tagUid;
  final String bindingStatus;

  static NfcCageBinding fromJson(Map<String, dynamic> json) {
    return NfcCageBinding(
      houseId: _intValue(json['houseId']),
      cageId: _intValue(json['cageId']),
      cageNumber: json['cageNumber'] as String? ?? '',
      tagUid: json['tagUid'] as String? ?? '',
      bindingStatus: json['bindingStatus'] as String? ?? '',
    );
  }
}

enum NfcWriteItemStatus { ready, completed, pendingSync, skipped, failed }

class NfcWriteSessionItem {
  const NfcWriteSessionItem({
    required this.queueItem,
    this.status = NfcWriteItemStatus.ready,
    this.writtenTagUid,
    this.errorMessage,
  });

  final NfcCageQueueItem queueItem;
  final NfcWriteItemStatus status;
  final String? writtenTagUid;
  final String? errorMessage;

  NfcWriteSessionItem copyWith({
    NfcWriteItemStatus? status,
    String? writtenTagUid,
    String? errorMessage,
    bool clearError = false,
  }) {
    return NfcWriteSessionItem(
      queueItem: queueItem,
      status: status ?? this.status,
      writtenTagUid: writtenTagUid ?? this.writtenTagUid,
      errorMessage: clearError ? null : errorMessage ?? this.errorMessage,
    );
  }

  Map<String, dynamic> toJson() => {
        'queueItem': queueItem.toJson(),
        'status': status.name,
        'writtenTagUid': writtenTagUid,
        'errorMessage': errorMessage,
      };

  static NfcWriteSessionItem fromJson(Map<String, dynamic> json) {
    final statusName = json['status'] as String? ?? '';
    return NfcWriteSessionItem(
      queueItem: NfcCageQueueItem.fromJson(
        Map<String, dynamic>.from(json['queueItem'] as Map),
      ),
      status: NfcWriteItemStatus.values.firstWhere(
        (item) => item.name == statusName,
        orElse: () => NfcWriteItemStatus.ready,
      ),
      writtenTagUid: json['writtenTagUid'] as String?,
      errorMessage: json['errorMessage'] as String?,
    );
  }
}

class NfcWriteSession {
  const NfcWriteSession({
    required this.houseId,
    required this.items,
    required this.currentIndex,
    required this.updatedAt,
  });

  final int houseId;
  final List<NfcWriteSessionItem> items;
  final int currentIndex;
  final int updatedAt;

  NfcWriteSession copyWith({
    List<NfcWriteSessionItem>? items,
    int? currentIndex,
  }) {
    return NfcWriteSession(
      houseId: houseId,
      items: items ?? this.items,
      currentIndex: currentIndex ?? this.currentIndex,
      updatedAt: DateTime.now().millisecondsSinceEpoch,
    );
  }

  Map<String, dynamic> toJson() => {
        'version': 1,
        'houseId': houseId,
        'items': items.map((item) => item.toJson()).toList(),
        'currentIndex': currentIndex,
        'updatedAt': updatedAt,
      };

  static NfcWriteSession? fromJson(Object? value) {
    if (value is! Map) {
      return null;
    }
    final json = Map<String, dynamic>.from(value);
    if (_intValue(json['version']) != 1) {
      return null;
    }
    final rawItems = json['items'];
    if (rawItems is! List) {
      return null;
    }
    return NfcWriteSession(
      houseId: _intValue(json['houseId']),
      items: rawItems
          .whereType<Map>()
          .map((item) => NfcWriteSessionItem.fromJson(
                Map<String, dynamic>.from(item),
              ))
          .toList(),
      currentIndex: _intValue(json['currentIndex']),
      updatedAt: _intValue(json['updatedAt']),
    );
  }
}

class NfcPendingBinding {
  const NfcPendingBinding({
    required this.houseId,
    required this.cageId,
    required this.tagUid,
    required this.payload,
    required this.requestId,
    required this.replaceExisting,
    this.status = NfcPendingBindingStatus.pending,
    this.errorMessage,
    this.updatedAt = 0,
  });

  final int houseId;
  final int cageId;
  final String tagUid;
  final String payload;
  final String requestId;
  final bool replaceExisting;
  final NfcPendingBindingStatus status;
  final String? errorMessage;
  final int updatedAt;

  String get storageKey => '$houseId:$requestId';

  NfcPendingBinding copyWith({
    String? requestId,
    bool? replaceExisting,
    NfcPendingBindingStatus? status,
    String? errorMessage,
    bool clearError = false,
    int? updatedAt,
  }) {
    return NfcPendingBinding(
      houseId: houseId,
      cageId: cageId,
      tagUid: tagUid,
      payload: payload,
      requestId: requestId ?? this.requestId,
      replaceExisting: replaceExisting ?? this.replaceExisting,
      status: status ?? this.status,
      errorMessage: clearError ? null : errorMessage ?? this.errorMessage,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  Map<String, dynamic> toJson() => {
        'houseId': houseId,
        'cageId': cageId,
        'tagUid': tagUid,
        'payload': payload,
        'requestId': requestId,
        'replaceExisting': replaceExisting,
        'status': status.name,
        'errorMessage': errorMessage,
        'updatedAt': updatedAt,
      };

  static NfcPendingBinding fromJson(Map<String, dynamic> json) {
    return NfcPendingBinding(
      houseId: _intValue(json['houseId']),
      cageId: _intValue(json['cageId']),
      tagUid: json['tagUid'] as String? ?? '',
      payload: json['payload'] as String? ?? '',
      requestId: json['requestId'] as String? ?? '',
      replaceExisting: json['replaceExisting'] as bool? ?? false,
      status: NfcPendingBindingStatus.values.firstWhere(
        (item) => item.name == json['status'],
        orElse: () => NfcPendingBindingStatus.pending,
      ),
      errorMessage: json['errorMessage'] as String?,
      updatedAt: _intValue(json['updatedAt']),
    );
  }
}

enum NfcPendingBindingStatus { pending, conflict, failed }

int _intValue(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? 0;
  return 0;
}
