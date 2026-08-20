import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';

import 'package:rabbit_flutter/src/data/repositories/houses/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/houses/invitation.dart';
import 'package:rabbit_flutter/src/domain/houses/member.dart';
import 'package:rabbit_flutter/src/domain/profile/code.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

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
  final _identifierController = TextEditingController();
  var _inviteRole = 'STAFF';
  var _inviting = false;
  String? _inviteError;

  @override
  void dispose() {
    _identifierController.dispose();
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
      fallbackBackLocation: '/houses/${widget.houseId}',
      child: permission.when(
        data: (perm) {
          if (!perm.canManageMembers) {
            return const EmptyState(
              icon: Icons.lock_outline,
              title: '无管理权限',
              message: '仅兔舍所有者可以查看和管理成员。',
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
              Text('邀请成员', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text(
                '填对方的手机号，或对方在「我的 → 账号设置」里看到的那串号。',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 12),
              TextField(
                key: const ValueKey('house-invitation-identifier-field'),
                controller: _identifierController,
                keyboardType: TextInputType.text,
                textCapitalization: TextCapitalization.characters,
                // 这里不能再限制成纯数字：账号是字母数字混排的。
                inputFormatters: [LengthLimitingTextInputFormatter(24)],
                decoration: const InputDecoration(
                  labelText: '手机号或账号',
                  hintText: '11位手机号，或形如 R3F9A0C21B7 的账号',
                  prefixIcon: Icon(Icons.person_search_outlined),
                ),
                onChanged: (_) {
                  if (_inviteError != null) {
                    setState(() => _inviteError = null);
                  }
                },
                onSubmitted: (_) => _inviteMember(),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                key: const ValueKey('house-invitation-role-field'),
                value: _inviteRole,
                decoration: const InputDecoration(
                  labelText: '邀请角色',
                  prefixIcon: Icon(Icons.badge_outlined),
                ),
                items: const [
                  DropdownMenuItem(value: 'STAFF', child: Text('生产人员')),
                  DropdownMenuItem(value: 'MANAGER', child: Text('设备管理员')),
                  DropdownMenuItem(value: 'VIEWER', child: Text('游客（只读）')),
                ],
                onChanged: _inviting
                    ? null
                    : (value) => setState(
                          () => _inviteRole = value ?? _inviteRole,
                        ),
              ),
              if (_inviteError != null) ...[
                const SizedBox(height: 8),
                Text(
                  _inviteError!,
                  style: TextStyle(color: palette.danger),
                ),
              ],
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  key: const ValueKey('submit-house-invitation'),
                  onPressed: _inviting ? null : _inviteMember,
                  icon: _inviting
                      ? const SizedBox.square(
                          dimension: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.person_add_alt_1_outlined),
                  label: const Text('发送邀请'),
                ),
              ),
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

  Future<void> _inviteMember() async {
    final identifier = _identifierController.text.trim();
    if (!UserCode.isInvitable(identifier)) {
      setState(() => _inviteError = '请填 11 位手机号，或对方的账号（形如 R3F9A0C21B7）');
      return;
    }
    setState(() {
      _inviting = true;
      _inviteError = null;
    });
    try {
      final result = await ref.read(houseRepositoryProvider).inviteMember(
            houseId: widget.houseId,
            identifier: identifier,
            role: _inviteRole,
          );
      ref.invalidate(houseMembersProvider(widget.houseId));
      if (mounted) {
        _identifierController.clear();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_inviteOutcomeMessage(result))),
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
        setState(() => _inviting = false);
      }
    }
  }

  String _roleLabel(String role) {
    switch (role) {
      case 'OWNER':
        return '所有者';
      case 'MANAGER':
        return '设备管理员';
      case 'VIEWER':
        return '游客';
      case 'STAFF':
        return '生产人员';
      default:
        return role.isEmpty ? '成员' : role;
    }
  }

  /// 两条通道结局不同，别都糊成一句「邀请已提交」：
  /// 账号邀请的人当场就进来了，手机号邀请还得等对方登录。
  String _inviteOutcomeMessage(HouseInvitationResult result) {
    if (!result.joined) {
      return '邀请已发出，对方登录后自动加入';
    }
    final label = _roleLabel(result.role);
    if (result.role.isNotEmpty && result.role != _inviteRole) {
      // 对方本来权限就更高，后端不会给降下去，这时候必须说清楚。
      return '对方已在本兔舍，权限保持为$label';
    }
    return '已加入本兔舍，角色：$label';
  }

  Future<void> _showMemberActions(HouseMember member) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) {
        return SafeArea(
          child: SingleChildScrollView(
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
                    title: const Text('设为所有者'),
                    onTap: () => Navigator.pop(context, 'owner'),
                  ),
                ListTile(
                  leading: Icon(
                    Icons.person_remove_outlined,
                    color: AppPalette.of(context).danger,
                  ),
                  title: Text(
                    member.isAdmin ? '移除所有者' : '移除成员',
                    style: TextStyle(color: AppPalette.of(context).danger),
                  ),
                  onTap: () => Navigator.pop(context, 'remove'),
                ),
              ],
            ),
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
      case 'owner':
        await _confirmAddOwner(member);
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

  Future<void> _confirmAddOwner(HouseMember member) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('新增共同所有者'),
        content: Text(
          '确认将 ${member.userName} 设为兔舍所有者？'
          '设置后，您和 ${member.userName} 都将保留所有者权限。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('设为所有者'),
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
    final isOwner = member.isAdmin;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(isOwner ? '移除所有者' : '移除成员'),
        content: Text(
          isOwner
              ? '确认将所有者 ${member.userName} 移出兔舍？'
              : '确认将 ${member.userName} 移出兔舍？',
        ),
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
