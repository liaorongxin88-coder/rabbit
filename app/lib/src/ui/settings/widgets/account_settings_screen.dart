import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/account_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/user_profile.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

class AccountSettingsScreen extends ConsumerWidget {
  const AccountSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(userProfileProvider);
    return AppPage(
      title: '账号设置',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: () => ref.invalidate(userProfileProvider),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: profile.when(
        data: (data) => _AccountSettingsContent(profile: data),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(userProfileProvider),
        ),
      ),
    );
  }
}

class _AccountSettingsContent extends ConsumerStatefulWidget {
  const _AccountSettingsContent({required this.profile});

  final UserProfile profile;

  @override
  ConsumerState<_AccountSettingsContent> createState() =>
      _AccountSettingsContentState();
}

class _AccountSettingsContentState
    extends ConsumerState<_AccountSettingsContent> {
  final _nameFormKey = GlobalKey<FormState>();
  final _passwordFormKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  final _oldPasswordController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  var _savingName = false;
  var _savingPassword = false;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.profile.userName);
  }

  @override
  void didUpdateWidget(covariant _AccountSettingsContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.profile.userName != widget.profile.userName && !_savingName) {
      _nameController.text = widget.profile.userName;
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _oldPasswordController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        SectionCard(
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: palette.primarySoft,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(Icons.person, color: palette.primary),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.profile.userName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '用户ID：${widget.profile.userId}',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      widget.profile.openidBound ? '微信已绑定' : '微信未绑定',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          child: Form(
            key: _nameFormKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('用户名', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _nameController,
                  decoration: const InputDecoration(labelText: '用户名'),
                  maxLength: 64,
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return '请输入用户名';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 8),
                ElevatedButton.icon(
                  onPressed: _savingName ? null : _saveName,
                  icon: _savingName
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.save_outlined),
                  label: const Text('保存用户名'),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          child: Form(
            key: _passwordFormKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('登录密码', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _oldPasswordController,
                  decoration: const InputDecoration(labelText: '旧密码'),
                  obscureText: true,
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return '请输入旧密码';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _newPasswordController,
                  decoration: const InputDecoration(labelText: '新密码'),
                  obscureText: true,
                  validator: (value) {
                    if (value == null ||
                        value.length < 6 ||
                        value.length > 32) {
                      return '密码长度需在6-32位';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _confirmPasswordController,
                  decoration: const InputDecoration(labelText: '确认新密码'),
                  obscureText: true,
                  validator: (value) {
                    if (value != _newPasswordController.text) {
                      return '两次输入的新密码不一致';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                OutlinedButton.icon(
                  onPressed: _savingPassword ? null : _savePassword,
                  icon: _savingPassword
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.lock_reset_outlined),
                  label: const Text('修改密码'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _saveName() async {
    if (!_nameFormKey.currentState!.validate()) {
      return;
    }
    setState(() => _savingName = true);
    try {
      final profile = await ref
          .read(accountRepositoryProvider)
          .updateUserName(_nameController.text.trim());
      await ref
          .read(authControllerProvider.notifier)
          .setUserName(profile.userName);
      ref.invalidate(userProfileProvider);
      _showMessage('用户名已保存');
    } catch (error) {
      _showMessage(_errorMessage(error));
    } finally {
      if (mounted) {
        setState(() => _savingName = false);
      }
    }
  }

  Future<void> _savePassword() async {
    if (!_passwordFormKey.currentState!.validate()) {
      return;
    }
    setState(() => _savingPassword = true);
    try {
      await ref.read(accountRepositoryProvider).updatePassword(
            oldPassword: _oldPasswordController.text,
            newPassword: _newPasswordController.text,
          );
      _oldPasswordController.clear();
      _newPasswordController.clear();
      _confirmPasswordController.clear();
      _showMessage('密码已修改');
    } catch (error) {
      _showMessage(_errorMessage(error));
    } finally {
      if (mounted) {
        setState(() => _savingPassword = false);
      }
    }
  }

  void _showMessage(String message) {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  String _errorMessage(Object error) {
    if (error is ApiException) {
      return error.message;
    }
    return error.toString();
  }
}
