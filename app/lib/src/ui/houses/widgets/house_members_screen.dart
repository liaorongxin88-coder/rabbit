import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/house_member.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

class HouseMembersScreen extends ConsumerStatefulWidget {
  const HouseMembersScreen({
    super.key,
    required this.houseId,
    this.houseName = '',
  });

  final int houseId;
  final String houseName;

  @override
  ConsumerState<HouseMembersScreen> createState() => _HouseMembersScreenState();
}

class _HouseMembersScreenState extends ConsumerState<HouseMembersScreen> {
  final _searchController = TextEditingController();
  var _searching = false;
  var _adding = false;
  List<UserSearchItem> _candidates = const [];
  String? _searchError;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final members = ref.watch(houseMembersProvider(widget.houseId));
    final permission = ref.watch(housePermissionProvider(widget.houseId));
    final title =
        widget.houseName.isEmpty ? '人员管理' : '${widget.houseName} · 人员管理';

    return AppPage(
      title: title,
      child: permission.when(
        data: (perm) {
          if (!perm.canManageMembers) {
            return const EmptyState(
              icon: Icons.lock_outline,
              title: '无管理权限',
              message: '仅兔舍管理员可以查看和管理成员。',
            );
          }
          return members.when(
            data: (items) => _buildBody(context, items),
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (error, _) => ErrorState(
              message: error.toString(),
              onRetry: () =>
                  ref.invalidate(houseMembersProvider(widget.houseId)),
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () =>
              ref.invalidate(housePermissionProvider(widget.houseId)),
        ),
      ),
    );
  }

  Widget _buildBody(BuildContext context, List<HouseMember> members) {
    final palette = AppPalette.of(context);
    final currentUserId =
        ref.read(authControllerProvider).valueOrNull?.userId ?? 0;

    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('添加成员', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text(
                '搜索同商户下账号，添加为生产人员、设备管理员或游客。',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _searchController,
                decoration: InputDecoration(
                  hintText: '输入用户名搜索',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _searchController.text.trim().isEmpty
                      ? null
                      : IconButton(
                          icon: const Icon(Icons.close),
                          onPressed: () {
                            _searchController.clear();
                            setState(() {
                              _candidates = const [];
                              _searchError = null;
                            });
                          },
                        ),
                ),
                onSubmitted: (_) => _searchUsers(),
              ),
              const SizedBox(height: 10),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton.icon(
                  onPressed: _searching ? null : _searchUsers,
                  icon: _searching
                      ? const SizedBox.square(
                          dimension: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.person_search_outlined),
                  label: const Text('查找账号'),
                ),
              ),
              if (_searchError != null) ...[
                const SizedBox(height: 8),
                Text(
                  _searchError!,
                  style: TextStyle(color: palette.danger),
                ),
              ],
              if (_candidates.isNotEmpty) ...[
                const SizedBox(height: 12),
                for (final candidate in _candidates)
                  _CandidateTile(
                    userName: candidate.userName,
                    adding: _adding,
                    onAddOrdinary: () =>
                        _addMember(candidate.userName, perms: 'edit'),
                    onAddControl: () =>
                        _addMember(candidate.userName, perms: 'control'),
                    onAddGuest: () =>
                        _addMember(candidate.userName, perms: 'view'),
                  ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          padding: EdgeInsets.zero,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                child: Text(
                  '当前成员 (${members.length})',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              if (members.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: Text('暂无其他成员'),
                )
              else
                for (var i = 0; i < members.length; i++) ...[
                  if (i > 0) Divider(height: 1, color: palette.line),
                  _MemberTile(
                    member: members[i],
                    isSelf: members[i].userId == currentUserId,
                    onTap: () => _showMemberActions(members[i]),
                  ),
                ],
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _searchUsers() async {
    final keyword = _searchController.text.trim();
    if (keyword.isEmpty) {
      setState(() => _searchError = '请输入要搜索的用户名');
      return;
    }
    setState(() {
      _searching = true;
      _searchError = null;
    });
    try {
      final items =
          await ref.read(houseRepositoryProvider).searchMemberCandidates(
                houseId: widget.houseId,
                keyword: keyword,
              );
      if (!mounted) {
        return;
      }
      setState(() {
        _candidates = items;
        if (items.isEmpty) {
          _searchError = '未找到可添加的同商户账号';
        }
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _candidates = const [];
        _searchError = error is ApiException ? error.message : error.toString();
      });
    } finally {
      if (mounted) {
        setState(() => _searching = false);
      }
    }
  }

  Future<void> _addMember(String userName, {required String perms}) async {
    setState(() => _adding = true);
    try {
      await ref.read(houseRepositoryProvider).addMember(
            houseId: widget.houseId,
            userName: userName,
            perms: perms,
          );
      ref.invalidate(houseMembersProvider(widget.houseId));
      if (mounted) {
        setState(() {
          _candidates = const [];
          _searchError = null;
        });
        _searchController.clear();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已添加 $userName')),
        );
      }
    } catch (error) {
      if (mounted) {
        final message =
            error is ApiException ? error.message : error.toString();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message)),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _adding = false);
      }
    }
  }

  Future<void> _showMemberActions(HouseMember member) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                title: Text(member.userName),
                subtitle: Text('${member.roleLabel} · ${member.permLabel}'),
              ),
              if (!member.isAdmin)
                ListTile(
                  leading: const Icon(Icons.edit_outlined),
                  title: const Text('设为生产人员'),
                  onTap: () => Navigator.pop(context, 'ordinary'),
                ),
              if (!member.isAdmin)
                ListTile(
                  leading: const Icon(Icons.settings_remote_outlined),
                  title: const Text('设为设备管理员'),
                  onTap: () => Navigator.pop(context, 'control'),
                ),
              if (!member.isAdmin)
                ListTile(
                  leading: const Icon(Icons.visibility_outlined),
                  title: const Text('设为游客（只读）'),
                  onTap: () => Navigator.pop(context, 'guest'),
                ),
              if (!member.isAdmin)
                ListTile(
                  leading: const Icon(Icons.admin_panel_settings_outlined),
                  title: const Text('转让管理员'),
                  onTap: () => Navigator.pop(context, 'transfer'),
                ),
              ListTile(
                leading: Icon(Icons.person_remove_outlined,
                    color: AppPalette.of(context).danger),
                title: Text(
                  member.isAdmin ? '移除管理员（需先转让）' : '移除成员',
                  style: TextStyle(color: AppPalette.of(context).danger),
                ),
                onTap: member.isAdmin
                    ? null
                    : () => Navigator.pop(context, 'remove'),
              ),
            ],
          ),
        );
      },
    );
    if (!mounted || action == null) {
      return;
    }
    switch (action) {
      case 'ordinary':
        await _updateMember(member, perms: 'edit', isAdmin: false);
      case 'control':
        await _updateMember(member, perms: 'control', isAdmin: false);
      case 'guest':
        await _updateMember(member, perms: 'view', isAdmin: false);
      case 'transfer':
        await _confirmTransferAdmin(member);
      case 'remove':
        await _confirmRemove(member);
    }
  }

  Future<void> _updateMember(
    HouseMember member, {
    required String perms,
    required bool isAdmin,
  }) async {
    try {
      await ref.read(houseRepositoryProvider).updateMember(
            houseId: widget.houseId,
            memberUserId: member.userId,
            perms: perms,
            isAdmin: isAdmin,
          );
      ref.invalidate(houseMembersProvider(widget.houseId));
      ref.invalidate(housePermissionProvider(widget.houseId));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已更新 ${member.userName}')),
        );
      }
    } catch (error) {
      if (mounted) {
        final message =
            error is ApiException ? error.message : error.toString();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message)),
        );
      }
    }
  }

  Future<void> _confirmTransferAdmin(HouseMember member) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('转让管理员'),
        content: Text('确认将兔舍管理员转让给 ${member.userName}？转让后您将变为普通成员。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('确认转让'),
          ),
        ],
      ),
    );
    if (ok != true) {
      return;
    }
    await _updateMember(member, perms: 'control', isAdmin: true);
  }

  Future<void> _confirmRemove(HouseMember member) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('移除成员'),
        content: Text('确认将 ${member.userName} 移出兔舍？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('移除'),
          ),
        ],
      ),
    );
    if (ok != true) {
      return;
    }
    try {
      await ref.read(houseRepositoryProvider).removeMember(
            houseId: widget.houseId,
            memberUserId: member.userId,
          );
      ref.invalidate(houseMembersProvider(widget.houseId));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已移除 ${member.userName}')),
        );
      }
    } catch (error) {
      if (mounted) {
        final message =
            error is ApiException ? error.message : error.toString();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message)),
        );
      }
    }
  }
}

