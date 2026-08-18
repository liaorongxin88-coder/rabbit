// 新场开张的真机验收：客户拿到 App 的第一天真正要走的那条路。
//
// 前面三个本子都从「fixture 已经建好兔舍、笼位、兔只」开始跑，等于把开张这段
// 一直挡在自动化外面——而这恰恰是每个新客户必然经历、且只经历一次的一段，
// 出问题的代价最大。所以这里的 fixture 只给两个空账号，兔舍、笼位、兔只
// 全部由用例在界面上一步步做出来。
//
// 覆盖：建兔舍 → 批量建笼 → 录入兔只 → 兔只总表行内编辑/换笼/单兔出库入口
//      → 按账号邀请同事 → 同事登录确认权限真的生效。
//
// 「按账号邀请」是这条链路的重点：场主拿不到对方手机号也得能把人拉进来，
// 而且必须验到最后一步——不是「邀请发出了」，是对方登录后真的看得见这个兔舍。

import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'package:rabbit_flutter/main.dart' as app;

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
const _password = String.fromEnvironment(
  'RABBIT_E2E_PASSWORD',
  defaultValue: '123456',
);
const _founderUser = String.fromEnvironment('RABBIT_E2E_FOUNDER_USER');
const _mateUser = String.fromEnvironment('RABBIT_E2E_MATE_USER');
const _mateCode = String.fromEnvironment('RABBIT_E2E_MATE_CODE');
const _apiBaseUrl = String.fromEnvironment('RABBIT_API_BASE_URL');

