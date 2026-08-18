// 真机验收：死亡记录收口、换笼位对调/并笼、笼内逐只管理、录入母兔入轨。
//
// 对应飞书 recvrpTL16SBwu / recvqh5TC8wd3y / recvsrEA6TRuK6 / recvsrnEJ8bKrk / recvsrpMlvu2SC。
// 走真机 + 真后端：这几条的核心风险都在「界面能不能真的走通」——弹窗被键盘顶掉、
// 目标笼位筛不出来、种母兔录入必定 400，都是单元测试和 mock 看不见的。
//
// NFC 碰标签的**硬件读写**仍是手工（得有人拿真卡贴上去），但碰到之后的整条链路
// 已经自动化：fixture 预先把 R1-C5 的标签绑好，用例从 GET /api/nfc/cages/write-queue
// 取回**真实签名**的 payload（HMAC 算不出来，SQL 造不了假的），再把它从
// com.rabbit.app.flutter/nfc_intents 通道注入，等同于 Android 把 NDEF intent 递给 Flutter。
// 真正碰不到的只剩“手机天线读到了卡”这一步。

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/main.dart' as app;

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
const _password = String.fromEnvironment(
  'RABBIT_E2E_PASSWORD',
  defaultValue: '123456',
);
const _houseId = int.fromEnvironment('RABBIT_E2E_HOUSE_ID');
const _doeRabbitId = int.fromEnvironment('RABBIT_E2E_DOE_RABBIT_ID');
const _reserveRabbitId = int.fromEnvironment('RABBIT_E2E_RESERVE_RABBIT_ID');
const _commodityARabbitId = int.fromEnvironment('RABBIT_E2E_COMM_A_RABBIT_ID');
const _commodityBRabbitId = int.fromEnvironment('RABBIT_E2E_COMM_B_RABBIT_ID');
const _commodityCRabbitId = int.fromEnvironment('RABBIT_E2E_COMM_C_RABBIT_ID');
/// fixture 的六个笼位是一条 INSERT 连号插入的，第 N 列的 id = 首列 id + (N-1)。
const _firstCageId = int.fromEnvironment('RABBIT_E2E_FIRST_CAGE_ID');

/// R1-C5 上预先贴好的标签 UID（fixture 写入 nfc_tags + cage_nfc_tags 两张表）。
const _c5TagUid = String.fromEnvironment('RABBIT_E2E_C5_TAG_UID');
const _apiBaseUrl = String.fromEnvironment(
  'RABBIT_API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080',
);

