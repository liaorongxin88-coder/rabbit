import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/nfc_local_store.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/cage_summary.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_entry_flow.dart';

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
        tooltip: '返回笼位列表',
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

    return ListView(
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
        rabbits.when(
          data: (items) => _RabbitSection(
            houseId: houseId,
            cage: cage ?? _fallbackCage(summary, houseId),
            rabbits: items,
            allCages: cages.valueOrNull ?? [_fallbackCage(summary, houseId)],
            canEdit: permission.canEdit,
            onChanged: () {
              ref.invalidate(cageSummaryProvider(key));
              ref.invalidate(cageRabbitsProvider(key));
              ref.invalidate(houseCagesProvider(houseId));
            },
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

class _RabbitSection extends StatelessWidget {
  const _RabbitSection({
    required this.houseId,
    required this.cage,
    required this.rabbits,
    required this.allCages,
    required this.canEdit,
    required this.onChanged,
  });

  final int houseId;
  final Cage cage;
  final List<Rabbit> rabbits;
  final List<Cage> allCages;
  final bool canEdit;
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
                onPressed: canEdit
                    ? () async {
                        await showRabbitEntryTypeSheet(
                          context: context,
                          houseId: houseId,
                          cage: cage,
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
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.pets_outlined),
                title: Text('兔 #${rabbit.id} · ${rabbit.typeLabel}'),
                subtitle: Text(
                  '${rabbit.genderLabel} · ${rabbit.breed.isEmpty ? '品种未填' : rabbit.breed} · ${rabbit.weightLabel}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: canEdit ? const Icon(Icons.chevron_right) : null,
                onTap: canEdit
                    ? () async {
                        await showRabbitEditSheet(
                          context: context,
                          houseId: houseId,
                          rabbit: rabbit,
                          cages: allCages,
                        );
                        onChanged();
                      }
                    : null,
              ),
          ],
        ],
      ),
    );
  }
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
