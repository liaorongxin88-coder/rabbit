import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/capture_scope.dart';
import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';
import 'package:rabbit_flutter/src/data/services/nfc/intents.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';

/// 「碰一下笼位标签选中点什么」的共用采集底座。
///
/// 标签绑定的永远是笼位，所以每个采集界面的前半段完全一样：确认硬件可用、抢下
/// 独占标记、订阅事件、解析载荷、拦掉别的兔舍。真正因人而异的只有最后一步——
/// 拿到笼位之后选谁。子类只实现 [onCageResolved]，返回停止采集时要显示的提示。
///
/// 独占标记（[nfcCaptureActiveProvider]）是这里唯一不能出错的东西：它一旦卡在
/// true，`app.dart` 的默认跳转会被永久吞掉，全 App 后续的碰一下都失灵，而且现场
/// 看不出任何异常。因此归还标记只走 [_teardown] 一个出口，并且由 `finally` 兜底。
abstract class _NfcCaptureState<W extends ConsumerStatefulWidget>
    extends ConsumerState<W> {
  StreamSubscription<NfcLaunchEvent>? _subscription;

  /// 独占标记的控制器提前取好：`ref` 在 dispose 里已不可用，
  /// 而此时恰恰是最需要归还它的时候。
  StateController<bool>? _captureFlag;
  var _listening = false;
  String? _hint;

  bool get isListening => _listening;

  String? get hint => _hint;

  int get houseId;

  /// 父级是否允许采集，通常是「没有正在提交」。
  bool get captureEnabled;

  String get waitingHint;

  String get unavailableHint;

  String get foreignHouseHint;

  String get failureHint;

  /// 标签已经解析到本兔舍的某个笼位，由子类决定选中什么。
  ///
  /// 返回值是停止采集后展示的提示；返回 null 表示不留提示。
  Future<String?> onCageResolved(NfcCageBinding binding);

  @override
  void didUpdateWidget(covariant W oldWidget) {
    super.didUpdateWidget(oldWidget);
    // 提交开始时父级会把 enabled 置为 false。此时还开着的采集窗口必须收掉：
    // 它等的那一下标签已经没有意义，独占标记却还攥在手里。
    // 这里不用 setState —— didUpdateWidget 之后框架必然重建。
    if (_listening && !captureEnabled) {
      _teardown(deferFlagRelease: true);
    }
  }

  @override
  void dispose() {
    _teardown(deferFlagRelease: true);
    super.dispose();
  }

  Future<void> startCapture() async {
    if (_listening || !captureEnabled) {
      return;
    }
    // 无硬件时先说明，不要让用户对着一个永远不会响的提示干等。
    // 写标签路径（hardware.dart 里的 writePayload）一直有这个检查，读取路径漏了。
    final available = await ref.read(nfcHardwareServiceProvider).isAvailable();
    if (!mounted) {
      return;
    }
    if (!available) {
      setState(() => _hint = unavailableHint);
      return;
    }
    final service = ref.read(nfcIntentServiceProvider);
    await service.initialize();
    if (!mounted) {
      return;
    }
    final flag = ref.read(nfcCaptureActiveProvider.notifier);
    flag.state = true;
    _captureFlag = flag;
    setState(() {
      _listening = true;
      _hint = waitingHint;
    });
    _subscription = service.events.listen(_handleEvent);
  }

  void stopCapture({String? hint}) {
    _teardown();
    if (!mounted) {
      return;
    }
    setState(() => _hint = hint);
  }

  /// 采集资源的唯一回收出口：退订、复位、归还独占标记。
  ///
  /// 先把字段置空再写 provider，这样即使写 provider 抛错（容器已销毁等），
  /// 本地状态也已经是「没有持有标记」，不会二次归还，也不会卡在监听态。
  ///
  /// [deferFlagRelease] 给 dispose 和 didUpdateWidget 用。这两个回调跑在
  /// 框架的构建与卸载锁里，此时写 provider 会被 Riverpod 的调试断言拦下
  /// （它要对 ProviderScope 调 markNeedsBuild，而那个元素正被锁着），
  /// 抛出 Tried to modify a provider while the widget tree was building。
  /// 只有 debug 构建会抛，但那正是开发和验收跑的构建，而「采集等待时
  /// 退出弹层」是现场天天会做的动作。本地状态仍然同步复位，
  /// 只把归还挪到锁释放之后。
  void _teardown({bool deferFlagRelease = false}) {
    _subscription?.cancel();
    _subscription = null;
    _listening = false;
    final flag = _captureFlag;
    _captureFlag = null;
    if (flag == null) {
      return;
    }
    if (!deferFlagRelease) {
      flag.state = false;
      return;
    }
    Future.microtask(() {
      // 容器可能在这一跑之前就没了（整个 ProviderScope 被拆掉），
      // 那时标记本身也不存在了，无需归还。
      if (flag.mounted) {
        flag.state = false;
      }
    });
  }

  Future<void> _handleEvent(NfcLaunchEvent event) async {
    String? hint;
    try {
      final target = NfcPayloadTarget.parse(event.payload);
      if (target.houseId != houseId) {
        // 跨兔舍的标签在本地就拦掉，不拿着别人兔舍的笼位 id 去问后端。
        hint = foreignHouseHint;
        return;
      }
      final binding = await ref.read(nfcRepositoryProvider).resolve(
            houseId: houseId,
            tagUid: event.tagUid,
            payload: event.payload,
          );
      if (!mounted) {
        return;
      }
      hint = await onCageResolved(binding);
    } catch (error) {
      hint = error is ApiException ? error.message : failureHint;
    } finally {
      // 无论走哪条路——正常选中、提示拒绝、解析失败、组件已销毁——
      // 都从这里收口，独占标记不会被留在 true。
      stopCapture(hint: hint);
    }
  }
}