String get _controlUser => 'cage_ops_fixture_${_runId}_control';
String get _houseName => 'H-CAGEOPS-$_runId';

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Android real-backend cage-level departure, cage swap/append and doe intake',
    (tester) async {
      _assertFixtureDefines();
      await _assertEntryPointDictionaryIsServed();
      _assertPortrait(tester);
      await _clearLocalAppState();
      await app.main();
      await binding.convertFlutterSurfaceToImage();

      await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
      await _login(tester, _controlUser);
      await _openHouseDetail(tester);
      // 落地页的原样截图（不滚动）：进入各个模块的入口能不能一眼看到，
      // 只能看真机的真屏，小屏模拟不算。
      await _takeScreenshot(binding, tester, '00-house-detail');
      await _enterCages(tester);
      await _takeScreenshot(binding, tester, '01-cage-grid');

      // 分层地图本身要有一张证据：排/层/位的网格、关注度颜色和图例。
      final firstCell = find.byKey(ValueKey('cage-map-cell-${_cageIdAt(1)}'));
      await _scrollUntilPresent(
        tester,
        firstCell,
        scrollable: find.byKey(const ValueKey('house-cage-list-scroll')),
      );
      expect(find.byKey(const ValueKey('cage-map-legend')), findsOneWidget,
          reason: '颜色必须配图例，否则用户只能猜');
      // fixture 故意把五种关注度都摆出来（已投喂/未投喂/空笼/停用/账不平），
      // 这样“颜色在分状态”才是被验过的，而不是一片同色。
      for (final state in const ['异常', '停用', '待投喂', '已满', '有空位']) {
        expect(find.textContaining(state), findsWidgets,
            reason: '图例应列出关注度「$state」');
      }
      await _takeScreenshot(binding, tester, '01b-cage-map');

      // ── 一、多只商品兔笼里挑一只登记死亡（recvrpTL16SBwu）
      // 这是原单点名的场景：单只兔笼可以直接标记，多只商品兔笼必须能在笼位详情里挑。
      await _openCageAt(tester, 3);
      await _waitFor(tester, _rabbitRow(_commodityARabbitId));
      expect(_rabbitRow(_commodityBRabbitId), findsOneWidget,
          reason: '笼内两只商品兔都应列出，才谈得上「挑一只」');
      await _takeScreenshot(binding, tester, '02-commodity-cage-two-rabbits');

      await _openRabbitMenu(tester, _commodityARabbitId);
      await tester.tap(find.text('登记离场').last);
      await _waitFor(tester, find.byKey(const ValueKey('rabbit-departure-submit')));
      // 批次详情之外也能登记，说明 batchId 已经不是必填。
      expect(find.text('登记离场'), findsWidgets);
      await _takeScreenshot(binding, tester, '03-departure-sheet');

      await tester.tap(find.byKey(const ValueKey('rabbit-departure-event-death')));
      await _enterField(
        tester,
        const ValueKey('rabbit-departure-reason'),
        '真机验收：笼内商品兔死亡',
      );
      // 离场表单是 ListView，风险确认勾选框在屏幕外时根本没被 build，
      // ensureVisible 对不存在的 widget 无效，必须先滚出来。
      await _scrollUntilPresent(
        tester,
        find.byKey(const ValueKey('rabbit-departure-confirm-risk')),
      );
      await tester.tap(find.byKey(const ValueKey('rabbit-departure-confirm-risk')));
      await tester.pumpAndSettle();
      await _takeScreenshot(binding, tester, '04-departure-filled');
      await _tapSubmit(tester, const ValueKey('rabbit-departure-submit'));

      await _waitForGone(tester, _rabbitRow(_commodityARabbitId));
      expect(_rabbitRow(_commodityBRabbitId), findsOneWidget,
          reason: '只应离场被选中的那一只');
      await _takeScreenshot(binding, tester, '05-departure-done');

      // ── 二、种母兔与后备兔对调笼位（recvqh5TC8wd3y 的第三条规则）
      await _backToCageGrid(tester);
      await _openCageAt(tester, 1);
      await _waitFor(tester, _rabbitRow(_doeRabbitId));
      // 阶段能显示，才算 recvsrEA6TRuK6 的投影列真的接上了调用方。
      expect(find.textContaining('生产阶段：'), findsWidgets,
          reason: 'current_stage 应出现在笼内兔只行');
      await _takeScreenshot(binding, tester, '06-doe-cage-with-stage');

      await _openRabbitMenu(tester, _doeRabbitId);
      await tester.tap(find.text('换笼位').last);
      await _waitFor(tester, find.byKey(const ValueKey('rabbit-move-cage-submit')));
      // 被占用的后备兔笼此前会被过滤掉，现在必须作为「对调」候选出现。
      // 地图选择器把这个信息写在格子上（而不是列表行的副标题），选中后再在底部说清。
      expect(find.text('对调'), findsWidgets,
          reason: '占用的非商品兔笼应在地图上标出对调');
      await _takeScreenshot(binding, tester, '07-move-sheet-swap-target');

      await _pickMoveTarget(tester, _cageIdAt(2));
      // 底部常驻的选中说明必须当场告知这是一次对调。
      expect(
        find.textContaining('将与笼内兔只对调'),
        findsWidgets,
        reason: '选中已占用的非商品兔笼后，底部应提示对调',
      );
      await _tapSubmit(tester, const ValueKey('rabbit-move-cage-submit'));
      await _waitFor(tester, find.textContaining('对调笼位'));
      await _takeScreenshot(binding, tester, '08-swap-done');

      // 对调后原笼里站的应该是后备兔。
      await _pumpUntilSettled(tester);
      await _waitFor(tester, _rabbitRow(_reserveRabbitId));
      expect(_rabbitRow(_doeRabbitId), findsNothing,
          reason: '种母兔已换到后备兔原来的笼位');

      // ── 三、商品兔并入未满的商品兔笼（recvqh5TC8wd3y 的第二条规则）
      await _backToCageGrid(tester);
      await _openCageAt(tester, 4);
      await _waitFor(tester, _rabbitRow(_commodityCRabbitId));
      await _openRabbitMenu(tester, _commodityCRabbitId);
      await tester.tap(find.text('换笼位').last);
      await _waitFor(tester, find.byKey(const ValueKey('rabbit-move-cage-submit')));
      await _pickMoveTarget(tester, _cageIdAt(3));
      await _takeScreenshot(binding, tester, '09-move-sheet-append-target');
      await _tapSubmit(tester, const ValueKey('rabbit-move-cage-submit'));
      await _waitFor(tester, find.textContaining('已换至'));
      await _takeScreenshot(binding, tester, '10-append-done');

      // ── 四、录入种母兔并直接入轨（recvsrnEJ8bKrk / recvsrpMlvu2SC）
      await _backToCageGrid(tester);
      await _openCageAt(tester, 6);
      await _tapAndSettle(tester, const ValueKey('cage-rabbit-entry'));
      await _waitFor(tester, find.text('请选择录入兔子类型'));
      await tester.tap(find.text('种公兔/种母兔'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('确定'));
      await _waitFor(tester, find.byKey(const ValueKey('rabbit-entry-submit')));

      // 母（默认）时不得再出现旧的繁殖阶段下拉——后端本就拒收，填了必定 400。
      expect(find.byKey(const ValueKey('rabbit-reproductive-stage')), findsNothing);
      // 入轨阶段字典是一次网络请求，真机上表单先渲染「正在读取」；
      // 直接 ensureVisible 会碰上还没 build 的下拉。
      final reproStage = find.byKey(const ValueKey('rabbit-repro-stage'));
      await _waitFor(tester, reproStage);
      await tester.ensureVisible(reproStage);
      await tester.pumpAndSettle();
      await _takeScreenshot(binding, tester, '11-doe-intake-form');

      await tester.tap(reproStage);
      await tester.pumpAndSettle();
      // 阶段名来自服务端字典，写死在客户端就会漂移。
      await tester.tap(find.text('待摸胎').last);
      await tester.pumpAndSettle();
      // 待摸胎要补录配种日期，字段随字典出现。
      final matingDate = find.byKey(const ValueKey('rabbit-mating-date'));
      expect(matingDate, findsOneWidget,
          reason: '待摸胎必须要求配种日期');
      await tester.ensureVisible(matingDate);
      await tester.tap(matingDate);
      // 日期选择器的确定是 TextButton，表单提交按钮同样写着「确定」但是 ElevatedButton，
      // 只按文本找会命中两个。
      final pickerConfirm = find.widgetWithText(TextButton, '确定');
      await _waitFor(tester, pickerConfirm);
      await tester.tap(pickerConfirm.last);
      await tester.pumpAndSettle();
      await _takeScreenshot(binding, tester, '12-doe-intake-stage-picked');

      await _enterField(
        tester,
        const ValueKey('rabbit-entry-breed'),
        'CAGEOPS-NEWDOE',
      );
      await _tapSubmit(tester, const ValueKey('rabbit-entry-submit'));
      // 提示语要说清楚入的是哪个阶段，否则人不知道还要不要再去生产流程里补一次。
      // 这里必须匹配完整句：只找「入轨」会被表单里的区域标题蒙对。
      await _waitFor(tester, find.textContaining('并从【待摸胎】入轨'));
      await _takeScreenshot(binding, tester, '13-doe-intake-done');
      // 弹窗提示会消失，笼内列表真的多了一只、并且带着刚入轨的阶段，才是结果。
      await _waitFor(tester, find.textContaining('CAGEOPS-NEWDOE'));
      expect(find.textContaining('生产阶段：待摸胎'), findsWidgets,
          reason: '新录入的母兔应当场就显示入轨阶段');

      // 场景 5：碰一下目标笼位的 NFC 标签。
      //
      // 同一张标签要在两种情境下给出两种结果，这才是采集窗口存在的意义：
      //   换笼表单开着时 -> 选中目标笼，绝不能跳页把表单顶掉；
      //   没有表单时   -> 直接跳进该笼位详情。
      final signedPayload = await _fetchSignedCagePayload(_cageIdAt(5));
      await _backToCageGrid(tester);
      await _openCageAt(tester, 1);
      // 对调把后备兔放进了原种兔笼，这里是它唯一的断言点：
      // 接下来碰标签会把它再搬走，终态的数据库元组就看不到它曾在 C1 了。
      expect(find.byKey(const ValueKey('cage-rabbit-row-$_reserveRabbitId')),
          findsOneWidget,
          reason: '对调后后备兔应当在原种兔笼 R1-C1-L1 里');
      await _openRabbitMenu(tester, _reserveRabbitId);
      await tester.tap(find.text('换笼位').last);
      await _waitFor(tester, find.byKey(const ValueKey('rabbit-move-cage-nfc')));
      await tester.tap(find.byKey(const ValueKey('rabbit-move-cage-nfc')));
      await _pumpUntilSettled(tester);
      await _takeScreenshot(binding, tester, '14-nfc-waiting');

      // 先碰一张别的兔舍的标签：这一脚必须在本地就被拦，不能拿着别人兔舍的
      // 笼位 id 去问后端——跨兔舍请求本身就不应该发出去。
      await _tapNfcTag(
        tester,
        payload: _foreignHousePayload(signedPayload),
        tagUid: _c5TagUid,
      );
      await _waitFor(tester, find.textContaining('属于其它兔舍'));

      // 再碰真标签：这次要选中 R1-C5。
      await tester.tap(find.byKey(const ValueKey('rabbit-move-cage-nfc')));
      await _pumpUntilSettled(tester);
      await _tapNfcTag(tester, payload: signedPayload, tagUid: _c5TagUid);
      await _waitFor(tester, find.textContaining('已选中 R1-C5-L1'));
      await _takeScreenshot(binding, tester, '15-nfc-target-picked');
      await _tapSubmit(tester, const ValueKey('rabbit-move-cage-submit'));
      await _waitFor(tester, find.textContaining('已换至 R1-C5-L1'));
      await _takeScreenshot(binding, tester, '16-nfc-move-done');

      // 没有采集窗口时碰同一张标签：应该直接跳进 R1-C5 详情，
      // 而且里面已经能看到刚搬过去的后备兔。
      await _backToCageGrid(tester);
      await _tapNfcTag(tester, payload: signedPayload, tagUid: _c5TagUid);
      await _waitFor(
        tester,
        find.byKey(const ValueKey('cage-rabbit-row-$_reserveRabbitId')),
        timeout: const Duration(seconds: 30),
      );
      expect(find.text('R1-C5-L1'), findsWidgets,
          reason: '碰标签应该落在该标签绑定的笼位详情页');
      await _takeScreenshot(binding, tester, '17-nfc-jump-to-cage');

      binding.reportData ??= <String, dynamic>{};
      binding.reportData!.addAll({
        'runId': _runId,
        'houseId': _houseId,
        'departedRabbitId': _commodityARabbitId,
        'swappedRabbitIds': [_doeRabbitId, _reserveRabbitId],
        'appendedRabbitId': _commodityCRabbitId,
        'nfcMovedRabbitId': _reserveRabbitId,
        'nfcTagUid': _c5TagUid,
        'logicalSize': _logicalSize(tester).toString(),
      });
    },
  );
}

