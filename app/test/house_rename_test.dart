import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/houses/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/houses/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  test('house update sends the selected house scope with name and remark',
      () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final adapter = _CapturingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    final result = await HouseRepository(client).updateHouse(
      houseId: 8,
      name: '朝阳兔舍',
      remark: '东侧二号棚',
    );

    expect(adapter.request.method, 'PUT');
    expect(adapter.request.path, '/api/houses/8');
    expect(adapter.request.headers['X-House-Id'], '8');
    expect(adapter.request.data, {
      'name': '朝阳兔舍',
      'remark': '东侧二号棚',
    });
    expect(result.name, '朝阳兔舍');
  });

  testWidgets('authorized user can edit a house name and remark',
      (tester) async {
    final repository = _RenamingHouseRepository();
    await tester.pumpWidget(_testApp(
      repository,
      const HousePermission(
        perms: 'control',
        isAdmin: false,
        role: 'MANAGER',
        permissions: ['rabbit:houses:edit'],
      ),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('house-edit-name-entry')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('house-edit-name-field')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('house-edit-remark-field')),
      findsOneWidget,
    );

    await tester.enterText(
      find.byKey(const ValueKey('house-edit-name-field')),
      '  朝阳兔舍  ',
    );
    await tester.enterText(
      find.byKey(const ValueKey('house-edit-remark-field')),
      '  靠近饲料间  ',
    );
    final submit = find.byKey(const ValueKey('house-edit-name-submit'));
    await tester.ensureVisible(submit);
    await tester.pumpAndSettle();
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(repository.updatedHouseId, 8);
    expect(repository.updatedName, '朝阳兔舍');
    expect(repository.updatedRemark, '靠近饲料间');
    expect(find.text('朝阳兔舍'), findsOneWidget);
    expect(find.text('靠近饲料间'), findsOneWidget);
    expect(find.text('兔舍信息已更新'), findsOneWidget);
  });

  testWidgets('authorized user can clear a house remark', (tester) async {
    final repository = _RenamingHouseRepository();
    await tester.pumpWidget(_testApp(
      repository,
      const HousePermission(
        perms: 'control',
        isAdmin: false,
        role: 'MANAGER',
        permissions: ['rabbit:houses:edit'],
      ),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('house-edit-name-entry')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('house-edit-remark-field')),
      '',
    );
    final submit = find.byKey(const ValueKey('house-edit-name-submit'));
    await tester.ensureVisible(submit);
    await tester.pumpAndSettle();
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(repository.updatedName, '默认兔舍');
    expect(repository.updatedRemark, '');
    expect(find.text('暂无备注'), findsOneWidget);
    expect(find.text('兔舍备注已更新'), findsOneWidget);
  });

  testWidgets('user without house-edit permission cannot rename the house',
      (tester) async {
    await tester.pumpWidget(_testApp(
      _RenamingHouseRepository(),
      const HousePermission(
        perms: 'edit',
        isAdmin: false,
        role: 'STAFF',
      ),
    ));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('house-edit-name-entry')),
      findsNothing,
    );
    expect(find.text('东侧二号棚'), findsOneWidget);
  });

  testWidgets('long house name editor fits 360x800 at 200 percent text',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final repository = _RenamingHouseRepository()
      ..house = const RabbitHouse(
        id: 8,
        name: '这是一个用于验证窄屏布局不会与编辑按钮重叠的超长自定义兔舍名称',
        remark: '东侧二号棚',
        layoutRows: 2,
        layoutCols: 3,
        layoutLayers: 2,
      );

    await tester.pumpWidget(_testApp(
      repository,
      const HousePermission(
        perms: 'control',
        isAdmin: false,
        role: 'MANAGER',
        permissions: ['rabbit:houses:edit'],
      ),
    ));
    await tester.pumpAndSettle();
    _expectNoLayoutException(tester);

    await tester.tap(find.byKey(const ValueKey('house-edit-name-entry')));
    await tester.pumpAndSettle();

    final submit = find.byKey(const ValueKey('house-edit-name-submit'));
    expect(submit, findsOneWidget);
    await tester.ensureVisible(submit);
    await tester.pumpAndSettle();
    _expectNoLayoutException(tester);
  });
}

void _expectNoLayoutException(WidgetTester tester) {
  final exception = tester.takeException();
  expect(
    exception,
    isNull,
    reason: exception is FlutterError
        ? exception.toStringDeep()
        : exception?.toString(),
  );
}

Widget _testApp(
  _RenamingHouseRepository repository,
  HousePermission permission,
) {
  return ProviderScope(
    overrides: [
      houseRepositoryProvider.overrideWithValue(repository),
      housesProvider.overrideWith((_) => repository.listHouses()),
      housePermissionProvider(8).overrideWith((_) async => permission),
      houseCagesProvider(8).overrideWith((_) async => const []),
      houseRabbitsProvider(8).overrideWith((_) async => const []),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: const HouseDetailScreen(houseId: 8),
    ),
  );
}

class _RenamingHouseRepository extends HouseRepository {
  _RenamingHouseRepository() : super(ApiClient(SessionStore()));

  RabbitHouse house = const RabbitHouse(
    id: 8,
    name: '默认兔舍',
    remark: '东侧二号棚',
    layoutRows: 2,
    layoutCols: 3,
    layoutLayers: 2,
  );
  int? updatedHouseId;
  String? updatedName;
  String? updatedRemark;

  @override
  Future<List<RabbitHouse>> listHouses() async => [house];

  @override
  Future<RabbitHouse> updateHouse({
    required int houseId,
    required String name,
    required String remark,
  }) async {
    updatedHouseId = houseId;
    updatedName = name;
    updatedRemark = remark;
    house = RabbitHouse(
      id: house.id,
      name: name,
      remark: remark,
      layoutRows: house.layoutRows,
      layoutCols: house.layoutCols,
      layoutLayers: house.layoutLayers,
    );
    return house;
  }
}

class _CapturingAdapter implements HttpClientAdapter {
  late RequestOptions request;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    request = options;
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'id': 8,
          'name': '朝阳兔舍',
          'remark': '东侧二号棚',
          'layoutRows': 2,
          'layoutCols': 3,
          'layoutLayers': 2,
        },
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
