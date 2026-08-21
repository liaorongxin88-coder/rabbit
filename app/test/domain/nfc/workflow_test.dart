import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:nfc_manager/nfc_manager.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';
import 'package:rabbit_flutter/src/data/services/storage/nfc.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';

void main() {
  test('parses versioned house and cage payload', () {
    final target = NfcPayloadTarget.parse('r1.3f.co.1.ABCDEF');

    expect(target.houseId, 123);
    expect(target.cageId, 456);
    expect(target.keyId, 1);
  });

  test('rejects malformed payload', () {
    expect(
      () => NfcPayloadTarget.parse('rabbit://cage/1/2'),
      throwsFormatException,
    );
  });

  test('write session round trips without losing queue order or status', () {
    const session = NfcWriteSession(
      houseId: 8,
      items: [
        NfcWriteSessionItem(
          queueItem: NfcCageQueueItem(
            cageId: 10,
            cageNumber: '1(上)1',
            bindingStatus: 'UNBOUND',
            tagUid: null,
            payload: 'r1.8.a.1.signature',
          ),
          status: NfcWriteItemStatus.pendingSync,
          writtenTagUid: '04AABBCC',
        ),
        NfcWriteSessionItem(
          queueItem: NfcCageQueueItem(
            cageId: 11,
            cageNumber: '1(中)2',
            bindingStatus: 'UNBOUND',
            tagUid: null,
            payload: 'r1.8.b.1.signature',
          ),
        ),
      ],
      currentIndex: 1,
      updatedAt: 123456,
    );

    final restored = NfcWriteSession.fromJson(
      jsonDecode(jsonEncode(session.toJson())),
    );

    expect(restored, isNotNull);
    expect(restored!.houseId, 8);
    expect(restored.currentIndex, 1);
    expect(restored.items.map((item) => item.queueItem.cageId), [10, 11]);
    expect(restored.items.first.status, NfcWriteItemStatus.pendingSync);
    expect(restored.items.first.writtenTagUid, '04AABBCC');
  });

  test('maximum compact payload fits NTAG213 NDEF capacity', () {
    const payload = 'r1.1y2p0ij32e8e7.1y2p0ij32e8e7.1.abcdefghijklmnop';
    final message = NdefMessage([
      NdefRecord.createExternal(
        'dzht.top',
        'rabbit-cage',
        Uint8List.fromList(ascii.encode(payload)),
      ),
    ]);

    expect(message.byteLength, lessThanOrEqualTo(144));
  });

  test('recognizes only an exact Rabbit payload for write recovery', () {
    const payload = 'r1.2.u1.1.DrCBtJgInkFtKMEF';
    final managedRecord = NdefRecord.createExternal(
      NfcHardwareService.externalDomain,
      NfcHardwareService.externalType,
      Uint8List.fromList(ascii.encode(payload)),
    );

    expect(
      hasExactManagedNfcPayload(NdefMessage([managedRecord]), payload),
      isTrue,
    );
    expect(
      hasExactManagedNfcPayload(
        NdefMessage([
          managedRecord,
          NdefRecord.createText('extra'),
        ]),
        payload,
      ),
      isFalse,
    );
    expect(
      hasExactManagedNfcPayload(NdefMessage([managedRecord]), 'other'),
      isFalse,
    );
  });

  test('NFC write diagnostics retain the failed stage and platform code', () {
    const error = NfcWriteException(
      NfcWriteError.writeFailed,
      '标签可能已写入，但回读校验失败',
      operation: NfcWriteOperation.verify,
      platformCode: 'io_exception',
      mayHaveWritten: true,
    );

    expect(error.diagnosticMessage, contains('stage=verify'));
    expect(error.diagnosticMessage, contains('code=io_exception'));
    expect(error.diagnosticMessage, contains('writeOutcome=ambiguous'));
  });

  test('same physical tag cannot advance to the next cage', () {
    expect(isRepeatedNfcTag('04AABBCC', '04aabbcc'), isTrue);
    expect(isRepeatedNfcTag('04AABBCC', '04DDEEFF'), isFalse);
    expect(isRepeatedNfcTag(null, '04DDEEFF'), isFalse);
  });

  test('persists active session and pending offline bindings', () async {
    SharedPreferences.setMockInitialValues({});
    final store = NfcLocalStore();
    const session = NfcWriteSession(
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
    );
    const pending = NfcPendingBinding(
      houseId: 8,
      cageId: 10,
      tagUid: '04AABBCC',
      payload: 'r1.8.a.1.signature',
      requestId: 'request-1',
      replaceExisting: false,
    );

    await store.saveSession(session);
    await store.savePendingBindings([pending]);

    expect((await store.readSession())?.items.single.queueItem.cageId, 10);
    expect((await store.readPendingBindings()).single.requestId, 'request-1');
  });
}