/// 新兔舍的名字带 run_id，避免和历史数据重名。
String get _houseName => 'H-SETUP-$_runId';

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Android real-backend farm onboarding: house, cages, rabbit, roster and invite by user code',
    (tester) async {
      _assertFixtureDefines();

      await _bootApp(tester, binding);
      _assertPortrait(tester);

      // ── 一、建兔舍：名下一个兔舍都没有的人，第一屏应该是空态
      await _login(tester, _founderUser);
      await _tapAndSettle(tester, const ValueKey('nav-houses'));
      await _waitFor(tester, find.text('尚未加入兔舍'));
      await _takeScreenshot(binding, tester, '01-empty-houses');

      await tester.tap(find.text('创建兔舍'));
      await tester.pumpAndSettle();
      await _enterField(tester, const ValueKey('house-create-name'), _houseName);
      await _enterField(tester, const ValueKey('house-create-rows'), '1');
      await _enterField(tester, const ValueKey('house-create-cols'), '3');
      await _enterField(tester, const ValueKey('house-create-layers'), '2');
      await _tapAndSettle(tester, const ValueKey('house-create-submit'));
      await _waitFor(tester, find.text(_houseName));
      await _takeScreenshot(binding, tester, '02-house-created');

      // ── 二、笼位：建兔舍时按布局自动铺好，再用「新增」补一排
      await tester.tap(find.text(_houseName));
      await _waitFor(tester, find.text('兔舍详情'));
      await _scrollUntilPresent(tester, find.text('笼位管理'));
      await tester.tap(find.text('进入笼位'));
      await _waitFor(tester, find.byKey(const ValueKey('cage-map')));
      // 1 排 × 3 列 × 2 层 = 6：建兔舍时填的布局会直接铺出笼位，
      // 新场不会停在「暂无笼位」的空态——这是开张的真实行为。
      final autoCages = await _fetchCageIds(_founderUser);
      expect(autoCages.length, 6, reason: '1 排 3 列 2 层应该自动铺出 6 个笼位');
      await _takeScreenshot(binding, tester, '03-cages-auto');

      // 手工批量建笼用在「后来又搭了一排」这种场景：再添 R2 两层三位。
      await _tapAndSettle(tester, const ValueKey('cage-create-entry'));
      await _waitFor(tester, find.byKey(const ValueKey('cage-bulk-row')));
      await _enterField(tester, const ValueKey('cage-bulk-row'), '2');
      await _enterField(tester, const ValueKey('cage-bulk-layers'), '2');
      await _enterField(tester, const ValueKey('cage-bulk-positions'), '3');
      await _tapAndSettle(tester, const ValueKey('cage-bulk-submit'));
      await _waitFor(tester, find.textContaining('已新增 6 个笼位'));
      await _waitFor(tester, find.byKey(const ValueKey('cage-map')));
      await _takeScreenshot(binding, tester, '04-cages-created');

      // 笼位是刚建出来的，id 只能从服务端捞，不能像别的本子那样从 fixture 推算。
      final cageIds = await _fetchCageIds(_founderUser);
      expect(cageIds.length, 12, reason: '自动铺的 6 个加手工补的 6 个');

      // ── 三、录入兔只：新场的第一只兔
      await _openCage(tester, cageIds.first);
      await _tapAndSettle(tester, const ValueKey('cage-rabbit-entry'));
      await _waitFor(tester, find.text('请选择录入兔子类型'));
      await tester.tap(find.text('商品兔'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('确定'));
      await _waitFor(tester, find.byKey(const ValueKey('rabbit-entry-submit')));
      await _enterField(
        tester,
        const ValueKey('rabbit-entry-breed'),
        'SETUP-FIRST',
      );
      await _tapAndSettle(tester, const ValueKey('rabbit-entry-submit'));
      await _waitFor(tester, find.textContaining('已录入到'));
      await _takeScreenshot(binding, tester, '05-first-rabbit');

      final rabbitId = await _fetchFirstRabbitId(_founderUser);

      // ── 四、兔只总表：行内编辑、换笼、单兔出库入口（B 档从没上过真机的一块）
      await _openRabbitRoster(tester);
      final row = find.byKey(ValueKey('house-rabbit-$rabbitId'));
      await _scrollUntilPresent(tester, row);
      await _takeScreenshot(binding, tester, '06-rabbit-roster');

      await _tapAndSettle(tester, ValueKey('rabbit-row-edit-$rabbitId'));
      await _waitFor(tester, find.byKey(const ValueKey('rabbit-entry-submit')));
      await _enterField(
        tester,
        const ValueKey('rabbit-entry-breed'),
        'SETUP-RENAMED',
      );
      await _tapAndSettle(tester, const ValueKey('rabbit-entry-submit'));
      await _waitFor(tester, find.textContaining('已更新兔'));
      await _waitFor(tester, find.textContaining('SETUP-RENAMED'));
      await _takeScreenshot(binding, tester, '07-rabbit-edited');

      // 换笼：目标是第 2 个笼位，地图上直接点。
      // 后端铺笼是排→位→层嵌套的，所以 id 逐个差一层：第 2 个笼在 2 层。
      // 地图一次只画一层，得先切过去——这正好把切层也拉进真机验收。
      await _scrollUntilPresent(tester, find.byKey(ValueKey('rabbit-row-move-$rabbitId')));
      await _tapAndSettle(tester, ValueKey('rabbit-row-move-$rabbitId'));
      await _waitFor(tester, find.text('换笼位'));
      final layerTwo = find.byKey(const ValueKey('cage-map-layer-2'));
      await _scrollUntilPresent(
        tester,
        layerTwo,
        scrollable: find.byKey(const ValueKey('rabbit-move-cage-scroll')),
      );
      await tester.ensureVisible(layerTwo);
      await tester.pumpAndSettle();
      await tester.tap(layerTwo);
      await tester.pumpAndSettle();
      final target = find.byKey(ValueKey('cage-map-cell-${cageIds[1]}'));
      await _scrollUntilPresent(
        tester,
        target,
        scrollable: find.byKey(const ValueKey('rabbit-move-cage-scroll')),
      );
      await tester.ensureVisible(target);
      await tester.pumpAndSettle();
      await tester.tap(target);
      await tester.pumpAndSettle();
      await _tapAndSettle(tester, const ValueKey('rabbit-move-cage-submit'));
      await _waitFor(tester, find.textContaining('已换至'));
      await _takeScreenshot(binding, tester, '08-rabbit-moved');

      // 单兔出库入口：出库流程本身另有本子专门跑，这里只验从总表进得去、
      // 且带对了这只兔，然后退出来，不重复走一遍下单。
      await _scrollUntilPresent(
        tester,
        find.byKey(ValueKey('rabbit-row-outbound-$rabbitId')),
      );
      await _tapAndSettle(tester, ValueKey('rabbit-row-outbound-$rabbitId'));
      await _waitFor(tester, find.textContaining('出库'));
      await _takeScreenshot(binding, tester, '09-single-outbound-entry');
      await _goBack(tester);
      await _waitFor(tester, find.byKey(const ValueKey('house-rabbit-list')));

      // ── 五、按账号邀请同事
      await _openMembers(tester);
      await _enterField(
        tester,
        const ValueKey('house-invitation-identifier-field'),
        _mateCode,
      );
      // 故意邀成只读：这样最后一步才能验「权限真的生效了」，
      // 而不是只验「人进来了」。
      final roleField = find.byKey(const ValueKey('house-invitation-role-field'));
      await tester.ensureVisible(roleField);
      await tester.pumpAndSettle();
      await tester.tap(roleField);
      await tester.pumpAndSettle();
      await tester.tap(find.text('游客（只读）').last);
      await tester.pumpAndSettle();
      await _tapAndSettle(tester, const ValueKey('submit-house-invitation'));
      // 账号的主人已经在平台上，所以是「已加入」，不是「邀请已发出」。
      await _waitFor(tester, find.textContaining('已加入本兔舍，角色：游客'));
      await _waitFor(tester, find.textContaining(_mateUser));
      await _takeScreenshot(binding, tester, '10-member-invited');

      // ── 六、被邀请人登录：真正的验收标准是「他自己看得见」
      await _logout(tester);
      await _login(tester, _mateUser);
      await _tapAndSettle(tester, const ValueKey('nav-houses'));
      await _waitFor(tester, find.text(_houseName));
      await _takeScreenshot(binding, tester, '11-mate-sees-house');

      await tester.tap(find.text(_houseName));
      await _waitFor(tester, find.text('兔舍详情'));
      await _waitFor(tester, find.textContaining('我的角色：游客'));
      // 只读的人不该看到「人员管理」这类管理入口。
      expect(find.text('人员管理'), findsNothing,
          reason: '游客不该看到人员管理入口');
      await _takeScreenshot(binding, tester, '12-mate-readonly');

      // 截图是挂在 reportData 上送回 driver 的，整个赋值会把它们连锅端掉
      // （表现是用例全绿但一张截图都没有），所以只能往里加。
      binding.reportData ??= <String, dynamic>{};
      binding.reportData!.addAll(<String, dynamic>{
        'runId': _runId,
        'houseName': _houseName,
        'cageIds': cageIds,
        'rabbitId': rabbitId,
        'mateCode': _mateCode,
      });
    },
  );
}

