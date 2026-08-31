import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/services/storage/nfc.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/summary.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/ui/cages/sheets/rabbit_picker.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/queue.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/abnormal.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/entry.dart';

class CageDetailScreen extends ConsumerWidget {
  const CageDetailScreen({
    super.key,
    required this.houseId,
    required this.cageId,
  });

  final int houseId;
  final int cageId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final key = (houseId: houseId, cageId: cageId);
    final summary = ref.watch(cageSummaryProvider(key));
    final permission = ref.watch(housePermissionProvider(houseId));
    return AppPage(
      title: '笼位管理',
      leading: IconButton(
        key: const ValueKey('cage-detail-back-button'),
        tooltip: '返回笼位地图',
        onPressed: () => context.go('/houses/$houseId/cages'),
        icon: const Icon(Icons.arrow_back),
      ),
      actions: [
        if (permission.valueOrNull?.canEdit == true)
          IconButton(
            tooltip: '该笼批量出库',
            onPressed: () => context.push(
                '/houses/$houseId/outbound?entryType=CAGE&cageId=$cageId'),
            icon: const Icon(Icons.local_shipping_outlined),
          ),
        IconButton(
          tooltip: '刷新',
          onPressed: () {
            ref.invalidate(cageSummaryProvider(key));
            ref.invalidate(cageRabbitsProvider(key));
            ref.invalidate(houseCagesProvider(houseId));
            ref.invalidate(housePermissionProvider(houseId));
            ref.invalidate(nfcCageWriteQueueProvider(houseId));
          },
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: summary.when(
        data: (value) => _DetailBody(
          houseId: houseId,
          summary: value,
          permission: permission.valueOrNull ??
              const HousePermission(perms: 'view', isAdmin: false),
        ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(cageSummaryProvider(key)),
        ),
      ),
    );
  }
}

class _DetailBody extends ConsumerWidget {
  const _DetailBody({
    required this.houseId,
    required this.summary,
    required this.permission,
  });

  final int houseId;
  final CageSummary summary;
  final HousePermission permission;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final key = (houseId: houseId, cageId: summary.cageId);
    final rabbits = ref.watch(cageRabbitsProvider(key));
    final cages = ref.watch(houseCagesProvider(houseId));
    final houses = ref.watch(housesProvider).valueOrNull ?? const [];
    final houseName = houses
        .where((house) => house.id == houseId)
        .map((house) => house.name)
        .firstOrNull;
    final cage = cages.valueOrNull
        ?.where((item) => item.id == summary.cageId)
        .firstOrNull;
    void refreshCage() {
      ref.invalidate(cageSummaryProvider(key));
      ref.invalidate(cageRabbitsProvider(key));
      ref.invalidate(houseCagesProvider(houseId));
    }

    return ListView(
      key: const ValueKey('cage-detail-scroll'),
      padding: AppSpacing.pagePadding,
      children: [
        _CageIdentityBand(summary: summary, houseName: houseName),
        const SizedBox(height: 12),
        _NfcBindingSection(
          houseId: houseId,
          cageId: summary.cageId,
          canControl: permission.canControl,
        ),
        const SizedBox(height: 12),
        _OperationsSummary(summary: summary),
        const SizedBox(height: 12),
        _CageRecordActions(
          houseId: houseId,
          rabbits: rabbits,
          permission: permission,
          onChanged: refreshCage,
        ),
        const SizedBox(height: 12),
        rabbits.when(
          data: (items) => _RabbitSection(
            houseId: houseId,
            cage: cage ?? _fallbackCage(summary, houseId),
            rabbits: items,
            permission: permission,
            onChanged: refreshCage,
          ),
          loading: () => const SectionCard(
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (error, _) => SectionCard(
            child: ErrorState(
              message: error.toString(),
              onRetry: () => ref.invalidate(cageRabbitsProvider(key)),
            ),
          ),
        ),
      ],
    );
  }

  Cage _fallbackCage(CageSummary value, int houseId) {
    return Cage(
      id: value.cageId,
      houseId: houseId,
      cageNumber: value.cageNumber,
      status: '',
      rabbitCount: value.rabbitCount,
      isEnabled: true,
    );
  }
}

class _CageIdentityBand extends StatelessWidget {
  const _CageIdentityBand({required this.summary, required this.houseName});

