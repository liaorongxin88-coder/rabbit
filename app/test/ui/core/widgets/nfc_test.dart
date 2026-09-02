import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/capture_scope.dart';
import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/nfc.dart';

import 'nfc_harness.dart';

const _cageButton = ValueKey('nfc-cage-picker-button');
const _cageHint = ValueKey('nfc-cage-picker-hint');
const _rabbitButton = ValueKey('nfc-rabbit-picker-button');
const _rabbitHint = ValueKey('nfc-rabbit-picker-hint');

const _houseId = 8;
const _otherHouseId = 9;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('碰一下选笼位', () {
    testWidgets('碰一下成功选中后，全局采集标记会归还', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);
      expect(
        env.captureActive,
        isTrue,
        reason: '采集窗口开着的时候必须真的占住独占标记，否则后面的断言是空的',
      );

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(picked.single.cageNumber, 'A-01');
      expect(_hint(tester, _cageHint), '已选中 A-01');
      expect(env.captureActive, isFalse);
    });

    testWidgets('笼位被拒绝后，全局采集标记也归还', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);
      expect(env.captureActive, isTrue);

      // 12 号笼里已经有兔，换笼不接收它。
      await env.nfc.tap(houseId: _houseId, cageId: 12);
      await tester.pumpAndSettle();

      expect(_hint(tester, _cageHint), 'A-03 不能接收该兔');
      expect(picked, isEmpty, reason: '被拒绝的笼位不能悄悄选进去');
      expect(env.captureActive, isFalse);
    });

    testWidgets('标签内容读不懂时，全局采集标记照样归还', (tester) async {
      final env = _Env();
      await tester.pumpWidget(env.host(_cagePicker()));

      await _press(tester, _cageButton);
      expect(env.captureActive, isTrue);

      await env.nfc.tapPayload(NfcHarness.malformedPayload());
      await tester.pumpAndSettle();

      expect(_hint(tester, _cageHint), '读取标签失败，请重试');
      expect(
        env.repository.resolveCalls,
        0,
        reason: '本地就解不开的载荷不该发到后端',
      );
      expect(env.captureActive, isFalse);
    });

    testWidgets('解析请求抛错时，全局采集标记不会卡住', (tester) async {
      final env = _Env()..repository.error = StateError('socket closed');
      await tester.pumpWidget(env.host(_cagePicker()));

      await _press(tester, _cageButton);
      expect(env.captureActive, isTrue);

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(_hint(tester, _cageHint), '读取标签失败，请重试');
      expect(env.captureActive, isFalse);
    });

    testWidgets('后端返回业务错误时，全局采集标记不会卡住', (tester) async {
      final env = _Env()..repository.error = const ApiException('该标签还没有绑定笼位');
      await tester.pumpWidget(env.host(_cagePicker()));

      await _press(tester, _cageButton);
      expect(env.captureActive, isTrue);

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(
        _hint(tester, _cageHint),
        '该标签还没有绑定笼位',
        reason: '后端说得清的原因要原样转给用户，不要盖成通用文案',
      );
      expect(env.captureActive, isFalse);
    });

    testWidgets('读标签失败把原因写在按钮下方，不用会被弹层盖住的浮层', (tester) async {
      final env = _Env()..repository.error = const ApiException('标签校验没通过');
      await tester.pumpWidget(env.host(_cagePicker()));

      await _press(tester, _cageButton);
      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(find.byType(SnackBar), findsNothing);
      expect(find.byKey(_cageHint), findsOneWidget);
      expect(find.text('标签校验没通过'), findsOneWidget);

      final button = tester.getRect(find.byKey(_cageButton));
      final hint = tester.getRect(find.byKey(_cageHint));
      expect(
        hint.top,
        greaterThanOrEqualTo(button.bottom),
        reason: '提示要紧跟在用户刚点的那个按钮下面',
      );
      expect(hint.bottom, lessThanOrEqualTo(600));
      expect(find.byKey(_cageHint).hitTestable(), findsOneWidget);
    });

    testWidgets('读标签失败不会清掉已经选好的笼位', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);
      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();
      expect(picked.single.cageNumber, 'A-01');

      env.repository.error = const ApiException('网络不给力');
      await _press(tester, _cageButton);
      await env.nfc.tap(houseId: _houseId, cageId: 11);
      await tester.pumpAndSettle();

      expect(_hint(tester, _cageHint), '网络不给力');
      expect(
        picked.map((cage) => cage.cageNumber),
        ['A-01'],
        reason: '失败一次不该把上一轮选好的目标抹掉',
      );
      expect(env.captureActive, isFalse);
    });

    testWidgets('不能接收该兔的笼位被拦下并说清原因', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);
      await env.nfc.tap(houseId: _houseId, cageId: 12);
      await tester.pumpAndSettle();

      expect(_hint(tester, _cageHint), 'A-03 不能接收该兔');
      expect(picked, isEmpty);
    });

    testWidgets('别的兔舍的标签当场拦下，不拿去问后端', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);
      await env.nfc.tap(houseId: _otherHouseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(_hint(tester, _cageHint), '该标签属于其他兔舍，未选中');
      expect(
        env.repository.resolveCalls,
        0,
        reason: '跨兔舍的笼位 id 不该拿去问后端',
      );
      expect(picked, isEmpty);
      expect(env.captureActive, isFalse);
    });

    testWidgets('标签指向的笼位不在当前列表时，提示刷新重试', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);
      await env.nfc.tap(houseId: _houseId, cageId: 99);
      await tester.pumpAndSettle();

      expect(_hint(tester, _cageHint), '未在当前兔舍找到该笼位，请刷新后重试');
      expect(picked, isEmpty);
      expect(env.captureActive, isFalse);
    });

    testWidgets('设备没有 NFC 时先说明，不去订阅也不占用采集标记', (tester) async {
      final env = _Env()..hardware.available = false;
      await tester.pumpWidget(env.host(_cagePicker()));

      await _press(tester, _cageButton);

      expect(
        _hint(tester, _cageHint),
        '设备不支持NFC或NFC未开启，请改用下方地图或列表选择',
      );
      expect(env.hardware.checks, 1);
      expect(
        env.nfc.pendingIntentCalls,
        0,
        reason: '硬件都没有就不该去初始化采集通道',
      );
      expect(env.captureActive, isFalse);
      expect(find.text('等待碰标签…'), findsNothing);

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(env.repository.resolveCalls, 0);
    });

    testWidgets('等待碰标签期间按钮不可再按，一次碰只选中一次', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);

      expect(
        tester.widget<OutlinedButton>(find.byKey(_cageButton)).onPressed,
        isNull,
        reason: '已经在等标签了，再点一次只会重复订阅',
      );
      await tester.tap(find.byKey(_cageButton), warnIfMissed: false);
      await tester.pumpAndSettle();
      expect(env.nfc.pendingIntentCalls, 1);

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(env.repository.resolveCalls, 1);
      expect(picked, hasLength(1));
      expect(
        tester.widget<OutlinedButton>(find.byKey(_cageButton)).onPressed,
        isNotNull,
        reason: '一轮结束后要能再来一次',
      );
    });

    testWidgets('选中之后再碰标签不会二次改动选择', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);
      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      await env.nfc.tap(houseId: _houseId, cageId: 11);
      await tester.pumpAndSettle();

      expect(env.repository.resolveCalls, 1);
      expect(
        picked.map((cage) => cage.cageNumber),
        ['A-01'],
        reason: '采集停了之后手机再蹭到别的标签，不该改掉已经选好的目标',
      );
      expect(_hint(tester, _cageHint), '已选中 A-01');
    });
  });

  group('碰一下选兔只', () {
    testWidgets('碰一下选中笼里那一只后归还采集标记', (tester) async {
      final env = _Env();
      final picked = <List<Rabbit>>[];
      await tester.pumpWidget(env.host(_rabbitPicker(onSelected: picked.add)));

      await _press(tester, _rabbitButton);
      expect(env.captureActive, isTrue);

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(picked.single.map((rabbit) => rabbit.id), [103]);
      expect(_hint(tester, _rabbitHint), '已选择兔 #103');
      expect(env.captureActive, isFalse);
    });

    testWidgets('单选场景遇到同笼多只可选兔，让用户回列表挑', (tester) async {
      final env = _Env();
      final picked = <List<Rabbit>>[];
      await tester.pumpWidget(env.host(_rabbitPicker(onSelected: picked.add)));

      await _press(tester, _rabbitButton);
      await env.nfc.tap(houseId: _houseId, cageId: 12);
      await tester.pumpAndSettle();

      expect(_hint(tester, _rabbitHint), '该笼位有 2 只可选兔只，请在列表中选择');
      expect(picked, isEmpty, reason: '不能替用户猜是哪一只');
      expect(env.captureActive, isFalse);
    });

    testWidgets('批量场景一次把同笼的可选兔都加进来', (tester) async {
      final env = _Env();
      final picked = <List<Rabbit>>[];
      await tester.pumpWidget(
        env.host(_rabbitPicker(onSelected: picked.add, allowMultiple: true)),
      );

      await _press(tester, _rabbitButton);
      await env.nfc.tap(houseId: _houseId, cageId: 12);
      await tester.pumpAndSettle();

      expect(picked.single.map((rabbit) => rabbit.id), [101, 102]);
      expect(_hint(tester, _rabbitHint), '已选择兔 #101、#102');
      expect(env.captureActive, isFalse);
    });

    testWidgets('笼位里没有可选兔只时说清楚，不静默无反应', (tester) async {
      final env = _Env();
      final picked = <List<Rabbit>>[];
      await tester.pumpWidget(env.host(_rabbitPicker(onSelected: picked.add)));

      await _press(tester, _rabbitButton);
      await env.nfc.tap(houseId: _houseId, cageId: 11);
      await tester.pumpAndSettle();

      expect(_hint(tester, _rabbitHint), '该笼位没有当前可选的兔只');
      expect(picked, isEmpty);
      expect(env.captureActive, isFalse);
    });

    testWidgets('别的兔舍的标签不会选中同号笼里的兔', (tester) async {
      final env = _Env();
      final picked = <List<Rabbit>>[];
      await tester.pumpWidget(env.host(_rabbitPicker(onSelected: picked.add)));

      await _press(tester, _rabbitButton);
      await env.nfc.tap(houseId: _otherHouseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(_hint(tester, _rabbitHint), '该标签属于其他兔舍，未选择兔只');
      expect(env.repository.resolveCalls, 0);
      expect(picked, isEmpty);
      expect(env.captureActive, isFalse);
    });

    testWidgets('读兔只失败把原因留在按钮下方且不清掉已选', (tester) async {
      final env = _Env();
      final picked = <List<Rabbit>>[];
      await tester.pumpWidget(env.host(_rabbitPicker(onSelected: picked.add)));

      await _press(tester, _rabbitButton);
      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();
      expect(picked, hasLength(1));

      env.repository.error = const ApiException('标签已失效，请重新绑定');
      await _press(tester, _rabbitButton);
      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(find.byType(SnackBar), findsNothing);
      expect(_hint(tester, _rabbitHint), '标签已失效，请重新绑定');
      expect(picked, hasLength(1), reason: '失败不该把上一轮选好的兔抹掉');
      expect(env.captureActive, isFalse);
    });
  });

  // 下面这组钉的是采集中途收摊的两条路：组件被销毁、enabled 被置假。
  // 它们放在文件最后，因为 `_teardown()` 会在生命周期回调里抛异常（见
  // `_expectNoEscapedError` 的说明），半拆一半的元素会顺着 flutter_test 复用的
  // root element 流到下一条用例，把无辜的用例一起拖红。排在最后就只剥自己。
  group('采集中途收摊时的独占标记', () {
    testWidgets('采集途中界面被关掉，全局采集标记跟着归还', (tester) async {
      final env = _Env();
      await tester.pumpWidget(env.host(_cagePicker()));

      await _press(tester, _cageButton);
      expect(env.captureActive, isTrue);

      env.closeForm();
      await tester.pumpAndSettle();

      expect(find.byKey(_cageButton), findsNothing);
      expect(
        env.captureActive,
        isFalse,
        reason: '采集界面消失后标记还留在 true，全 App 后续的碰一下都会静默失灵',
      );

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(env.repository.resolveCalls, 0, reason: '订阅要跟着一起退掉');
      _expectNoEscapedError(tester, path: 'dispose');
    });

    testWidgets('提交一开始就收掉采集窗口并归还标记', (tester) async {
      final env = _Env();
      final picked = <Cage>[];
      await tester.pumpWidget(env.host(_cagePicker(onSelected: picked.add)));

      await _press(tester, _cageButton);
      expect(env.captureActive, isTrue);
      expect(find.text('等待碰标签…'), findsOneWidget);

      // 父级开始提交，把 enabled 置为 false。
      env.startSubmitting();
      await tester.pumpAndSettle();

      expect(
        env.captureActive,
        isFalse,
        reason: '提交开始时还开着的采集窗口必须收掉，标记不能被攥在手里',
      );

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(env.repository.resolveCalls, 0);
      expect(picked, isEmpty, reason: '提交开始后碰到的标签已经没有意义');
      _expectNoEscapedError(tester, path: 'didUpdateWidget');
    });

    testWidgets('提交开始时兔只采集窗口一起收掉并归还标记', (tester) async {
      final env = _Env();
      final picked = <List<Rabbit>>[];
      await tester.pumpWidget(env.host(_rabbitPicker(onSelected: picked.add)));

      await _press(tester, _rabbitButton);
      expect(env.captureActive, isTrue);

      env.startSubmitting();
      await tester.pumpAndSettle();

      expect(env.captureActive, isFalse);

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(env.repository.resolveCalls, 0);
      expect(picked, isEmpty);
      _expectNoEscapedError(tester, path: 'didUpdateWidget');
    });

    testWidgets('采集途中弹层被关掉，兔只采集也归还标记', (tester) async {
      final env = _Env();
      await tester.pumpWidget(env.host(_rabbitPicker()));

      await _press(tester, _rabbitButton);
      expect(env.captureActive, isTrue);

      env.closeForm();
      await tester.pumpAndSettle();

      expect(find.byKey(_rabbitButton), findsNothing);
      expect(env.captureActive, isFalse);

      await env.nfc.tap(houseId: _houseId, cageId: 10);
      await tester.pumpAndSettle();

      expect(env.repository.resolveCalls, 0);
      _expectNoEscapedError(tester, path: 'dispose');
    });
  });

  testWidgets('两个采集入口在 360x800、200% 字号下不塌', (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    // 关掉硬件，两个入口都会渲染各自最长的一段说明文案。
    final env = _Env()..hardware.available = false;
    await tester.pumpWidget(
      env.host(
        (enabled) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            _cagePickerWidget(enabled: enabled),
            const SizedBox(height: 12),
            _rabbitPickerWidget(enabled: enabled),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _press(tester, _cageButton);
    await _press(tester, _rabbitButton);

    expect(tester.takeException(), isNull);
    for (final key in const [
      _cageButton,
      _cageHint,
      _rabbitButton,
      _rabbitHint,
    ]) {
      final rect = tester.getRect(find.byKey(key));
      expect(rect.left, greaterThanOrEqualTo(0), reason: '$key 左侧被切掉');
      expect(rect.right, lessThanOrEqualTo(360), reason: '$key 溢出右边界');
      expect(rect.bottom, lessThanOrEqualTo(800), reason: '$key 掉出屏幕');
      expect(rect.height, greaterThan(0), reason: '$key 没有被布局出来');
    }
    expect(find.byKey(_cageButton).hitTestable(), findsOneWidget);
    expect(find.byKey(_rabbitButton).hitTestable(), findsOneWidget);
  });
}

// ---------------------------------------------------------------------------
// 夹具：取自四个调用点（换笼、配种、留种、批次加成员）的真实参数形状。
// ---------------------------------------------------------------------------

Cage _cage(
  int id,
  String number, {
  int rabbitCount = 0,
  String status = '0',
}) {
  return Cage(
    id: id,
    houseId: _houseId,
    cageNumber: number,
    status: status,
    rabbitCount: rabbitCount,
    isEnabled: true,
  );
}

final _cages = <Cage>[
  _cage(10, 'A-01'),
  _cage(11, 'A-02'),
  _cage(12, 'A-03', rabbitCount: 2, status: '1'),
];

Rabbit _rabbit(int id, int cageId) {
  return Rabbit(
    id: id,
    houseId: _houseId,
    cageId: cageId,
    motherId: null,
    type: '0',
    gender: '1',
    breed: '伊拉',
    arrivalMethod: '0',
    arrivalDate: null,
    weight: null,
    isActive: true,
  );
}

final _rabbits = <Rabbit>[
  _rabbit(101, 12),
  _rabbit(102, 12),
  _rabbit(103, 10),
];

/// 换笼的规则：只接收还空着的笼位，被拒时把笼号说出来。
_PickerBuilder _cagePicker({ValueChanged<Cage>? onSelected}) {
  return (enabled) => _cagePickerWidget(
        enabled: enabled,
        onSelected: onSelected,
      );
}

_PickerBuilder _rabbitPicker({
  ValueChanged<List<Rabbit>>? onSelected,
  bool allowMultiple = false,
}) {
  return (enabled) => _rabbitPickerWidget(
        enabled: enabled,
        onSelected: onSelected,
        allowMultiple: allowMultiple,
      );
}

Widget _cagePickerWidget({
  required bool enabled,
  ValueChanged<Cage>? onSelected,
}) {
  return NfcCagePicker(
    houseId: _houseId,
    cages: _cages,
    enabled: enabled,
    accepts: (cage) => cage.rabbitCount == 0,
    rejectReason: (cage) => '${cage.cageNumber} 不能接收该兔',
    onSelected: onSelected ?? (_) {},
  );
}

Widget _rabbitPickerWidget({
  required bool enabled,
  ValueChanged<List<Rabbit>>? onSelected,
  bool allowMultiple = false,
}) {
  return NfcRabbitPicker(
    houseId: _houseId,
    candidates: _rabbits,
    idleLabel: '碰一下选择种公兔',
    waitingLabel: '请靠近种公兔所在笼位的 NFC 标签',
    enabled: enabled,
    allowMultiple: allowMultiple,
    onSelected: onSelected ?? (_) {},
  );
}

// ---------------------------------------------------------------------------
// 测试台
// ---------------------------------------------------------------------------

/// 采集入口的构造器，参数就是调用点的 `enabled: !_saving`。
typedef _PickerBuilder = Widget Function(bool enabled);

class _HostState {
  const _HostState({this.enabled = true, this.visible = true});

  final bool enabled;
  final bool visible;
}

class _Env {
  _Env() {
    nfc = NfcHarness();
    container = ProviderContainer(
      overrides: [
        nfcHardwareServiceProvider.overrideWithValue(hardware),
        nfcRepositoryProvider.overrideWithValue(repository),
      ],
    );
    addTearDown(container.dispose);
  }

  late final NfcHarness nfc;
  late final ProviderContainer container;
  final hardware = _FakeNfcHardware();
  final repository = _FakeNfcRepository();
  final state = ValueNotifier<_HostState>(const _HostState());

  bool get captureActive => container.read(nfcCaptureActiveProvider);

  void startSubmitting() =>
      state.value = _HostState(enabled: false, visible: state.value.visible);

  void closeForm() =>
      state.value = _HostState(enabled: state.value.enabled, visible: false);

  Widget host(_PickerBuilder builder) {
    return UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        theme: buildAppTheme(),
        home: Scaffold(
          body: ValueListenableBuilder<_HostState>(
            valueListenable: state,
            builder: (_, value, __) => Padding(
              padding: const EdgeInsets.all(16),
              child:
                  value.visible ? builder(value.enabled) : const Text('表单已关闭'),
            ),
          ),
        ),
      ),
    );
  }
}