// ───────────────────────── 基础设施 ─────────────────────────

Future<void> _bootApp(
  WidgetTester tester,
  IntegrationTestWidgetsFlutterBinding binding,
) async {
  final preferences = await SharedPreferences.getInstance();
  await preferences.clear();
  await const FlutterSecureStorage().deleteAll();
  await app.main();
  // 先转图像表面再跑用例，否则 takeScreenshot 拿不到帧。
  await binding.convertFlutterSurfaceToImage();
}

/// 真机上网络写请求会让 pumpAndSettle 直接超时（SnackBar 动画也会），
/// 所以用有界的轮询代替。
Future<void> _pumpUntilSettled(
  WidgetTester tester, {
  Duration timeout = const Duration(seconds: 15),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 120));
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 80)),
    );
    if (!tester.binding.hasScheduledFrame) return;
  }
}

void _assertFixtureDefines() {
  final missing = <String>[
    if (_runId.isEmpty) 'RABBIT_E2E_RUN_ID',
    if (_founderUser.isEmpty) 'RABBIT_E2E_FOUNDER_USER',
    if (_mateUser.isEmpty) 'RABBIT_E2E_MATE_USER',
    if (_mateCode.isEmpty) 'RABBIT_E2E_MATE_CODE',
    if (_apiBaseUrl.isEmpty) 'RABBIT_API_BASE_URL',
  ];
  expect(missing, isEmpty, reason: '缺少 fixture 注入：${missing.join(", ")}');
}

/// 横屏下逻辑视口只剩 ~360px 高，ListView 里靠下的卡片不会被构建，
/// 后面会满屏「Found 0 widgets」——那是手机躺歪了，不是界面坏了。
void _assertPortrait(WidgetTester tester) {
  final size = tester.view.physicalSize / tester.view.devicePixelRatio;
  expect(
    size.height,
    greaterThan(size.width),
    reason: '设备当前是横屏（${size.width.toStringAsFixed(0)}x'
        '${size.height.toStringAsFixed(0)}）。先关自动旋转并竖过来。',
  );
}

