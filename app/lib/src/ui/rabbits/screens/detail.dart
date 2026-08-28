import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_membership.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/screens/list.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/abnormal.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/bind_batch.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/entry.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/move.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/replacement.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/sale.dart';

class RabbitDetailScreen extends ConsumerWidget {
  const RabbitDetailScreen({
    super.key,
    required this.houseId,
    required this.rabbitId,
  });

  final int houseId;
  final int rabbitId;

  RabbitDetailRequest get _request => RabbitDetailRequest(
        houseId: houseId,
        rabbitId: rabbitId,
      );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rabbit = ref.watch(rabbitDetailProvider(_request));
    final permission = ref.watch(housePermissionProvider(houseId));
    final canCreateAbnormal = permission.valueOrNull?.canEdit == true &&
        rabbit.valueOrNull?.isActive == true;

    return AppPage(
      title: '兔只详情',
      fallbackBackLocation: '/houses/$houseId/rabbits',
      actions: [
        if (canCreateAbnormal)
          IconButton(
            key: const ValueKey('rabbit-add-abnormal-action'),
            tooltip: '新增异常记录',
            onPressed: () async {
              final currentRabbit = rabbit.valueOrNull;
              if (currentRabbit == null) {
                return;
              }
              final recorded = await showRabbitAbnormalSheet(
                context: context,
                houseId: houseId,
                rabbit: currentRabbit,
              );
              if (recorded && context.mounted) {
                _refresh(ref);
              }
            },
            icon: const Icon(Icons.report_problem_outlined),
          ),
        IconButton(
          tooltip: '刷新兔只详情',
          onPressed: () => _refresh(ref),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: rabbit.when(
        data: (value) => _RabbitDetailContent(
          request: _request,
          rabbit: value,
          onRefresh: () => _refresh(ref),
        ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(rabbitDetailProvider(_request)),
        ),
      ),
    );
  }

  void _refresh(WidgetRef ref) {
    final membership = RabbitBatchMembershipRequest(
      houseId: houseId,
      rabbitId: rabbitId,
    );
    ref.invalidate(rabbitDetailProvider(_request));
    ref.invalidate(houseRabbitsProvider(houseId));
    ref.invalidate(houseCagesProvider(houseId));
    ref.invalidate(cageRabbitsProvider);
    ref.invalidate(cageSummaryProvider);
    ref.invalidate(rabbitBatchMembershipsProvider(membership));
    ref.invalidate(
      rabbitBatchMembershipsProvider(
        RabbitBatchMembershipRequest(
          houseId: houseId,
          rabbitId: rabbitId,
          active: false,
        ),
      ),
    );
    ref.invalidate(
      rabbitReproTasksProvider(
        RabbitReproTasksRequest(houseId: houseId, rabbitId: rabbitId),
      ),
    );
    ref.invalidate(homeEventsProvider);
  }
}

class _RabbitDetailContent extends ConsumerWidget {
  const _RabbitDetailContent({
    required this.request,
    required this.rabbit,
    required this.onRefresh,
  });

  final RabbitDetailRequest request;
  final Rabbit rabbit;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cages = ref.watch(houseCagesProvider(request.houseId));
    final permission = ref.watch(housePermissionProvider(request.houseId));
    final activeMemberships = ref.watch(
      rabbitBatchMembershipsProvider(
        RabbitBatchMembershipRequest(
          houseId: request.houseId,
          rabbitId: rabbit.id,
        ),
      ),
    );

