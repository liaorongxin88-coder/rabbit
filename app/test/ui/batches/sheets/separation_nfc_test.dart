import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/separation.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

import '../../core/widgets/nfc_harness.dart';

/// 分笼表单里的「碰一下目标笼位」。
///
/// 现场的动作是手里拎着断奶兔站在架子前，碰笼子比在一屏笼号里找那一行自然。
/// 碰一下是并行的第三条路：标签会掉、会没贴、手机可能没有 NFC，下拉必须一直在。
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late NfcHarness nfc;

  setUp(() {
    SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'tester'});
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});
    nfc = NfcHarness();
  });

  testWidgets('碰一下有空位的商品兔笼，目标笼位下拉直接选中它', (tester) async {
    final resolver = _FakeNfcResolver();
    await tester.pumpWidget(_testApp(resolver: resolver));
    await _openAndCapture(tester);

    await nfc.tap(houseId: 8, cageId: 12);
    await _settleCapture(tester);

    expect(find.text('C-01 · 商品兔 · 还可放 8 只'), findsOneWidget);
    expect(_hint(tester), '已选中 C-01 · 商品兔 · 还可放 8 只');
    expect(
      find.textContaining('母兔 #101（待分 6 只）→ C-01 · 商品兔 · 还可放 8 只'),
      findsOneWidget,
      reason: '提交前要说清这次动的是哪条记录、进的是哪个笼',
    );
    expect(resolver.calls, hasLength(1));
  });

  testWidgets('碰已放满的笼被拒绝，提示写出上限而不是只说不可选', (tester) async {
    await tester.pumpWidget(_testApp(cages: const [_targetCage, _fullCage]));
    await _openAndCapture(tester);

    await nfc.tap(houseId: 8, cageId: 13);
    await _settleCapture(tester);

    expect(_hint(tester), 'C-02 已放 10 只，商品兔笼上限 10 只，放不下了');
    expect(find.text('C-02 · 商品兔 · 还可放 0 只'), findsNothing);
    expect(
      find.textContaining('→ 尚未选择目标笼位'),
      findsOneWidget,
      reason: '被拒绝的笼不能悄悄留在目标笼位里',
    );
  });

  testWidgets('碰停用笼和种兔笼各自说明被拒的原因', (tester) async {
    await tester.pumpWidget(
      _testApp(cages: const [_targetCage, _disabledCage, _breedingCage]),
    );

    await _openAndCapture(tester);
    await nfc.tap(houseId: 8, cageId: 14);
    await _settleCapture(tester);
    expect(_hint(tester), 'C-03 已停用，不能作为分笼目标');

    await _startCapture(tester);
    await nfc.tap(houseId: 8, cageId: 15);
    await _settleCapture(tester);
    expect(_hint(tester), 'C-04 是种兔笼，分笼只能进空笼或商品兔笼');

    expect(find.textContaining('→ 尚未选择目标笼位'), findsOneWidget);
  });

  testWidgets('碰其他兔舍的标签直接拒绝，不去问后端', (tester) async {
    final resolver = _FakeNfcResolver();
    await tester.pumpWidget(_testApp(resolver: resolver));
    await _openAndCapture(tester);

    await nfc.tap(houseId: 9, cageId: 12);
    await _settleCapture(tester);

    expect(_hint(tester), '该标签属于其他兔舍，未选中');
    expect(resolver.calls, isEmpty, reason: '别的兔舍的笼位 id 不该拿去解析');
    expect(find.textContaining('→ 尚未选择目标笼位'), findsOneWidget);
  });

  testWidgets('读标签失败后已填的数量还在，可以直接改完重提', (tester) async {
    final resolver = _FakeNfcResolver(
      failure: const ApiException('标签未绑定笼位'),
    );
    await tester.pumpWidget(_testApp(resolver: resolver));
    await _open(tester);
    await _replaceText(tester, 'production-count', '4');
    await _replaceText(tester, 'production-male-count', '2');
    await _replaceText(tester, 'production-female-count', '2');
    await _startCapture(tester);

    await nfc.tap(houseId: 8, cageId: 12);
    await _settleCapture(tester);

    expect(_hint(tester), '标签未绑定笼位');
    expect(_textOf(tester, 'production-count'), '4');
    expect(_textOf(tester, 'production-male-count'), '2');
    expect(_textOf(tester, 'production-female-count'), '2');
    expect(
      find.byKey(const ValueKey('production-submit')),
      findsOneWidget,
      reason: '失败不能把表单收掉',
    );

    // 失败之后还能再碰一次：采集窗口已经收干净了，按钮回到可点状态。
    expect(
      tester
          .widget<OutlinedButton>(
            _inTargetCagePicker(const ValueKey('nfc-cage-picker-button')),
          )
          .onPressed,
      isNotNull,
    );
  });

  testWidgets('没有 NFC 硬件时说明改用下拉，不让人对着按钮干等', (tester) async {
    await tester.pumpWidget(_testApp(nfcAvailable: false));
    await _open(tester);
    await _startCapture(tester);
    await tester.pumpAndSettle();

    expect(_hint(tester), '设备不支持NFC或NFC未开启，请改用下方地图或列表选择');
    expect(find.byKey(const ValueKey('production-cage')), findsOneWidget);
  });

  testWidgets('固定了当前笼位的入口不出现碰一下按钮', (tester) async {
    await tester.pumpWidget(_testApp(currentCage: _targetCage));
    await _open(tester);

    expect(find.byKey(const ValueKey('production-cage-nfc')), findsNothing);
    expect(
      find.byKey(const ValueKey('production-current-cage')),
      findsOneWidget,
    );
  });

  group('碰一下选待分笼记录', () {
    testWidgets('碰一下母兔笼位，直接选中她那条待分笼记录', (tester) async {
      final resolver = _FakeNfcResolver();
      await tester.pumpWidget(_testApp(
        resolver: resolver,
        cages: const [_targetCage, _doeCage],
        parents: [_doe101],
        initialRecord: null,
      ));
      await _open(tester);
      expect(
        find.byKey(const ValueKey('production-scope')),
        findsNothing,
        reason: '还没选记录时不该有影响范围行',
      );

      await _startRecordCapture(tester);
      await nfc.tap(houseId: 8, cageId: 16);
      await _settleCapture(tester);

      expect(_recordHint(tester), '已选中 母兔 #101 · 待分 6 只');
      expect(
        find.textContaining('母兔 #101（待分 6 只）'),
        findsOneWidget,
        reason: '选中后影响范围行要报出是哪条记录',
      );
      expect(resolver.calls, hasLength(1));
    });

    testWidgets('碰到没有种母兔的笼，说清楚而不是只说不可选', (tester) async {
      await tester.pumpWidget(_testApp(
        cages: const [_targetCage, _doeCage],
        parents: [_doe101],
        initialRecord: null,
      ));
      await _open(tester);
      await _startRecordCapture(tester);

      await nfc.tap(houseId: 8, cageId: 12);
      await _settleCapture(tester);

      expect(_recordHint(tester), 'C-01 没有种母兔');
      expect(find.byKey(const ValueKey('production-scope')), findsNothing);
    });

    testWidgets('笼里有母兔但本批次没待分笼记录，要点名是哪只', (tester) async {
      await tester.pumpWidget(_testApp(
        cages: const [_targetCage, _doeCage, _fullCage],
        parents: [_doe101, _doe104],
        initialRecord: null,
      ));
      await _open(tester);
      await _startRecordCapture(tester);

      await nfc.tap(houseId: 8, cageId: 13);
      await _settleCapture(tester);

      expect(_recordHint(tester), 'C-02 的母兔（#104）在本批次没有待分笼记录');
      expect(find.byKey(const ValueKey('production-scope')), findsNothing);
    });

    testWidgets('同笼两只母兔都待分笼时不猜，让人回列表选', (tester) async {
      await tester.pumpWidget(_testApp(
        cages: const [_targetCage, _twoDoeCage],
        parents: [_doe102, _doe103],
        records: const [_recordFor102, _recordFor103],
        initialRecord: null,
      ));
      await _open(tester);
      await _startRecordCapture(tester);

      await nfc.tap(houseId: 8, cageId: 17);
      await _settleCapture(tester);

      expect(_recordHint(tester), 'C-06 有 2 只母兔待分笼（#102、#103），请在下方选择');
      expect(find.byKey(const ValueKey('production-scope')), findsNothing);
    });

    testWidgets('一只母兔有多条待分笼记录时不猜最新那条', (tester) async {
      await tester.pumpWidget(_testApp(
        cages: const [_targetCage, _doeCage],
        parents: [_doe101],
        records: const [_pendingRecord, _secondRecordFor101],
        initialRecord: null,
      ));
      await _open(tester);
      await _startRecordCapture(tester);

      await nfc.tap(houseId: 8, cageId: 16);
      await _settleCapture(tester);

      expect(_recordHint(tester), '母兔 #101 在本批次有 2 条待分笼记录，请在下方选择');
      expect(find.byKey(const ValueKey('production-scope')), findsNothing);
    });

    testWidgets('同笼的公兔不会把母兔的记录顶成多选', (tester) async {
      await tester.pumpWidget(_testApp(
        cages: const [_targetCage, _doeCage],
        parents: [_doe101, _buck105],
        initialRecord: null,
      ));
      await _open(tester);
      await _startRecordCapture(tester);

      await nfc.tap(houseId: 8, cageId: 16);
      await _settleCapture(tester);

      expect(
        _recordHint(tester),
        '已选中 母兔 #101 · 待分 6 只',
        reason: '公兔同笼不影响判定，否则会误报「有 2 只母兔」',
      );
    });

    testWidgets('只有公兔的笼报「没有种母兔」，不把公兔叫成母兔', (tester) async {
      await tester.pumpWidget(_testApp(
        cages: const [_targetCage, _doeCage],
        parents: [_doe101, _buck106],
        initialRecord: null,
      ));
      await _open(tester);
      await _startRecordCapture(tester);

      await nfc.tap(houseId: 8, cageId: 12);
      await _settleCapture(tester);

      expect(_recordHint(tester), 'C-01 没有种母兔');
    });

    testWidgets('碰其他兔舍的标签直接拒绝，不去问后端', (tester) async {
      final resolver = _FakeNfcResolver();
      await tester.pumpWidget(_testApp(
        resolver: resolver,
        cages: const [_targetCage, _doeCage],
        parents: [_doe101],
        initialRecord: null,
      ));
      await _open(tester);
      await _startRecordCapture(tester);

      await nfc.tap(houseId: 9, cageId: 16);
      await _settleCapture(tester);

      expect(_recordHint(tester), '该标签属于其他兔舍，未选中');
      expect(resolver.calls, isEmpty);
      expect(find.byKey(const ValueKey('production-scope')), findsNothing);
    });

    testWidgets('没有 NFC 硬件时说明改用下拉选记录', (tester) async {
      await tester.pumpWidget(_testApp(
        cages: const [_targetCage, _doeCage],
        parents: [_doe101],
        nfcAvailable: false,
        initialRecord: null,
      ));
      await _open(tester);
      await _startRecordCapture(tester);
      await tester.pumpAndSettle();

      expect(_recordHint(tester), '设备不支持NFC或NFC未开启，请在下方选择待分笼记录');
      expect(
        find.byKey(const ValueKey('production-weaning-record')),
        findsOneWidget,
        reason: '下拉必须还在，否则没硬件就没路了',
      );
    });

    testWidgets('读标签失败不会把已选好的记录弄丢', (tester) async {
      final resolver = _FakeNfcResolver(failure: Exception('boom'));
      await tester.pumpWidget(_testApp(
        resolver: resolver,
        cages: const [_targetCage, _doeCage],
        parents: [_doe101],
      ));
      await _open(tester);
      await _startRecordCapture(tester);

      await nfc.tap(houseId: 8, cageId: 16);
      await _settleCapture(tester);

      expect(_recordHint(tester), '读取标签失败，请重试');
      expect(
        find.textContaining('母兔 #101（待分 6 只）'),
        findsOneWidget,
        reason: '失败不能把进来时就选好的记录清掉',
      );
    });
  });
}

