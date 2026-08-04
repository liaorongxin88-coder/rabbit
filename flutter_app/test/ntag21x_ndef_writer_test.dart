import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:nfc_manager/nfc_manager.dart';

import 'package:rabbit_flutter/src/data/services/nfc_hardware_service.dart';
import 'package:rabbit_flutter/src/data/services/ntag21x_ndef_writer.dart';

void main() {
  test('identifies only supported NXP NTAG21x version responses', () {
    expect(
      identifyNtag21x(Uint8List.fromList([0, 4, 4, 2, 1, 0, 0x0F, 3])),
      Ntag21xModel.ntag213,
    );
    expect(
      identifyNtag21x(Uint8List.fromList([0, 4, 4, 2, 1, 0, 0x11, 3])),
      Ntag21xModel.ntag215,
    );
    expect(
      identifyNtag21x(Uint8List.fromList([0, 4, 4, 2, 1, 0, 0x13, 3])),
      Ntag21xModel.ntag216,
    );
    expect(
      identifyNtag21x(Uint8List.fromList([0, 5, 4, 2, 1, 0, 0x0F, 3])),
      isNull,
    );
  });

  test('encodes the same single external record as nfc_manager', () {
    const payload = 'r1.2.u1.1.DrCBtJgInkFtKMEF';
    final raw = encodeExternalTypeNdef(
      domain: NfcHardwareService.externalDomain,
      type: NfcHardwareService.externalType,
      payload: payload,
    );
    final message = NdefMessage([
      NdefRecord.createExternal(
        NfcHardwareService.externalDomain,
        NfcHardwareService.externalType,
        Uint8List.fromList(ascii.encode(payload)),
      ),
    ]);

    expect(raw.length, message.byteLength);
    expect(raw.first, 0xD4);
    expect(
      decodeExternalTypeNdef(raw, NfcHardwareService.fullExternalType),
      payload,
    );
  });

  test('preserves the NTAG213 factory lock control TLV', () {
    final memory = Uint8List(Ntag21xModel.ntag213.ndefMemoryBytes)
      ..setRange(0, 8, [0x01, 0x03, 0xA0, 0x0C, 0x34, 0x03, 0x00, 0xFE]);

    final data = inspectType2Data(memory);

    expect(data.controlPrefix, [0x01, 0x03, 0xA0, 0x0C, 0x34]);
    expect(data.ndefMessage, isEmpty);
    expect(data.hasForeignData, isFalse);
  });

  test('detects another Type 2 TLV after the NDEF message', () {
    final message = encodeExternalTypeNdef(
      domain: NfcHardwareService.externalDomain,
      type: NfcHardwareService.externalType,
      payload: 'r1.2.u1.1.DrCBtJgInkFtKMEF',
    );
    final memory = Uint8List.fromList([
      0x03,
      message.length,
      ...message,
      0xFD,
      0x01,
      0x01,
      0xFE,
    ]);

    final data = inspectType2Data(memory);

    expect(data.hasForeignData, isTrue);
    expect(
      data.managedPayload(NfcHardwareService.fullExternalType),
      'r1.2.u1.1.DrCBtJgInkFtKMEF',
    );
  });

  test('writes and verifies NDEF through Type 2 pages', () async {
    final io = _FakeNtag21xIo.ntag213();
    const writer = Ntag21xNdefWriter();
    final snapshot = await writer.inspectIo(io);

    expect(snapshot, isNotNull);
    expect(snapshot!.writeBlocker, isNull);

    await writer.writeExternal(
      snapshot: snapshot,
      domain: NfcHardwareService.externalDomain,
      type: NfcHardwareService.externalType,
      payload: 'r1.2.u1.1.DrCBtJgInkFtKMEF',
    );

    final updated = await writer.inspectIo(io);
    expect(
      updated!.type2Data.managedPayload(
        NfcHardwareService.fullExternalType,
      ),
      'r1.2.u1.1.DrCBtJgInkFtKMEF',
    );
    expect(io.writtenPages.first, 5);
    expect(io.writtenPages.last, 5);
  });

  test('detects static locks before raw page writes', () async {
    final io = _FakeNtag21xIo.ntag213()..memory[10] = 0x01;

    final snapshot = await const Ntag21xNdefWriter().inspectIo(io);

    expect(snapshot!.writeBlocker, Ntag21xWriteBlocker.staticLocked);
  });

  test('keeps an empty recoverable NDEF when a page write is interrupted',
      () async {
    final io = _FakeNtag21xIo.ntag213()..failOnWrite = 3;
    const writer = Ntag21xNdefWriter();
    final snapshot = await writer.inspectIo(io);

    await expectLater(
      writer.writeExternal(
        snapshot: snapshot!,
        domain: NfcHardwareService.externalDomain,
        type: NfcHardwareService.externalType,
        payload: 'r1.2.u1.1.DrCBtJgInkFtKMEF',
      ),
      throwsStateError,
    );

    final interrupted = await writer.inspectIo(io);
    expect(interrupted!.type2Data.ndefMessage, isEmpty);
    expect(interrupted.type2Data.hasForeignData, isFalse);
  });
}

class _FakeNtag21xIo implements Ntag21xIo {
  _FakeNtag21xIo.ntag213()
      : version = Uint8List.fromList([0, 4, 4, 2, 1, 0, 0x0F, 3]),
        memory = Uint8List(45 * 4) {
    memory.setRange(12, 16, [0xE1, 0x10, 0x12, 0x00]);
    memory.setRange(
      16,
      24,
      [0x01, 0x03, 0xA0, 0x0C, 0x34, 0x03, 0x00, 0xFE],
    );
    memory[40 * 4 + 3] = 0xBD;
    memory[41 * 4 + 3] = 0xFF;
  }

  final Uint8List version;
  final Uint8List memory;
  final List<int> writtenPages = [];
  int? failOnWrite;
  var _writeCount = 0;

  @override
  Future<Uint8List> getVersion() async => Uint8List.fromList(version);

  @override
  Future<Uint8List> readPages(int pageOffset) async {
    final offset = pageOffset * 4;
    return Uint8List.fromList(memory.sublist(offset, offset + 16));
  }

  @override
  Future<void> writePage(int pageOffset, Uint8List data) async {
    _writeCount++;
    if (_writeCount == failOnWrite) {
      throw StateError('simulated interrupted write');
    }
    writtenPages.add(pageOffset);
    memory.setRange(pageOffset * 4, pageOffset * 4 + 4, data);
  }
}
