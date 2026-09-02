import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/services/nfc/intents.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';

import '../../../ui/core/widgets/nfc_harness.dart';

void main() {
  late NfcHarness nfc;
  late NfcIntentService service;

  setUp(() {
    nfc = NfcHarness();
    service = NfcIntentService();
    addTearDown(service.dispose);
  });

  Future<List<NfcLaunchEvent>> collect() async {
    final events = <NfcLaunchEvent>[];
    final subscription = service.events.listen(events.add);
    addTearDown(subscription.cancel);
    return events;
  }

  test('delivers an injected tap with its payload and tag uid', () async {
    await service.initialize();
    final events = await collect();

    await nfc.tap(houseId: 8, cageId: 10, tagUid: '04C0FFEE');
    await pumpEventQueue();

    expect(events, hasLength(1));
    expect(events.single.payload, 'r1.8.a.1.signature');
    expect(events.single.tagUid, '04C0FFEE');
    expect(events.single.receivedAt, greaterThan(0));

    final target = NfcPayloadTarget.parse(events.single.payload);
    expect(target.houseId, 8);
    expect(target.cageId, 10);
  });

  test('stamps the tap with the moment it was received', () async {
    final tappedAt = DateTime.fromMillisecondsSinceEpoch(1700000000000);
    await service.initialize();
    final events = await collect();

    await nfc.tap(houseId: 8, cageId: 10, receivedAt: tappedAt);
    await pumpEventQueue();

    expect(events.single.receivedAt, tappedAt.millisecondsSinceEpoch);
  });

  test('drops taps that carry nothing usable', () async {
    await service.initialize();
    final events = await collect();

    await nfc.tapPayload('');
    await nfc.tapRaw(null);
    await nfc.tapRaw('not a map');
    await pumpEventQueue();

    expect(events, isEmpty);

    await nfc.tap(houseId: 8, cageId: 10);
    await pumpEventQueue();

    expect(
      events.map((event) => event.payload),
      ['r1.8.a.1.signature'],
      reason: 'the stream stays open for real taps after a dropped one',
    );
  });

  test('emits a tag that launched the app before any tap', () async {
    nfc.stubPendingTap(houseId: 8, cageId: 10, tagUid: '04LAUNCH');
    final events = await collect();

    await service.initialize();
    await pumpEventQueue();

    expect(nfc.pendingIntentCalls, 1);
    expect(events.single.tagUid, '04LAUNCH');
    expect(NfcPayloadTarget.parse(events.single.payload).cageId, 10);
  });

  test('initialize survives a host without the native bridge', () async {
    nfc.stubPendingMissingPlugin();
    final events = await collect();

    await expectLater(service.initialize(), completes);
    await pumpEventQueue();

    expect(nfc.pendingIntentCalls, 1);
    expect(events, isEmpty);

    await nfc.tap(houseId: 8, cageId: 10);
    await pumpEventQueue();

    expect(
      events,
      hasLength(1),
      reason: 'a missing pending intent must not stop later taps',
    );
  });

  test('surfaces a denied pending intent to the caller', () async {
    nfc.stubPendingFailure(code: 'denied');

    await expectLater(
      service.initialize(),
      throwsA(isA<PlatformException>()),
    );
  });

  test('initialize claims the pending intent only once', () async {
    nfc.stubPendingNothing();

    await service.initialize();
    await service.initialize();

    expect(nfc.pendingIntentCalls, 1);
  });

  test('dispose closes the stream and clears the handler', () async {
    await service.initialize();
    final closed = Completer<void>();
    final subscription = service.events.listen(
      null,
      onDone: closed.complete,
    );
    addTearDown(subscription.cancel);

    service.dispose();

    await expectLater(closed.future, completes);
    expect(
      await nfc.tap(houseId: 8, cageId: 10),
      isNull,
      reason: 'no handler is left listening on the channel',
    );
  });
}
