import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/weaning.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/providers.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
      'weaning derives confirmation average from immutable total weight',
      (tester) async {
    SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'repro'});
    FlutterSecureStorage.setMockInitialValues({'token': 'repro-token'});
    final adapter = _WeaningAdapter();
    final client = ApiClient(
      SessionStore(),
      dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
        ..httpClientAdapter = adapter,
      appBuildLoader: () async => '4020',
    );
    addTearDown(client.dispose);
    final repository = ReproRepository(client);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          reproRepositoryProvider.overrideWithValue(repository),
          houseSettingProvider(8).overrideWith(
            (_) async => HouseSettingState(
              setting: GlobalSetting.defaults(),
              customized: false,
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Builder(
              builder: (context) => FilledButton(
                key: const ValueKey('open-weaning'),
                onPressed: () => showWeaningSheet(
                  context: context,
                  event: const EventItem(
                    recordId: 9,
                    category: '生产周期',
                    eventType: '断奶',
                    eventDate: null,
                    batchId: 11,
                    rabbitId: 31,
                    status: 'due',
                    sourceHouseId: 8,
                    sourceHouseName: '一号兔舍',
                  ),
                ),
                child: const Text('打开断奶'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-weaning')));
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<TextField>(find.byKey(const ValueKey('weaning-count')))
          .controller
          ?.text,
      '6',
    );

    final totalWeight = find.byKey(const ValueKey('weaning-total-weight'));
    await _scrollUntilBuilt(tester, totalWeight);
    await tester.enterText(totalWeight, '4.410');
    await tester.ensureVisible(find.byKey(const ValueKey('weaning-submit')));
    await tester.tap(find.byKey(const ValueKey('weaning-submit')));
    await tester.pumpAndSettle();

    final request = adapter.actionRequest!;
    expect(request.data['weaningTotalWeightKg'], 4.41);
    expect(request.data.containsKey('avgWeaningWeight'), isFalse);
    expect(request.data['weanedCount'], 6);
    expect(request.headers['X-App-Build'], '4020');
    expect(adapter.litterReads, 2);
    expect(find.textContaining('服务端断奶均重 0.735 kg/只'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('positive weaning count requires total weight', (tester) async {
    final adapter = await _openWeaning(tester);
    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pump();

    expect(find.textContaining('请填写大于 0 的断奶总重'), findsOneWidget);
    expect(adapter.actionRequest, isNull);
  });

  testWidgets('zero weaning count rejects positive total weight',
      (tester) async {
    final adapter = await _openWeaning(tester);
    await tester.enterText(
      find.byKey(const ValueKey('weaning-count')),
      '0',
    );
    final totalWeight = find.byKey(const ValueKey('weaning-total-weight'));
    await _scrollUntilBuilt(tester, totalWeight);
    await tester.enterText(totalWeight, '1.000');
    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pump();

    expect(find.textContaining('断奶总重只能留空或填写 0'), findsOneWidget);
    expect(adapter.actionRequest, isNull);
  });

  testWidgets('zero weaning count accepts numeric zero total weight',
      (tester) async {
    final adapter = await _openWeaning(tester);
    await tester.enterText(
      find.byKey(const ValueKey('weaning-count')),
      '0',
    );
    final totalWeight = find.byKey(const ValueKey('weaning-total-weight'));
    await _scrollUntilBuilt(tester, totalWeight);
    await tester.enterText(totalWeight, '0');
    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(adapter.actionRequest?.data['weanedCount'], 0);
    expect(adapter.actionRequest?.data['weaningTotalWeightKg'], 0);
  });
}

Future<_WeaningAdapter> _openWeaning(WidgetTester tester) async {
  SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'repro'});
  FlutterSecureStorage.setMockInitialValues({'token': 'repro-token'});
  final adapter = _WeaningAdapter();
  final client = ApiClient(
    SessionStore(),
    dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter,
    appBuildLoader: () async => '4020',
  );
  addTearDown(client.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        reproRepositoryProvider.overrideWithValue(ReproRepository(client)),
        houseSettingProvider(8).overrideWith(
          (_) async => HouseSettingState(
            setting: GlobalSetting.defaults(),
            customized: false,
          ),
        ),
      ],
      child: MaterialApp(
        theme: buildAppTheme(),
        home: Scaffold(
          body: Builder(
            builder: (context) => FilledButton(
              key: const ValueKey('open-weaning'),
              onPressed: () => showWeaningSheet(
                context: context,
                event: const EventItem(
                  recordId: 9,
                  category: '生产周期',
                  eventType: '断奶',
                  eventDate: null,
                  batchId: 11,
                  rabbitId: 31,
                  status: 'due',
                  sourceHouseId: 8,
                  sourceHouseName: '一号兔舍',
                ),
              ),
              child: const Text('打开断奶'),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.byKey(const ValueKey('open-weaning')));
  await tester.pumpAndSettle();
  return adapter;
}

Future<void> _scrollUntilBuilt(WidgetTester tester, Finder target) async {
  final list = find.byKey(const ValueKey('weaning-form-list'));
  for (var attempt = 0; attempt < 8 && target.evaluate().isEmpty; attempt++) {
    await tester.drag(list, const Offset(0, -220));
    await tester.pump();
  }
  expect(target, findsOneWidget);
}

class _WeaningAdapter implements HttpClientAdapter {
  var litterReads = 0;
  RequestOptions? actionRequest;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.method == 'GET' && options.path.endsWith('/litter')) {
      litterReads++;
      return _json({
        'id': 51,
        'cycleId': 9,
        'motherRabbitId': 31,
        'batchId': 11,
        'keptKits': 6,
        'currentNursing': litterReads == 1 ? 6 : 0,
        'status': litterReads == 1 ? 'NURSING' : 'WEANED',
        if (litterReads > 1) 'weaningTotalWeightKg': 4.410,
        if (litterReads > 1) 'avgWeaningWeight': 9.999,
      });
    }
    actionRequest = options;
    return _json({'cycleId': 9, 'stage': 'AWAIT_ESTRUS'});
  }

  static ResponseBody _json(Object? data) => ResponseBody.fromString(
        jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );

  @override
  void close({bool force = false}) {}
}
