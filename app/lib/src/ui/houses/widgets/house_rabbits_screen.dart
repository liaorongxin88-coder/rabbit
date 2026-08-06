import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_entry_flow.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_move_cage_sheet.dart';

class HouseRabbitsScreen extends ConsumerWidget {
  const HouseRabbitsScreen({super.key, required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final houses = ref.watch(housesProvider);
    final canEdit =
        ref.watch(housePermissionProvider(houseId)).valueOrNull?.canEdit ==
            true;

    return AppPage(
      title: '兔只管理',
      actions: [
        if (canEdit)
          IconButton(
            tooltip: '批量出库',
            onPressed: () =>
                context.push('/houses/$houseId/outbound?entryType=HOUSE'),
            icon: const Icon(Icons.local_shipping_outlined),
          ),
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
    final palette = AppPalette.of(context);
    final rabbits = ref.watch(houseRabbitsProvider(house.id));
    final cages = ref.watch(houseCagesProvider(house.id));

    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        SectionCard(
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
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _AddHint(houseId: house.id),
        const SizedBox(height: 12),
        SectionCard(
          padding: EdgeInsets.zero,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: palette.successSoft,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Icon(
                        Icons.cruelty_free,
                        color: palette.success,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        '兔只列表',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    IconButton(
                      tooltip: '刷新兔只',
                      onPressed: () {
                        ref.invalidate(houseRabbitsProvider(house.id));
                        ref.invalidate(houseCagesProvider(house.id));
                      },
                      icon: const Icon(Icons.refresh),
                    ),
                  ],
                ),
              ),
              Divider(height: 1, color: palette.line),
              Padding(
                padding: const EdgeInsets.all(16),
                child: cages.when(
                  data: (cageItems) => rabbits.when(
                    data: (rabbitItems) {
                      final permission =
                          ref.watch(housePermissionProvider(house.id));
                      return permission.when(
                        data: (perm) => _RabbitListBody(
                          houseId: house.id,
                          rabbits: rabbitItems,
                          cages: cageItems,
                          canEdit: perm.canEdit,
                        ),
                        loading: () => const _SectionLoading(label: '加载权限中...'),
                        error: (error, _) => _InlineError(
                          message: error.toString(),
                          onRetry: () => ref.invalidate(
                            housePermissionProvider(house.id),
                          ),
                        ),
                      );
                    },
                    loading: () => const _SectionLoading(label: '加载兔只中...'),
                    error: (error, _) => _InlineError(
                      message: error.toString(),
                      onRetry: () =>
                          ref.invalidate(houseRabbitsProvider(house.id)),
                    ),
                  ),
                  loading: () => const _SectionLoading(label: '加载笼位中...'),
                  error: (error, _) => _InlineError(
                    message: error.toString(),
                    onRetry: () => ref.invalidate(houseCagesProvider(house.id)),
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
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

class _RabbitListBody extends StatelessWidget {
  const _RabbitListBody({
    required this.houseId,
    required this.rabbits,
    required this.cages,
    required this.canEdit,
  });

  final int houseId;
  final List<Rabbit> rabbits;
  final List<Cage> cages;
  final bool canEdit;

  @override
  Widget build(BuildContext context) {
    if (rabbits.isEmpty) {
      return _CompactEmpty(
        icon: Icons.cruelty_free,
        title: '暂无兔只',
        message: '请先进入笼位管理，点击具体笼位录入第一只兔子。',
        actionLabel: '去笼位',
        onAction: () => context.go('/houses/$houseId/cages'),
      );
    }

    final cageDisplayById = <int, String>{
      for (final cage in cages)
        cage.id: cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber,
    };

    return Column(
      children: [
        for (final rabbit in rabbits) ...[
          _RabbitListTile(
            houseId: houseId,
            rabbit: rabbit,
            cages: cages,
            cageDisplay: cageDisplayById[rabbit.cageId] ?? '#${rabbit.cageId}',
            canEdit: canEdit,
          ),
          const SizedBox(height: 8),
        ],
      ],
    );
  }
}

class _RabbitListTile extends StatelessWidget {
  const _RabbitListTile({
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
      child: Row(
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
          if (canEdit) ...[
            IconButton(
              tooltip: '单兔出库',
              onPressed: rabbit.type == '2'
                  ? () => context.push(
                      '/houses/$houseId/outbound?entryType=RABBIT&rabbitId=${rabbit.id}')
                  : null,
              icon: const Icon(Icons.local_shipping_outlined),
            ),
            IconButton(
              tooltip: '换笼位',
              onPressed: () => showRabbitMoveCageSheet(
                context: context,
                houseId: houseId,
                rabbit: rabbit,
                cages: cages,
              ),
              icon: const Icon(Icons.move_down_outlined),
            ),
            IconButton(
              tooltip: '编辑兔子',
              onPressed: () => showRabbitEditSheet(
                context: context,
                houseId: houseId,
                rabbit: rabbit,
                cages: cages,
              ),
              icon: const Icon(Icons.edit_outlined),
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
