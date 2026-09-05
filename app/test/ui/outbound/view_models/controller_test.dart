import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/outbound/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/storage/outbound.dart';
import 'package:rabbit_flutter/src/domain/outbound/workflow.dart';
import 'package:rabbit_flutter/src/ui/outbound/view_models/controller.dart';

void main() {
  test(
      'selection, early sale, confirmation and idempotent submit share one state',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final store = OutboundLocalStore();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: store,
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    expect(controller.state.selectedRabbitIds, {1});
    controller.selectEarlySale(controller.state.rabbits[1], '客户提前采购');
    expect(controller.state.selectedRabbitIds, {1, 2});
    expect(controller.state.selectedItems.last.selectionType, 'EARLY_SALE');
    controller.toggleRabbit(controller.state.rabbits[1]);
    expect(controller.state.selectedRabbitIds, {1});
    controller.selectEarlySale(controller.state.rabbits[1], '客户提前采购');

    controller.updateForm(totalWeight: '6.5', unitPrice: '18');
    await controller.continueToConfirm();
    expect(controller.state.isConfirming, isTrue);
    expect(gateway.lastSaveStatus, 'WAITING_CONFIRMATION');
    expect(gateway.lastDraftBatchAllocations.single.batchId, 20);
    expect(gateway.lastDraftBatchAllocations.single.actualWeightKg, 6.5);

    await controller.submit();
    expect(controller.state.submitStatus, OutboundSubmitStatus.success);
    expect(controller.state.result?.rabbitCount, 2);
    expect(gateway.lastRequestId, isNotEmpty);
    expect(gateway.lastUnitPrice, 18);
    expect(gateway.lastBatchAllocations.single.batchId, 20);
    expect(gateway.lastBatchAllocations.single.actualWeightKg, 6.5);
    expect(gateway.submitCalls, 1);
    expect(await store.readSnapshot(101, 8), isNull);
    expect(await store.readPendingRequest(101, 8), isNull);
  });

  test('NFC cage selection adds eligible rabbits without reversing selection',
      () async {
    SharedPreferences.setMockInitialValues({});
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 102, houseId: 8, entryType: 'HOUSE'),
      repository: FakeOutboundGateway(),
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    controller.toggleCage(10);
    expect(controller.state.selectedRabbitIds, isEmpty);

    controller.selectCage(10);
    expect(controller.state.selectedRabbitIds, {1});
    controller.selectCage(10);
    expect(controller.state.selectedRabbitIds, {1});
    expect(controller.state.selectedRabbitIds.contains(2), isFalse);
    expect(controller.state.selectedRabbitIds.contains(3), isFalse);
  });

  test('bulk selection excludes exceptional rabbits and survives filtering',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    controller.selectEarlySale(controller.state.rabbits[1], '客户提前采购');
    controller.setFilter(OutboundEligibility.blocked);
    expect(controller.state.visibleRabbits.map((item) => item.rabbitId), [3]);
    expect(controller.state.selectedRabbitIds, {1, 2});

    controller.toggleHouse();
    expect(controller.state.selectedRabbitIds, {2});
    controller.toggleHouse();
    expect(controller.state.selectedRabbitIds, {1, 2});
    controller.setSelectedOnly(true);
    expect(
        controller.state.visibleRabbits.map((item) => item.rabbitId), [1, 2]);
    controller.setFilter(OutboundEligibility.needsAction);
    expect(controller.state.selectedOnly, isFalse);
    expect(controller.state.visibleRabbits.map((item) => item.rabbitId), [3]);
    expect(controller.state.selectedCount, 2);
    expect(controller.state.selectedCageCount, 1);
    expect(controller.state.selectedRowCount, 1);
    expect(controller.state.selectedRabbitIds.contains(3), isFalse);
  });

  test('entry range sets the mode and a draft restores the chosen mode',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final store = OutboundLocalStore();
    const entry = OutboundEntry(
      userId: 909,
      houseId: 8,
      entryType: 'HOUSE',
    );
    final first = OutboundController(
      entry: entry,
      repository: gateway,
      store: store,
      onCompleted: () {},
    );
    await _ready(first);

    expect(first.state.mode, OutboundSelectionMode.house);
    final selected = {...first.state.selectedRabbitIds};
    first.setMode(OutboundSelectionMode.row);
    first.setSelectedOnly(true);
    expect(first.state.selectedRabbitIds, selected);
    await store.flush();
    first.dispose();

    final restored = OutboundController(
      entry: entry,
      repository: gateway,
      store: store,
      onCompleted: () {},
    );
    addTearDown(restored.dispose);
    await _ready(restored);

    expect(restored.state.mode, OutboundSelectionMode.row);
    expect(restored.state.selectedOnly, isTrue);
    expect(restored.state.selectedRabbitIds, selected);

    var userId = 910;
    for (final scope in const [
      (entryType: 'ROW', mode: OutboundSelectionMode.row),
      (entryType: 'CAGE', mode: OutboundSelectionMode.cage),
      (entryType: 'RABBIT', mode: OutboundSelectionMode.cage),
    ]) {
      final scoped = OutboundController(
        entry: OutboundEntry(
          userId: userId++,
          houseId: 8,
          entryType: scope.entryType,
        ),
        repository: gateway,
        store: store,
        onCompleted: () {},
      );
      await _ready(scoped);
      expect(scoped.state.mode, scope.mode);
      scoped.dispose();
    }
  });

  test('conflicts retain form and remove only affected rabbits', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()..returnConflict = true;
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.selectEarlySale(controller.state.rabbits[1], '提前采购');
    controller.updateForm(
      totalWeight: '6.5',
      unitPrice: '18',
      customer: '测试客户',
    );
    await controller.continueToConfirm();
    final frozenSaveCalls = gateway.saveCalls;
    await controller.submit();

    expect(controller.state.submitStatus, OutboundSubmitStatus.conflict);
    expect(
      gateway.saveCalls,
      frozenSaveCalls + 1,
      reason: '正式提交前必须强制保存一次最新确认草稿',
    );
    expect(controller.state.customer, '测试客户');
    final conflictRequestId = gateway.lastRequestId;
    await controller.removeConflicts();
    expect(controller.state.selectedRabbitIds, {2});
    expect(controller.state.customer, '测试客户');
    expect(controller.state.totalWeight, isEmpty);
    expect(controller.state.batchAllocationWeights, isEmpty);
    expect(controller.state.submitStatus, OutboundSubmitStatus.idle);

    gateway.returnConflict = false;
    controller.updateForm(totalWeight: '3.0');
    await controller.submit();
    expect(controller.state.submitStatus, OutboundSubmitStatus.success);
    expect(gateway.lastRequestId, isNot(conflictRequestId));
    expect(gateway.requestIds.toSet().length, 2);
  });

  test('same-batch removal invalidates measured group and order weights',
      () async {
    SharedPreferences.setMockInitialValues({});
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 111, houseId: 8, entryType: 'HOUSE'),
      repository: FakeOutboundGateway(),
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    controller.selectEarlySale(controller.state.rabbits[1], '客户提前采购');
    controller.updateForm(totalWeight: '6.200', unitPrice: '18');
    controller.updateBatchAllocation('20', '6.200');
    controller.removeRabbit(2);

    expect(controller.state.selectedRabbitIds, {1});
    expect(controller.state.batchAllocationWeights, isEmpty);
    expect(controller.state.totalWeight, isEmpty);
  });

  test('same-batch addition invalidates measured group and order weights',
      () async {
    SharedPreferences.setMockInitialValues({});
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 112, houseId: 8, entryType: 'HOUSE'),
      repository: FakeOutboundGateway(),
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    controller.updateForm(totalWeight: '3.200', unitPrice: '18');
    controller.updateBatchAllocation('20', '3.200');
    controller.selectEarlySale(controller.state.rabbits[1], '客户提前采购');

    expect(controller.state.selectedRabbitIds, {1, 2});
    expect(controller.state.batchAllocationWeights, isEmpty);
    expect(controller.state.totalWeight, isEmpty);
  });

  test('whole-group removal retains other measured group and recomputes total',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()..task = _mixedTask();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 113, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    controller.selectEarlySale(controller.state.rabbits[1], '客户提前采购');
    controller.updateForm(totalWeight: '6.500', unitPrice: '18');
    controller.updateBatchAllocation('20', '3.200');
    controller.updateBatchAllocation('21', '3.300');
    controller.removeRabbit(2);

    expect(controller.state.selectedRabbitIds, {1});
    expect(controller.state.batchAllocationWeights, {'20': '3.200'});
    expect(controller.state.totalWeight, '3.200');
  });

  test(
      'allocation-only edits save empty, partial, complete, and cleared drafts',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()..task = _mixedTask();
    final store = OutboundLocalStore();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 115, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: store,
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.selectEarlySale(controller.state.rabbits[1], '客户提前采购');
    await controller.continueToConfirm();
    expect(gateway.lastDraftBatchAllocations, isEmpty);

    var previousSaveCalls = gateway.saveCalls;
    controller.updateBatchAllocation('20', '3.200');
    expect(controller.state.syncStatus, OutboundSyncStatus.saving);
    await store.flush();
    expect(
      (await store.readSnapshot(115, 8))!.batchAllocationWeights,
      {'20': '3.200'},
    );
    await Future<void>.delayed(const Duration(milliseconds: 800));
    await _waitFor(() => gateway.saveCalls > previousSaveCalls);
    expect(gateway.lastDraftBatchAllocations, hasLength(1));
    expect(gateway.lastDraftBatchAllocations.single.batchId, 20);
    expect(controller.state.syncStatus, OutboundSyncStatus.saved);

    previousSaveCalls = gateway.saveCalls;
    controller.updateBatchAllocation('21', '3.300');
    await Future<void>.delayed(const Duration(milliseconds: 800));
    await _waitFor(() => gateway.saveCalls > previousSaveCalls);
    expect(
      gateway.lastDraftBatchAllocations.map((item) => item.batchId),
      [20, 21],
    );

    previousSaveCalls = gateway.saveCalls;
    controller.updateBatchAllocation('20', '');
    await Future<void>.delayed(const Duration(milliseconds: 800));
    await _waitFor(() => gateway.saveCalls > previousSaveCalls);
    expect(gateway.lastDraftBatchAllocations, hasLength(1));
    expect(gateway.lastDraftBatchAllocations.single.batchId, 21);

    gateway.saveError = const ApiException('草稿保存失败');
    controller.updateBatchAllocation('21', '3.400');
    expect(controller.state.syncStatus, OutboundSyncStatus.saving);
    await Future<void>.delayed(const Duration(milliseconds: 800));
    await _waitFor(
      () => controller.state.syncStatus == OutboundSyncStatus.failed,
    );
    expect(controller.state.errorMessage, '草稿保存失败');
  });

  test('immediate allocation edit is force-saved before final submit',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()..task = _mixedTask();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 117, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.selectEarlySale(controller.state.rabbits[1], '客户提前采购');
    controller.updateForm(totalWeight: '6.500', unitPrice: '18');
    controller.updateBatchAllocation('21', '3.300');
    await controller.continueToConfirm();
    final saveCallsBeforeEdit = gateway.saveCalls;

    controller.updateBatchAllocation('20', '3.200');
    await controller.submit();

    expect(gateway.saveCalls, saveCallsBeforeEdit + 1);
    expect(
      gateway.lastDraftBatchAllocations.map((item) => item.actualWeightKg),
      [3.2, 3.3],
    );
    expect(
      gateway.lastBatchAllocations.map((item) => item.actualWeightKg),
      [3.2, 3.3],
    );
    expect(gateway.submitCalls, 1);
  });

  test('immediate price edit is force-saved before final submit', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 118, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.updateForm(totalWeight: '3.2', unitPrice: '18');
    await controller.continueToConfirm();

    controller.updateForm(unitPrice: '19');
    await controller.submit();

    expect(gateway.lastDraftUnitPrice, 19);
    expect(gateway.lastUnitPrice, 19);
    expect(gateway.submitCalls, 1);
  });

  test('failed forced save never sends or persists a final submit', () async {
    SharedPreferences.setMockInitialValues({});
    final store = OutboundLocalStore();
    final gateway = FakeOutboundGateway();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 119, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: store,
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.updateForm(totalWeight: '3.2', unitPrice: '18');
    await controller.continueToConfirm();
    gateway.saveError = const ApiException('草稿保存失败');

    controller.updateForm(unitPrice: '19');
    await controller.submit();

    expect(controller.state.submitStatus, OutboundSubmitStatus.failed);
    expect(controller.state.errorMessage, '草稿保存失败');
    expect(controller.state.requestId, isNull);
    expect(gateway.submitCalls, 0);
    expect(await store.readPendingRequest(119, 8), isNull);
  });

  test('final submit uses the forced-save response as its source', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 120, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.updateForm(totalWeight: '3.2', unitPrice: '18');
    await controller.continueToConfirm();
    gateway.saveResponse = gateway.task.copyWith(
      revision: gateway.task.revision + 1,
      totalWeight: 3.4,
      unitPrice: 19,
      batchAllocations: const [
        OutboundBatchAllocation(batchId: 20, actualWeightKg: 3.4),
      ],
    );

    await controller.submit();

    expect(controller.state.totalWeight, '3.4');
    expect(controller.state.unitPrice, '19.0');
    expect(gateway.lastBatchAllocations.single.actualWeightKg, 3.4);
    expect(gateway.lastUnitPrice, 19);
  });

  test('server-normalized forced save rotates an existing requestId', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()
      ..submitError = const ApiException('校验失败', statusCode: 400);
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 123, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.updateForm(totalWeight: '3.2', unitPrice: '18');
    await controller.continueToConfirm();
    await controller.submit();
    final firstRequestId = gateway.lastRequestId;

    gateway
      ..submitError = null
      ..saveResponse = gateway.task.copyWith(
        status: 'WAITING_CONFIRMATION',
        revision: gateway.task.revision + 1,
        totalWeight: 3.4,
        unitPrice: 19,
        batchAllocations: const [
          OutboundBatchAllocation(batchId: 20, actualWeightKg: 3.4),
        ],
      );
    await controller.submit();

    expect(gateway.requestIds, hasLength(2));
    expect(gateway.requestIds.last, isNot(firstRequestId));
    expect(gateway.lastBatchAllocations.single.actualWeightKg, 3.4);
    expect(gateway.lastUnitPrice, 19);
  });

  test('server-resumed draft restores batch allocation weights', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()
      ..task = _task().copyWith(
        resumed: true,
        totalWeight: 3.2,
        unitPrice: 18,
        batchAllocations: const [
          OutboundBatchAllocation(batchId: 20, actualWeightKg: 3.2),
        ],
      );
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 114, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    expect(controller.state.totalWeight, '3.2');
    expect(controller.state.unitPrice, '18.0');
    expect(controller.state.batchAllocationWeights, {'20': '3.2'});
  });

  test('offline draft field presence migrates legacy and explicit empty data',
      () async {
    final serverTask = _task().copyWith(
      resumed: true,
      totalWeight: 3.2,
      unitPrice: 18,
      batchAllocations: const [
        OutboundBatchAllocation(batchId: 20, actualWeightKg: 3.2),
      ],
    );
    final legacy = _snapshot(customer: '', totalWeight: '3.2').toJson()
      ..remove('batchAllocationWeights');
    SharedPreferences.setMockInitialValues({
      'outbound.task.121.8': jsonEncode(legacy),
    });
    final legacyController = OutboundController(
      entry: const OutboundEntry(userId: 121, houseId: 8, entryType: 'HOUSE'),
      repository: FakeOutboundGateway()..task = serverTask,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    await _ready(legacyController);
    expect(legacyController.state.batchAllocationWeights, {'20': '3.2'});
    legacyController.dispose();

    final explicitEmpty = _snapshot(customer: '', totalWeight: '3.2').toJson();
    SharedPreferences.setMockInitialValues({
      'outbound.task.122.8': jsonEncode(explicitEmpty),
    });
    final currentController = OutboundController(
      entry: const OutboundEntry(userId: 122, houseId: 8, entryType: 'HOUSE'),
      repository: FakeOutboundGateway()..task = serverTask,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(currentController.dispose);
    await _ready(currentController);
    expect(currentController.state.batchAllocationWeights, isEmpty);
  });

  test('failed submit returns to editable selection state', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()
      ..submitError = const ApiException('提交被拒绝', statusCode: 400);
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    controller.updateForm(totalWeight: '6.5', unitPrice: '18');
    await controller.continueToConfirm();
    await controller.submit();

    expect(controller.state.submitStatus, OutboundSubmitStatus.failed);
    expect(controller.state.errorMessage, '提交被拒绝');

    await controller.backToSelection();

    expect(controller.state.submitStatus, OutboundSubmitStatus.idle);
    expect(controller.state.isConfirming, isFalse);
    expect(controller.state.errorMessage, isNull);
    expect(controller.state.task?.status, 'SELECTING');
    expect(gateway.lastSaveStatus, 'SELECTING');
  });

  test('rejected back-to-selection save still leaves the draft editable',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()
      ..submitError = const ApiException(
        'RABBIT_NOT_ELIGIBLE: 1',
        statusCode: 400,
      );
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 124, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.updateForm(totalWeight: '3.2', unitPrice: '18');
    await controller.continueToConfirm();
    await controller.submit();
    gateway.saveError = const ApiException('RABBIT_NOT_ELIGIBLE: 1');

    await expectLater(controller.backToSelection(), completes);

    expect(controller.state.isConfirming, isFalse);
    expect(controller.state.submitStatus, OutboundSubmitStatus.idle);
    expect(controller.state.syncStatus, OutboundSyncStatus.failed);
    expect(controller.state.errorMessage, 'RABBIT_NOT_ELIGIBLE: 1');
  });

  test('discarding a resumed draft cancels it before creating a new task',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()
      ..task = _task().copyWith(resumed: true);
    final controller = OutboundController(
      entry: const OutboundEntry(
          userId: 101, houseId: 8, entryType: 'CAGE', cageId: 10),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    expect(controller.state.task?.resumed, isTrue);
    await controller.discardResumedDraft();

    expect(gateway.cancelCalls, 1);
    expect(gateway.lastResumeExisting, isFalse);
    expect(controller.state.task?.resumed, isFalse);
  });

  test('refresh removes a selected rabbit that is now blocked', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    final blocked = _task().rabbits.first.copyWithBlockedForTest();
    gateway.task =
        _task().copyWith(rabbits: [blocked, ..._task().rabbits.skip(1)]);
    await controller.refresh();

    expect(controller.state.selectedRabbitIds, isEmpty);
    expect(controller.state.bannerMessage, '1只兔因状态变化已移出');
  });

  test('known stale request is cleared before task recovery', () async {
    SharedPreferences.setMockInitialValues(
        {'outbound.request.101.8': 'stale-request'});
    final gateway = FakeOutboundGateway()
      ..statusError = const ApiException('提交请求不存在', businessCode: 404);
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    expect(controller.state.submitStatus, OutboundSubmitStatus.idle);
    expect(controller.state.task, isNotNull);
    expect(await OutboundLocalStore().readPendingRequest(101, 8), isNull);
  });

  test('ambiguous request without a cached task does not create a replacement',
      () async {
    SharedPreferences.setMockInitialValues(
        {'outbound.request.101.8': 'unknown-request'});
    final gateway = FakeOutboundGateway()
      ..statusError = const ApiException('网络中断');
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _settled(controller);

    expect(controller.state.submitStatus, OutboundSubmitStatus.unknown);
    expect(controller.state.task, isNull);
    expect(gateway.createCalls, 0);
  });

  test(
      'app restart resolves a completed pending request before creating a task',
      () async {
    const requestId = '11111111-1111-1111-1111-111111111111';
    SharedPreferences.setMockInitialValues(
        {'outbound.request.101.8': requestId});
    final gateway = FakeOutboundGateway()
      ..statusResult = _completedResult(requestId);
    var completionNotifications = 0;
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () => completionNotifications++,
    );
    addTearDown(controller.dispose);
    await _ready(controller);

    expect(controller.state.submitStatus, OutboundSubmitStatus.success);
    expect(controller.state.result?.requestId, requestId);
    expect(gateway.createCalls, 0);
    expect(completionNotifications, 1);
    expect(await OutboundLocalStore().readPendingRequest(101, 8), isNull);
  });

  test('local outbound data is isolated by user and clearing preserves peers',
      () async {
    SharedPreferences.setMockInitialValues({});
    final store = OutboundLocalStore();
    await store.saveSnapshot(
        101, _snapshot(customer: '用户甲', totalWeight: '3.1'));
    await store.savePendingRequest(
        101, 8, '11111111-1111-1111-1111-111111111111');
    await store.saveSnapshot(
        202, _snapshot(customer: '用户乙', totalWeight: '4.2'));
    await store.savePendingRequest(
        202, 8, '22222222-2222-2222-2222-222222222222');

    expect((await store.readSnapshot(101, 8))?.customer, '用户甲');
    expect((await store.readSnapshot(202, 8))?.customer, '用户乙');

    await store.clear(101, 8);

    expect(await store.readSnapshot(101, 8), isNull);
    expect(await store.readPendingRequest(101, 8), isNull);
    expect((await store.readSnapshot(202, 8))?.customer, '用户乙');
    expect(await store.readPendingRequest(202, 8),
        '22222222-2222-2222-2222-222222222222');
  });

  test('form mutation survives immediate disposal and recreation', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final first = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    await _ready(first);

    first.updateForm(
        totalWeight: '6.50',
        unitPrice: '18.',
        customer: '立即离开客户',
        remark: '未经过服务端防抖');
    first.dispose();
    gateway.createError = const ApiException('网络中断');

    final recreated = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(recreated.dispose);
    await _ready(recreated);

    expect(recreated.state.totalWeight, '6.50');
    expect(recreated.state.unitPrice, '18.');
    expect(recreated.state.customer, '立即离开客户');
    expect(recreated.state.remark, '未经过服务端防抖');
    expect(recreated.state.syncStatus, OutboundSyncStatus.offline);
  });

  test('mixed batch weights and common price survive immediate recreation',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()..task = _mixedTask();
    final first = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    await _ready(first);
    first.selectEarlySale(first.state.rabbits[1], '客户提前采购');
    first.updateForm(totalWeight: '6.500', unitPrice: '18.25');
    first.updateBatchAllocation('20', '3.200');
    first.updateBatchAllocation('21', '3.300');
    first.dispose();
    gateway.createError = const ApiException('网络中断');

    final recreated = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(recreated.dispose);
    await _ready(recreated);

    expect(recreated.state.totalWeight, '6.500');
    expect(recreated.state.unitPrice, '18.25');
    expect(recreated.state.batchAllocationWeights, {
      '20': '3.200',
      '21': '3.300',
    });
    expect(recreated.state.batchAllocations.map((item) => item.actualWeightKg),
        [3.2, 3.3]);
    expect(recreated.state.syncStatus, OutboundSyncStatus.offline);
  });

  test('unchanged retry reuses requestId and meaningful edit rotates it',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()
      ..submitError = const ApiException('校验失败', statusCode: 400);
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.updateForm(totalWeight: '3.2', unitPrice: '18');
    await controller.continueToConfirm();

    await controller.submit();
    final firstRequestId = gateway.lastRequestId;
    controller.updateForm(totalWeight: '3.20', customer: '  ');
    await controller.submit();
    expect(gateway.lastRequestId, firstRequestId);

    controller.updateForm(customer: '新客户');
    await controller.submit();
    expect(gateway.lastRequestId, isNot(firstRequestId));
  });

  test('refresh rotates a failed request when selected state versions change',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()
      ..submitError = const ApiException('校验失败', statusCode: 400);
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 116, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.updateForm(totalWeight: '3.2', unitPrice: '18');
    await controller.continueToConfirm();
    await controller.submit();
    final firstRequestId = gateway.lastRequestId;
    expect(controller.state.requestId, firstRequestId);

    final taskJson = gateway.task.toJson();
    final rabbits = (taskJson['rabbits']! as List<dynamic>)
        .map((item) => Map<String, dynamic>.from(item as Map))
        .toList();
    rabbits.first['stateVersion'] = 4;
    taskJson['rabbits'] = rabbits;
    gateway.task = OutboundTask.fromJson(taskJson);

    await controller.refresh();
    expect(controller.state.requestId, isNull);
    await controller.submit();
    expect(gateway.lastRequestId, isNot(firstRequestId));
  });

  test('newer initialize response wins over an older response', () async {
    SharedPreferences.setMockInitialValues({});
    final first = Completer<OutboundTask>();
    final second = Completer<OutboundTask>();
    final gateway = FakeOutboundGateway()
      ..createWaiters.addAll([first, second]);
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _waitFor(() => gateway.createCalls == 1);

    final latestInitialize = controller.initialize();
    await _waitFor(() => gateway.createCalls == 2);
    second.complete(_taskWithId('task-new', revision: 4));
    await latestInitialize;
    first.complete(_taskWithId('task-old', revision: 1));
    await Future<void>.delayed(Duration.zero);

    expect(controller.state.task?.taskId, 'task-new');
    expect(controller.state.task?.revision, 4);
  });

  test('newer refresh response wins and keeps current selection', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    final older = Completer<OutboundTask>();
    final newer = Completer<OutboundTask>();
    gateway.precheckWaiters.addAll([older, newer]);

    final olderRefresh = controller.refresh();
    await _waitFor(() => gateway.precheckCalls == 1);
    final newerRefresh = controller.refresh();
    await _waitFor(() => gateway.precheckCalls == 2);
    newer.complete(_task().copyWith(revision: 5));
    await newerRefresh;
    older.complete(_task().copyWith(revision: 2, rabbits: [
      _task().rabbits.first.copyWithBlockedForTest(),
      ..._task().rabbits.skip(1)
    ]));
    await olderRefresh;

    expect(controller.state.task?.revision, greaterThanOrEqualTo(5));
    expect(controller.state.selectedRabbitIds, {1});
  });

  test('late save response cannot overwrite a newer form snapshot', () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    final olderSave = Completer<OutboundTask>();
    final newerSave = Completer<OutboundTask>();
    gateway.saveWaiters.addAll([olderSave, newerSave]);
    controller.updateForm(customer: '旧客户', totalWeight: '3.2');

    final freezing = controller.continueToConfirm();
    await _waitFor(() => gateway.saveCalls == 1);
    controller.updateForm(customer: '新客户', remark: '响应返回前修改');
    gateway.task = _task().copyWith(revision: 5);
    final refresh = controller.refresh();
    await _waitFor(() => controller.state.task?.revision == 5);
    olderSave.complete(_task().copyWith(
        status: 'WAITING_CONFIRMATION', revision: 1, customer: '旧客户'));
    await _waitFor(() => gateway.saveCalls == 2);

    expect(controller.state.task?.revision, 5);
    newerSave.complete(_task().copyWith(revision: 6));
    await freezing;
    await refresh;

    expect(controller.state.customer, '新客户');
    expect(controller.state.remark, '响应返回前修改');
    expect(controller.state.task?.revision, 6);
  });

  test('slow initialize refresh and save do not write state after dispose',
      () async {
    SharedPreferences.setMockInitialValues({});
    final slowCreate = Completer<OutboundTask>();
    final createGateway = FakeOutboundGateway()..createWaiters.add(slowCreate);
    final initializing = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: createGateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    await _waitFor(() => createGateway.createCalls == 1);
    initializing.dispose();
    slowCreate.complete(_task());
    await Future<void>.delayed(Duration.zero);

    final gateway = FakeOutboundGateway();
    final refreshing = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    await _ready(refreshing);
    final slowRefresh = Completer<OutboundTask>();
    gateway.precheckWaiters.add(slowRefresh);
    final refresh = refreshing.refresh();
    await _waitFor(() => gateway.precheckCalls == 1);
    refreshing.dispose();
    slowRefresh.complete(_task().copyWith(revision: 8));
    await refresh;

    final saving = OutboundController(
      entry: const OutboundEntry(userId: 202, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    await _ready(saving);
    final slowSave = Completer<OutboundTask>();
    gateway.saveWaiters.add(slowSave);
    final freeze = saving.continueToConfirm();
    await _waitFor(() => gateway.saveCalls >= 1);
    saving.dispose();
    slowSave.complete(
        _task().copyWith(status: 'WAITING_CONFIRMATION', revision: 3));
    await freeze;
  });

  test('two immediate submits create one request and one server call',
      () async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final controller = OutboundController(
      entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
      repository: gateway,
      store: OutboundLocalStore(),
      onCompleted: () {},
    );
    addTearDown(controller.dispose);
    await _ready(controller);
    controller.updateForm(totalWeight: '3.2', unitPrice: '18');
    await controller.continueToConfirm();
    final response = Completer<OutboundSubmitResult>();
    gateway.submitWaiter = response;

    final first = controller.submit();
    final second = controller.submit();
    await _waitFor(() => gateway.submitCalls == 1);
    final requestId = gateway.lastRequestId!;
    response.complete(_completedResult(requestId));
    await Future.wait([first, second]);

    expect(gateway.submitCalls, 1);
    expect(gateway.requestIds, [requestId]);
    expect(controller.state.submitStatus, OutboundSubmitStatus.success);
  });

  for (final failure in [
    const ApiException('HTTP 500', statusCode: 500),
    const ApiException('HTTP 500 with validation body',
        statusCode: 500, businessCode: 400),
    const ApiException('业务码 500', businessCode: 500),
    const ApiException('连接超时'),
  ]) {
    test('${failure.message} preserves pending requestId for polling',
        () async {
      SharedPreferences.setMockInitialValues({});
      final gateway = FakeOutboundGateway()..submitError = failure;
      final controller = OutboundController(
        entry: const OutboundEntry(userId: 101, houseId: 8, entryType: 'HOUSE'),
        repository: gateway,
        store: OutboundLocalStore(),
        onCompleted: () {},
      );
      addTearDown(controller.dispose);
      await _ready(controller);
      controller.updateForm(totalWeight: '3.2', unitPrice: '18');
      await controller.continueToConfirm();

      await controller.submit();

      expect(controller.state.submitStatus, OutboundSubmitStatus.unknown);
      expect(controller.state.requestId, gateway.lastRequestId);
      expect(await OutboundLocalStore().readPendingRequest(101, 8),
          gateway.lastRequestId);
    });
  }
}

