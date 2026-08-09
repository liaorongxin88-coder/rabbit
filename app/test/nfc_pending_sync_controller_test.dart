import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/storage/nfc_local_store.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_pending_sync_controller.dart';

void main() {
  test('keeps network failures pending and retries them', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = _FakeGateway()..error = const ApiException('网络不可用');
    final controller = NfcPendingSyncController(
      repository: gateway,
      store: NfcLocalStore(),
      onQueueChanged: (_) {},
    );
    addTearDown(controller.dispose);
    const item = _pendingBinding;

    await controller.enqueue(item);
    await controller.syncAll();

    expect(controller.state.items, hasLength(1));
    expect(
      controller.state.items.single.status,
      NfcPendingBindingStatus.pending,
    );
    expect(controller.state.items.single.errorMessage, '网络不可用');

    gateway.error = null;
    await controller.retry(controller.state.items.single);

    expect(controller.state.items, isEmpty);
    expect(gateway.callCount, 2);
  });

  test('marks conflicts and can force a replacement with a new request id',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = _FakeGateway()
      ..error = const ApiException('标签已绑定', businessCode: 4606);
    var changedHouseId = 0;
    final controller = NfcPendingSyncController(
      repository: gateway,
      store: NfcLocalStore(),
      onQueueChanged: (houseId) => changedHouseId = houseId,
    );
    addTearDown(controller.dispose);

    await controller.enqueue(_pendingBinding);
    await controller.syncAll();

    final conflict = controller.state.items.single;
    expect(conflict.status, NfcPendingBindingStatus.conflict);

    gateway.error = null;
    await controller.forceReplace(conflict);

    expect(controller.state.items, isEmpty);
    expect(gateway.lastReplaceExisting, isTrue);
    expect(gateway.lastRequestId, isNot(conflict.requestId));
    expect(changedHouseId, 8);
  });
}

const _pendingBinding = NfcPendingBinding(
  houseId: 8,
  cageId: 10,
  tagUid: '04AABBCC',
  payload: 'r1.8.a.1.signature',
  requestId: 'request-1',
  replaceExisting: false,
);

class _FakeGateway implements NfcBindingGateway {
  ApiException? error;
  int callCount = 0;
  String? lastRequestId;
  bool? lastReplaceExisting;

  @override
  Future<NfcCageBinding> bind({
    required int houseId,
    required int cageId,
    required String tagUid,
    required String payload,
    required bool replaceExisting,
    String? requestId,
  }) async {
    callCount++;
    lastRequestId = requestId;
    lastReplaceExisting = replaceExisting;
    final currentError = error;
    if (currentError != null) throw currentError;
    return NfcCageBinding(
      houseId: houseId,
      cageId: cageId,
      cageNumber: '1-1-1',
      tagUid: tagUid,
      bindingStatus: 'BOUND',
    );
  }
}
