import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/replacement.dart';

import '../../core/widgets/nfc_harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  testWidgets('replacement submits once with target cage and exits batch',
      (tester) async {
    final adapter = _DelayedAdapter();
    final repository = _repository(adapter);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          rabbitRepositoryProvider.overrideWithValue(repository),
          houseCagesProvider(8).overrideWith((_) async => const [
                _targetCage,
                _occupiedCage,
                _crossHouseCage,
              ]),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Builder(
              builder: (context) => FilledButton(
                key: const ValueKey('open-rabbit-replacement-sheet'),
                onPressed: () => showRabbitReplacementSheet(
                  context: context,
                  houseId: 8,
                  rabbit: _commodityRabbit,
                ),
                child: const Text('留种'),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(
      find.byKey(const ValueKey('open-rabbit-replacement-sheet')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('rabbit-replacement-cage-21')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-replacement-cage-22')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-replacement-cage-23')),
      findsNothing,
    );

    await tester.tap(
      find.byKey(const ValueKey('rabbit-replacement-cage-21')),
    );
    await tester.pump();
    final submit = find.byKey(const ValueKey('rabbit-replacement-submit'));
    await tester.tap(submit);
    await tester.tap(submit);
    for (var attempt = 0; attempt < 20 && adapter.requests.isEmpty; attempt++) {
      await tester.pump(const Duration(milliseconds: 10));
    }

    expect(adapter.requests, hasLength(1));
    expect(tester.widget<ElevatedButton>(submit).onPressed, isNull);
    expect(adapter.requests.single.path, '/api/rabbits/replacement');
    expect(adapter.requests.single.body['rabbitIds'], [31]);
    expect(adapter.requests.single.body['targetCageId'], 21);
    expect(adapter.requests.single.body['forceExitBatch'], isTrue);
    expect(adapter.requests.single.body['requestId'], isNotEmpty);

    adapter.completeSuccess();
    await tester.pumpAndSettle();
    expect(find.text('商品兔 #31 已转入 B-01'), findsOneWidget);
  });

  group('留种转后备时碰笼位标签', () {
    late NfcHarness nfc;

    setUp(() {
      nfc = NfcHarness();
    });

    Future<void> openSheet(
      WidgetTester tester, {
      required List<Cage> cages,
    }) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            houseCagesProvider(8).overrideWith((_) async => cages),
            nfcHardwareServiceProvider
                .overrideWithValue(_AvailableNfcHardware()),
            nfcRepositoryProvider.overrideWithValue(_TagResolvingRepository()),
          ],
          child: MaterialApp(
            theme: buildAppTheme(),
            home: Scaffold(
              body: Builder(
                builder: (context) => FilledButton(
                  key: const ValueKey('open-rabbit-replacement-sheet'),
                  onPressed: () => showRabbitReplacementSheet(
                    context: context,
                    houseId: 8,
                    rabbit: _commodityRabbit,
                  ),
                  child: const Text('留种'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(
        find.byKey(const ValueKey('open-rabbit-replacement-sheet')),
      );
      await tester.pumpAndSettle();
    }

    /// 打开采集窗口。硬件探测和通道初始化都是异步的，多泵几帧让它们落地。
    Future<void> startCapture(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('nfc-cage-picker-button')));
      await tester.pump();
      await tester.pump();
      await tester.pumpAndSettle();
    }

    Future<void> tapTag(
      WidgetTester tester, {
      int houseId = 8,
      required int cageId,
    }) async {
      await nfc.tap(houseId: houseId, cageId: cageId);
      await tester.pump();
      await tester.pump();
      await tester.pumpAndSettle();
    }

    int? selectedCageId(WidgetTester tester, int cageId) {
      return tester
          .widget<RadioListTile<int>>(
            find.byKey(ValueKey('rabbit-replacement-cage-$cageId')),
          )
          .groupValue;
    }

    testWidgets('碰一下空闲后备笼的标签，列表里那一格就被选中', (tester) async {
      await openSheet(
        tester,
        cages: const [_targetCage, _occupiedCage, _commodityCage],
      );

      // 列表这条路必须还在：标签会失灵，手机也可能没有 NFC。
      expect(
        find.byKey(const ValueKey('rabbit-replacement-cage-list')),
        findsOneWidget,
      );
      expect(selectedCageId(tester, 21), isNull);

      await startCapture(tester);
      await tapTag(tester, cageId: 21);

      expect(selectedCageId(tester, 21), 21);
      expect(find.text('已选中 B-01'), findsOneWidget);
      expect(
        tester
            .widget<ElevatedButton>(
              find.byKey(const ValueKey('rabbit-replacement-submit')),
            )
            .onPressed,
        isNotNull,
      );
    });

    testWidgets('碰中的笼位在屏幕外时，列表会滚到它，不让人怀疑没碰上', (tester) async {
      final cages = _manyReplacementCages(20);
      final last = cages.last;
      await openSheet(tester, cages: cages);

      final lastRow =
          find.byKey(ValueKey('rabbit-replacement-cage-${last.id}'));
      expect(lastRow, findsNothing);

      await startCapture(tester);
      await tapTag(tester, cageId: last.id);

      expect(lastRow, findsOneWidget, reason: '选中的那一行应该被滚到列表里建出来');
      expect(selectedCageId(tester, last.id), last.id);
      final listRect = tester.getRect(
        find.byKey(const ValueKey('rabbit-replacement-cage-list')),
      );
      final rowRect = tester.getRect(lastRow);
      expect(rowRect.top, greaterThanOrEqualTo(listRect.top));
      expect(rowRect.bottom, lessThanOrEqualTo(listRect.bottom));
    });

    testWidgets('碰到商品兔笼会被拒绝，并说明这个笼是什么笼', (tester) async {
      await openSheet(
        tester,
        cages: const [_targetCage, _occupiedCage, _commodityCage],
      );

      await startCapture(tester);
      await tapTag(tester, cageId: 24);

      expect(find.text('3-2-1 是商品兔笼，不能作为后备兔笼'), findsOneWidget);
      expect(selectedCageId(tester, 21), isNull);
    });

    testWidgets('碰到已经有兔的后备笼会被拒绝，并说明它被几只兔占着', (tester) async {
      await openSheet(
        tester,
        cages: const [_targetCage, _occupiedCage, _commodityCage],
      );

      await startCapture(tester);
      await tapTag(tester, cageId: 22);

      expect(find.text('B-02 已有 1 只兔，请选空闲笼位'), findsOneWidget);
      expect(selectedCageId(tester, 21), isNull);
    });

    testWidgets('碰别的兔舍的标签不会选中任何笼位', (tester) async {
      await openSheet(
        tester,
        cages: const [_targetCage, _occupiedCage, _commodityCage],
      );

      await startCapture(tester);
      await tapTag(tester, houseId: 9, cageId: 21);

      expect(find.text('该标签属于其他兔舍，未选中'), findsOneWidget);
      expect(selectedCageId(tester, 21), isNull);
    });

    testWidgets('碰错标签不会把已经选好的笼位弄丢', (tester) async {
      await openSheet(
        tester,
        cages: const [_targetCage, _occupiedCage, _commodityCage],
      );

      await tester.tap(
        find.byKey(const ValueKey('rabbit-replacement-cage-21')),
      );
      await tester.pump();
      expect(selectedCageId(tester, 21), 21);

      await startCapture(tester);
      await tapTag(tester, cageId: 24);

      expect(find.text('3-2-1 是商品兔笼，不能作为后备兔笼'), findsOneWidget);
      expect(selectedCageId(tester, 21), 21);
      expect(
        tester
            .widget<ElevatedButton>(
              find.byKey(const ValueKey('rabbit-replacement-submit')),
            )
            .onPressed,
        isNotNull,
      );
    });
  });
}