/// 采集按钮加提示文案的统一排布，两个 picker 共用。
class _NfcCaptureButton extends StatelessWidget {
  const _NfcCaptureButton({
    required this.buttonKey,
    required this.hintKey,
    required this.label,
    required this.onPressed,
    required this.hint,
  });

  final Key buttonKey;
  final Key hintKey;
  final String label;
  final VoidCallback? onPressed;
  final String? hint;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        OutlinedButton.icon(
          key: buttonKey,
          onPressed: onPressed,
          icon: const Icon(Icons.nfc),
          label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
        ),
        if (hint != null) ...[
          const SizedBox(height: 6),
          Text(
            hint!,
            key: hintKey,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ],
    );
  }
}

/// 通过笼位 NFC 标签选中该笼里可用的兔只。
///
/// NFC 标签绑定的是笼位而不是兔只，因此先验证标签所属兔舍，再按笼位从当前
/// 表单候选中筛选。单选场景要求恰好一只候选兔，批量场景可一次加入同笼兔只。
class NfcRabbitPicker extends ConsumerStatefulWidget {
  const NfcRabbitPicker({
    super.key,
    required this.houseId,
    required this.candidates,
    required this.idleLabel,
    required this.waitingLabel,
    required this.onSelected,
    this.enabled = true,
    this.allowMultiple = false,
  });

  final int houseId;
  final List<Rabbit> candidates;
  final String idleLabel;
  final String waitingLabel;
  final ValueChanged<List<Rabbit>> onSelected;
  final bool enabled;
  final bool allowMultiple;

  @override
  ConsumerState<NfcRabbitPicker> createState() => _NfcRabbitPickerState();
}

class _NfcRabbitPickerState extends _NfcCaptureState<NfcRabbitPicker> {
  @override
  int get houseId => widget.houseId;

  @override
  bool get captureEnabled => widget.enabled;

  @override
  String get waitingHint => widget.waitingLabel;

  @override
  String get unavailableHint => '设备不支持NFC或NFC未开启，请在下方列表中选择';

  @override
  String get foreignHouseHint => '该标签属于其他兔舍，未选择兔只';

  @override
  String get failureHint => '读取 NFC 标签失败，请重试';