Widget _testApp({
  _FakeNfcResolver? resolver,
  List<Cage> cages = const [_targetCage],
  Cage? currentCage,
  bool nfcAvailable = true,
  List<Rabbit> parents = const <Rabbit>[],
  List<PendingWeaningRecord> records = const [_pendingRecord],
  PendingWeaningRecord? initialRecord = _pendingRecord,
}) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = _StubAdapter();
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  const request = BatchDetailRequest(houseId: 8, batchId: 20);

  return ProviderScope(
    overrides: [
      batchRepositoryProvider.overrideWithValue(BatchRepository(client)),
      nfcHardwareServiceProvider.overrideWithValue(
        _StubNfcHardware(available: nfcAvailable),
      ),
      nfcRepositoryProvider.overrideWithValue(
        (resolver ?? _FakeNfcResolver()).bind(client),
      ),
      houseBatchesProvider(8).overrideWith((_) async => const [_openBatch]),
      houseCagesProvider(8).overrideWith((_) async => cages),
      pendingWeaningRecordsProvider(request).overrideWith((_) async => records),
      houseBreedingParentCandidatesProvider(8)
          .overrideWith((_) async => parents),
      houseRabbitsProvider(8).overrideWith((_) async => const <Rabbit>[]),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: FilledButton(
              key: const ValueKey('open-production-separation'),
              onPressed: () => showProductionWeaningSeparationSheet(
                context: context,
                houseId: 8,
                currentCage: currentCage,
                initialBatchId: 20,
                initialRecord: initialRecord,
              ),
              child: const Text('打开'),
            ),
          ),
        ),
      ),
    ),
  );
}

