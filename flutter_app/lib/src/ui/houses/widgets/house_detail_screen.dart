import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

class HouseDetailScreen extends ConsumerWidget {
  const HouseDetailScreen({super.key, required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final houses = ref.watch(housesProvider);

    return AppPage(
      title: '兔舍详情',
      actions: [
        IconButton(
          tooltip: '返回兔舍列表',
          onPressed: () => context.go('/houses'),
          icon: const Icon(Icons.list_alt_outlined),
        ),
        IconButton(
          tooltip: '刷新',
          onPressed: () {
            ref.invalidate(housesProvider);
            ref.invalidate(houseCagesProvider(houseId));
            ref.invalidate(houseRabbitsProvider(houseId));
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
          final selectedHouseId =
              ref.read(authControllerProvider).valueOrNull?.houseId ?? 0;
          if (selectedHouseId != house.id) {
            Future.microtask(
              () => ref
                  .read(authControllerProvider.notifier)
                  .setHouseId(house.id),
            );
          }
          return _HouseDetailContent(house: house);
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

class _HouseDetailContent extends ConsumerWidget {
  const _HouseDetailContent({required this.house});

  final RabbitHouse house;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final palette = AppPalette.of(context);
    final cages = ref.watch(houseCagesProvider(house.id));
    final rabbits = ref.watch(houseRabbitsProvider(house.id));

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 22),
      children: [
        SectionCard(
          child: Row(
            children: [
              IconButton(
                tooltip: '返回',
                onPressed: () => context.go('/houses'),
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
                      house.layoutLabel,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (house.remark.trim().isNotEmpty) ...[
          const SizedBox(height: 12),
          SectionCard(
            child: Text(
              house.remark,
              style: Theme.of(context).textTheme.bodyLarge,
            ),
          ),
        ],
        const SizedBox(height: 12),
        _OverviewMetrics(cages: cages, rabbits: rabbits),
        const SizedBox(height: 12),
        _DetailEntryCard(
          icon: Icons.grid_view_rounded,
          iconColor: palette.primary,
          iconBackground: palette.primarySoft,
          title: '笼位管理',
          message: '新增笼位、搜索笼位，并从具体笼位录入兔子。',
          actionLabel: '进入笼位',
          onTap: () => context.go('/houses/${house.id}/cages'),
        ),
        const SizedBox(height: 10),
        _DetailEntryCard(
          icon: Icons.cruelty_free,
          iconColor: palette.success,
          iconBackground: palette.successSoft,
          title: '兔只管理',
          message: '查看当前兔舍兔只档案；新增兔子请先进入笼位。',
          actionLabel: '查看兔只',
          onTap: () => context.go('/houses/${house.id}/rabbits'),
        ),
        const SizedBox(height: 10),
        _DetailEntryCard(
          icon: Icons.calendar_month_outlined,
          iconColor: palette.warning,
          iconBackground: palette.warningSoft,
          title: '生产设置',
          message: '为当前兔舍单独配置生产周期；未保存时使用默认配置。',
          actionLabel: '配置',
          onTap: () => context.go(
            '/houses/${house.id}/settings/production?name=${Uri.encodeComponent(house.name)}',
          ),
        ),
      ],
    );
  }
}

class _OverviewMetrics extends StatelessWidget {
  const _OverviewMetrics({required this.cages, required this.rabbits});

  final AsyncValue<List<dynamic>> cages;
  final AsyncValue<List<dynamic>> rabbits;

  @override
  Widget build(BuildContext context) {
    final cageCount = cages.valueOrNull?.length;
    final rabbitCount = rabbits.valueOrNull?.length;

    return SectionCard(
      child: Row(
        children: [
          Expanded(
            child: _MetricBlock(
              label: '笼位',
              value: cageCount == null ? '--' : '$cageCount',
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: _MetricBlock(
              label: '兔只',
              value: rabbitCount == null ? '--' : '$rabbitCount',
            ),
          ),
        ],
      ),
    );
  }
}

class _MetricBlock extends StatelessWidget {
  const _MetricBlock({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 6),
          Text(value, style: Theme.of(context).textTheme.titleLarge),
        ],
      ),
    );
  }
}

class _DetailEntryCard extends StatelessWidget {
  const _DetailEntryCard({
    required this.icon,
    required this.iconColor,
    required this.iconBackground,
    required this.title,
    required this.message,
    required this.actionLabel,
    required this.onTap,
  });

  final IconData icon;
  final Color iconColor;
  final Color iconBackground;
  final String title;
  final String message;
  final String actionLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Material(
      color: palette.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: palette.line),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: iconBackground,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, color: iconColor),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 4),
                    Text(
                      message,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              TextButton(onPressed: onTap, child: Text(actionLabel)),
            ],
          ),
        ),
      ),
    );
  }
}