OutboundLocalSnapshot _snapshot(
    {required String customer, required String totalWeight}) {
  return OutboundLocalSnapshot(
    task: _task(),
    saleTime: DateTime(2026, 7, 30),
    totalWeight: totalWeight,
    unitPrice: '',
    customer: customer,
    remark: '',
  );
}

Future<void> _ready(OutboundController controller) async {
  for (var i = 0; i < 20; i++) {
    if (controller.state.loadStatus == OutboundLoadStatus.ready) return;
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
  fail('controller did not initialize');
}

Future<void> _settled(OutboundController controller) async {
  for (var i = 0; i < 20; i++) {
    if (controller.state.loadStatus != OutboundLoadStatus.loading) return;
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
  fail('controller did not settle');
}

Future<void> _waitFor(bool Function() condition) async {
  for (var i = 0; i < 100; i++) {
    if (condition()) return;
    await Future<void>.delayed(const Duration(milliseconds: 2));
  }
  fail('condition was not reached');
}

class FakeOutboundGateway implements OutboundGateway {
  var task = _task();
  bool returnConflict = false;
  String? lastSaveStatus;
  String? lastRequestId;
  double? lastUnitPrice;
  double? lastDraftUnitPrice;
  List<OutboundBatchAllocation> lastBatchAllocations = const [];
  List<OutboundBatchAllocation> lastDraftBatchAllocations = const [];
  int submitCalls = 0;
  int cancelCalls = 0;
  int createCalls = 0;
  int precheckCalls = 0;
  int saveCalls = 0;
  bool? lastResumeExisting;
  ApiException? statusError;
  OutboundSubmitResult? statusResult;
  ApiException? createError;
  ApiException? submitError;
  Object? saveError;
  OutboundTask? saveResponse;
  Completer<OutboundSubmitResult>? submitWaiter;
  final requestIds = <String>[];
  final createWaiters = <Completer<OutboundTask>>[];
  final precheckWaiters = <Completer<OutboundTask>>[];
  final saveWaiters = <Completer<OutboundTask>>[];

  @override
  Future<OutboundTask> createTask(
      {required int houseId,
      required String entryType,
      int? rabbitId,
      int? cageId,
      String? rowCode,
      bool resumeExisting = true}) async {
    createCalls++;
    if (createError != null) throw createError!;
    if (createWaiters.isNotEmpty) {
      return createWaiters.removeAt(0).future;
    }
    lastResumeExisting = resumeExisting;
    task = task.copyWith(resumed: resumeExisting ? task.resumed : false);
    return task;
  }

  @override
  Future<OutboundTask> precheck(
      {required int houseId, required String taskId}) async {
    precheckCalls++;
    if (precheckWaiters.isNotEmpty) {
      return precheckWaiters.removeAt(0).future;
    }
    return task;
  }

  @override
  Future<OutboundTask> saveDraft(
      {required int houseId,
      required OutboundTask task,
      required String status,
      required List<OutboundSelectedItem> items,
      required DateTime saleTime,
      double? totalWeight,
      double? unitPrice,
      required List<OutboundBatchAllocation> batchAllocations,
      String? customer,
      String? remark}) async {
    saveCalls++;
    lastSaveStatus = status;
    lastDraftUnitPrice = unitPrice;
    lastDraftBatchAllocations = batchAllocations;
    if (saveError != null) throw saveError!;
    if (saveWaiters.isNotEmpty) {
      return saveWaiters.removeAt(0).future;
    }
    final response = saveResponse;
    if (response != null) {
      this.task = response;
      return response;
    }
    this.task = task.copyWith(
        status: status,
        revision: task.revision + 1,
        saleTime: saleTime,
        totalWeight: totalWeight,
        unitPrice: unitPrice,
        customer: customer,
        remark: remark,
        selectedItems: items,
        batchAllocations: batchAllocations);
    return this.task;
  }

  @override
  Future<OutboundSubmitResult> submit(
      {required int houseId,
      required OutboundTask task,
      required List<OutboundSelectedItem> items,
      required String requestId,
      required DateTime saleTime,
      required double totalWeight,
      required double unitPrice,
      required List<OutboundBatchAllocation> batchAllocations,
      String? customer,
      String? remark}) async {
    submitCalls++;
    lastRequestId = requestId;
    lastUnitPrice = unitPrice;
    lastBatchAllocations = batchAllocations;
    requestIds.add(requestId);
    if (submitError != null) throw submitError!;
    if (submitWaiter != null) return submitWaiter!.future;
    if (returnConflict) {
      return OutboundSubmitResult(
        status: 'CONFLICT',
        requestId: requestId,
        taskId: task.taskId,
        rabbitCount: 0,
        cageCount: 0,
        rowCount: 0,
        message: '本次出库未生效，草稿已保留',
        conflicts: const [
          OutboundConflict(
              rabbitId: 1,
              errorCode: 'RABBIT_STATE_CHANGED',
              currentState: '隔离',
              message: '状态已变化',
              recommendedAction: '移出'),
        ],
      );
    }
    return OutboundSubmitResult(
      status: 'COMPLETED',
      requestId: requestId,
      taskId: task.taskId,
      saleOrderId: 99,
      saleOrderNumber: 'SO-99',
      saleTime: saleTime,
      rabbitCount: items.length,
      cageCount: 1,
      rowCount: 1,
      totalWeight: totalWeight,
      totalAmount: totalWeight * unitPrice,
      message: '完成',
      conflicts: const [],
    );
  }

  @override
  Future<OutboundSubmitResult> status(
      {required int houseId, required String requestId}) async {
    if (statusError != null) throw statusError!;
    if (statusResult != null) return statusResult!;
    throw const ApiException('未配置状态结果');
  }

  @override
  Future<void> cancel({required int houseId, required String taskId}) async {
    cancelCalls++;
  }
}

OutboundTask _taskWithId(String taskId, {required int revision}) {
  final json = _task().toJson()
    ..['taskId'] = taskId
    ..['revision'] = revision;
  return OutboundTask.fromJson(json);
}

OutboundSubmitResult _completedResult(String requestId) {
  return OutboundSubmitResult(
    status: 'COMPLETED',
    requestId: requestId,
    taskId: 'task-1',
    saleOrderId: 99,
    saleOrderNumber: 'SO-99',
    saleTime: DateTime(2026, 7, 30),
    rabbitCount: 1,
    cageCount: 1,
    rowCount: 1,
    totalWeight: 3.2,
    message: '完成',
    conflicts: const [],
  );
}

extension on OutboundRabbit {
  OutboundRabbit copyWithBlockedForTest() {
    return OutboundRabbit(
      rabbitId: rabbitId,
      cageId: cageId,
      cageNumber: cageNumber,
      rowCode: rowCode,
      layerIndex: layerIndex,
      positionIndex: positionIndex,
      rabbitType: rabbitType,
      gender: gender,
      weight: weight,
      stage: '隔离',
      batchId: batchId,
      stateVersion: stateVersion + 1,
      eligibility: OutboundEligibility.blocked,
      reasonCode: 'RABBIT_QUARANTINED',
      message: '兔只处于隔离状态',
      recommendedAction: '解除隔离后重新预检',
      defaultSelected: false,
    );
  }
}

OutboundTask _mixedTask() {
  final json = _task().toJson();
  final rabbits = (json['rabbits']! as List<dynamic>)
      .map((item) => Map<String, dynamic>.from(item as Map))
      .toList();
  rabbits[1]['batchId'] = 21;
  json['rabbits'] = rabbits;
  return OutboundTask.fromJson(json);
}

OutboundTask _task() {
  const rabbits = [
    OutboundRabbit(
        rabbitId: 1,
        cageId: 10,
        cageNumber: '1-1-1',
        rowCode: 'R1',
        layerIndex: 1,
        positionIndex: 1,
        rabbitType: '2',
        gender: '0',
        weight: 3.2,
        stage: '可出售',
        batchId: 20,
        stateVersion: 0,
        eligibility: OutboundEligibility.normal,
        reasonCode: 'ELIGIBLE',
        message: '可正常出库',
        recommendedAction: '纳入',
        defaultSelected: true),
    OutboundRabbit(
        rabbitId: 2,
        cageId: 10,
        cageNumber: '1-1-1',
        rowCode: 'R1',
        layerIndex: 1,
        positionIndex: 1,
        rabbitType: '2',
        gender: '1',
        weight: 3.0,
        stage: '成长期',
        batchId: 20,
        stateVersion: 0,
        eligibility: OutboundEligibility.earlySale,
        reasonCode: 'EARLY',
        message: '需提前出售原因',
        recommendedAction: '逐兔处理',
        defaultSelected: false),
    OutboundRabbit(
        rabbitId: 3,
        cageId: 10,
        cageNumber: '1-1-1',
        rowCode: 'R1',
        layerIndex: 1,
        positionIndex: 1,
        rabbitType: '2',
        gender: '0',
        weight: 2.9,
        stage: '隔离',
        batchId: 20,
        stateVersion: 2,
        eligibility: OutboundEligibility.blocked,
        reasonCode: 'QUARANTINE',
        message: '隔离中',
        recommendedAction: '解除隔离',
        defaultSelected: false),
  ];
  return const OutboundTask(
    taskId: 'task-1',
    houseId: 8,
    entryType: 'HOUSE',
    status: 'SELECTING',
    revision: 0,
    resumed: false,
    summary:
        OutboundSummary(normal: 1, earlySale: 1, needsAction: 0, blocked: 1),
    rabbits: rabbits,
    selectedItems: [
      OutboundSelectedItem(
          rabbitId: 1, stateVersion: 0, selectionType: 'NORMAL')
    ],
  );
}