List<Cage> _manyReplacementCages(int count) {
  return List<Cage>.generate(
    count,
    (index) => Cage(
      id: 100 + index,
      houseId: 8,
      cageNumber: 'B-${(index + 1).toString().padLeft(2, '0')}',
      status: '2',
      rabbitCount: 0,
      isEnabled: true,
    ),
  );
}

/// 强制报告硬件可用，测试机没有 NFC 也能跑完采集流程。
class _AvailableNfcHardware extends NfcHardwareService {
  @override
  Future<bool> isAvailable() async => true;
}

/// 后端解析的替身：直接把标签载荷里的笼位交回去，等价于一次成功的 resolve。
class _TagResolvingRepository implements NfcRepository {
  @override
  Future<NfcCageBinding> resolve({
    required int houseId,
    required String tagUid,
    required String payload,
  }) async {
    final target = NfcPayloadTarget.parse(payload);
    return NfcCageBinding(
      houseId: target.houseId,
      cageId: target.cageId,
      cageNumber: '',
      tagUid: tagUid,
      bindingStatus: 'BOUND',
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
    throw UnimplementedError();
  }

  @override
  Future<List<NfcCageQueueItem>> listWriteQueue(int houseId) {
    throw UnimplementedError();
  }
}

RabbitRepository _repository(_DelayedAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return RabbitRepository(client);
}

class _CapturedRequest {
  const _CapturedRequest({required this.path, required this.body});

  final String path;
  final Map<String, dynamic> body;
}

class _DelayedAdapter implements HttpClientAdapter {
  final requests = <_CapturedRequest>[];
  final _response = Completer<ResponseBody>();

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    requests.add(
      _CapturedRequest(
        path: options.path,
        body: Map<String, dynamic>.from(options.data as Map),
      ),
    );
    return _response.future;
  }

  void completeSuccess() {
    _response.complete(
      ResponseBody.fromString(
        jsonEncode({
          'code': 0,
          'message': 'ok',
          'data': {
            'items': [
              {
                'rabbitId': 31,
                'replacementRecordId': 901,
                'targetCageId': 21,
              },
            ],
          },
        }),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      ),
    );
  }

  @override
  void close({bool force = false}) {}
}

const _commodityRabbit = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '2',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.5,
  isActive: true,
);

const _targetCage = Cage(
  id: 21,
  houseId: 8,
  cageNumber: 'B-01',
  status: '2',
  rabbitCount: 0,
  isEnabled: true,
);

const _occupiedCage = Cage(
  id: 22,
  houseId: 8,
  cageNumber: 'B-02',
  status: '2',
  rabbitCount: 1,
  isEnabled: true,
);

const _crossHouseCage = Cage(
  id: 23,
  houseId: 9,
  cageNumber: 'B-03',
  status: '2',
  rabbitCount: 0,
  isEnabled: true,
);

const _commodityCage = Cage(
  id: 24,
  houseId: 8,
  cageNumber: '3-2-1',
  status: '3',
  rabbitCount: 0,
  isEnabled: true,
);
