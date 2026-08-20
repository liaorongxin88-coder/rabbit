import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

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
    final permission = ref.watch(housePermissionProvider(house.id));

    return permission.when(
      data: (perm) => ListView(
        padding: AppSpacing.pagePadding,
        children: [
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
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
                            '${house.layoutLabel} · 我的角色：${perm.roleLabel}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                    if (perm.canEditHouse)
                      IconButton(
                        key: const ValueKey('house-edit-name-entry'),
                        tooltip: '编辑兔舍信息',
                        onPressed: () => _showEditHouseSheet(context, house),
                        icon: const Icon(Icons.edit_outlined),
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                Divider(height: 1, color: palette.line),
                const SizedBox(height: 12),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.notes_outlined, size: 19, color: palette.muted),
                    const SizedBox(width: 9),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '兔舍备注',
                            style: Theme.of(context)
                                .textTheme
                                .labelLarge
                                ?.copyWith(fontWeight: FontWeight.w800),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            house.remark.trim().isEmpty ? '暂无备注' : house.remark,
                            key: const ValueKey('house-remark-value'),
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _OverviewMetrics(cages: cages, rabbits: rabbits),
          const SizedBox(height: 12),
          _DetailEntryCard(
            icon: Icons.grid_view_rounded,
            iconColor: palette.primary,
            iconBackground: palette.primarySoft,
            title: '笼位管理',
            message: perm.canEdit
                ? '新增笼位、搜索笼位，并从具体笼位录入兔子。'
                : '您当前为只读权限，可查看笼位但无法录入或修改。',
            actionLabel: '进入笼位',
            onTap: () => context.go('/houses/${house.id}/cages'),
          ),
          const SizedBox(height: 10),
          _DetailEntryCard(
            key: const ValueKey('house-batches-entry'),
            icon: Icons.playlist_add_check_outlined,
            iconColor: palette.warning,
            iconBackground: palette.warningSoft,
            title: '生产批次',
            message:
                perm.canEdit ? '查看全部批次，按状态筛选或创建新的生产批次。' : '查看当前兔舍全部批次及其生产周期状态。',
            actionLabel: '查看批次',
            onTap: () => context.go('/houses/${house.id}/batches'),
          ),
          if (perm.canControl) ...[
            const SizedBox(height: 10),
            _DetailEntryCard(
              key: const ValueKey('house-production-settings-entry'),
              icon: Icons.calendar_month_outlined,
              iconColor: palette.warning,
              iconBackground: palette.warningSoft,
              title: '生产设置',
              message: '配置当前兔舍的生产周期，以及你在首页看到的事件提醒。',
              actionLabel: '配置',
              onTap: () => context.push(
                '/houses/${house.id}/settings/production?name=${Uri.encodeComponent(house.name)}',
              ),
            ),
          ],
          if (perm.canManageMembers) ...[
            const SizedBox(height: 10),
            _DetailEntryCard(
              icon: Icons.groups_outlined,
              iconColor: palette.primary,
              iconBackground: palette.primarySoft,
              title: '人员管理',
              message: '管理兔舍成员权限：添加生产人员、设备管理员、游客，或新增共同所有者。',
              actionLabel: '管理',
              onTap: () => context.push(
                '/houses/${house.id}/members?name=${Uri.encodeComponent(house.name)}',
              ),
            ),
          ],
          if (!perm.isAdmin) ...[
            const SizedBox(height: 18),
            _LeaveHouseSection(house: house),
          ],
        ],
      ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => ErrorState(
        message: error.toString(),
        onRetry: () => ref.invalidate(housePermissionProvider(house.id)),
      ),
    );
  }

  Future<void> _showEditHouseSheet(
    BuildContext context,
    RabbitHouse house,
  ) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useRootNavigator: true,
      useSafeArea: true,
      builder: (_) => _EditHouseSheet(
        house: house,
        hostContext: context,
      ),
    );
  }
}

class _EditHouseSheet extends ConsumerStatefulWidget {
  const _EditHouseSheet({
    required this.house,
    required this.hostContext,
  });

  final RabbitHouse house;
  final BuildContext hostContext;

  @override
  ConsumerState<_EditHouseSheet> createState() => _EditHouseSheetState();
}

