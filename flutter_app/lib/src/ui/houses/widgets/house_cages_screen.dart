import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/cages/widgets/cage_management_section.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

class HouseCagesScreen extends ConsumerStatefulWidget {
  const HouseCagesScreen({super.key, required this.houseId});

  final int houseId;

  @override
  ConsumerState<HouseCagesScreen> createState() => _HouseCagesScreenState();
}

class _HouseCagesScreenState extends ConsumerState<HouseCagesScreen> {
  final _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final houseId = widget.houseId;
    final houses = ref.watch(housesProvider);
    final canEdit =
        ref.watch(housePermissionProvider(houseId)).valueOrNull?.canEdit ==
            true;

    return AppPage(
      title: '笼位管理',
      actions: [
        if (canEdit)
          IconButton(
            key: const ValueKey('house-outbound-action'),
            tooltip: '整舍批量出库',
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
            ref.invalidate(housesProvider);
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
          return ListView(
            key: const ValueKey('house-cage-list-scroll'),
            controller: _scrollController,
            padding: AppSpacing.pagePadding,
            children: [
              _PageHeader(house: house),
              const SizedBox(height: 12),
              CageManagementSection(
                house: house,
                scrollController: _scrollController,
              ),
            ],
          );
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
      if (house.id == widget.houseId) {
        return house;
      }
    }
    return null;
  }
}

class _PageHeader extends StatelessWidget {
  const _PageHeader({required this.house});

  final RabbitHouse house;

  @override
  Widget build(BuildContext context) {
    final largeText = MediaQuery.textScalerOf(context).scale(10) / 10 >= 1.3;
    return SectionCard(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
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
                  maxLines: largeText ? 2 : 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 4),
                Text(
                  '点击具体笼位进入管理 · ${house.layoutLabel}',
                  maxLines: largeText ? 2 : 1,
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