  final CageSummary summary;
  final String? houseName;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: palette.surface,
        border: Border.all(color: palette.line),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              color: palette.primarySoft,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(Icons.location_on_outlined, color: palette.primary),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  summary.cageNumber.isEmpty
                      ? '#${summary.cageId}'
                      : summary.cageNumber,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  '${houseName ?? '当前兔舍'} · ${summary.rabbitCount} 只兔',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _NfcBindingSection extends ConsumerWidget {
  const _NfcBindingSection({
    required this.houseId,
    required this.cageId,
    required this.canControl,
  });

  final int houseId;
  final int cageId;
  final bool canControl;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (!canControl) {
      return SectionCard(
        child: Row(
          children: [
            Icon(Icons.nfc_outlined, color: AppPalette.of(context).muted),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('NFC标签', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 3),
                  Text(
                    '当前权限不可管理标签',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }
    final queue = ref.watch(nfcCageWriteQueueProvider(houseId));
    return SectionCard(
      child: queue.when(
        data: (items) {
          NfcCageQueueItem? item;
          for (final candidate in items) {
            if (candidate.cageId == cageId) item = candidate;
          }
          final current = item;
          return Row(
            children: [
              Icon(
                current?.isBound == true ? Icons.nfc : Icons.nfc_outlined,
                color: current?.isBound == true
                    ? AppPalette.of(context).success
                    : AppPalette.of(context).muted,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('NFC标签',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 3),
                    Text(
                      current?.hasConflict == true
                          ? '绑定异常'
                          : current?.isBound == true
                              ? '已绑定 ${current?.tagUid ?? ''}'
                              : '未绑定',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
              IconButton.filledTonal(
                tooltip: current?.isBound == true ? '更换标签' : '写入标签',
                onPressed: current == null
                    ? null
                    : () => _startSingle(context, ref, current),
                icon: const Icon(Icons.edit),
              ),
            ],
          );
        },
        loading: () => const LinearProgressIndicator(),
        error: (_, __) => Text(
          '当前账号无标签写入权限',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ),
    );
  }

  Future<void> _startSingle(
    BuildContext context,
    WidgetRef ref,
    NfcCageQueueItem item,
  ) async {
    final session = NfcWriteSession(
      houseId: houseId,
      items: [NfcWriteSessionItem(queueItem: item)],
      currentIndex: 0,
      updatedAt: DateTime.now().millisecondsSinceEpoch,
    );
    await ref.read(nfcLocalStoreProvider).saveSession(session);
    if (context.mounted) {
      context.go('/houses/$houseId/nfc/write/session');
    }
  }
}

class _OperationsSummary extends StatelessWidget {
  const _OperationsSummary({required this.summary});

  final CageSummary summary;

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('MM-dd HH:mm');
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('笼位状态', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 14),
          _SummaryRow(
            icon: summary.isFed ? Icons.check_circle : Icons.schedule,
            label: '投喂',
            value: summary.lastFeedTime == null
                ? (summary.isFed ? '今日已投喂' : '暂无记录')
                : '${dateFormat.format(summary.lastFeedTime!)} ${summary.lastFeedAmount ?? ''}${summary.lastFeedUnit}',
          ),
          const SizedBox(height: 12),
          _SummaryRow(
            icon: summary.abnormalUndealCount > 0
                ? Icons.warning_amber
                : Icons.health_and_safety_outlined,
            label: '异常',
            value: summary.abnormalUndealCount > 0
                ? '${summary.abnormalUndealCount} 条待处理'
                : '无待处理异常',
          ),
        ],
      ),
    );
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 22),
        const SizedBox(width: 10),
        SizedBox(width: 52, child: Text(label)),
        Expanded(
          child: Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.end,
          ),
        ),
      ],
    );
  }
}

class _CageRecordActions extends StatelessWidget {
  const _CageRecordActions({
    required this.houseId,
    required this.rabbits,
    required this.permission,
    required this.onChanged,
  });

