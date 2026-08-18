import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';

import 'package:rabbit_flutter/src/data/repositories/account_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/auth_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/user_profile.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/profile/view_models/profile_providers.dart';

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
                    const SizedBox(height: 4),
                    Text(
                      widget.profile.phoneBound
                          ? widget.profile.maskedPhone.isEmpty
                              ? '手机号已绑定'
                              : '手机号：${widget.profile.maskedPhone}'
                          : '手机号未绑定',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _UserCodeCard(userCode: widget.profile.userCode),
        _PhoneSecurityCard(profile: widget.profile),
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
                  key: const ValueKey('account-user-name-field'),
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
                  key: const ValueKey('account-user-name-save'),
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
                Text(
                  widget.profile.hasPassword ? '登录密码' : '设置登录密码',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 12),
                if (widget.profile.hasPassword) ...[
                  TextFormField(
                    key: const ValueKey('account-old-password'),
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
                ],
                TextFormField(
                  key: const ValueKey('account-new-password'),
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
                  key: const ValueKey('account-confirm-password'),
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
                  key: const ValueKey('account-password-save'),
                  onPressed: _savingPassword ? null : _savePassword,
                  icon: _savingPassword
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.lock_reset_outlined),
                  label: Text(
                    widget.profile.hasPassword ? '修改密码' : '设置密码',
                  ),
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
      _showMessage('用户名已保存');
      ref.invalidate(userProfileProvider);
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
            oldPassword:
                widget.profile.hasPassword ? _oldPasswordController.text : '',
            newPassword: _newPasswordController.text,
          );
      _oldPasswordController.clear();
      _newPasswordController.clear();
      _confirmPasswordController.clear();
      _showMessage(widget.profile.hasPassword ? '密码已修改' : '密码已设置');
      ref.invalidate(userProfileProvider);
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

/// 账号卡片。它存在的意义就是「报给别人」，所以必须能选中、能一键复制，
/// 而不是一行只能干看的小字。
class _UserCodeCard extends StatelessWidget {
  const _UserCodeCard({required this.userCode});

  final String userCode;

  @override
  Widget build(BuildContext context) {
    // 老后端还没返回账号时不占位，总比摆一个空卡片强。
    if (userCode.isEmpty) {
      return const SizedBox.shrink();
    }
    final palette = AppPalette.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: SectionCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('我的账号', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(
              '把它报给场主，就能被拉进兔舍，不用把手机号给出去。',
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: palette.muted),
            ),
            const SizedBox(height: 12),
            // 字号大一点、字间距宽一点，因为这东西经常是对着屏幕念出去的。
            // 用 Wrap 而不是 Row：200% 字号下号码和按钮肯定一行放不下。
            Wrap(
              spacing: 12,
              runSpacing: 4,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                SelectableText(
                  userCode,
                  key: const ValueKey('account-user-code'),
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        letterSpacing: 1.5,
                        fontFeatures: const [FontFeature.tabularFigures()],
                      ),
                ),
                TextButton.icon(
                  key: const ValueKey('account-user-code-copy'),
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: userCode));
                    if (!context.mounted) {
                      return;
                    }
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('账号已复制')),
                    );
                  },
                  icon: const Icon(Icons.copy_outlined),
                  label: const Text('复制'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _PhoneSecurityCard extends ConsumerStatefulWidget {
  const _PhoneSecurityCard({required this.profile});

  final UserProfile profile;

  @override
  ConsumerState<_PhoneSecurityCard> createState() => _PhoneSecurityCardState();
}

class _PhoneSecurityCardState extends ConsumerState<_PhoneSecurityCard> {
  final _formKey = GlobalKey<FormState>();
  final _currentPasswordController = TextEditingController();
  final _currentPhoneController = TextEditingController();
  final _currentPhoneCodeController = TextEditingController();
  final _newPhoneController = TextEditingController();
  final _newPhoneCodeController = TextEditingController();
  Timer? _currentPhoneTimer;
  Timer? _newPhoneTimer;
  var _currentPhoneSeconds = 0;
  var _newPhoneSeconds = 0;
  var _sendingCurrentCode = false;
  var _sendingNewCode = false;
  var _saving = false;

  @override
  void dispose() {
    _currentPhoneTimer?.cancel();
    _newPhoneTimer?.cancel();
    _currentPasswordController.dispose();
    _currentPhoneController.dispose();
    _currentPhoneCodeController.dispose();
    _newPhoneController.dispose();
    _newPhoneCodeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final needsCurrentPhoneCode =
        widget.profile.phoneBound && !widget.profile.hasPassword;
    return SectionCard(
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('手机号', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(
              widget.profile.phoneBound
                  ? '当前绑定：${widget.profile.maskedPhone}'
                  : '绑定后可使用短信登录和找回密码',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 12),
            if (widget.profile.phoneBound && widget.profile.hasPassword) ...[
              TextFormField(
                controller: _currentPasswordController,
                decoration: const InputDecoration(labelText: '当前密码'),
                obscureText: true,
                autofillHints: const [AutofillHints.password],
                validator: (value) =>
                    value == null || value.isEmpty ? '请输入当前密码' : null,
              ),
              const SizedBox(height: 12),
            ],
            if (needsCurrentPhoneCode) ...[
              TextFormField(
                controller: _currentPhoneController,
                decoration: const InputDecoration(labelText: '原手机号'),
                keyboardType: TextInputType.phone,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                maxLength: 11,
                validator: _validatePhone,
              ),
              const SizedBox(height: 12),
              _CodeField(
                controller: _currentPhoneCodeController,
                label: '原手机号验证码',
                sending: _sendingCurrentCode,
                remainingSeconds: _currentPhoneSeconds,
                onSend: _sendCurrentPhoneCode,
              ),
              const SizedBox(height: 12),
            ],
            TextFormField(
              controller: _newPhoneController,
              decoration: const InputDecoration(labelText: '新手机号'),
              keyboardType: TextInputType.phone,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              maxLength: 11,
              validator: _validatePhone,
            ),
            const SizedBox(height: 12),
            _CodeField(
              controller: _newPhoneCodeController,
              label: '新手机号验证码',
              sending: _sendingNewCode,
              remainingSeconds: _newPhoneSeconds,
              onSend: _sendNewPhoneCode,
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: _saving ? null : _savePhone,
              icon: _saving
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.phone_android_outlined),
              label: Text(widget.profile.phoneBound ? '更换手机号' : '绑定手机号'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _sendCurrentPhoneCode() async {
    if (_sendingCurrentCode || _currentPhoneSeconds > 0) return;
    final error = _validatePhone(_currentPhoneController.text);
    if (error != null) {
      _showMessage(error);
      return;
    }
    setState(() => _sendingCurrentCode = true);
    try {
      final delivery =
          await ref.read(authRepositoryProvider).sendSmsCodeForPurpose(
                _currentPhoneController.text,
                'VERIFY_CURRENT_PHONE',
              );
      if (mounted) {
        _startCountdown(
          delivery.retryAfterSeconds,
          currentPhone: true,
        );
        _showMessage('原手机号验证码已发送');
      }
    } catch (error) {
      _showMessage(_errorMessage(error));
    } finally {
      if (mounted) setState(() => _sendingCurrentCode = false);
    }
  }

  Future<void> _sendNewPhoneCode() async {
    if (_sendingNewCode || _newPhoneSeconds > 0) return;
    final error = _validatePhone(_newPhoneController.text);
    if (error != null) {
      _showMessage(error);
      return;
    }
    setState(() => _sendingNewCode = true);
    try {
      final delivery =
          await ref.read(authRepositoryProvider).sendSmsCodeForPurpose(
                _newPhoneController.text,
                'BIND_PHONE',
              );
      if (mounted) {
        _startCountdown(delivery.retryAfterSeconds, currentPhone: false);
        _showMessage('新手机号验证码已发送');
      }
    } catch (error) {
      _showMessage(_errorMessage(error));
    } finally {
      if (mounted) setState(() => _sendingNewCode = false);
    }
  }

  void _startCountdown(int seconds, {required bool currentPhone}) {
    final existing = currentPhone ? _currentPhoneTimer : _newPhoneTimer;
    existing?.cancel();
    setState(() {
      if (currentPhone) {
        _currentPhoneSeconds = seconds;
      } else {
        _newPhoneSeconds = seconds;
      }
    });
    final timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      final remaining = currentPhone ? _currentPhoneSeconds : _newPhoneSeconds;
      if (remaining <= 1) {
        timer.cancel();
        setState(() {
          if (currentPhone) {
            _currentPhoneSeconds = 0;
          } else {
            _newPhoneSeconds = 0;
          }
        });
        return;
      }
      setState(() {
        if (currentPhone) {
          _currentPhoneSeconds -= 1;
        } else {
          _newPhoneSeconds -= 1;
        }
      });
    });
    if (currentPhone) {
      _currentPhoneTimer = timer;
    } else {
      _newPhoneTimer = timer;
    }
  }

  Future<void> _savePhone() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      await ref.read(accountRepositoryProvider).updatePhone(
            phone: _newPhoneController.text,
            code: _newPhoneCodeController.text,
            currentPassword: _currentPasswordController.text,
            currentPhone: _currentPhoneController.text,
            currentPhoneCode: _currentPhoneCodeController.text,
          );
      _currentPasswordController.clear();
      _currentPhoneController.clear();
      _currentPhoneCodeController.clear();
      _newPhoneController.clear();
      _newPhoneCodeController.clear();
      _showMessage(widget.profile.phoneBound ? '手机号已更换' : '手机号已绑定');
      ref.invalidate(userProfileProvider);
    } catch (error) {
      _showMessage(_errorMessage(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String? _validatePhone(String? value) {
    return RegExp(r'^1[3-9]\d{9}$').hasMatch(value ?? '')
        ? null
        : '请输入有效的11位手机号';
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  String _errorMessage(Object error) {
    return error is ApiException ? error.message : error.toString();
  }
}

class _CodeField extends StatelessWidget {
  const _CodeField({
    required this.controller,
    required this.label,
    required this.sending,
    required this.remainingSeconds,
    required this.onSend,
  });

  final TextEditingController controller;
  final String label;
  final bool sending;
  final int remainingSeconds;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: TextFormField(
            controller: controller,
            decoration: InputDecoration(labelText: label),
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            maxLength: 6,
            validator: (value) =>
                RegExp(r'^\d{6}$').hasMatch(value ?? '') ? null : '请输入6位验证码',
          ),
        ),
        const SizedBox(width: 8),
        SizedBox(
          width: 116,
          height: 56,
          child: OutlinedButton(
            onPressed: sending || remainingSeconds > 0 ? null : onSend,
            child: sending
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text(
                      remainingSeconds > 0 ? '${remainingSeconds}s' : '获取验证码',
                      maxLines: 1,
                      softWrap: false,
                    ),
                  ),
          ),
        ),
      ],
    );
  }
}