class _CandidateTile extends StatelessWidget {
  const _CandidateTile({
    required this.userName,
    required this.adding,
    required this.onAddOrdinary,
    required this.onAddControl,
    required this.onAddGuest,
  });

  final String userName;
  final bool adding;
  final VoidCallback onAddOrdinary;
  final VoidCallback onAddControl;
  final VoidCallback onAddGuest;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Wrap(
        spacing: 8,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          SizedBox(
            width: 120,
            child:
                Text(userName, style: Theme.of(context).textTheme.titleMedium),
          ),
          TextButton(
            onPressed: adding ? null : onAddGuest,
            child: const Text('游客'),
          ),
          OutlinedButton(
            onPressed: adding ? null : onAddOrdinary,
            child: const Text('生产'),
          ),
          FilledButton(
            onPressed: adding ? null : onAddControl,
            child: const Text('设备'),
          ),
        ],
      ),
    );
  }
}

class _MemberTile extends StatelessWidget {
  const _MemberTile({
    required this.member,
    required this.isSelf,
    required this.onTap,
  });

  final HouseMember member;
  final bool isSelf;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return ListTile(
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor:
            member.isAdmin ? palette.primarySoft : palette.surfaceSubtle,
        child: Icon(
          member.isAdmin ? Icons.shield_outlined : Icons.person_outline,
          color: member.isAdmin ? palette.primary : palette.muted,
        ),
      ),
      title: Text('${member.userName}${isSelf ? '（我）' : ''}'),
      subtitle: Text('${member.roleLabel} · ${member.permLabel}'),
      trailing: const Icon(Icons.chevron_right),
    );
  }
}