  final int houseId;
  final AsyncValue<List<Rabbit>> rabbits;
  final HousePermission permission;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final activeRabbits = (rabbits.valueOrNull ?? const <Rabbit>[])
        .where((rabbit) => rabbit.isActive)
        .toList(growable: false);
    final unavailableReason = _unavailableReason(activeRabbits);

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('现场记录', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 10),
          Semantics(
            hint: unavailableReason,
            child: Tooltip(
              message: unavailableReason ?? '为笼内兔只新增异常记录',
              child: OutlinedButton.icon(
                key: const ValueKey('cage-abnormal-entry'),
                onPressed: unavailableReason == null
                    ? () => _openAbnormalSheet(context, activeRabbits)
                    : null,
                icon: const Icon(Icons.report_problem_outlined),
                label: const Text('异常记录'),
              ),
            ),
          ),
          if (unavailableReason != null) ...[
            const SizedBox(height: 8),
            Text(
              unavailableReason,
              key: const ValueKey('cage-abnormal-unavailable-reason'),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ],
      ),
    );
  }

  String? _unavailableReason(List<Rabbit> activeRabbits) {
    if (!permission.canEdit) {
      return '当前账号仅可查看，无法新增异常记录';
    }
    if (rabbits.isLoading) {
      return '正在读取笼内兔只，暂时无法新增异常记录';
    }
    if (rabbits.hasError) {
      return '无法读取笼内兔只，请重试后新增异常记录';
    }
    if (activeRabbits.isEmpty) {
      return '当前笼位没有在栏兔只，无法新增异常记录';
    }
    return null;
  }

  Future<void> _openAbnormalSheet(
    BuildContext context,
    List<Rabbit> activeRabbits,
  ) async {
    Rabbit? rabbit;
    if (activeRabbits.length == 1) {
      rabbit = activeRabbits.single;
    } else {
      rabbit = await showCageRabbitPickerSheet(
        context: context,
        rabbits: activeRabbits,
      );
    }
    if (rabbit == null || !context.mounted) {
      return;
    }
    final recorded = await showRabbitAbnormalSheet(
      context: context,
      houseId: houseId,
      rabbit: rabbit,
    );
    if (recorded) {
      onChanged();
    }
  }
}

class _RabbitSection extends StatelessWidget {
  const _RabbitSection({
    required this.houseId,
    required this.cage,
    required this.rabbits,
    required this.permission,
    required this.onChanged,
  });

  final int houseId;
  final Cage cage;
  final List<Rabbit> rabbits;
  final HousePermission permission;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '兔只 ${rabbits.length}',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              FilledButton.icon(
                key: const ValueKey('cage-rabbit-entry'),
                onPressed: permission.canAddRabbit ||
                        (permission.canQueryBatches &&
                            permission.canEditBatches)
                    ? () async {
                        await showRabbitIntakeSheet(
                          context: context,
                          houseId: houseId,
                          cage: cage,
                          permission: permission,
                        );
                        onChanged();
                      }
                    : null,
                icon: const Icon(Icons.add),
                label: const Text('录入'),
              ),
            ],
          ),
          if (rabbits.isEmpty) ...[
            const SizedBox(height: 18),
            Text('当前笼位暂无兔只', style: Theme.of(context).textTheme.bodyMedium),
          ] else ...[
            const SizedBox(height: 8),
            for (final rabbit in rabbits)
              ListTile(
                key: ValueKey('cage-rabbit-row-${rabbit.id}'),
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.pets_outlined),
                title: Text('兔 #${rabbit.id} · ${rabbit.typeLabel}'),
                subtitle: Text(
                  '${rabbit.genderLabel} · ${rabbit.breed.isEmpty ? '品种未填' : rabbit.breed} · ${rabbit.weightLabel}'
                  '\n${_rabbitStageLabel(rabbit)}',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                isThreeLine: true,
                trailing: const Icon(Icons.chevron_right),
                onTap: () => context.push(
                  '/houses/$houseId/rabbits/${rabbit.id}',
                ),
              ),
          ],
        ],
      ),
    );
  }
}

/// 兔只阶段。种母兔以生产阶段投影为准，其它兔才看旧的生长/繁殖阶段。
String _rabbitStageLabel(Rabbit rabbit) {
  final current = ReproStage.tryParse(rabbit.currentStage);
  if (current != null) {
    return '生产阶段：${current.label}';
  }
  final growth = _growthStageLabels[rabbit.growthStage];
  final repro = _reproductiveStageLabels[rabbit.reproductiveStage];
  final parts = [growth, repro].whereType<String>().toList();
  return parts.isEmpty ? '阶段未填写' : parts.join(' · ');
}

const _growthStageLabels = <String?, String>{
  'JUVENILE': '适应期',
  'ADAPTATION': '适应期',
  'GROWING': '成长期',
  'FATTENING': '育肥期',
  'MATURE': '成熟可售',
};

const _reproductiveStageLabels = <String?, String>{
  'RESERVE': '后备',
  'EMPTY': '空怀',
  'MATED': '已配种',
  'PREGNANT': '妊娠',
  'LACTATING': '哺乳',
  'RESTING': '休整',
  'READY': '可配',
};

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