/// 横屏下逻辑视口只剩 ~360px 高，ListView 里靠下的卡片不会被构建，
/// 后面会满屏「Found 0 widgets」——那是手机躺歪了，不是界面坏了。
/// 验收截图在横屏下也没法看，所以直接拒跑。
void _assertPortrait(WidgetTester tester) {
  final size = _logicalSize(tester);
  expect(
    size.height,
    greaterThan(size.width),
    reason: '设备当前是横屏（${size.width.toStringAsFixed(0)}x'
        '${size.height.toStringAsFixed(0)}）。先关自动旋转并竖过来：'
        'adb shell settings put system accelerometer_rotation 0 \u0026\u0026 '
        'adb shell settings put system user_rotation 0',
  );
}

void _assertFixtureDefines() {
  expect(_runId, isNotEmpty, reason: 'RABBIT_E2E_RUN_ID is required');
  expect(_houseId, greaterThan(0), reason: 'RABBIT_E2E_HOUSE_ID is required');
  for (final entry in {
    'RABBIT_E2E_DOE_RABBIT_ID': _doeRabbitId,
    'RABBIT_E2E_RESERVE_RABBIT_ID': _reserveRabbitId,
    'RABBIT_E2E_COMM_A_RABBIT_ID': _commodityARabbitId,
    'RABBIT_E2E_COMM_B_RABBIT_ID': _commodityBRabbitId,
    'RABBIT_E2E_COMM_C_RABBIT_ID': _commodityCRabbitId,
    'RABBIT_E2E_FIRST_CAGE_ID': _firstCageId,
  }.entries) {
    expect(entry.value, greaterThan(0), reason: '${entry.key} is required');
  }
  expect(_c5TagUid, isNotEmpty, reason: 'RABBIT_E2E_C5_TAG_UID is required');
}

