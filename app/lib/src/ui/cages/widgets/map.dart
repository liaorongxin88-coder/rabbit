import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/attention.dart';
import 'package:rabbit_flutter/src/domain/cages/layout.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';

/// 笼位状态色板。刻意**不给任何状态用主蓝**：地图同时充当选择器，
/// 蓝色只表示「我选中了这一格」，否则选中态和「已满」会混成一片。
///
/// 每个状态都同时带图标和文字，不单靠色相区分，色觉障碍下也能读。
class CageAttentionStyle {
  const CageAttentionStyle({
    required this.fill,
    required this.border,
    required this.foreground,
    required this.icon,
  });

  final Color fill;
  final Color border;
  final Color foreground;

  /// 图标是颜色之外的第二道区分：可加/不可加/停用/待办/异常各不相同。
  final IconData icon;

  static CageAttentionStyle of(CageAttention attention, AppPalette palette) {
    switch (attention) {
      case CageAttention.alert:
        return CageAttentionStyle(
          fill: palette.dangerSoft,
          border: palette.danger,
          foreground: palette.danger,
          icon: Icons.error_outline,
        );
      case CageAttention.disabled:
        return CageAttentionStyle(
          fill: palette.surfaceSubtle,
          border: palette.line,
          foreground: palette.muted,
          icon: Icons.block,
        );
      case CageAttention.needsFeeding:
        return CageAttentionStyle(
          fill: palette.warningSoft,
          border: palette.warning,
          foreground: palette.warning,
          icon: Icons.schedule,
        );
      case CageAttention.full:
        return CageAttentionStyle(
          fill: palette.surface,
          border: palette.line,
          foreground: palette.text,
          icon: Icons.remove_circle_outline,
        );
      case CageAttention.vacancy:
        return CageAttentionStyle(
          fill: palette.successSoft,
          border: palette.success,
          foreground: palette.success,
          icon: Icons.add_circle_outline,
        );
    }
  }
}

/// 分层笼位地图：排 → 层 → 位。
///
/// 同一个组件既用于笼位管理，也用于换笼选目标笼，靠 [selectableCage]
/// 和 [selectedCageId] 切换语义，避免两处各画一套地图后慢慢长歪。
/// 分层笼位地图。
///
/// **层是切换出来的空间，不是叠在一起的格子。** 现场的多层笼是错位的阶梯，
/// 人站在某一层前面时眼里只有这一层的那几排；把三层画成剖面图看着信息全，
/// 实际找笼时对不上眼前的架子。所以这里一次只画一层，层之间用切换器换。
///
/// 一排是双面笼架，位号绕着架子走，所以一排折成两行、回程那行反着排
/// （见 [CageLayout]）。
class CageMapView extends StatefulWidget {
  const CageMapView({
    super.key,
    required this.layout,
    required this.onTapCage,
    this.selectedCageId,
    this.isMatch,
    this.selectableCage,
    this.cellNote,
    this.statusLabel,
    this.visibleRowLimit,
    this.onShowMoreRows,
    this.rowTrailingBuilder,
  });

  final CageLayout layout;
  final ValueChanged<Cage> onTapCage;

  /// 选中态（换笼选目标笼时用），主蓝加粗边框 + 对勾。
  /// 选中的笼在别的层时会自动切过去，否则用户会看到「已选中」却找不到那一格。
  final int? selectedCageId;

  /// 搜索/筛选命中判定。不命中的格子压暗但保留位置，
  /// 这样用户仍能看出「它在这一排的第几位」。
  final bool Function(Cage cage)? isMatch;

  /// 该格是否可选；返回 false 时不可点。
  final bool Function(Cage cage)? selectableCage;

  /// 格子右下角的极短标注，例如换笼时的「对调」。
  final String? Function(Cage cage)? cellNote;

  /// 笼位管理中的母兔生产阶段；其它选择场景仍显示在栏数。
  final String Function(Cage cage)? statusLabel;

  /// 只渲染当前层的前 N 排，避免大兔舍一次铺几千个格子。
  final int? visibleRowLimit;
  final VoidCallback? onShowMoreRows;