  @override
  Future<String?> onCageResolved(NfcCageBinding binding) async {
    final matches = widget.candidates
        .where((rabbit) => rabbit.cageId == binding.cageId)
        .toList(growable: false);
    if (matches.isEmpty) {
      return '该笼位没有当前可选的兔只';
    }
    if (!widget.allowMultiple && matches.length != 1) {
      return '该笼位有 ${matches.length} 只可选兔只，请在列表中选择';
    }
    widget.onSelected(matches);
    final rabbitLabels = matches.map((rabbit) => '#${rabbit.id}').join('、');
    return '已选择兔 $rabbitLabels';
  }

  @override
  Widget build(BuildContext context) {
    return _NfcCaptureButton(
      buttonKey: const ValueKey('nfc-rabbit-picker-button'),
      hintKey: const ValueKey('nfc-rabbit-picker-hint'),
      label: isListening ? widget.waitingLabel : widget.idleLabel,
      onPressed: !widget.enabled || isListening
          ? null
          : () => unawaited(startCapture()),
      hint: hint,
    );
  }
}

/// 碰一下目标笼位的 NFC 标签直接选中它。
///
/// 现场的真实动作是「手里拎着兔、手机碰笼子」，在一屏笼位号里找到那一行才是不自然的。
///
/// 「哪些笼能选」没有统一答案：换笼要放行可对调的满笼，出栏、投喂各有各的规则。
/// 因此由调用方通过 [accepts] 和 [rejectReason] 自己定义，这个组件只管把笼位交出来。
class NfcCagePicker extends ConsumerStatefulWidget {
  const NfcCagePicker({
    super.key,
    required this.houseId,
    required this.cages,
    required this.accepts,
    required this.rejectReason,
    required this.onSelected,
    this.cageLabel = defaultNfcCageLabel,
    this.idleLabel = '碰一下目标笼位',
    this.waitingLabel = '等待碰标签…',
    this.waitingHint = '请将手机靠近目标笼位的 NFC 标签',
    this.unavailableLabel = '设备不支持NFC或NFC未开启，请改用下方地图或列表选择',
    this.enabled = true,
  });

  final int houseId;
  final List<Cage> cages;

  /// 该笼位是否可选。
  final bool Function(Cage cage) accepts;

  /// [accepts] 为 false 时展示的原因。
  final String Function(Cage cage) rejectReason;

  final ValueChanged<Cage> onSelected;

  /// 提示语里怎么称呼这个笼位。
  final String Function(Cage cage) cageLabel;

  /// 采集中的按钮文案。
  final String idleLabel;
  final String waitingLabel;

  /// 采集中按钮下方的说明文案，比按钮文案长，讲清楚该把手机贴到哪。
  final String waitingHint;

  final String unavailableLabel;
  final bool enabled;

  @override
  ConsumerState<NfcCagePicker> createState() => _NfcCagePickerState();
}

String defaultNfcCageLabel(Cage cage) =>
    cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;

class _NfcCagePickerState extends _NfcCaptureState<NfcCagePicker> {
  @override
  int get houseId => widget.houseId;

  @override
  bool get captureEnabled => widget.enabled;

  @override
  String get waitingHint => widget.waitingHint;

  @override
  String get unavailableHint => widget.unavailableLabel;

  @override
  String get foreignHouseHint => '该标签属于其他兔舍，未选中';

  @override
  String get failureHint => '读取标签失败，请重试';

  @override
  Future<String?> onCageResolved(NfcCageBinding binding) async {
    final cage = _cageById(binding.cageId);
    if (cage == null) {
      return '未在当前兔舍找到该笼位，请刷新后重试';
    }
    if (!widget.accepts(cage)) {
      return widget.rejectReason(cage);
    }
    widget.onSelected(cage);
    return '已选中 ${widget.cageLabel(cage)}';
  }

  Cage? _cageById(int cageId) {
    for (final cage in widget.cages) {
      if (cage.id == cageId) {
        return cage;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return _NfcCaptureButton(
      buttonKey: const ValueKey('nfc-cage-picker-button'),
      hintKey: const ValueKey('nfc-cage-picker-hint'),
      label: isListening ? widget.waitingLabel : widget.idleLabel,
      onPressed: !widget.enabled || isListening
          ? null
          : () => unawaited(startCapture()),
      hint: hint,
    );
  }
}