Future<void> _open(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('open-production-separation')));
  await tester.pumpAndSettle();
}

/// 打开表单并按下「碰一下目标笼位」，停在等待标签的状态。
Future<void> _openAndCapture(WidgetTester tester) async {
  await _open(tester);
  await _startCapture(tester);
}

/// 限定到「目标笼位」那个选择器内部。
///
/// 这个表单里有两个 [NfcCagePicker]：一个选待分笼记录（production-record-nfc），
/// 一个选目标笼位（production-cage-nfc）。两者内部按钮的 key 相同，
/// 直接按 key 找会同时命中两个，`ensureVisible` 会抛 Too many elements。
Finder _inTargetCagePicker(Key key) => find.descendant(
      of: find.byKey(const ValueKey('production-cage-nfc')),
      matching: find.byKey(key),
    );

Future<void> _startCapture(WidgetTester tester) async {
  final button = _inTargetCagePicker(const ValueKey('nfc-cage-picker-button'));
  await tester.ensureVisible(button);
  await tester.pump();
  await tester.tap(button);
  // 硬件探测和 takePendingIntent 都是异步的，采集要先真正开起来才收得到注入的碰一下。
  for (var attempt = 0; attempt < 10; attempt += 1) {
    await tester.runAsync(() => Future<void>.delayed(Duration.zero));
    await tester.pump();
  }
}

