import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';

final nfcRepositoryProvider = Provider<NfcRepository>((ref) {
  return NfcRepository(ref.watch(apiClientProvider));
});

final nfcCageWriteQueueProvider =
    FutureProvider.family<List<NfcCageQueueItem>, int>((ref, houseId) {
  return ref.watch(nfcRepositoryProvider).listWriteQueue(houseId);
});

abstract interface class NfcBindingGateway {
  Future<NfcCageBinding> bind({
    required int houseId,
    required int cageId,
    required String tagUid,
    required String payload,
    required bool replaceExisting,
    String? requestId,
  });
}

class NfcRepository implements NfcBindingGateway {
  NfcRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<NfcCageQueueItem>> listWriteQueue(int houseId) {
    return _api.get<List<NfcCageQueueItem>>(
      '/api/nfc/cages/write-queue',
      houseId: houseId,
      decode: (data) {
        if (data is! List) {
          throw const ApiException('NFC写入队列格式不正确');
        }
        return data
            .whereType<Map>()
            .map((item) => NfcCageQueueItem.fromJson(
                  Map<String, dynamic>.from(item),
                ))
            .where((item) => item.cageId > 0 && item.payload.isNotEmpty)
            .toList();
      },
    );
  }

  @override
  Future<NfcCageBinding> bind({
    required int houseId,
    required int cageId,
    required String tagUid,
    required String payload,
    required bool replaceExisting,
    String? requestId,
  }) {
    return _api.post<NfcCageBinding>(
      '/api/nfc/cages/bind',
      houseId: houseId,
      body: {
        'cageId': cageId,
        'tagUid': tagUid,
        'payload': payload,
        'replaceExisting': replaceExisting,
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('NFC绑定结果格式不正确');
        }
        return NfcCageBinding.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<NfcCageBinding> resolve({
    required int houseId,
    required String tagUid,
    required String payload,
  }) {
    return _api.post<NfcCageBinding>(
      '/api/nfc/cages/resolve',
      houseId: houseId,
      body: {'tagUid': tagUid, 'payload': payload},
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('NFC解析结果格式不正确');
        }
        return NfcCageBinding.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }
}