Future<void> _login(WidgetTester tester, String userName) async {
  // 登录页默认停在「手机号」页签，账号密码表单在另一个页签里。
  await _waitFor(tester, find.text('账号'));
  await tester.tap(find.text('账号'));
  await tester.pumpAndSettle();
  await _waitFor(tester, find.byKey(const ValueKey('account-username-field')));
  await _enterField(
    tester,
    const ValueKey('account-username-field'),
    userName,
  );
  await _enterField(
    tester,
    const ValueKey('account-password-field'),
    _password,
  );
  // 登录表单是 ListView，键盘顶上来会把同意行挤出树（不是看不见，是不在树上）。
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final consent = find.byKey(const ValueKey('legal-consent-checkbox'));
  await _scrollUntilPresent(tester, consent);
  await tester.tap(consent);
  await tester.pumpAndSettle();
  await _tapAndSettle(tester, const ValueKey('account-login-button'));
  await _waitFor(tester, find.text('兔舍'), timeout: const Duration(seconds: 40));
}

Future<void> _logout(WidgetTester tester) async {
  await _tapAndSettle(tester, const ValueKey('nav-profile'));
  final logout = find.text('退出登录');
  await _scrollUntilPresent(tester, logout);
  await tester.tap(logout);
  await tester.pumpAndSettle();
  // 登录页回到默认的「手机号」页签，账号输入框此时不在树上，
  // 所以只能等页签本身。
  await _waitFor(
    tester,
    find.text('登录后管理兔舍、预警和生产流程。'),
    timeout: const Duration(seconds: 30),
  );
}

Future<void> _openRabbitRoster(WidgetTester tester) async {
  await _tapAndSettle(tester, const ValueKey('nav-houses'));
  await _waitFor(tester, find.text(_houseName));
  await tester.tap(find.text(_houseName));
  await _waitFor(tester, find.text('兔舍详情'));
  await _scrollUntilPresent(tester, find.text('查看兔只'));
  await tester.tap(find.text('查看兔只'));
  await _waitFor(tester, find.byKey(const ValueKey('house-rabbit-list')));
}

Future<void> _openMembers(WidgetTester tester) async {
  await _tapAndSettle(tester, const ValueKey('nav-houses'));
  await _waitFor(tester, find.text(_houseName));
  await tester.tap(find.text(_houseName));
  await _waitFor(tester, find.text('兔舍详情'));
  await _scrollUntilPresent(tester, find.text('人员管理'));
  await tester.tap(find.text('管理'));
  await _waitFor(
    tester,
    find.byKey(const ValueKey('house-invitation-identifier-field')),
  );
}

Future<void> _openCage(WidgetTester tester, int cageId) async {
  final cell = find.byKey(ValueKey('cage-map-cell-$cageId'));
  await _scrollUntilPresent(
    tester,
    cell,
    scrollable: find.byKey(const ValueKey('house-cage-list-scroll')),
  );
  await tester.ensureVisible(cell);
  await tester.pumpAndSettle();
  await tester.tap(cell);
  await _waitFor(tester, find.byKey(const ValueKey('cage-rabbit-entry')));
}

Future<void> _goBack(WidgetTester tester) async {
  final back = find.byTooltip('返回');
  if (back.evaluate().isNotEmpty) {
    await tester.tap(back.first);
  } else {
    await tester.pageBack();
  }
  await tester.pumpAndSettle();
}

// ───────────────────────── 服务端取数 ─────────────────────────

/// 笼位和兔只都是用例现场造出来的，id 只能问服务端要。
Future<Dio> _authedDio(String userName) async {
  final dio = Dio(BaseOptions(baseUrl: _apiBaseUrl));
  final login = await dio.post<Map<String, dynamic>>(
    '/api/auth/login',
    data: {'userName': userName, 'password': _password},
  );
  final token = (login.data?['data'] as Map?)?['token'] as String?;
  expect(token, isNotNull, reason: '取不到 token：${login.data}');
  dio.options.headers['Authorization'] = 'Bearer $token';
  return dio;
}

Future<int> _houseId(Dio dio) async {
  final response = await dio.get<Map<String, dynamic>>('/api/houses');
  // /api/houses 直接返回数组，其它列表接口是分页对象，两种都得接住。
  final data = response.data?['data'];
  final list = data is List
      ? data
      : (data is Map ? (data['records'] as List? ?? const []) : const []);
  for (final item in list) {
    final map = (item as Map).cast<String, dynamic>();
    if (map['name'] == _houseName) {
      return (map['id'] as num).toInt();
    }
  }
  throw StateError('服务端没有找到刚建的兔舍 $_houseName：${jsonEncode(list)}');
}