  /// 排标题右侧的操作位（例如整排出库）。
  final Widget? Function(CageMapRow row)? rowTrailingBuilder;

  @override
  State<CageMapView> createState() => _CageMapViewState();
}

class _CageMapViewState extends State<CageMapView> {
  int? _activeLayer;

  @override
  void initState() {
    super.initState();
    _activeLayer = _layerOfSelection() ?? _firstLayer();
  }

  @override
  void didUpdateWidget(CageMapView oldWidget) {
    super.didUpdateWidget(oldWidget);
    // 换笼时可以靠碰标签或输入编号选中一个笼，它可能不在当前层。
    // 不跟着切层的话，用户看到的是「已选中 B7」加一屏没有高亮的格子。
    if (widget.selectedCageId != oldWidget.selectedCageId) {
      final layer = _layerOfSelection();
      if (layer != null && layer != _activeLayer) {
        _activeLayer = layer;
      }
    }
    if (!widget.layout.layers.any((l) => l.layerIndex == _activeLayer)) {
      _activeLayer = _layerOfSelection() ?? _firstLayer();
    }
  }

  int? _firstLayer() => widget.layout.layers.isEmpty
      ? null
      : widget.layout.layers.first.layerIndex;

  int? _layerOfSelection() {
    final selected = widget.selectedCageId;
    if (selected == null) return null;
    for (final layer in widget.layout.layers) {
      if (layer.cages.any((cage) => cage.id == selected)) {
        return layer.layerIndex;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final layers = widget.layout.layers;
    final active =
        layers.where((l) => l.layerIndex == _activeLayer).firstOrNull ??
            (layers.isEmpty ? null : layers.first);

    final rows = active?.rows ?? const <CageMapRow>[];
    final limit = widget.visibleRowLimit ?? rows.length;
    final visibleRows = rows.take(limit).toList();
    final hiddenRowCount = rows.length - visibleRows.length;

    return Column(
      key: const ValueKey('cage-map'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (layers.length > 1) ...[
          _LayerSwitcher(
            layers: layers,
            activeLayer: active?.layerIndex,
            onSelect: (index) => setState(() => _activeLayer = index),
          ),
          const SizedBox(height: 10),
        ],
        for (final row in visibleRows) ...[
          _CageMapRowSection(
            row: row,
            onTapCage: widget.onTapCage,
            selectedCageId: widget.selectedCageId,
            isMatch: widget.isMatch,
            selectableCage: widget.selectableCage,
            cellNote: widget.cellNote,
            statusLabel: widget.statusLabel,
            trailing: widget.rowTrailingBuilder?.call(row),
          ),
          const SizedBox(height: 12),
        ],
        if (hiddenRowCount > 0)
          Align(
            alignment: Alignment.center,
            child: TextButton(
              key: const ValueKey('cage-map-more-rows'),
              onPressed: widget.onShowMoreRows,
              child: Text('显示更多排（还有 $hiddenRowCount 排）'),
            ),
          ),
        if (widget.layout.unplaced.isNotEmpty)
          _UnplacedCages(
            cages: widget.layout.unplaced,
            onTapCage: widget.onTapCage,
            selectedCageId: widget.selectedCageId,
            isMatch: widget.isMatch,
            selectableCage: widget.selectableCage,
            cellNote: widget.cellNote,
            statusLabel: widget.statusLabel,
            palette: palette,
          ),
      ],
    );
  }
}

/// 层切换器。
///
/// 切层会把别的层整个藏起来，所以每个层签上带该层「要处理的笼」的数量
/// （异常 + 待投喂）。否则站在 1 层永远不知道 3 层有笼子等着喂。
class _LayerSwitcher extends StatelessWidget {
  const _LayerSwitcher({
    required this.layers,
    required this.activeLayer,
    required this.onSelect,
  });

  final List<CageMapLayer> layers;
  final int? activeLayer;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Semantics(
      container: true,
      label: '楼层切换，共 ${layers.length} 层',
      child: SingleChildScrollView(
        key: const ValueKey('cage-map-layer-switcher'),
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (final layer in layers)
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: _LayerChip(
                  layer: layer,
                  selected: layer.layerIndex == activeLayer,
                  onTap: () => onSelect(layer.layerIndex),
                  palette: palette,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _LayerChip extends StatelessWidget {
  const _LayerChip({
    required this.layer,
    required this.selected,
    required this.onTap,
    required this.palette,
  });

  final CageMapLayer layer;
  final bool selected;
  final VoidCallback onTap;
  final AppPalette palette;

  @override
  Widget build(BuildContext context) {
    final todo = layer.countAttention(CageAttention.alert) +
        layer.countAttention(CageAttention.needsFeeding);
    final label = '${layer.layerIndex}层';

    return Semantics(
      container: true,
      button: true,
      selected: selected,
      label: todo > 0 ? '$label，$todo 个笼要处理' : label,
      child: Material(
        color: selected ? palette.primarySoft : palette.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: BorderSide(
            color: selected ? palette.primary : palette.line,
            width: selected ? 2 : 1,
          ),
        ),
        child: InkWell(
          key: ValueKey('cage-map-layer-${layer.layerIndex}'),
          onTap: onTap,
          borderRadius: BorderRadius.circular(8),
          child: ConstrainedBox(
            // 48 是可点区域下限，切层是这张图上最高频的操作。
            constraints: const BoxConstraints(minHeight: 48, minWidth: 64),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    label,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: selected ? palette.primary : palette.text,
                    ),
                  ),
                  if (todo > 0) ...[
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: palette.warningSoft,
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        '$todo',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: palette.warning,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CageMapRowSection extends StatelessWidget {
  const _CageMapRowSection({
    required this.row,
    required this.onTapCage,
    required this.selectedCageId,
    required this.isMatch,
    required this.selectableCage,
    required this.cellNote,
    required this.statusLabel,
    required this.trailing,
  });

  final CageMapRow row;
  final ValueChanged<Cage> onTapCage;
  final int? selectedCageId;
  final bool Function(Cage cage)? isMatch;
  final bool Function(Cage cage)? selectableCage;
  final String? Function(Cage cage)? cellNote;
  final String Function(Cage cage)? statusLabel;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final textScale = MediaQuery.textScalerOf(context).scale(10) / 10;
    // 字号放大时格子必须跟着长，否则 200% 下数字会被裁掉。
    // 带标注（换笼时的「对调」）的格子多一行文字，真机上正是这一行把 56 的方格撑出 2px。
    final base = cellNote == null ? 56.0 : 68.0;
    final cellExtent = (base * textScale).clamp(base, 128.0);

    final vacancy = row.countAttention(CageAttention.vacancy);
    final feeding = row.countAttention(CageAttention.needsFeeding);
    final alert = row.countAttention(CageAttention.alert);

    return Container(
      key: ValueKey('cage-map-row-${row.rowCode}'),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      '${row.rowCode} 排',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w700,
                            color: palette.text,
                          ),
                    ),
                    Text(
                      '${row.cages.length} 笼',
                      style: TextStyle(color: palette.muted, fontSize: 12),
                    ),
                    if (vacancy > 0)
                      _RowMetric(
                        label: '空位 $vacancy',
                        color: palette.success,
                      ),
                    if (feeding > 0)
                      _RowMetric(
                        label: '待投喂 $feeding',
                        color: palette.warning,
                      ),
                    if (alert > 0)
                      _RowMetric(label: '异常 $alert', color: palette.danger),
                  ],
                ),
              ),
              if (trailing != null) trailing!,
            ],
          ),
          const SizedBox(height: 10),
          SingleChildScrollView(
            key: ValueKey('cage-map-row-scroll-${row.rowCode}'),
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                // 一排就是一条线，位号从左往右递增。
                for (final cell in row.cells)
                  Padding(
                    padding: const EdgeInsets.only(right: 6),
                    child: SizedBox(
                      width: cellExtent,
                      height: cellExtent,
                      child: cell.cage == null
                          ? _EmptySlot(positionIndex: cell.positionIndex)
                          : _CageMapCellTile(
                              cage: cell.cage!,
                              positionIndex: cell.positionIndex,
                              onTap: onTapCage,
                              selected: selectedCageId == cell.cage!.id,
                              dimmed: isMatch != null && !isMatch!(cell.cage!),
                              selectable:
                                  selectableCage?.call(cell.cage!) ?? true,
                              note: cellNote?.call(cell.cage!),
                              statusLabel: statusLabel?.call(cell.cage!),
                            ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RowMetric extends StatelessWidget {
  const _RowMetric({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600),
    );
  }
}

class _CageMapCellTile extends StatelessWidget {
  const _CageMapCellTile({
    required this.cage,
    required this.positionIndex,
    required this.onTap,
    required this.selected,
    required this.dimmed,
    required this.selectable,
    required this.note,
    required this.statusLabel,
  });

  final Cage cage;
  final int positionIndex;
  final ValueChanged<Cage> onTap;
  final bool selected;
  final bool dimmed;
  final bool selectable;
  final String? note;
  final String? statusLabel;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final attention = cage.attention;
    final style = CageAttentionStyle.of(attention, palette);
    final displayStatus = statusLabel ?? cage.occupancyText;
    final enabled = selectable;
    // 不可选的格子也要压暗：只把 onTap 置空的话，用户会反复点一个看起来正常的格子。
    final faded = dimmed || !enabled;

    final borderColor = selected ? palette.primary : style.border;
    final semantics = [
      cage.cageNumber.isEmpty ? '笼位 ${cage.id}' : cage.cageNumber,
      '第 $positionIndex 位',
      cage.usageLabel,
      cage.occupancyText,
      if (statusLabel != null) '母兔状态 $displayStatus',
      attention.label,
      if (cage.attentionAlertReason != null) cage.attentionAlertReason!,
      if (!enabled) '不可选择',
    ].join('，');

    return Opacity(
      opacity: faded ? 0.35 : 1,
      child: Semantics(
        // container: true 让每个格子自成一个语义节点；不加的话标签会往上并入整页，
        // TalkBack 逐格朗读不了，测试也只能拿到整屏的拼接文本。
        container: true,
        label: semantics,
        button: true,
        selected: selected,
        child: Material(
          color: selected ? palette.primarySoft : style.fill,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
            side: BorderSide(color: borderColor, width: selected ? 2 : 1),
          ),
          child: InkWell(
            key: ValueKey('cage-map-cell-${cage.id}'),
            onTap: enabled ? () => onTap(cage) : null,
            borderRadius: BorderRadius.circular(8),
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // 位号写在格子里：折行之后底下那条 1..N 的标尺对不上号了，
                  // 而现场找笼靠的就是笼上那个号。排号在排头、层号在切换器，
                  // 加上位号就能拼回完整笼位（完整编号在无障碍标签里）。
                  Row(
                    children: [
                      Text(
                        '$positionIndex',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: selected ? palette.primary : palette.muted,
                        ),
                      ),
                      const Spacer(),
                      Icon(
                        selected ? Icons.check_circle : style.icon,
                        size: 14,
                        color: selected ? palette.primary : style.foreground,
                      ),
                    ],
                  ),
                  // 不用 FittedBox 缩字：那等于把系统字号设置抹掉。
                  // 格子本身跟着 textScale 长；Flexible 只是最后一道保险，
                  // 防住字体行高在不同机器上比算出来的高一两像素。
                  Flexible(
                    child: Text(
                      displayStatus,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: selected ? palette.primary : style.foreground,
                      ),
                    ),
                  ),
                  if (note != null && note!.isNotEmpty)
                    Flexible(
                      child: Text(
                        note!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(fontSize: 10, color: palette.muted),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 该坐标没有笼位（例如这一层只装了 4 个笼，第 5 位是空的）。
class _EmptySlot extends StatelessWidget {
  const _EmptySlot({required this.positionIndex});

  final int positionIndex;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Semantics(
      container: true,
      label: '第 $positionIndex 位，缺笼',
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          color: palette.background,
        ),
        child: Center(
          child: Text(
            '—',
            style: TextStyle(color: palette.line, fontSize: 14),
          ),
        ),
      ),
    );
  }
}

class _UnplacedCages extends StatelessWidget {
  const _UnplacedCages({
    required this.cages,
    required this.onTapCage,
    required this.selectedCageId,
    required this.isMatch,
    required this.selectableCage,
    required this.cellNote,
    required this.statusLabel,
    required this.palette,
  });

  final List<Cage> cages;
  final ValueChanged<Cage> onTapCage;
  final int? selectedCageId;
  final bool Function(Cage cage)? isMatch;
  final bool Function(Cage cage)? selectableCage;
  final String? Function(Cage cage)? cellNote;
  final String Function(Cage cage)? statusLabel;
  final AppPalette palette;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const ValueKey('cage-map-unplaced'),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '未编排 ${cages.length} 笼',
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                  color: palette.text,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            '缺少排/层/位坐标，放不进地图。补齐编号后会自动归位。',
            style: TextStyle(color: palette.muted, fontSize: 12),
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final cage in cages)
                _UnplacedChip(
                  cage: cage,
                  onTap: onTapCage,
                  selected: selectedCageId == cage.id,
                  dimmed: isMatch != null && !isMatch!(cage),
                  selectable: selectableCage?.call(cage) ?? true,
                  note: cellNote?.call(cage),
                  statusLabel: statusLabel?.call(cage),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _UnplacedChip extends StatelessWidget {
  const _UnplacedChip({
    required this.cage,
    required this.onTap,
    required this.selected,
    required this.dimmed,
    required this.selectable,
    required this.note,
    required this.statusLabel,
  });

  final Cage cage;
  final ValueChanged<Cage> onTap;
  final bool selected;
  final bool dimmed;
  final bool selectable;

  /// 与网格格子一致的标注（例如换笼时的「对调」）；
  /// 未编排的笼位不能因为没坐标就少一层提醒。
  final String? note;
  final String? statusLabel;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final style = CageAttentionStyle.of(cage.attention, palette);

    return Opacity(
      opacity: dimmed ? 0.35 : 1,
      child: Material(
        color: selected ? palette.primarySoft : style.fill,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: BorderSide(
            color: selected ? palette.primary : style.border,
            width: selected ? 2 : 1,
          ),
        ),
        child: InkWell(
          key: ValueKey('cage-map-cell-${cage.id}'),
          onTap: selectable ? () => onTap(cage) : null,
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  selected ? Icons.check_circle : style.icon,
                  size: 14,
                  color: selected ? palette.primary : style.foreground,
                ),
                const SizedBox(width: 6),
                // 笼位编号可以很长（“一号繁育区-R1-01-上层”），200% 字号下必须能缩，
                // 否则整个 chip 会把 Wrap 撑破——这正是跡象里报的那条 overflow。
                Flexible(
                  child: Text(
                    cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: selected ? palette.primary : palette.text,
                    ),
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  statusLabel ?? cage.occupancyText,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 12, color: palette.muted),
                ),
                if (note != null && note!.isNotEmpty) ...[
                  const SizedBox(width: 6),
                  Text(
                    note!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 11, color: palette.muted),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 地图图例。颜色只有配上说明才算信息，否则用户得靠猜。
class CageAttentionLegend extends StatelessWidget {
  const CageAttentionLegend({super.key, this.counts});

  /// 各状态的笼位数量；给出时图例同时充当汇总。
  final Map<CageAttention, int>? counts;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);

    return Wrap(
      key: const ValueKey('cage-map-legend'),
      spacing: 10,
      runSpacing: 6,
      children: [
        for (final attention in CageAttention.values)
          _LegendItem(
            attention: attention,
            palette: palette,
            count: counts?[attention],
          ),
      ],
    );
  }
}

class _LegendItem extends StatelessWidget {
  const _LegendItem({
    required this.attention,
    required this.palette,
    required this.count,
  });

  final CageAttention attention;
  final AppPalette palette;
  final int? count;

  @override
  Widget build(BuildContext context) {
    final style = CageAttentionStyle.of(attention, palette);
    final label = count == null ? attention.label : '${attention.label} $count';

    return Semantics(
      label: '${attention.label}：${attention.hint}',
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 14,
            height: 14,
            decoration: BoxDecoration(
              color: style.fill,
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: style.border),
            ),
            child: Icon(style.icon, size: 10, color: style.foreground),
          ),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(fontSize: 12, color: palette.muted),
          ),
        ],
      ),
    );
  }
}