/// 录入表单靠这个字典决定要填哪些日期；端点不在（旧镜像）时界面会静默退化，
/// 所以先直接问一次后端，把「跑的是旧后端」和「界面坏了」区分开。
Future<void> _assertEntryPointDictionaryIsServed() async {
  final dio = Dio(BaseOptions(
    baseUrl: _apiBaseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 20),
    headers: const {'Content-Type': 'application/json'},
  ));
  try {
    final login = await dio.post<Map<String, dynamic>>(
      '/api/auth/login',
      data: {'userName': _controlUser, 'password': _password},
    );
    expect(login.data?['code'], 0, reason: 'fixture user must be able to log in');
    final token =
        Map<String, dynamic>.from(login.data!['data'] as Map)['token'] as String;
    final response = await dio.get<Map<String, dynamic>>(
      '/api/repro/entry-points',
      options: Options(headers: {
        'Authorization': 'Bearer $token',
        'X-House-Id': '$_houseId',
      }),
    );
    expect(response.data?['code'], 0,
        reason: 'GET /api/repro/entry-points must be served by the backend');
    final rows = List<Map<String, dynamic>>.from(
      (response.data!['data'] as List).map((e) => Map<String, dynamic>.from(e as Map)),
    );
    expect(
      rows.map((row) => row['stage']),
      contains('AWAIT_PALPATION'),
      reason: '待摸胎必须是入轨点，否则录入表单选不到它',
    );
  } finally {
    dio.close(force: true);
  }
}

