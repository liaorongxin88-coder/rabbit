import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/data/services/nfc/nfc_hardware_service.dart';
import 'package:rabbit_flutter/src/data/services/storage/nfc_local_store.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_pending_sync_controller.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_write_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('persists platform diagnostics and completes after retry', () async {
    SharedPreferences.setMockInitialValues({});
    final store = NfcLocalStore();
    await store.saveSession(const NfcWriteSession(
      houseId: 8,
      items: [
        NfcWriteSessionItem(
          queueItem: NfcCageQueueItem(
            cageId: 10,
            cageNumber: '1-1-1',
            bindingStatus: 'UNBOUND',
            tagUid: null,
            payload: 'r1.8.a.1.signature',
          ),
        ),
      ],
      currentIndex: 0,
      updatedAt: 1,
    ));

    final repository = _FakeNfcBindingGateway();
    final hardware = _FakeNfcHardwareService(
      error: const NfcWriteException(
        NfcWriteError.verifyFailed,
        '标签可能已写入，但回读校验失败',
        operation: NfcWriteOperation.verify,
        platformCode: 'io_exception',
        mayHaveWritten: true,
      ),
    );
    final pendingSync = NfcPendingSyncController(
      repository: repository,
      store: store,
      onQueueChanged: (_) {},
    );
    final controller = NfcWriteController(
      houseId: 8,
      repository: repository,
      hardware: hardware,
      store: store,
      pendingSync: pendingSync,
      onQueueChanged: () {},
    );
    addTearDown(controller.dispose);
    addTearDown(pendingSync.dispose);

    await _waitFor(() => controller.state.phase == NfcWritePhase.error);

    final failedSession = await store.readSession();
    expect(failedSession!.items.single.status, NfcWriteItemStatus.ready);
    expect(failedSession.items.single.errorMessage, contains('stage=verify'));
    expect(
      failedSession.items.single.errorMessage,
      contains('code=io_exception'),
    );

    hardware.error = null;
    await controller.retry();

    expect(controller.state.phase, NfcWritePhase.completed);
    expect(repository.bindCalls, 1);
    final completedSession = await store.readSession();
    expect(completedSession!.currentIndex, 1);
    expect(
      completedSession.items.single.status,
      NfcWriteItemStatus.completed,
    );
    expect(completedSession.items.single.errorMessage, isNull);
  });
}

Future<void> _waitFor(bool Function() condition) async {
  for (var attempt = 0; attempt < 100; attempt++) {
    if (condition()) return;
    await Future<void>.delayed(const Duration(milliseconds: 10));
  }
  fail('Timed out waiting for controller state');
}

class _FakeNfcHardwareService extends NfcHardwareService {
  _FakeNfcHardwareService({this.error});

  Object? error;

  @override
  Future<NfcWriteResult> writePayload({
    required String payload,
    String? previousCompletedUid,
    bool allowOverwrite = false,
  }) async {
    final failure = error;
    if (failure != null) throw failure;
    return NfcWriteResult(tagUid: '04AABBCC', payload: payload);
  }

  @override
  Future<void> stop() async {}
}

class _FakeNfcBindingGateway implements NfcBindingGateway {
  int bindCalls = 0;

  @override
  Future<NfcCageBinding> bind({
    required int houseId,
    required int cageId,
    required String tagUid,
    required String payload,
    required bool replaceExisting,
    String? requestId,
  }) async {
    bindCalls++;
    return NfcCageBinding(
      houseId: houseId,
      cageId: cageId,
      cageNumber: '1-1-1',
      tagUid: tagUid,
      bindingStatus: 'BOUND',
    );
  }
}
