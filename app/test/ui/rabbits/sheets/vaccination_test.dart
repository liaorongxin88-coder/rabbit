import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/vaccinations/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/vaccination.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  testWidgets('records a single rabbit vaccination and confirms by snack bar',
      (tester) async {
    final adapter = _CapturingAdapter(data: {'created': 1, 'records': []});
    await _pumpSheet(tester, adapter, cageRabbits: const [_commodityRabbit]);

    await tester.tap(find.byKey(const ValueKey('open-vaccination')));
    await tester.pumpAndSettle();

    expect(find.text('接种疫苗'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('rabbit-vaccination-name')),
      '兔瘟疫苗',
    );
    await tester.enterText(
      find.byKey(const ValueKey('rabbit-vaccination-batch-no')),
      'B20260301',
    );

    // 单只兔在笼时不该出现整笼选项。先滚到底，否则 ListView 懒构建会让
    // 这条断言无论如何都通过。
    await _scrollSheetToBottom(tester);
    expect(
      find.byKey(const ValueKey('rabbit-vaccination-whole-cage')),
      findsNothing,
    );

    await tester.tap(find.byKey(const ValueKey('rabbit-vaccination-submit')));
    await tester.pumpAndSettle();

    expect(adapter.requests, hasLength(1));
    final body = adapter.requests.single.body;
    expect(body['rabbitIds'], [31]);
    expect(body['vaccineName'], '兔瘟疫苗');
    expect(body['vaccineBatchNo'], 'B20260301');
    expect(body['requestId'], isNotEmpty);
    // 没勾下次接种就不该下发该字段
    expect(body.containsKey('nextDueDate'), isFalse);

    expect(find.text('接种疫苗'), findsNothing, reason: '提交成功后应关闭');
    expect(find.textContaining('已记录「兔瘟疫苗」接种'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('offers whole-cage vaccination and sends every penned rabbit',
      (tester) async {
    final adapter = _CapturingAdapter(data: {'created': 3, 'records': []});
    await _pumpSheet(
      tester,
      adapter,
      cageRabbits: const [_commodityRabbit, _penMate, _penMateTwo],
    );

    await tester.tap(find.byKey(const ValueKey('open-vaccination')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('rabbit-vaccination-name')),
      '兔瘟疫苗',
    );

    // 整笼选项在表单下方，ListView 懒构建，不滚到位就不存在于树中。
    final wholeCage =
        find.byKey(const ValueKey('rabbit-vaccination-whole-cage'));
    await _scrollSheetToBottom(tester);
    expect(wholeCage, findsOneWidget);
    expect(find.textContaining('本笼在栏 3 只'), findsOneWidget);

    await tester.tap(wholeCage);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('rabbit-vaccination-submit')));
    await tester.pumpAndSettle();

    final ids = (adapter.requests.single.body['rabbitIds'] as List).cast<int>();
    expect(ids.toSet(), {31, 32, 33});
    expect(find.textContaining('已为 3 只兔记录'), findsOneWidget);
  });

  testWidgets('keeps the sheet open and reuses the requestId after a failure',
      (tester) async {
    // 首次返回业务失败，重试成功——模拟提交时网络/服务端出错后再点一次。
    final adapter = _CapturingAdapter(
      data: {'created': 1, 'records': []},
      failFirst: true,
    );
    await _pumpSheet(tester, adapter, cageRabbits: const [_commodityRabbit]);

    await tester.tap(find.byKey(const ValueKey('open-vaccination')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('rabbit-vaccination-name')),
      '兔瘟疫苗',
    );
    await tester.tap(find.byKey(const ValueKey('rabbit-vaccination-submit')));
    await tester.pumpAndSettle();

    // 失败后不关闭，错误原文可见，可以直接重试
    expect(find.text('接种疫苗'), findsOneWidget);
    expect(find.textContaining('兔子不在场'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('rabbit-vaccination-submit')));
    await tester.pumpAndSettle();

    expect(adapter.requests, hasLength(2));
    // 表单没改动，幂等键必须保持不变，否则重试会写出第二条记录
    expect(
      adapter.requests.first.body['requestId'],
      adapter.requests.last.body['requestId'],
    );
    expect(find.text('接种疫苗'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('refuses to submit without a vaccine name', (tester) async {
    final adapter = _CapturingAdapter(data: {'created': 1, 'records': []});
    await _pumpSheet(tester, adapter, cageRabbits: const [_commodityRabbit]);

    await tester.tap(find.byKey(const ValueKey('open-vaccination')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('rabbit-vaccination-submit')));
    await tester.pumpAndSettle();

    expect(find.text('请填写疫苗名称'), findsOneWidget);
    expect(adapter.requests, isEmpty, reason: '校验不过不应发请求');
    expect(find.text('接种疫苗'), findsOneWidget);
  });
}

/// 表单跑在 ListView 里，视口外的字段不会被构建。断言底部控件前必须先滚到位，
/// 否则 findsNothing 会因为“没构建”而假通过。
Future<void> _scrollSheetToBottom(WidgetTester tester) async {
  // 输入框拿到焦点后，列表会自动把它滚回可视区，把刚拖上去的位置又拉回来。
  // 先收焦点再滚，否则底部控件永远进不了视口。
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();

  // 输入框自带横向 Scrollable，这里只要表单那个纵向 ListView（第一个）。
  final scrollable = find
      .descendant(of: find.byType(Form), matching: find.byType(Scrollable))
      .first;
  await tester.drag(scrollable, const Offset(0, -600));
  await tester.pumpAndSettle();
}

Future<void> _pumpSheet(
  WidgetTester tester,
  _CapturingAdapter adapter, {
  required List<Rabbit> cageRabbits,
}) async {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        vaccinationRepositoryProvider
            .overrideWithValue(VaccinationRepository(client)),
        cageRabbitsProvider((houseId: 8, cageId: 12))
            .overrideWith((_) async => cageRabbits),
      ],
      child: MaterialApp(
        theme: buildAppTheme(),
        home: Scaffold(
          body: Builder(
            builder: (context) => Center(
              child: FilledButton(
                key: const ValueKey('open-vaccination'),
                onPressed: () => showRabbitVaccinationSheet(
                  context: context,
                  houseId: 8,
                  rabbit: _commodityRabbit,
                ),
                child: const Text('打开接种录入'),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

const _commodityRabbit = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '2',
  gender: '1',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.5,
  isActive: true,
);

const _penMate = Rabbit(
  id: 32,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '2',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.4,
  isActive: true,
);

const _penMateTwo = Rabbit(
  id: 33,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '2',
  gender: '1',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.6,
  isActive: true,
);

class _CapturedRequest {
  const _CapturedRequest({required this.path, required this.body});

  final String path;
  final Map<String, dynamic> body;
}

class _CapturingAdapter implements HttpClientAdapter {
  _CapturingAdapter({this.data, this.failFirst = false});

  final Object? data;
  final bool failFirst;
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
        body: options.data is Map
            ? Map<String, dynamic>.from(options.data as Map)
            : <String, dynamic>{},
      ),
    );
    if (failFirst && requests.length == 1) {
      return ResponseBody.fromString(
        jsonEncode({'code': 400, 'message': '兔子不在场：31', 'data': null}),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );
    }
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