Future<List<int>> _fetchCageIds(String userName) async {
  final dio = await _authedDio(userName);
  final houseId = await _houseId(dio);
  final response = await dio.get<Map<String, dynamic>>(
    '/api/cages',
    queryParameters: {'houseId': houseId},
    options: Options(headers: {'X-House-Id': '$houseId'}),
  );
  final list = (response.data?['data'] as List?) ?? const [];
  final ids = <int>[
    for (final item in list)
      ((item as Map).cast<String, dynamic>()['id'] as num).toInt(),
  ]..sort();
  return ids;
}

Future<int> _fetchFirstRabbitId(String userName) async {
  final dio = await _authedDio(userName);
  final houseId = await _houseId(dio);
  final response = await dio.get<Map<String, dynamic>>(
    '/api/rabbits',
    queryParameters: {'houseId': houseId, 'pageSize': 50},
    options: Options(headers: {'X-House-Id': '$houseId'}),
  );
  final data = response.data?['data'];
  final list = data is Map ? (data['records'] as List? ?? const []) : (data as List? ?? const []);
  expect(list, isNotEmpty, reason: '刚录入的兔子应该能查到');
  return ((list.first as Map).cast<String, dynamic>()['id'] as num).toInt();
}

// ───────────────────────── 等待与交互 ─────────────────────────

Future<void> _waitFor(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 25),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 100));
    if (finder.evaluate().isNotEmpty) return;
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
  }
  // 只说「没等到 X」信息太少：到底是停在上一页、转圈圈、还是报错页，
  // 靠这一行就能分清，不用再跑一遍猜。
  fail('Timed out waiting for $finder\n当前屏上的文字：${_visibleTexts()}');
}

String _visibleTexts() {
  final texts = <String>[];
  for (final element in find.byType(Text).evaluate()) {
    final widget = element.widget;
    if (widget is Text) {
      final value = widget.data ?? widget.textSpan?.toPlainText() ?? '';
      if (value.trim().isNotEmpty) texts.add(value.trim());
    }
    if (texts.length >= 40) break;
  }
  return texts.isEmpty ? '(一个文本都没有，页面可能还在加载)' : texts.join(' | ');
}

/// 懒构建的列表项不在树上时 ensureVisible 会直接抛，所以先拖到它出现。
Future<void> _scrollUntilPresent(
  WidgetTester tester,
  Finder finder, {
  Finder? scrollable,
  int maxDrags = 24,
}) async {
  for (var attempt = 0; attempt < maxDrags; attempt++) {
    if (finder.evaluate().isNotEmpty) return;
    final target = scrollable ?? find.byType(Scrollable).last;
    if (target.evaluate().isEmpty) break;
    await tester.drag(target.first, const Offset(0, -160));
    await tester.pump(const Duration(milliseconds: 120));
  }
  await _waitFor(tester, finder, timeout: const Duration(seconds: 10));
}

Future<void> _enterField(WidgetTester tester, Key key, String value) async {
  final field = find.byKey(key);
  await _scrollUntilPresent(tester, field);
  await tester.ensureVisible(field);
  await tester.pumpAndSettle();
  await tester.enterText(field, value);
  await tester.pump(const Duration(milliseconds: 150));
}

Future<void> _tapAndSettle(WidgetTester tester, Key key) async {
  final target = find.byKey(key);
  await _scrollUntilPresent(tester, target);
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  await tester.tap(target);
  // 真机上写请求要走一圈网络，pumpAndSettle 会在 SnackBar 动画上超时，
  // 所以这里只推进固定几帧，等待交给 _waitFor。
  await tester.pump(const Duration(milliseconds: 300));
  await tester.pump(const Duration(milliseconds: 600));
}

Future<void> _takeScreenshot(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  String name,
) async {
  // takeScreenshot 要走一趟真实的平台通道，必须先给它 runAsync 的真异步窗口，
  // 否则它会静静地什么都不产出（用例照跑绿，截图一张没有）。
  await _pumpUntilSettled(tester);
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 150)),
  );
  await tester.pump();
  await binding.takeScreenshot(name);
}