Future<void> _settleCapture(WidgetTester tester) async {
  for (var attempt = 0; attempt < 10; attempt += 1) {
    await tester.runAsync(() => Future<void>.delayed(Duration.zero));
    await tester.pump();
  }
  await tester.pumpAndSettle();
}

String? _hint(WidgetTester tester) {
  final finder = _inTargetCagePicker(const ValueKey('nfc-cage-picker-hint'));
  if (finder.evaluate().isEmpty) {
    return null;
  }
  return tester.widget<Text>(finder).data;
}

/// 限定到「待分笼记录」那个选择器内部，理由同 [_inTargetCagePicker]。
Finder _inRecordPicker(Key key) => find.descendant(
      of: find.byKey(const ValueKey('production-record-nfc')),
      matching: find.byKey(key),
    );

Future<void> _startRecordCapture(WidgetTester tester) async {
  final button = _inRecordPicker(const ValueKey('nfc-cage-picker-button'));
  await tester.ensureVisible(button);
  await tester.pump();
  await tester.tap(button);
  for (var attempt = 0; attempt < 10; attempt += 1) {
    await tester.runAsync(() => Future<void>.delayed(Duration.zero));
    await tester.pump();
  }
}

String? _recordHint(WidgetTester tester) {
  final finder = _inRecordPicker(const ValueKey('nfc-cage-picker-hint'));
  if (finder.evaluate().isEmpty) {
    return null;
  }
  return tester.widget<Text>(finder).data;
}

String? _textOf(WidgetTester tester, String key) {
  return tester.widget<TextField>(find.byKey(ValueKey(key))).controller?.text;
}

Future<void> _replaceText(
  WidgetTester tester,
  String key,
  String value,
) async {
  final field = find.byKey(ValueKey(key));
  await tester.ensureVisible(field);
  await tester.enterText(field, value);
  await tester.pump();
}

class _StubNfcHardware extends NfcHardwareService {
  _StubNfcHardware({required this.available});

  final bool available;

  @override
  Future<bool> isAvailable() async => available;
}

/// 记录并伪造 `resolve`，让测试不依赖真实的 `/api/nfc/cages/resolve`。
class _FakeNfcResolver {
  _FakeNfcResolver({this.failure});

  final Object? failure;
  final calls = <String>[];

  NfcRepository bind(ApiClient client) => _StubNfcRepository(client, this);
}

class _StubNfcRepository extends NfcRepository {
  _StubNfcRepository(super.api, this._resolver);

  final _FakeNfcResolver _resolver;

  @override
  Future<NfcCageBinding> resolve({
    required int houseId,
    required String tagUid,
    required String payload,
  }) async {
    _resolver.calls.add(payload);
    final failure = _resolver.failure;
    if (failure != null) {
      throw failure;
    }
    final target = NfcPayloadTarget.parse(payload);
    return NfcCageBinding(
      houseId: target.houseId,
      cageId: target.cageId,
      cageNumber: '',
      tagUid: tagUid,
      bindingStatus: 'BOUND',
    );
  }
}

class _StubAdapter implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': null}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