    if (cages.isLoading || permission.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (cages.hasError) {
      return ErrorState(
        message: cages.error.toString(),
        onRetry: () => ref.invalidate(houseCagesProvider(request.houseId)),
      );
    }
    if (permission.hasError) {
      return ErrorState(
        message: permission.error.toString(),
        onRetry: () => ref.invalidate(housePermissionProvider(request.houseId)),
      );
    }

    final cageItems = cages.valueOrNull ?? const <Cage>[];
    final currentPermission = permission.valueOrNull ??
        const HousePermission(perms: 'view', isAdmin: false);
    final cageDisplay = _cageDisplay(cageItems, rabbit.cageId);
    final canEdit = currentPermission.canEdit;
    final canOperate = canEdit && rabbit.isActive;
    final canConvertToReplacement =
        currentPermission.canControl && rabbit.isActive && rabbit.type == '2';
    final canSell = rabbit.isActive &&
        (rabbit.type == '0' || rabbit.type == '1') &&
        currentPermission.canAddSales;
    final boundBatchIds = (activeMemberships.valueOrNull ?? const [])
        .where((membership) => membership.isActive)
        .map((membership) => membership.batchId)
        .toSet();

    return RabbitDetailSheet(
      key: ValueKey(
        'rabbit-detail-page-${rabbit.id}-${rabbit.cageId}-${rabbit.isActive}',
      ),
      houseId: request.houseId,
      rabbit: rabbit,
      cageDisplay: cageDisplay,
      canEdit: canEdit,
      pageMode: true,
      onChanged: onRefresh,
      onMove: canOperate
          ? () async {
              await showRabbitMoveCageSheet(
                context: context,
                houseId: request.houseId,
                rabbit: rabbit,
                cages: cageItems,
              );
              if (context.mounted) {
                onRefresh();
              }
            }
          : null,
      onEdit: canEdit
          ? () async {
              await showRabbitEditSheet(
                context: context,
                houseId: request.houseId,
                rabbit: rabbit,
                cages: cageItems,
              );
              if (context.mounted) {
                onRefresh();
              }
            }
          : null,
      onSale: canSell
          ? () async {
              final sold = await showRabbitSaleSheet(
                context: context,
                houseId: request.houseId,
                rabbit: rabbit,
              );
              if (sold && context.mounted) {
                onRefresh();
              }
            }
          : null,
      onOutbound: canOperate && rabbit.type == '2'
          ? () => context.push(
                '/houses/${request.houseId}/outbound'
                '?entryType=RABBIT&rabbitId=${rabbit.id}',
              )
          : null,
      onConvertToReplacement: canConvertToReplacement
          ? () async {
              final converted = await showRabbitReplacementSheet(
                context: context,
                houseId: request.houseId,
                rabbit: rabbit,
              );
              if (converted && context.mounted) {
                onRefresh();
              }
            }
          : null,
      onOpenBatch: (batchId) => context.push(
        '/houses/${request.houseId}/batches/$batchId',
      ),
      onBindBatch: canOperate &&
              activeMemberships.hasValue &&
              rabbitBatchPurposeLabel(rabbit) != null
          ? () async {
              final bound = await showRabbitBindBatchSheet(
                context: context,
                houseId: request.houseId,
                rabbit: rabbit,
                excludedBatchIds: boundBatchIds,
              );
              if (bound && context.mounted) {
                onRefresh();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('兔只已绑定批次')),
                );
              }
            }
          : null,
      onRemoveBatch: canEdit
          ? (membership) => _removeBatchTag(
                context,
                ref,
                membership,
              )
          : null,
    );
  }

  Future<void> _removeBatchTag(
    BuildContext context,
    WidgetRef ref,
    RabbitBatchMembership membership,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('移除批次标签'),
        content: Text(
          '从批次 #${membership.batchId} 移除兔 #${rabbit.id}？'
          '此操作只解除标签关系，不会终止繁育周期或让兔离场。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            key: const ValueKey('confirm-remove-batch-tag'),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('确认移除'),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) {
      return;
    }

    final requestId = BatchWriteRequestController().requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'removeRabbitBatchTag',
        'houseId': request.houseId,
        'batchId': membership.batchId,
        'rabbitIds': [rabbit.id],
      }),
    );
    try {
      await ref.read(batchRepositoryProvider).removeBatchRabbit(
            houseId: request.houseId,
            batchId: membership.batchId,
            rabbitId: rabbit.id,
            requestId: requestId,
          );
      if (context.mounted) {
        onRefresh();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('批次标签已移除')),
        );
      }
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              error is ApiException ? error.message : '移除失败，请检查网络后重试',
            ),
          ),
        );
      }
    }
  }

  String _cageDisplay(List<Cage> cages, int cageId) {
    for (final cage in cages) {
      if (cage.id == cageId) {
        return cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;
      }
    }
    return cageId > 0 ? '#$cageId' : '未关联';
  }
}
