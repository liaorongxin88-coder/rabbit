import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_entry_flow.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_move_cage_sheet.dart';

class HouseRabbitsScreen extends ConsumerWidget {
  const HouseRabbitsScreen({super.key, required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final houses = ref.watch(housesProvider);

    return AppPage(
      title: '兔只管理',
      actions: [
        IconButton(
          tooltip: '返回兔舍详情',
          onPressed: () => context.go('/houses/$houseId'),
          icon: const Icon(Icons.storefront_outlined),
        ),
        IconButton(
          tooltip: '刷新',
          onPressed: () {
            ref.invalidate(houseRabbitsProvider(houseId));
            ref.invalidate(houseCagesProvider(houseId));
          },
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: houses.when(
        data: (items) {
          final house = _findHouse(items);
          if (house == null) {
            return EmptyState(
              icon: Icons.storefront_outlined,
              title: '兔舍不存在',
              message: '返回兔舍列表后重新选择一个兔舍。',
              actionLabel: '返回列表',
              onAction: () => context.go('/houses'),
            );
          }
          return _RabbitsContent(house: house);
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(housesProvider),
        ),
      ),
    );
  }

  RabbitHouse? _findHouse(List<RabbitHouse> houses) {
    for (final house in houses) {
      if (house.id == houseId) {
        return house;
      }
    }
    return null;
  }
}

class _RabbitsContent extends ConsumerWidget {
  const _RabbitsContent({required this.house});

  final RabbitHouse house;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rabbits = ref.watch(houseRabbitsProvider(house.id));
    final cages = ref.watch(houseCagesProvider(house.id));
    final permission = ref.watch(housePermissionProvider(house.id));
    final cageItems = cages.valueOrNull;
    final rabbitItems = rabbits.valueOrNull;
    final currentPermission = permission.valueOrNull;

    if (cageItems == null) {
      return _RabbitListShell(
        house: house,
        statusLabel: cages.hasError ? '笼位加载失败' : '正在加载完整列表...',
        onRefresh: () => ref.invalidate(houseCagesProvider(house.id)),
        body: cages.hasError
            ? _InlineError(
                message: cages.error.toString(),
                onRetry: () => ref.invalidate(houseCagesProvider(house.id)),
              )
            : const _SectionLoading(label: '加载笼位中...'),
      );
    }

    if (rabbitItems == null) {
      return _RabbitListShell(
        house: house,
        statusLabel: rabbits.hasError ? '完整列表加载失败' : '正在加载完整列表...',
        onRefresh: () => ref.invalidate(houseRabbitsProvider(house.id)),
        body: rabbits.hasError
            ? _InlineError(
                message: rabbits.error.toString(),
                onRetry: () => ref.invalidate(houseRabbitsProvider(house.id)),
              )
            : const _SectionLoading(label: '加载全部兔只中...'),
      );
    }

    if (currentPermission == null) {
      return _RabbitListShell(
        house: house,
        statusLabel: permission.hasError ? '权限加载失败' : '正在加载权限...',
        onRefresh: () => ref.invalidate(housePermissionProvider(house.id)),
        body: permission.hasError
            ? _InlineError(
                message: permission.error.toString(),
                onRetry: () =>
                    ref.invalidate(housePermissionProvider(house.id)),
              )
            : const _SectionLoading(label: '加载权限中...'),
      );
    }

    return _LoadedRabbitList(
      house: house,
      rabbits: rabbitItems,
      cages: cageItems,
      canEdit: currentPermission.canEdit,
      onRefresh: () {
        ref.invalidate(houseRabbitsProvider(house.id));
        ref.invalidate(houseCagesProvider(house.id));
      },
    );
  }
}

class _RabbitListShell extends StatelessWidget {
  const _RabbitListShell({
    required this.house,
    required this.statusLabel,
    required this.onRefresh,
    required this.body,
  });

  final RabbitHouse house;
  final String statusLabel;
  final VoidCallback onRefresh;
  final Widget body;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        _HouseSummaryCard(house: house),
        const SizedBox(height: 12),
        _AddHint(houseId: house.id),
        const SizedBox(height: 12),
        _RabbitListHeader(
          statusLabel: statusLabel,
          onRefresh: onRefresh,
        ),
        const SizedBox(height: 8),
        SectionCard(child: body),
      ],
    );
  }
}

class _LoadedRabbitList extends StatelessWidget {
  const _LoadedRabbitList({
    required this.house,
    required this.rabbits,
    required this.cages,
    required this.canEdit,
    required this.onRefresh,
  });

  final RabbitHouse house;
  final List<Rabbit> rabbits;
  final List<Cage> cages;
  final bool canEdit;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final cageDisplayById = <int, String>{
      for (final cage in cages)
        cage.id: cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber,
    };
    final listItemCount = rabbits.isEmpty ? 1 : rabbits.length;

    return ListView.builder(
      key: const ValueKey('house-rabbit-list'),
      padding: AppSpacing.pagePadding,
      itemCount: 5 + listItemCount,
      itemBuilder: (context, index) {
        switch (index) {
          case 0:
            return _HouseSummaryCard(house: house);
          case 1:
            return const SizedBox(height: 12);
          case 2:
            return _AddHint(houseId: house.id);
          case 3:
            return const SizedBox(height: 12);
          case 4:
            return _RabbitListHeader(
              statusLabel: '共 ${rabbits.length} 只 · 已全部加载',
              onRefresh: onRefresh,
              canEdit: canEdit,
              onOutbound: () => context.push(
                '/houses/${house.id}/outbound?entryType=HOUSE',
              ),
            );
        }

        if (rabbits.isEmpty) {
          return Padding(
            padding: const EdgeInsets.only(top: 8),
            child: _CompactEmpty(
              icon: Icons.cruelty_free,
              title: '暂无兔只',
              message: '请先进入笼位管理，点击具体笼位录入第一只兔子。',
              actionLabel: '去笼位',
              onAction: () => context.go('/houses/${house.id}/cages'),
            ),
          );
        }

        final rabbit = rabbits[index - 5];
        return Padding(
          padding: const EdgeInsets.only(top: 8),
          child: _RabbitListTile(
            key: ValueKey('house-rabbit-${rabbit.id}'),
            houseId: house.id,
            rabbit: rabbit,
            cages: cages,
            cageDisplay: cageDisplayById[rabbit.cageId] ?? '#${rabbit.cageId}',
            canEdit: canEdit,
          ),
        );
      },
    );
  }
}