Future<void> _clearLocalAppState() async {
  final preferences = await SharedPreferences.getInstance();
  await preferences.clear();
  await const FlutterSecureStorage().deleteAll();
}

Future<void> _login(WidgetTester tester, String userName) async {
  await tester.tap(find.text('账号'));
  await tester.pumpAndSettle();
  await tester.enterText(
    find.byKey(const ValueKey('account-username-field')),
    userName,
  );
  await tester.enterText(
    find.byKey(const ValueKey('account-password-field')),
    _password,
  );
  // 登录表单是 ListView：输入后键盘顶起内容，同意行被挤出可视区就会被直接销毁
  // （不是「看不见」，是「不在树上」），tap 于是报 Found 0 widgets。先收键盘，
  // 万一还不在树上再滚回来——这条曾让三个真机本子随机红一次。
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final consent = find.byKey(const ValueKey('legal-consent-checkbox'));
  for (var attempt = 0; attempt < 8 && consent.evaluate().isEmpty; attempt++) {
    final scrollable = find.byType(Scrollable);
    if (scrollable.evaluate().isEmpty) break;
    await tester.drag(scrollable.first, const Offset(0, -120));
    await tester.pumpAndSettle();
  }
  await tester.ensureVisible(consent);
  await tester.tap(consent);
  await tester.pumpAndSettle();
  final loginButton = find.byKey(const ValueKey('account-login-button'));
  await tester.ensureVisible(loginButton);
  await tester.tap(loginButton);
  await _waitFor(tester, find.text('兔舍'));
}

Future<void> _openHouseDetail(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('nav-houses')));
  final houseCard = find.byKey(const ValueKey('house-card-$_houseId'));
  await _waitFor(tester, houseCard);
  expect(find.text(_houseName), findsOneWidget);
  await tester.tap(houseCard);
  // 先等一个肯定在首屏的东西，确认确实进了详情页。
  await _waitFor(tester, find.text('兔舍详情'));
}

