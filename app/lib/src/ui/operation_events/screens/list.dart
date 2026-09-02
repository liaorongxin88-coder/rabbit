import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/operation_events/repository.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/operation_events/event.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

class HouseOperationEventsScreen extends ConsumerStatefulWidget {
  const HouseOperationEventsScreen({super.key, required this.houseId});

  final int houseId;

  @override
  ConsumerState<HouseOperationEventsScreen> createState() =>
      _HouseOperationEventsScreenState();
}

class _HouseOperationEventsScreenState
    extends ConsumerState<HouseOperationEventsScreen> {
  static const _pageSize = 50;

  var _initialRequestScheduled = false;
  var _isLoadingInitial = true;
  var _isLoadingMore = false;
  var _hasMore = false;
  List<OperationEvent> _items = const [];
  String? _nextCursor;
  Object? _initialError;
  Object? _loadMoreError;

  bool get _canLoadMore =>
      _hasMore && (_nextCursor?.trim().isNotEmpty ?? false);

  @override
  Widget build(BuildContext context) {
    final permission = ref.watch(housePermissionProvider(widget.houseId));

    return AppPage(
      title: '操作记录',
      parentRoute: '/houses/${widget.houseId}',
      actions: [
        if (_canViewAudit(permission.valueOrNull))
          IconButton(
            tooltip: '刷新操作记录',
            onPressed: _isLoadingInitial ? null : _loadInitial,
            icon: const Icon(Icons.refresh),
          ),
      ],
      child: permission.when(
        data: (value) {
          if (!_canViewAudit(value)) {
            return const _OperationEventsPermissionDenied();
          }
          _scheduleInitialRequest();
          return _buildEventList();
        },
        loading: () => const Center(
          key: ValueKey('operation-events-permission-loading'),
          child: CircularProgressIndicator(),
        ),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () =>
              ref.invalidate(housePermissionProvider(widget.houseId)),
        ),
      ),
    );
  }

  bool _canViewAudit(HousePermission? permission) {
    return permission != null &&
        (permission.isAdmin || permission.hasPermission('rabbit:audit:list'));
  }

  void _scheduleInitialRequest() {
    if (_initialRequestScheduled) {
      return;
    }
    _initialRequestScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _loadInitial();
      }
    });
  }

  Future<void> _loadInitial() async {
    setState(() {
      _isLoadingInitial = true;
      _initialError = null;
      _loadMoreError = null;
    });
    try {
      final page =
          await ref.read(operationEventsRepositoryProvider).listOperationEvents(
                houseId: widget.houseId,
                query: const OperationEventsQuery(limit: _pageSize),
              );
      if (!mounted) {
        return;
      }
      setState(() {
        _items = page.items;
        _nextCursor = page.nextCursor;
        _hasMore = page.hasMore;
        _isLoadingInitial = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _initialError = error;
        _isLoadingInitial = false;
      });
    }
  }

  Future<void> _loadMore() async {
    final cursor = _nextCursor;
    if (_isLoadingMore || !_canLoadMore || cursor == null) {
      return;
    }
    setState(() {
      _isLoadingMore = true;
      _loadMoreError = null;
    });
    try {
      final page =
          await ref.read(operationEventsRepositoryProvider).listOperationEvents(
                houseId: widget.houseId,
                query: OperationEventsQuery(cursor: cursor, limit: _pageSize),
              );
      if (!mounted) {
        return;
      }
      setState(() {
        _items = [..._items, ...page.items];
        _nextCursor = page.nextCursor;
        _hasMore = page.hasMore;
        _isLoadingMore = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loadMoreError = error;
        _isLoadingMore = false;
      });
    }
  }

  Widget _buildEventList() {
    if (_isLoadingInitial) {
      return const Center(
        key: ValueKey('operation-events-loading'),
        child: CircularProgressIndicator(),
      );
    }
    final error = _initialError;
    if (error != null) {
      return Center(
        key: const ValueKey('operation-events-error'),
        child: ErrorState(
          message: error.toString(),
          onRetry: _loadInitial,
        ),
      );
    }
    if (_items.isEmpty) {
      return const _OperationEventsEmpty();
    }

    final footer = _eventListFooter();
    final itemCount = _items.length + (footer == null ? 0 : 1);
    return RefreshIndicator(
      onRefresh: _loadInitial,
      child: ListView.separated(
        key: const ValueKey('operation-events-list'),
        padding: AppSpacing.pagePadding,
        itemCount: itemCount,
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          if (index == _items.length) {
            return footer!;
          }
          return _OperationEventCard(event: _items[index]);
        },
      ),
    );
  }

  Widget? _eventListFooter() {
    if (_isLoadingMore) {
      return const Padding(
        key: ValueKey('operation-events-loading-more'),
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    final error = _loadMoreError;
    if (error != null) {
      return Center(
        child: TextButton.icon(
          key: const ValueKey('operation-events-retry-more'),
          onPressed: _loadMore,
          icon: const Icon(Icons.refresh),
          label: Text('重新加载：${error.toString()}'),
        ),
      );
    }
    if (_canLoadMore) {
      return Center(
        child: OutlinedButton.icon(
          key: const ValueKey('operation-events-load-more'),
          onPressed: _loadMore,
          icon: const Icon(Icons.expand_more),
          label: const Text('加载更多'),
        ),
      );
    }
    return null;
  }
}

class _OperationEventsPermissionDenied extends StatelessWidget {
  const _OperationEventsPermissionDenied();

  @override
  Widget build(BuildContext context) {
    return const EmptyState(
      key: ValueKey('operation-events-permission-denied'),
      icon: Icons.lock_outline,
      title: '没有查看权限',
      message: '当前账号没有查看操作记录的权限。',
    );
  }
}

class _OperationEventsEmpty extends StatelessWidget {
  const _OperationEventsEmpty();

  @override
  Widget build(BuildContext context) {
    return const EmptyState(
      key: ValueKey('operation-events-empty'),
      icon: Icons.history_toggle_off_outlined,
      title: '这里还没有操作记录',
      message: '投喂和接种等已留痕的操作会显示在这里。',
    );
  }
}

class _OperationEventCard extends StatelessWidget {
  const _OperationEventCard({required this.event});

  final OperationEvent event;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final stageLabel = _stageLabel(event);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            event.title,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          Text(
            event.occurredAtLabel,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 12),
          Text(
            '操作代码：${event.operationCode}',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          Text(
            '事件类型：${event.eventType}',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 8),
          Text(
            '对象：${event.targetLabel}',
            maxLines: 4,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (stageLabel != null) ...[
            const SizedBox(height: 4),
            Text(
              stageLabel,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
          const SizedBox(height: 8),
          Text(
            '操作人：${event.operatorLabel}',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: palette.text,
                ),
          ),
        ],
      ),
    );
  }

  String? _stageLabel(OperationEvent event) {
    final from = event.fromStage?.trim() ?? '';
    final to = event.toStage?.trim() ?? '';
    if (from.isEmpty && to.isEmpty) {
      return null;
    }
    return '阶段：${from.isEmpty ? '未记录' : from} -> ${to.isEmpty ? '未记录' : to}';
  }
}
