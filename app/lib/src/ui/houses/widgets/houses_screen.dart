import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

class HousesScreen extends ConsumerWidget {
  const HousesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final houses = ref.watch(housesProvider);

    return AppPage(
      title: '兔舍',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: () => ref.invalidate(housesProvider),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: houses.when(
        data: (items) => _HousesContent(houses: items),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(housesProvider),
        ),
      ),
    );
  }
}

class _HousesContent extends ConsumerStatefulWidget {
  const _HousesContent({required this.houses});

  final List<RabbitHouse> houses;

  @override
  ConsumerState<_HousesContent> createState() => _HousesContentState();
}

class _HousesContentState extends ConsumerState<_HousesContent> {
  static const _infiniteScrollThreshold = 30;
  static const _batchSize = 20;

  late int _visibleHouseCount = _initialVisibleCount(widget.houses.length);
  var _loadingMore = false;

  @override
  void didUpdateWidget(covariant _HousesContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.houses.length != widget.houses.length) {
      _visibleHouseCount = _initialVisibleCount(widget.houses.length);
      _loadingMore = false;
    }
  }

  int _initialVisibleCount(int total) {
    return total > _infiniteScrollThreshold ? _batchSize : total;
  }

  bool _handleScroll(ScrollNotification notification) {
    if (notification.metrics.extentAfter > 240 ||
        _loadingMore ||
        _visibleHouseCount >= widget.houses.length) {
      return false;
    }

    setState(() => _loadingMore = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _visibleHouseCount = (_visibleHouseCount + _batchSize).clamp(
          0,
          widget.houses.length,
        );
        _loadingMore = false;
      });
    });
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final houses = widget.houses;
    if (houses.isEmpty) {
      return EmptyState(
        icon: Icons.storefront_outlined,
        title: '尚未加入兔舍',
        message: '可以创建兔舍，或等待管理员通过手机号邀请后刷新。',
        actionLabel: '创建兔舍',
        onAction: () => _showCreateHouseSheet(context),
      );
    }

    final hasMore = _visibleHouseCount < houses.length;
    return NotificationListener<ScrollNotification>(
      onNotification: _handleScroll,
      child: ListView.builder(
        key: const ValueKey('house-list'),
        padding: AppSpacing.pagePadding,
        itemCount: 1 + _visibleHouseCount + (hasMore ? 1 : 0),
        itemBuilder: (context, index) {
          if (index == 0) {
            return SectionCard(
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '兔舍列表',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '共 ${houses.length} 个兔舍，点击兔舍进入管理。',
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ],
                    ),
                  ),
                  TextButton.icon(
                    key: const ValueKey('house-create-entry'),
                    onPressed: () => _showCreateHouseSheet(context),
                    icon: const Icon(Icons.add),
                    label: const Text('创建'),
                  ),
                ],
              ),
            );
          }

          if (index > _visibleHouseCount) {
            return const Padding(
              key: ValueKey('house-list-loading-more'),
              padding: EdgeInsets.symmetric(vertical: 18),
              child: Center(
                child: SizedBox.square(
                  dimension: 24,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            );
          }

          final house = houses[index - 1];
          return Padding(
            padding: EdgeInsets.only(top: index == 1 ? 12 : 10),
            child: _HouseListCard(
              house: house,
              onTap: () {
                ref.read(authControllerProvider.notifier).setHouseId(house.id);
                context.go('/houses/${house.id}');
              },
            ),
          );
        },
      ),
    );
  }

  Future<void> _showCreateHouseSheet(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useRootNavigator: true,
      useSafeArea: true,
      builder: (_) => _CreateHouseSheet(hostContext: context),
    );
  }
}

class _HouseListCard extends StatelessWidget {
  const _HouseListCard({
    required this.house,
    required this.onTap,
  });

  final RabbitHouse house;
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
        key: ValueKey('house-card-${house.id}'),
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: palette.surfaceSubtle,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  Icons.storefront_outlined,
                  color: palette.muted,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      house.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      house.remark.isEmpty
                          ? '${house.layoutLabel} · 点击进入管理'
                          : '${house.remark} · 点击进入管理',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Icon(Icons.chevron_right, color: palette.muted),
            ],
          ),
        ),
      ),
    );
  }
}

class _CreateHouseSheet extends ConsumerStatefulWidget {
  const _CreateHouseSheet({required this.hostContext});

  final BuildContext hostContext;

  @override
  ConsumerState<_CreateHouseSheet> createState() => _CreateHouseSheetState();
}

class _CreateHouseSheetState extends ConsumerState<_CreateHouseSheet> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _remarkController = TextEditingController();
  final _rowsController = TextEditingController(text: '0');
  final _colsController = TextEditingController(text: '0');
  final _layersController = TextEditingController(text: '0');
  var _saving = false;

  @override
  void dispose() {
    _nameController.dispose();
    _remarkController.dispose();
    _rowsController.dispose();
    _colsController.dispose();
    _layersController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 18, 20, 20 + inset),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('创建兔舍', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              TextFormField(
                key: const ValueKey('house-create-name'),
                controller: _nameController,
                decoration: const InputDecoration(labelText: '兔舍名称'),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return '请输入兔舍名称';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: _NumberField(
                      fieldKey: const ValueKey('house-create-rows'),
                      label: '排数',
                      controller: _rowsController,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _NumberField(
                      fieldKey: const ValueKey('house-create-cols'),
                      label: '列数',
                      controller: _colsController,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _NumberField(
                      fieldKey: const ValueKey('house-create-layers'),
                      label: '层数',
                      controller: _layersController,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextFormField(
                key: const ValueKey('house-create-remark'),
                controller: _remarkController,
                decoration: const InputDecoration(labelText: '备注（可选）'),
              ),
              const SizedBox(height: 18),
              ElevatedButton(
                key: const ValueKey('house-create-submit'),
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('保存兔舍'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() => _saving = true);
    try {
      final house = await ref.read(houseRepositoryProvider).createHouse(
            name: _nameController.text.trim(),
            rows: int.tryParse(_rowsController.text) ?? 0,
            cols: int.tryParse(_colsController.text) ?? 0,
            layers: int.tryParse(_layersController.text) ?? 0,
            remark: _remarkController.text.trim(),
          );
      ref.invalidate(housesProvider);
      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseCagesProvider(house.id));
      ref.invalidate(houseRabbitsProvider(house.id));
      if (mounted) {
        Navigator.of(context).pop();
        if (widget.hostContext.mounted) {
          ref.read(authControllerProvider.notifier).setHouseId(house.id);
          widget.hostContext.go('/houses/${house.id}');
        }
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

class _NumberField extends StatelessWidget {
  const _NumberField({
    required this.label,
    required this.controller,
    this.fieldKey,
  });

  final String label;
  final TextEditingController controller;
  final Key? fieldKey;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: fieldKey,
      controller: controller,
      decoration: InputDecoration(labelText: label),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      validator: (value) {
        if (value == null || value.isEmpty) {
          return '必填';
        }
        return null;
      },
    );
  }
}