class _EditHouseSheetState extends ConsumerState<_EditHouseSheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController =
      TextEditingController(text: widget.house.name);
  late final TextEditingController _remarkController =
      TextEditingController(text: widget.house.remark);
  var _saving = false;

  @override
  void dispose() {
    _nameController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 18, 20, 20 + inset),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                '编辑兔舍信息',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 16),
              TextFormField(
                key: const ValueKey('house-edit-name-field'),
                controller: _nameController,
                autofocus: true,
                maxLength: 100,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(labelText: '兔舍名称'),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return '请输入兔舍名称';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 10),
              TextFormField(
                key: const ValueKey('house-edit-remark-field'),
                controller: _remarkController,
                minLines: 3,
                maxLines: 5,
                keyboardType: TextInputType.multiline,
                textInputAction: TextInputAction.newline,
                decoration: const InputDecoration(
                  labelText: '兔舍备注',
                  hintText: '例如位置、用途或现场注意事项',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 14),
              FilledButton(
                key: const ValueKey('house-edit-name-submit'),
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('保存兔舍信息'),
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
    final name = _nameController.text.trim();
    final remark = _remarkController.text.trim();
    final nameChanged = name != widget.house.name;
    final remarkChanged = remark != widget.house.remark.trim();
    if (!nameChanged && !remarkChanged) {
      Navigator.of(context).pop();
      return;
    }

    setState(() => _saving = true);
    try {
      await ref.read(houseRepositoryProvider).updateHouse(
            houseId: widget.house.id,
            name: name,
            remark: remark,
          );
      ref.invalidate(housesProvider);
      if (!mounted) {
        return;
      }
      Navigator.of(context).pop();
      final hostContext = widget.hostContext;
      if (hostContext.mounted) {
        ScaffoldMessenger.of(hostContext).showSnackBar(
          SnackBar(
            content: Text(
              nameChanged && remarkChanged
                  ? '兔舍信息已更新'
                  : nameChanged
                      ? '兔舍名称已更新为“$name”'
                      : '兔舍备注已更新',
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

class _LeaveHouseSection extends ConsumerStatefulWidget {
  const _LeaveHouseSection({required this.house});

  final RabbitHouse house;

  @override
  ConsumerState<_LeaveHouseSection> createState() => _LeaveHouseSectionState();
}

class _LeaveHouseSectionState extends ConsumerState<_LeaveHouseSection> {
  var _leaving = false;

  Future<void> _confirmLeave() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('退出兔舍'),
        content: Text('确定退出「${widget.house.name}」吗？退出后将无法查看该兔舍数据。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('确认退出'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      return;
    }

    setState(() => _leaving = true);
    try {
      await ref
          .read(houseRepositoryProvider)
          .leaveHouse(houseId: widget.house.id);
      ref.invalidate(housesProvider);
      ref.invalidate(housePermissionProvider(widget.house.id));
      final houses = await ref.read(housesProvider.future);
      final nextHouseId = houses.isNotEmpty ? houses.first.id : 0;
      await ref.read(authControllerProvider.notifier).setHouseId(nextHouseId);
      if (!mounted) {
        return;
      }
      context.go('/houses');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('已退出「${widget.house.name}」')),
      );
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
        setState(() => _leaving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('退出兔舍', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 6),
          Text(
            '您不是该兔舍所有者。若不再参与此兔舍，可主动退出。',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _leaving ? null : _confirmLeave,
            icon: _leaving
                ? SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: palette.danger,
                    ),
                  )
                : Icon(Icons.logout, color: palette.danger),
            label: Text(
              '退出兔舍',
              style: TextStyle(color: palette.danger),
            ),
          ),
        ],
      ),
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
              status: _loadStatus(cages),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: _MetricBlock(
              label: '兔只',
              value: rabbitCount == null ? '--' : '$rabbitCount',
              status: _loadStatus(rabbits),
            ),
          ),
        ],
      ),
    );
  }

  String _loadStatus(AsyncValue<List<dynamic>> value) {
    if (value.valueOrNull != null) {
      return value.isLoading ? '正在更新' : '已全部加载';
    }
    return value.hasError ? '加载失败' : '正在加载';
  }
}

class _MetricBlock extends StatelessWidget {
  const _MetricBlock({
    required this.label,
    required this.value,
    required this.status,
  });

  final String label;
  final String value;
  final String status;

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
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 3),
          Text(
            status,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}

class _DetailEntryCard extends StatelessWidget {
  const _DetailEntryCard({
    super.key,
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