Future<void> _enterCages(WidgetTester tester) async {
  // 兔舍详情是 ListView，「笼位管理」卡片在概览指标下面；屏幕矮或备注长时
  // 它压根不在 widget 树上（不是看不见，是没构建），直接 tap 会报 Found 0。
  final entry = find.text('笼位管理');
  await _scrollUntilPresent(tester, entry);
  await tester.ensureVisible(entry);
  await tester.pumpAndSettle();
  await tester.tap(find.text('进入笼位'));
  await _waitFor(tester, find.byKey(const ValueKey('cage-map')));
}

Future<void> _backToCageGrid(WidgetTester tester) async {
  final back = find.byKey(const ValueKey('cage-detail-back-button'));
  if (back.evaluate().isNotEmpty) {
    await tester.tap(back);
  }
  await _waitFor(tester, find.byKey(const ValueKey('cage-map')));
}

/// 笼位区默认是分层地图，格子上只写在栏数与关注状态，不再写笼位编号，
/// 所以按 id 点格子（比认文字更稳）。position 从 1 开始，fixture 笼位 id 连号。
Future<void> _openCageAt(WidgetTester tester, int positionIndex) async {
  final cell = find.byKey(ValueKey('cage-map-cell-${_cageIdAt(positionIndex)}'));
  await _scrollUntilPresent(
    tester,
    cell,
    scrollable: find.byKey(const ValueKey('house-cage-list-scroll')),
  );
  await tester.ensureVisible(cell);
  await tester.pumpAndSettle();
  await tester.tap(cell);
  await _waitFor(tester, find.byKey(const ValueKey('cage-detail-back-button')));
  await _pumpUntilSettled(tester);
}

int _cageIdAt(int positionIndex) => _firstCageId + positionIndex - 1;

Finder _rabbitRow(int rabbitId) =>
    find.byKey(ValueKey('cage-rabbit-row-$rabbitId'));

/// 把一次“碰标签”递给 App。
///
/// Android 那边是 MainActivity 收到 NDEF intent 后调 nfcIntent；这里直接从通道注入
/// 同形状的消息。真实 intent 带的是 Parcelable，adb 造不出来，所以“天线读到卡”
/// 这一步仍是手工；从这一步之后的解析、校签、路由、选中全是真的。
Future<void> _tapNfcTag(
  WidgetTester tester, {
  required String payload,
  required String tagUid,
}) async {
  const codec = StandardMethodCodec();
  final message = codec.encodeMethodCall(MethodCall('nfcIntent', {
    'payload': payload,
    'tagUid': tagUid,
    'receivedAt': DateTime.now().millisecondsSinceEpoch,
  }));
  await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
    'com.rabbit.app.flutter/nfc_intents',
    message,
    (_) {},
  );
  await _pumpUntilSettled(tester);
}

/// 从写卡队列取回笼位的**真实签名** payload。
///
/// 签名是 HMAC-SHA256，fixture 的 SQL 里算不出来；而随便造一个字符串只会被
/// 后端以 4602 驳回，那就变成在测“错误处理”而不是测碰标签。
Future<String> _fetchSignedCagePayload(int cageId) async {
  final dio = Dio(BaseOptions(
    baseUrl: _apiBaseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 20),
    headers: const {'Content-Type': 'application/json'},
  ));
  try {
    final login = await dio.post<Map<String, dynamic>>(
      '/api/auth/login',
      data: {'userName': _controlUser, 'password': _password},
    );
    final token =
        Map<String, dynamic>.from(login.data!['data'] as Map)['token'] as String;
    final response = await dio.get<Map<String, dynamic>>(
      '/api/nfc/cages/write-queue',
      options: Options(headers: {
        'Authorization': 'Bearer $token',
        'X-House-Id': '$_houseId',
      }),
    );
    expect(response.data?['code'], 0,
        reason: 'GET /api/nfc/cages/write-queue must be served by the backend');
    final rows = List<Map<String, dynamic>>.from(
      (response.data!['data'] as List)
          .map((e) => Map<String, dynamic>.from(e as Map)),
    );
    final item = rows.firstWhere(
      (row) => (row['cageId'] as num).toInt() == cageId,
      orElse: () => throw StateError('write queue has no cage $cageId'),
    );
    expect(item['bindingStatus'], 'BOUND',
        reason: 'fixture 已把 R1-C5 的标签绑定，否则碰上去会报 4603');
    final payload = item['payload'] as String;
    expect(payload, startsWith('r1.'), reason: 'payload 必须是 r1 协议');
    return payload;
  } finally {
    dio.close(force: true);
  }
}