const _openBatch = Batch(
  id: 20,
  houseId: 8,
  batchCode: 'OPEN-20',
  status: '进行中',
  startDate: null,
  endDate: null,
  remark: '',
);

const _targetCage = Cage(
  id: 12,
  houseId: 8,
  cageNumber: 'C-01',
  status: '3',
  rabbitCount: 2,
  isEnabled: true,
);

const _fullCage = Cage(
  id: 13,
  houseId: 8,
  cageNumber: 'C-02',
  status: '3',
  rabbitCount: Cage.commodityCapacity,
  isEnabled: true,
);

const _disabledCage = Cage(
  id: 14,
  houseId: 8,
  cageNumber: 'C-03',
  status: '3',
  rabbitCount: 0,
  isEnabled: false,
);

const _breedingCage = Cage(
  id: 15,
  houseId: 8,
  cageNumber: 'C-04',
  status: '1',
  rabbitCount: 0,
  isEnabled: true,
);

const _pendingRecord = PendingWeaningRecord(
  id: 501,
  batchId: 20,
  rabbitId: 101,
  breedingCycleId: 301,
  sireRabbitId: 88,
  weaningCount: 8,
  waitingCount: 6,
  maleCount: 4,
  femaleCount: 4,
  waitingMaleCount: 3,
  waitingFemaleCount: 3,
);

/// 母兔 #101 所在的笼。
const _doeCage = Cage(
  id: 16,
  houseId: 8,
  cageNumber: 'C-05',
  status: '1',
  rabbitCount: 1,
  isEnabled: true,
);

/// 同笼两只母兔（#102、#103），两只都有待分笼记录。
const _twoDoeCage = Cage(
  id: 17,
  houseId: 8,
  cageNumber: 'C-06',
  status: '1',
  rabbitCount: 2,
  isEnabled: true,
);

Rabbit _breedingRabbit({
  required int id,
  required int cageId,
  required String gender,
}) =>
    Rabbit(
      id: id,
      houseId: 8,
      cageId: cageId,
      motherId: null,
      type: '0',
      gender: gender,
      breed: '新西兰白兔',
      arrivalMethod: '0',
      arrivalDate: DateTime.utc(2026, 1, 1),
      weight: 4.2,
      isActive: true,
    );

final _doe101 = _breedingRabbit(id: 101, cageId: 16, gender: '0');
final _doe102 = _breedingRabbit(id: 102, cageId: 17, gender: '0');
final _doe103 = _breedingRabbit(id: 103, cageId: 17, gender: '0');

/// 在 C-02，本批次没有待分笼记录。
final _doe104 = _breedingRabbit(id: 104, cageId: 13, gender: '0');

/// 和 #101 同笼的公兔，不该影响判定。
final _buck105 = _breedingRabbit(id: 105, cageId: 16, gender: '1');

/// 独占 C-01 的公兔：用来验证性别过滤真的在承重。
///
/// 没有它的话，一个只有公兔的笼会被报成「的母兔（#106）在本批次没有待分笼记录」，
/// 把公兔叫成母兔。
final _buck106 = _breedingRabbit(id: 106, cageId: 12, gender: '1');

/// 母兔 #101 的第二条待分笼记录。
///
/// 库上 `weaning_records` 没有 (batch_id, rabbit_id) 唯一约束，所以这种形态
/// 结构上成立（开发库里历史上有 31 组同批次同母兔多条），
/// 只是待分数量大于 0 的目前只会有一条。不能因为“目前是 0”就猜。
const _secondRecordFor101 = PendingWeaningRecord(
  id: 502,
  batchId: 20,
  rabbitId: 101,
  breedingCycleId: 302,
  sireRabbitId: 88,
  weaningCount: 5,
  waitingCount: 2,
  maleCount: 3,
  femaleCount: 2,
  waitingMaleCount: 1,
  waitingFemaleCount: 1,
);

const _recordFor102 = PendingWeaningRecord(
  id: 503,
  batchId: 20,
  rabbitId: 102,
  breedingCycleId: 303,
  sireRabbitId: 88,
  weaningCount: 7,
  waitingCount: 4,
  maleCount: 4,
  femaleCount: 3,
  waitingMaleCount: 2,
  waitingFemaleCount: 2,
);

const _recordFor103 = PendingWeaningRecord(
  id: 504,
  batchId: 20,
  rabbitId: 103,
  breedingCycleId: 304,
  sireRabbitId: 88,
  weaningCount: 6,
  waitingCount: 3,
  maleCount: 3,
  femaleCount: 3,
  waitingMaleCount: 2,
  waitingFemaleCount: 1,
);