class _FakeNfcHardware extends NfcHardwareService {
  bool available = true;
  int checks = 0;

  @override
  Future<bool> isAvailable() async {
    checks++;
    return available;
  }
}

/// 标签绑定的是笼位，所以解析结果直接由载荷里的笼位号推出来，
/// 让「碰 12 号笼」和「后端答 12 号笼」保持一致。
class _FakeNfcRepository extends NfcRepository {
  _FakeNfcRepository() : super(ApiClient(SessionStore()));

  Object? error;
  int resolveCalls = 0;

  @override
  Future<NfcCageBinding> resolve({
    required int houseId,
    required String tagUid,
    required String payload,
  }) async {
    resolveCalls++;
    final failure = error;
    if (failure != null) {
      throw failure;
    }
    final target = NfcPayloadTarget.parse(payload);
    return NfcCageBinding(
      houseId: target.houseId,
      cageId: target.cageId,
      cageNumber: 'A-${target.cageId}',
      tagUid: tagUid,
      bindingStatus: 'BOUND',
    );
  }
}

/// 采集中途收摊时，不应该有异常漏到框架层。
///
/// 【当前是红的，是 nfc.dart 的真实缺陷，不要改这条断言】
/// `_teardown()`（nfc.dart:120 的 `flag?.state = false`）会从 `dispose()` 和
/// `didUpdateWidget()` 两条生命周期回调里被调到，而 Riverpod 在 debug 下
/// 禁止构建/卸载期修改 provider，会抛
/// `Tried to modify a provider while the widget tree was building.`
///
/// 独占标记本身确实已经归还（StateNotifier 先落值再通知监听者，
/// 上面的 captureActive 断言能证明），但真机 debug 包里用户只要在等标签时
/// 返回关掉弹层，就会报错。修的是 nfc.dart（把归还改成延后执行），
/// 不是把这条断言放水。
void _expectNoEscapedError(WidgetTester tester, {required String path}) {
  expect(
    tester.takeException(),
    isNull,
    reason: '$path 收摊时不该有异常漏出来；'
        '现在 _teardown() 直接在生命周期回调里写 nfcCaptureActiveProvider',
  );
}

Future<void> _press(WidgetTester tester, Key button) async {
  await tester.tap(find.byKey(button));
  await tester.pumpAndSettle();
}

String? _hint(WidgetTester tester, Key key) {
  final finder = find.byKey(key);
  if (finder.evaluate().isEmpty) {
    return null;
  }
  return tester.widget<Text>(finder).data;
}