/// 把真 payload 的兔舍段改成另一个兔舍，用来验证客户端的就地拒绝。
///
/// 格式是 r1.{houseId36}.{cageId36}.{keyId36}.{sig}，只改第二段；签名因此失效，
/// 但这正是重点：它根本不应该被发到后端去验签。
String _foreignHousePayload(String payload) {
  final parts = payload.split('.');
  expect(parts.length, 5, reason: 'payload 应为五段');
  final otherHouse = (_houseId + 1).toRadixString(36);
  return [parts[0], otherHouse, parts[2], parts[3], parts[4]].join('.');
}

Future<void> _openRabbitMenu(WidgetTester tester, int rabbitId) async {
  final menu = find.byKey(ValueKey('cage-rabbit-menu-$rabbitId'));
  await tester.ensureVisible(menu);
  await tester.pumpAndSettle();
  await tester.tap(menu);
  await tester.pumpAndSettle();
}

/// 在换笼弹窗的地图上选中目标笼（默认就是地图选择）。
Future<void> _pickMoveTarget(WidgetTester tester, int cageId) async {
  final cell = find.byKey(ValueKey('cage-map-cell-$cageId'));
  await _scrollUntilPresent(
    tester,
    cell,
    scrollable: find.byKey(const ValueKey('rabbit-move-cage-scroll')),
  );
  await tester.ensureVisible(cell);
  await tester.pumpAndSettle();
  await tester.tap(cell);
  await tester.pumpAndSettle();
}

/// 惰性列表里的目标可能还没 build（ListView / SliverList 只建可见项）。
/// 先把它滚进视口，再交给 ensureVisible 做精细对齐。
///
/// [scrollable] 必要时要显式指定：分层地图每一排都是一个横向滚动区，
/// 默认的 `Scrollable.last` 会抓到横向那个，竖着拖它永远不会动。
Future<void> _scrollUntilPresent(
  WidgetTester tester,
  Finder finder, {
  Finder? scrollable,
  int maxDrags = 30,
}) async {
  for (var i = 0; i < maxDrags && finder.evaluate().isEmpty; i++) {
    final target = scrollable ?? find.byType(Scrollable).last;
    await tester.drag(target, const Offset(0, -120));
    await tester.pump(const Duration(milliseconds: 120));
  }
  expect(finder, findsWidgets, reason: '滚动到底仍找不到 $finder');
}

Future<void> _enterField(
  WidgetTester tester,
  ValueKey<String> key,
  String value,
) async {
  final finder = find.byKey(key);
  await tester.ensureVisible(finder);
  await tester.tap(finder);
  await tester.enterText(finder, value);
  await tester.pump(const Duration(milliseconds: 150));
}

Future<void> _tapAndSettle(WidgetTester tester, ValueKey<String> key) async {
  final finder = find.byKey(key);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await _pumpUntilSettled(tester);
}

/// 提交后只推一帧就交给调用方轮询。
///
/// 不能在这里等到「无待处理帧」：SnackBar 本身就是一段四秒动画，
/// 等它停下来等于等到提示已经消失，然后才去找提示。
Future<void> _tapSubmit(WidgetTester tester, ValueKey<String> key) async {
  final finder = find.byKey(key);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 120));
}

/// 真机上写请求要等网络，pumpAndSettle 会先超时；这里退化成有界轮询。
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
  // 光说“没等到 X”信息太少：到底是停在上一页、转圈圈、还是报错页，
  // 靠这一行就能分清，不用再跑一遍猜。
  fail('Timed out waiting for $finder\n当前屏上的文字：${_visibleTexts()}');
}

/// 当前 widget 树上的文本，用于超时时的现场取证。
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

Future<void> _waitForGone(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 25),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 100));
    if (finder.evaluate().isEmpty) return;
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
  }
  fail('Timed out waiting for $finder to disappear');
}

Future<void> _takeScreenshot(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  String name,
) async {
  await _pumpUntilSettled(tester);
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 150)),
  );
  await tester.pump();
  await binding.takeScreenshot(name);
}

Size _logicalSize(WidgetTester tester) {
  final view = tester.view;
  return view.physicalSize / view.devicePixelRatio;
}
