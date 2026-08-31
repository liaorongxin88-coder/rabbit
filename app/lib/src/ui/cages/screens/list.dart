import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/widgets/management.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/header.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

class HouseCagesScreen extends ConsumerStatefulWidget {
  const HouseCagesScreen({
    super.key,
    required this.houseId,
    this.parentRoute,
  });

  final int houseId;
  final String? parentRoute;

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
      parentRoute: widget.parentRoute ?? '/houses/$houseId',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: () {
            ref.invalidate(housesProvider);
            ref.invalidate(houseCagesProvider(houseId));
            ref.invalidate(houseBreedingRabbitsProvider(houseId));
            ref.invalidate(pendingCommodityAllocationCountProvider(houseId));
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
              ContextHeaderCard(
                title: house.name,
                subtitle: '点击具体笼位进入管理 · ${house.layoutLabel}',
                expandForLargeText: true,
                footer: LayoutBuilder(
                  builder: (context, constraints) {
                    final palette = AppPalette.of(context);
                    final textScale =
                        MediaQuery.textScalerOf(context).scale(16) / 16;
                    final vertical =
                        textScale > 1.35 || constraints.maxWidth < 320;
                    final actions = <Widget>[
                      OutlinedButton.icon(
                        key: const ValueKey('house-feed-entry-action'),
                        onPressed: canEdit
                            ? () => context.push('/houses/$houseId/feed')
                            : null,
                        style: OutlinedButton.styleFrom(
                          minimumSize: const Size.fromHeight(48),
                        ),
                        icon: const Icon(Icons.restaurant_outlined),
                        label: const Text('投喂录入'),
                      ),
                      FilledButton.icon(
                        key: const ValueKey('house-outbound-action'),
                        onPressed: canEdit
                            ? () => context.push(
                                  '/houses/$houseId/outbound?entryType=HOUSE',
                                )
                            : null,
                        style: FilledButton.styleFrom(
                          backgroundColor: palette.danger,
                          foregroundColor: Colors.white,
                          minimumSize: const Size.fromHeight(48),
                        ),
                        icon: const Icon(Icons.sell_outlined),
                        label: const Text('整舍出库'),
                      ),
                    ];
                    if (vertical) {
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          actions[0],
                          const SizedBox(height: 8),
                          actions[1],
                        ],
                      );
                    }
                    return Row(
                      children: [
                        Expanded(child: actions[0]),
                        const SizedBox(width: 8),
                        Expanded(child: actions[1]),
                      ],
                    );
                  },
                ),
              ),
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
