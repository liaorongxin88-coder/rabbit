import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/nfc_capture_scope.dart';
import 'package:rabbit_flutter/src/data/services/nfc/nfc_intent_service.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/cage_layout.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/widgets/cage_map_view.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

Future<void> showRabbitMoveCageSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
  required List<Cage> cages,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _MoveCageSheet(
      houseId: houseId,
      rabbit: rabbit,
      cages: cages,
    ),
  );
}

class _MoveCageSheet extends ConsumerStatefulWidget {
  const _MoveCageSheet({
    required this.houseId,
    required this.rabbit,
    required this.cages,
  });

  final int houseId;
  final Rabbit rabbit;
  final List<Cage> cages;

  @override
  ConsumerState<_MoveCageSheet> createState() => _MoveCageSheetState();
}

/// 选目标笼三条路：碰 NFC、在地图上点、直接输编号。
/// 现场三种习惯都存在，只给一条路就总有人被卡住。
enum _TargetPickerMode { map, list }

class _MoveCageSheetState extends ConsumerState<_MoveCageSheet> {
  static const _rowBatchSize = 4;

  final _searchController = TextEditingController();
  StreamSubscription<NfcLaunchEvent>? _nfcSubscription;
  /// 独占标记的控制器提前取好：`ref` 在 dispose 里已不可用，
  /// 而此时恰恰是最需要归还它的时候。
  StateController<bool>? _captureFlag;
  late int _selectedCageId;
  var _keyword = '';
  var _saving = false;
  var _nfcListening = false;
  var _pickerMode = _TargetPickerMode.map;
  var _visibleRowCount = _rowBatchSize;
  String? _nfcHint;
  String? _numberHint;

  Rabbit get _rabbit => widget.rabbit;

  Cage? get _currentCage {
    for (final cage in widget.cages) {
      if (cage.id == _rabbit.cageId) {
        return cage;
      }
    }
    return null;
  }

  @override
  void initState() {
    super.initState();
    _selectedCageId = _rabbit.cageId;
    _searchController.addListener(() {
      final next = _searchController.text.trim();
      if (next != _keyword) {
        setState(() => _keyword = next);
        _selectByExactNumber(next);
      }
    });
  }

  @override
  void dispose() {
    _nfcSubscription?.cancel();
    _nfcSubscription = null;
    _releaseCaptureFlag();
    _searchController.dispose();
    super.dispose();
  }

  void _releaseCaptureFlag() {
    _captureFlag?.state = false;
    _captureFlag = null;
  }

  /// 目标笼位是否可选。
  ///
  /// 不能直接用 [Cage.canAcceptRabbit]：那个判断只知道“能不能再塞一只进去”，
  /// 而种兔 / 后备兔遇到已占用的非商品兔笼时走的是两笼对调，目标笼满反而是对调的前提。
  bool _acceptsTarget(Cage cage) {
    if (!cage.isEnabled) {
      return false;
    }
    if (cage.id == _rabbit.cageId) {
      return true;
    }
    if (cage.canAcceptRabbit(_rabbit.type, exceptRabbitCageId: _rabbit.cageId)) {
      return true;
    }
    // 商品兔没有对调路径：它们是多只共笼的，“两笼互换”对它不成立。
    if (_rabbit.type == '2') {
      return false;
    }
    return cage.status == '1' || cage.status == '2';
  }

  /// 真的会发生两笼对调的目标。
  ///
  /// 必须同时限定目标是种兔/后备兔笼：商品兔笼没有对调路径（后端直接拒）。
  /// 旧定义漏了这一条，列表模式下因为不可选的笼根本不显示而没暴露，
  /// 地图会把不可选的笼也画出来，于是商品兔笼上错贴了一个「对调」。
  bool _isSwapTarget(Cage cage) {
    return cage.id != _rabbit.cageId &&
        cage.rabbitCount > 0 &&
        _rabbit.type != '2' &&
        (cage.status == '1' || cage.status == '2');
  }