class _HouseSummaryCard extends StatelessWidget {
  const _HouseSummaryCard({required this.house});

  final RabbitHouse house;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: Row(
        children: [
          IconButton(
            tooltip: '返回',
            onPressed: () => context.go('/houses/${house.id}'),
            icon: const Icon(Icons.arrow_back),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  house.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 4),
                Text(
                  '兔只档案查看与编辑',
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

class _RabbitListHeader extends StatelessWidget {
  const _RabbitListHeader({
    required this.statusLabel,
    required this.onRefresh,
    this.canEdit = false,
    this.onOutbound,
  });

  final String statusLabel;
  final VoidCallback onRefresh;
  final bool canEdit;
  final VoidCallback? onOutbound;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: palette.successSoft,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(Icons.cruelty_free, color: palette.success),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '兔只列表',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      statusLabel,
                      key: const ValueKey('house-rabbit-load-status'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: '刷新兔只',
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          if (canEdit && onOutbound != null) ...[
            const SizedBox(height: 12),
            Tooltip(
              message: '整舍批量出库',
              child: FilledButton.icon(
                key: const ValueKey('house-rabbits-outbound-action'),
                onPressed: onOutbound,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(48),
                ),
                icon: const Icon(Icons.local_shipping_outlined),
                label: const Text('整舍批量出库'),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _AddHint extends StatelessWidget {
  const _AddHint({required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return SectionCard(
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: palette.primarySoft,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(Icons.touch_app_outlined, color: palette.primary),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              '新增兔子需要先选择笼位，进入笼位管理后点击具体笼位录入。',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
          const SizedBox(width: 8),
          TextButton(
            onPressed: () => context.go('/houses/$houseId/cages'),
            child: const Text('去笼位'),
          ),
        ],
      ),
    );
  }
}

class _RabbitListTile extends StatelessWidget {
  const _RabbitListTile({
    super.key,
    required this.houseId,
    required this.rabbit,
    required this.cages,
    required this.cageDisplay,
    required this.canEdit,
  });

  final int houseId;
  final Rabbit rabbit;
  final List<Cage> cages;
  final String cageDisplay;
  final bool canEdit;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: palette.successSoft,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(Icons.cruelty_free, color: palette.success),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '兔 #${rabbit.id} · ${rabbit.typeLabel} · ${rabbit.genderLabel}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '笼位 $cageDisplay · ${rabbit.breed.isEmpty ? '品种未填' : rabbit.breed} · ${rabbit.weightLabel}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (canEdit) ...[
            const SizedBox(height: 8),
            Wrap(
              alignment: WrapAlignment.end,
              spacing: 4,
              runSpacing: 4,
              children: [
                if (rabbit.type == '2')
                  Tooltip(
                    message: '单兔出库',
                    child: TextButton.icon(
                      key: ValueKey('rabbit-row-outbound-${rabbit.id}'),
                      onPressed: () => context.push(
                        '/houses/$houseId/outbound?entryType=RABBIT&rabbitId=${rabbit.id}',
                      ),
                      style: TextButton.styleFrom(
                        minimumSize: const Size(0, 48),
                      ),
                      icon: const Icon(Icons.local_shipping_outlined),
                      label: const Text('单兔出库'),
                    ),
                  ),
                Tooltip(
                  message: '换笼位',
                  child: TextButton.icon(
                    key: ValueKey('rabbit-row-move-${rabbit.id}'),
                    onPressed: () => showRabbitMoveCageSheet(
                      context: context,
                      houseId: houseId,
                      rabbit: rabbit,
                      cages: cages,
                    ),
                    style: TextButton.styleFrom(
                      minimumSize: const Size(0, 48),
                    ),
                    icon: const Icon(Icons.move_down_outlined),
                    label: const Text('换笼'),
                  ),
                ),
                Tooltip(
                  message: '编辑兔子',
                  child: TextButton.icon(
                    key: ValueKey('rabbit-row-edit-${rabbit.id}'),
                    onPressed: () => showRabbitEditSheet(
                      context: context,
                      houseId: houseId,
                      rabbit: rabbit,
                      cages: cages,
                    ),
                    style: TextButton.styleFrom(
                      minimumSize: const Size(0, 48),
                    ),
                    icon: const Icon(Icons.edit_outlined),
                    label: const Text('编辑'),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _CompactEmpty extends StatelessWidget {
  const _CompactEmpty({
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        children: [
          Icon(icon, color: palette.muted),
          const SizedBox(height: 8),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 12),
            OutlinedButton(onPressed: onAction, child: Text(actionLabel!)),
          ],
        ],
      ),
    );
  }
}

class _SectionLoading extends StatelessWidget {
  const _SectionLoading({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        const SizedBox(width: 10),
        Text(label, style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _InlineError extends StatelessWidget {
  const _InlineError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.dangerSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline, color: palette.danger),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          TextButton(onPressed: onRetry, child: const Text('重试')),
        ],
      ),
    );
  }
}
