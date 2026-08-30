import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/departure.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/promotion.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('submitRabbitEvent sends a JSON-compatible terminal event', () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);
    final actionDate = DateTime(2026, 8, 14);

    await repository.submitRabbitEvent(
      houseId: 8,
      rabbitId: 801,
      eventType: ' CULL ',
      actionDate: actionDate,
      reason: '  繁殖性能下降  ',
      remark: '  更换后备母兔  ',
      forceExitBatch: true,
      requestId: 'departure-request-1',
    );

    expect(adapter.requests, hasLength(1));
    final request = adapter.requests.single;
    expect(request.path, '/api/rabbits/events');
    expect(request.headers['Authorization'], 'Bearer operator-token');
    expect(request.headers['X-House-Id'], '8');
    expect(request.body, {
      'rabbitId': 801,
      'eventType': 'cull',
      'actionDate': actionDate.millisecondsSinceEpoch,
      'reason': '繁殖性能下降',
      'remark': '更换后备母兔',
      'forceExitBatch': true,
      'requestId': 'departure-request-1',
    });
  });

  test('replacement accepts a stable requestId and canonical rabbit ids',
      () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);

    final result = await repository.convertToReplacement(
      houseId: 8,
      rabbitIds: const [803, 801, 803, 802],
      targetCageId: 19,
      requestId: 'replacement-request-1',
    );

    expect(adapter.requests.single.path, '/api/rabbits/replacement');
    expect(adapter.requests.single.body, {
      'rabbitIds': [801, 802, 803],
      'forceExitBatch': true,
      'requestId': 'replacement-request-1',
      'targetCageId': 19,
    });
    expect(result.single.replacementRecordId, 901);
  });

  test('replacement promotion sends its reason to the dedicated endpoint',
      () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);

    await repository.promoteReplacement(
      houseId: 8,
      rabbitId: 801,
      reason: '育种计划调整',
      requestId: 'promote-request-1',
    );

    expect(
      adapter.requests.single.path,
      '/api/rabbits/801/promote-breeder',
    );
    expect(adapter.requests.single.body, {
      'requestId': 'promote-request-1',
      'reason': '育种计划调整',
    });
  });

  testWidgets('promotion sheet requires reason and confirmation',
      (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 800);
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(
      tester.platformDispatcher.clearTextScaleFactorTestValue,
    );
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);
    const rabbit = Rabbit(
      id: 801,
      houseId: 8,
      cageId: 19,
      motherId: null,
      type: '1',
      gender: '0',
      breed: '新西兰白兔',
      arrivalMethod: '0',
      arrivalDate: null,
      weight: 3.2,
      isActive: true,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [rabbitRepositoryProvider.overrideWithValue(repository)],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Builder(
              builder: (context) => FilledButton(
                key: const ValueKey('open-promotion-sheet'),
                onPressed: () => showRabbitPromotionSheet(
                  context: context,
                  houseId: 8,
                  rabbit: rabbit,
                ),
                child: const Text('打开'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-promotion-sheet')));
    await tester.pumpAndSettle();

    final submit = find.byKey(const ValueKey('rabbit-promotion-submit'));
    expect(tester.widget<FilledButton>(submit).onPressed, isNull);
    await tester.enterText(
      find.byKey(const ValueKey('rabbit-promotion-reason')),
      '育种计划提前',
    );
    await tester.tap(find.byKey(const ValueKey('rabbit-promotion-confirm')));
    await tester.pumpAndSettle();
    expect(tester.widget<FilledButton>(submit).onPressed, isNotNull);
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(adapter.requests, hasLength(1));
    expect(adapter.requests.single.path, '/api/rabbits/801/promote-breeder');
    expect(adapter.requests.single.body['reason'], '育种计划提前');
    expect(adapter.requests.single.body['requestId'], isNotEmpty);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'departure sheet requires reason and explicit risk confirmation before death',
      (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 800);
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(
      tester.platformDispatcher.clearTextScaleFactorTestValue,
    );

    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [rabbitRepositoryProvider.overrideWithValue(repository)],
        child: MaterialApp(
          theme: buildAppTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: AppTypography.ergonomicTextScaler(
                MediaQuery.textScalerOf(context),
              ),
            ),
            child: child!,
          ),
          home: Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: FilledButton(
                  key: const ValueKey('open-departure-sheet'),
                  onPressed: () => showRabbitDepartureSheet(
                    context: context,
                    houseId: 8,
                    batchId: 88,
                    rabbitId: 801,
                  ),
                  child: const Text('打开'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-departure-sheet')));
    await tester.pumpAndSettle();

    expect(find.text('登记离场'), findsOneWidget);
    for (final deviceSize in const [
      Size(360, 800),
      Size(393, 852),
      Size(412, 915),
    ]) {
      tester.view.physicalSize = deviceSize;
      for (final keyboardHeight in const [180.0, 300.0, 420.0]) {
        tester.view.viewInsets = FakeViewPadding(bottom: keyboardHeight);
        await tester.pumpAndSettle();
        final submitRect = tester.getRect(
          find.byKey(const ValueKey('rabbit-departure-submit')),
        );
        expect(submitRect.top, greaterThanOrEqualTo(0));
        expect(
          submitRect.bottom,
          lessThanOrEqualTo(deviceSize.height - keyboardHeight),
        );
      }
    }
    tester.view.resetViewInsets();
    tester.view.physicalSize = const Size(360, 800);
    await tester.pumpAndSettle();
    final formList = find.byType(ListView).last;
    final deathOption = find.byKey(
      const ValueKey('rabbit-departure-event-death'),
    );
    await _dragUntilBuilt(
      tester,
      target: deathOption,
      scrollable: formList,
    );
    await tester.ensureVisible(deathOption);
    await tester.tap(
      deathOption,
    );
    await tester.pumpAndSettle();
    final riskConfirmation = find.byKey(
      const ValueKey('rabbit-departure-confirm-risk'),
    );
    await _dragUntilBuilt(
      tester,
      target: riskConfirmation,
      scrollable: formList,
    );
    await tester.ensureVisible(riskConfirmation);
    await tester.drag(formList, const Offset(0, -96));
    await tester.pumpAndSettle();
    await tester.tap(
      riskConfirmation,
    );
    await tester.pumpAndSettle();

    final enabledSubmit = tester.widget<FilledButton>(
      find.byKey(const ValueKey('rabbit-departure-submit')),
    );
    expect(enabledSubmit.onPressed, isNotNull);

    // A missing reason is rejected in the form and does not send a request.
    await tester.tap(find.byKey(const ValueKey('rabbit-departure-submit')));
    await tester.pumpAndSettle();
    expect(find.text('请填写离场原因'), findsWidgets);
    expect(adapter.requests, isEmpty);

    final reasonField = find.byKey(
      const ValueKey('rabbit-departure-reason'),
    );
    await _dragUntilBuilt(
      tester,
      target: reasonField,
      scrollable: formList,
      scrollDelta: const Offset(0, 180),
    );
    await tester.ensureVisible(reasonField);
    await tester.enterText(
      reasonField,
      '突发疾病死亡',
    );
    final remarkField = find.byKey(
      const ValueKey('rabbit-departure-remark'),
    );
    await _dragUntilBuilt(
      tester,
      target: remarkField,
      scrollable: formList,
    );
    await tester.ensureVisible(remarkField);
    await tester.enterText(
      remarkField,
      '兽医确认',
    );
    tester.testTextInput.hide();
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('rabbit-departure-submit')));
    await tester.pumpAndSettle();

    expect(find.text('登记离场'), findsNothing);
    expect(adapter.requests, hasLength(1));
    expect(adapter.requests.single.body['eventType'], 'death');
    expect(adapter.requests.single.body['rabbitId'], 801);
    expect(adapter.requests.single.body['forceExitBatch'], isTrue);
    expect(adapter.requests.single.body['reason'], '突发疾病死亡');
    expect(adapter.requests.single.body['remark'], '兽医确认');
    expect(adapter.requests.single.body['requestId'], isNotEmpty);
    expect(adapter.requests.single.body['actionDate'], isA<int>());
  });
}

Future<void> _dragUntilBuilt(
  WidgetTester tester, {
  required Finder target,
  required Finder scrollable,
  Offset scrollDelta = const Offset(0, -180),
}) async {
  for (var attempt = 0; attempt < 12 && target.evaluate().isEmpty; attempt++) {
    await tester.drag(scrollable, scrollDelta);
    await tester.pumpAndSettle();
  }
  expect(target, findsOneWidget);
}

RabbitRepository _repository(_CapturingAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return RabbitRepository(client);
}

class _CapturedRequest {
  const _CapturedRequest({
    required this.path,
    required this.headers,
    required this.body,
  });

  final String path;
  final Map<String, dynamic> headers;
  final Map<String, dynamic> body;
}

class _CapturingAdapter implements HttpClientAdapter {
  final requests = <_CapturedRequest>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(
      _CapturedRequest(
        path: options.path,
        headers: Map<String, dynamic>.from(options.headers),
        body: Map<String, dynamic>.from(options.data as Map),
      ),
    );
    final data = options.path == '/api/rabbits/replacement'
        ? {
            'items': [
              {
                'rabbitId': 801,
                'replacementRecordId': 901,
                'targetCageId': 19,
              },
            ],
          }
        : null;
    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