  List<Cage> get _targetCages {
    final keyword = _keyword.toLowerCase();
    return widget.cages.where((cage) {
      if (!_acceptsTarget(cage)) {
        return false;
      }
      if (keyword.isEmpty) {
        return true;
      }
      final number = cage.cageNumber.toLowerCase();
      return number.contains(keyword) || '${cage.id}'.contains(keyword);
    }).toList()
      ..sort((a, b) {
        if (a.id == _rabbit.cageId) {
          return -1;
        }
        if (b.id == _rabbit.cageId) {
          return 1;
        }
        return a.cageNumber.compareTo(b.cageNumber);
      });
  }

  String _cageLabel(Cage cage) {
    final name = cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;
    if (cage.id == _rabbit.cageId) {
      return '$name（当前）';
    }
    if (cage.rabbitCount > 0) {
      return '$name · ${cage.usageLabel} · ${cage.rabbitCount} 只';
    }
    return '$name · 空笼';
  }

  /// 碰一下目标笼位的 NFC 标签直接选中它。
  ///
  /// 现场的真实动作是“手里拎着兔、手机碰笼子”，在一屏笼位号里找到那一行才是不自然的。
  Future<void> _startNfcCapture() async {
    if (_nfcListening || _saving) {
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
      _nfcListening = true;
      _nfcHint = '请将手机靠近目标笼位的 NFC 标签';
    });
    _nfcSubscription = service.events.listen(_onNfcEvent);
  }

  void _stopNfcCapture({String? hint}) {
    _nfcSubscription?.cancel();
    _nfcSubscription = null;
    _releaseCaptureFlag();
    if (!mounted) {
      return;
    }
    setState(() {
      _nfcListening = false;
      _nfcHint = hint;
    });
  }

  Future<void> _onNfcEvent(NfcLaunchEvent event) async {
    try {
      final target = NfcPayloadTarget.parse(event.payload);
      if (target.houseId != widget.houseId) {
        _stopNfcCapture(hint: '该标签属于其它兔舍，未选中');
        return;
      }
      final binding = await ref.read(nfcRepositoryProvider).resolve(
            houseId: widget.houseId,
            tagUid: event.tagUid,
            payload: event.payload,
          );
      if (!mounted) {
        return;
      }
      final cage = _cageById(binding.cageId);
      if (cage == null) {
        _stopNfcCapture(hint: '未在当前兔舍找到该笼位，请刷新后重试');
        return;
      }
      if (!_acceptsTarget(cage)) {
        _stopNfcCapture(hint: '${_cageLabel(cage)} 不能接收该兔');
        return;
      }
      setState(() => _selectedCageId = cage.id);
      _stopNfcCapture(hint: '已选中 ${_cageLabel(cage)}');
    } catch (error) {
      if (mounted) {
        _stopNfcCapture(
          hint: error is ApiException ? error.message : '读取标签失败，请重试',
        );
      }
    }
  }

  /// 输入的编号能唯一对上时直接选中。
  ///
  /// 只过滤不选中的话，用户输完完整编号还要再点一下，而他输完整编号时意图已经很明确了。
  void _selectByExactNumber(String input) {
    if (input.isEmpty) {
      if (_numberHint != null) {
        setState(() => _numberHint = null);
      }
      return;
    }
    final normalized = input.toLowerCase();
    final hits = widget.cages
        .where((cage) =>
            cage.cageNumber.toLowerCase() == normalized ||
            '${cage.id}' == normalized)
        .toList();
    if (hits.length != 1) {
      // 模糊匹配不自作主张选中，交给用户点。
      if (_numberHint != null) {
        setState(() => _numberHint = null);
      }
      return;
    }
    final cage = hits.single;
    if (!_acceptsTarget(cage)) {
      setState(() => _numberHint = '${_cageLabel(cage)} 不能接收该兔');
      return;
    }
    setState(() {
      _selectedCageId = cage.id;
      _numberHint = '已选中 ${_cageLabel(cage)}';
    });
  }

  /// 底部常驻的选中说明。
  String get _selectionSummary {
    if (_selectedCageId == _rabbit.cageId) {
      return '尚未选择目标笼位：可碰标签、在地图上点，或直接输入编号。';
    }
    final cage = _cageById(_selectedCageId);
    if (cage == null) {
      return '已选目标笼位 #$_selectedCageId';
    }
    if (_isSwapTarget(cage)) {
      return '目标：${_cageLabel(cage)}，将与笼内兔只对调';
    }
    return '目标：${_cageLabel(cage)}';
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
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final availableHeight = (mediaQuery.size.height - keyboardInset).clamp(
      0.0,
      mediaQuery.size.height,
    );
    final targets = _targetCages;
    final current = _currentCage;

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: availableHeight * 0.92),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: CustomScrollView(
                  key: const ValueKey('rabbit-move-cage-scroll'),
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  slivers: [
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '换笼位',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style:
                                        Theme.of(context).textTheme.titleLarge,
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '兔 #${_rabbit.id} · ${_rabbit.typeLabel} · ${_rabbit.genderLabel}',
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style:
                                        Theme.of(context).textTheme.bodyMedium,
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              tooltip: '关闭',
                              onPressed:
                                  _saving ? null : () => Navigator.pop(context),
                              icon: const Icon(Icons.close),
                            ),
                          ],
                        ),
                      ),
                    ),
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 20),
                        child: _InfoBox(
                          text: current == null
                              ? '当前笼位 #${_rabbit.cageId}'
                              : '当前笼位：${_cageLabel(current)}',
                        ),
                      ),
                    ),
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(20, 12, 20, 4),
                        child: OutlinedButton.icon(
                          key: const ValueKey('rabbit-move-cage-nfc'),
                          onPressed: _saving || _nfcListening
                              ? null
                              : () => unawaited(_startNfcCapture()),
                          icon: const Icon(Icons.nfc),
                          label: Text(_nfcListening ? '等待碰标签…' : '碰一下目标笼位'),
                        ),
                      ),
                    ),
                    if (_nfcHint != null)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
                          child: Text(
                            _nfcHint!,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ),
                      ),
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
                        child: TextField(
                          key: const ValueKey('rabbit-move-cage-search'),
                          controller: _searchController,
                          decoration: const InputDecoration(
                            hintText: '输入笼位编号，完整对上就直接选中',
                            prefixIcon: Icon(Icons.keyboard_alt_outlined),
                          ),
                        ),
                      ),
                    ),
                    if (_numberHint != null)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(20, 6, 20, 0),
                          child: Text(
                            _numberHint!,
                            key: const ValueKey('rabbit-move-cage-number-hint'),
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ),
                      ),
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
                        child: Row(
                          children: [
                            ChoiceChip(
                              key: const ValueKey('rabbit-move-cage-view-map'),
                              label: const Text('分层地图'),
                              selected: _pickerMode == _TargetPickerMode.map,
                              onSelected: (_) => setState(
                                () => _pickerMode = _TargetPickerMode.map,
                              ),
                            ),
                            const SizedBox(width: 8),
                            ChoiceChip(
                              key: const ValueKey('rabbit-move-cage-view-list'),
                              label: const Text('列表'),
                              selected: _pickerMode == _TargetPickerMode.list,
                              onSelected: (_) => setState(
                                () => _pickerMode = _TargetPickerMode.list,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    if (targets.isEmpty)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
                          child: Text(
                            '没有可用的目标笼位。商品兔只能进空笼或未满的商品兔笼；'
                            '种兔、后备兔可进空笼，或与已占用的非商品兔笼对调。',
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ),
                      )
                    else if (_pickerMode == _TargetPickerMode.map)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Padding(
                                padding: EdgeInsets.fromLTRB(8, 0, 8, 8),
                                child: CageAttentionLegend(),
                              ),
                              CageMapView(
                                layout: CageLayout.fromCages(widget.cages),
                                // 没选之前不能把兔子自己的笼画成「已选中」，
                                // 否则底部写着尚未选择、地图上却打着对勾。
                                selectedCageId: _selectedCageId == _rabbit.cageId
                                    ? null
                                    : _selectedCageId,
                                selectableCage: (cage) =>
                                    !_saving && _acceptsTarget(cage),
                                // 会发生对调的格子先标出来，别让用户提交后才发现自己动了两只兔。
                                cellNote: (cage) => cage.id == _rabbit.cageId
                                    ? '当前'
                                    : _isSwapTarget(cage)
                                        ? '对调'
                                        : null,
                                isMatch: _keyword.isEmpty
                                    ? null
                                    : (cage) => cage.cageNumber
                                            .toLowerCase()
                                            .contains(_keyword.toLowerCase()) ||
                                        '${cage.id}'.contains(_keyword),
                                visibleRowLimit: _visibleRowCount,
                                onShowMoreRows: () => setState(
                                  () => _visibleRowCount += _rowBatchSize,
                                ),
                                onTapCage: (cage) => setState(() {
                                  _selectedCageId = cage.id;
                                  _numberHint = null;
                                }),
                              ),
                            ],
                          ),
                        ),
                      )
                    else
                      SliverPadding(
                        padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                        sliver: SliverList.builder(
                          itemCount: targets.length,
                          itemBuilder: (context, index) {
                            final cage = targets[index];
                            final selected = cage.id == _selectedCageId;
                            return RadioListTile<int>(
                              key: ValueKey(
                                  'rabbit-move-cage-target-${cage.id}'),
                              value: cage.id,
                              groupValue: _selectedCageId,
                              onChanged: _saving
                                  ? null
                                  : (value) => setState(
                                        () => _selectedCageId =
                                            value ?? _selectedCageId,
                                      ),
                              title: Text(_cageLabel(cage)),
                              subtitle: Text(
                                _isSwapTarget(cage)
                                    ? '${cage.usageLabel} · 将与笼内兔只对调'
                                    : cage.usageLabel,
                              ),
                              selected: selected,
                            );
                          },
                        ),
                      ),
                  ],
                ),
              ),
              DecoratedBox(
                key: const ValueKey('rabbit-move-cage-actions'),
                decoration: BoxDecoration(
                  border: Border(
                    top: BorderSide(color: AppPalette.of(context).line),
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 10, 20, 16),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // 选中结果跟着按钮走，而不是留在顶上：在地图下方点完一个格子后，
                      // 顶部提示已经滚出屏外，用户根本看不到自己选了谁、会不会发生对调。
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Text(
                          _selectionSummary,
                          key: const ValueKey('rabbit-move-cage-selection'),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ),
                      Row(
                        children: [
                          Expanded(
                            child: OutlinedButton(
                              onPressed: _saving
                                  ? null
                                  : () => Navigator.pop(context),
                              child: const Text('取消'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: ElevatedButton(
                              key: const ValueKey('rabbit-move-cage-submit'),
                              onPressed:
                                  _saving || _selectedCageId == _rabbit.cageId
                                      ? null
                                      : _save,
                              child: _saving
                                  ? const SizedBox.square(
                                      dimension: 20,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white,
                                      ),
                                    )
                                  : const Text('确认换笼'),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _save() async {
    _stopNfcCapture();
    setState(() => _saving = true);
    try {
      final result = await ref.read(rabbitRepositoryProvider).transferRabbitCage(
            houseId: widget.houseId,
            rabbitId: _rabbit.id,
            targetCageId: _selectedCageId,
          );
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(allActiveHouseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        final target = _cageById(_selectedCageId);
        final targetLabel =
            target == null ? '#$_selectedCageId' : _cageLabel(target);
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              result.isSwap
                  ? '兔 #${_rabbit.id} 已与兔 #${result.swappedRabbitId} 对调笼位'
                  : '兔 #${_rabbit.id} 已换至 $targetLabel',
            ),
          ),
        );
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      final message = error is ApiException ? error.message : error.toString();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }
}

class _InfoBox extends StatelessWidget {
  const _InfoBox({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Text(text, style: Theme.of(context).textTheme.bodyMedium),
    );
  }
}
