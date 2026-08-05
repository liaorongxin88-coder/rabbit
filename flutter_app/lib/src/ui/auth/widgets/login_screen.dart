import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/phone_number_detector.dart';
import 'package:rabbit_flutter/src/domain/legal_documents.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/auth/widgets/legal_document_screen.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

enum _LoginMode { phone, account }

const _legalConsentReminder = '请阅读并同意《隐私政策》与《用户协议》';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _accountFormKey = GlobalKey<FormState>();
  final _phoneController = TextEditingController();
  final _userNameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _phoneFocusNode = FocusNode();
  var _mode = _LoginMode.phone;
  var _passwordVisible = false;
  var _submitting = false;
  var _detectingPhone = false;
  var _phoneFocused = false;
  var _agreedToTerms = false;
  var _horizontalDragDelta = 0.0;

  @override
  void initState() {
    super.initState();
    _phoneFocusNode.addListener(() {
      if (mounted) {
        setState(() => _phoneFocused = _phoneFocusNode.hasFocus);
      }
    });
  }

  @override
  void dispose() {
    _phoneFocusNode.dispose();
    _phoneController.dispose();
    _userNameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final contentWidth =
                constraints.maxWidth > 520 ? 480.0 : constraints.maxWidth;
            return Align(
              alignment: Alignment.topCenter,
              child: SizedBox(
                width: contentWidth,
                child: ListView(
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: AppSpacing.loginPagePadding,
                  children: [
                    const _LoginHeader(),
                    const SizedBox(height: 24),
                    SegmentedButton<_LoginMode>(
                      key: const ValueKey('login-mode-selector'),
                      expandedInsets: EdgeInsets.zero,
                      showSelectedIcon: false,
                      segments: const [
                        ButtonSegment(
                          value: _LoginMode.phone,
                          icon: Icon(Icons.phone_android_outlined),
                          label: Text('手机号'),
                        ),
                        ButtonSegment(
                          value: _LoginMode.account,
                          icon: Icon(Icons.person_outline),
                          label: Text('账号'),
                        ),
                      ],
                      selected: {_mode},
                      onSelectionChanged: _submitting
                          ? null
                          : (next) => _selectMode(next.first),
                    ),
                    const SizedBox(height: 16),
                    AnimatedSize(
                      duration: const Duration(milliseconds: 220),
                      curve: Curves.easeOutCubic,
                      alignment: Alignment.topCenter,
                      child: GestureDetector(
                        key: const ValueKey('login-mode-content'),
                        behavior: HitTestBehavior.opaque,
                        onHorizontalDragStart: _submitting
                            ? null
                            : (_) => _horizontalDragDelta = 0,
                        onHorizontalDragUpdate: _submitting
                            ? null
                            : (details) =>
                                _horizontalDragDelta += details.delta.dx,
                        onHorizontalDragEnd: _submitting
                            ? null
                            : (_) {
                                if (_horizontalDragDelta < -40) {
                                  _selectMode(_LoginMode.account);
                                } else if (_horizontalDragDelta > 40) {
                                  _selectMode(_LoginMode.phone);
                                }
                                _horizontalDragDelta = 0;
                              },
                        child: AnimatedSwitcher(
                          duration: const Duration(milliseconds: 180),
                          switchInCurve: Curves.easeOut,
                          switchOutCurve: Curves.easeIn,
                          child: _mode == _LoginMode.phone
                              ? _buildPhoneLogin(context)
                              : _buildAccountLogin(context),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    _LegalConsentRow(
                      agreed: _agreedToTerms,
                      enabled: !_submitting,
                      onChanged: (value) =>
                          setState(() => _agreedToTerms = value),
                      onOpenPrivacyPolicy: () => _openLegalDocument(
                        title: LegalDocuments.privacyPolicyTitle,
                        body: LegalDocuments.privacyPolicy,
                      ),
                      onOpenUserAgreement: () => _openLegalDocument(
                        title: LegalDocuments.userAgreementTitle,
                        body: LegalDocuments.userAgreement,
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  void _openLegalDocument({
    required String title,
    required String body,
  }) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (context) => LegalDocumentScreen(
          title: title,
          body: body,
        ),
      ),
    );
  }

  bool _ensureLegalConsent() {
    if (_agreedToTerms) {
      return true;
    }
    _showMessage(_legalConsentReminder);
    return false;
  }

  void _selectMode(_LoginMode mode) {
    if (_mode == mode) return;
    setState(() => _mode = mode);
  }

  Widget _buildPhoneLogin(BuildContext context) {
    return Column(
      key: const ValueKey('phone-login'),
      children: [
        _PhoneNumberInput(
          controller: _phoneController,
          focusNode: _phoneFocusNode,
          focused: _phoneFocused,
          detecting: _detectingPhone,
          onDetect: _detectPhoneNumber,
          onSubmitted: _showPhonePlaceholder,
        ),
        const SizedBox(height: 16),
        const _PhoneLoginFlow(),
        const SizedBox(height: 20),
        SizedBox(
          key: const ValueKey('phone-login-button'),
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: _submitting ? null : _showPhonePlaceholder,
            icon: const Icon(Icons.login_outlined),
            label: const Text('手机号一键进入'),
          ),
        ),
      ],
    );
  }

  Widget _buildAccountLogin(BuildContext context) {
    return Column(
      key: const ValueKey('account-login'),
      children: [
        Form(
          key: _accountFormKey,
          child: Column(
            children: [
              TextFormField(
                key: const ValueKey('account-username-field'),
                controller: _userNameController,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  hintText: '用户名',
                  prefixIcon: Icon(Icons.person_outline),
                  floatingLabelBehavior: FloatingLabelBehavior.never,
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return '请输入用户名';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 14),
              TextFormField(
                key: const ValueKey('account-password-field'),
                controller: _passwordController,
                obscureText: !_passwordVisible,
                decoration: InputDecoration(
                  hintText: '密码',
                  prefixIcon: const Icon(Icons.lock_outline),
                  suffixIcon: IconButton(
                    key: const ValueKey('password-visibility-toggle'),
                    tooltip: _passwordVisible ? '隐藏密码' : '显示密码',
                    onPressed: () {
                      setState(() => _passwordVisible = !_passwordVisible);
                    },
                    icon: Icon(
                      _passwordVisible
                          ? Icons.visibility_off_outlined
                          : Icons.visibility_outlined,
                    ),
                  ),
                  floatingLabelBehavior: FloatingLabelBehavior.never,
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return '请输入密码';
                  }
                  return null;
                },
                onFieldSubmitted: (_) => _submitAccount(),
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        SizedBox(
          key: const ValueKey('account-login-button'),
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _submitting ? null : _submitAccount,
            child: _submitting
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('登录'),
          ),
        ),
      ],
    );
  }

  void _showPhonePlaceholder() {
    if (!_ensureLegalConsent()) {
      return;
    }
    final detector = ref.read(phoneNumberDetectorProvider);
    final phone = detector.normalize(_phoneController.text);
    if (phone.isEmpty) {
      _showMessage('请先输入或检测手机号');
      return;
    }
    if (!detector.isValidMainlandMobile(phone)) {
      _showMessage('请输入有效手机号');
      return;
    }
    _phoneController.text = phone;
    _phoneController.selection = TextSelection.collapsed(offset: phone.length);
    _showMessage('手机号快捷登录暂未开放');
  }

  Future<void> _detectPhoneNumber() async {
    if (_detectingPhone) {
      return;
    }
    setState(() => _detectingPhone = true);
    try {
      final detector = ref.read(phoneNumberDetectorProvider);
      final result = await detector.detect();
      if (!mounted) {
        return;
      }
      if (result.hasPhoneNumber) {
        final phone = detector.normalize(result.phoneNumber!);
        _phoneController.text = phone;
        _phoneController.selection = TextSelection.collapsed(
          offset: phone.length,
        );
        _showMessage(result.message ?? '已检测到手机号');
        return;
      }
      _showMessage(result.message ?? '当前平台暂未提供手机号检测，请手动输入');
    } catch (error) {
      if (mounted) {
        _showMessage(error.toString());
      }
    } finally {
      if (mounted) {
        setState(() => _detectingPhone = false);
      }
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  Future<void> _submitAccount() async {
    if (!_ensureLegalConsent()) {
      return;
    }
    if (!_accountFormKey.currentState!.validate()) {
      return;
    }
    final controller = ref.read(authControllerProvider.notifier);
    await _runAuth(() {
      return controller.login(
        _userNameController.text.trim(),
        _passwordController.text.trim(),
      );
    });
  }

  Future<void> _runAuth(Future<void> Function() action) async {
    setState(() => _submitting = true);
    try {
      await action();
      if (mounted) {
        context.go('/');
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
        setState(() => _submitting = false);
      }
    }
  }
}

class _LegalConsentRow extends StatelessWidget {
  const _LegalConsentRow({
    required this.agreed,
    required this.enabled,
    required this.onChanged,
    required this.onOpenPrivacyPolicy,
    required this.onOpenUserAgreement,
  });

  final bool agreed;
  final bool enabled;
  final ValueChanged<bool> onChanged;
  final VoidCallback onOpenPrivacyPolicy;
  final VoidCallback onOpenUserAgreement;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final linkStyle = Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: palette.primary,
          fontWeight: FontWeight.w600,
        );
    final baseStyle = Theme.of(context).textTheme.bodyMedium;

    return Row(
      key: const ValueKey('legal-consent-row'),
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox.square(
          dimension: 48,
          child: Checkbox(
            key: const ValueKey('legal-consent-checkbox'),
            value: agreed,
            onChanged: enabled ? (value) => onChanged(value ?? false) : null,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: FittedBox(
            alignment: Alignment.centerLeft,
            fit: BoxFit.scaleDown,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('请阅读并同意', style: baseStyle),
                GestureDetector(
                  onTap: onOpenPrivacyPolicy,
                  child: Text('《隐私政策》', style: linkStyle),
                ),
                Text('与', style: baseStyle),
                GestureDetector(
                  onTap: onOpenUserAgreement,
                  child: Text('《用户协议》', style: linkStyle),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _LoginHeader extends StatelessWidget {
  const _LoginHeader();

  static const _logoAsset = 'assets/branding/hongtu_logo.png';

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Image.asset(
            _logoAsset,
            key: const ValueKey('hongtu-logo'),
            width: 72,
            height: 72,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) {
              final palette = AppPalette.of(context);
              return Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(
                  color: palette.primarySoft,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: palette.line),
                ),
                child: Icon(
                  Icons.pets_outlined,
                  color: palette.primary,
                  size: 36,
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 16),
        Text(
          '智能兔管家',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 8),
        Text(
          '登录后管理兔舍、预警和生产流程。',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ],
    );
  }
}

class _PhoneNumberInput extends StatelessWidget {
  const _PhoneNumberInput({
    required this.controller,
    required this.focusNode,
    required this.focused,
    required this.detecting,
    required this.onDetect,
    required this.onSubmitted,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool focused;
  final bool detecting;
  final VoidCallback onDetect;
  final VoidCallback onSubmitted;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final borderColor = focused ? palette.primary : palette.line;
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.5;
    return AnimatedContainer(
      key: const ValueKey('phone-number-input'),
      duration: const Duration(milliseconds: 160),
      curve: Curves.easeOut,
      height: largeText ? 72 : 56,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: borderColor,
          width: focused ? 1.5 : 1,
        ),
        boxShadow: focused
            ? [
                BoxShadow(
                  color: palette.primary.withOpacity(0.10),
                  blurRadius: 14,
                  offset: const Offset(0, 6),
                ),
              ]
            : null,
      ),
      child: Row(
        children: [
          Icon(Icons.phone_android_outlined, color: palette.muted, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: TextField(
              controller: controller,
              focusNode: focusNode,
              keyboardType: TextInputType.phone,
              textInputAction: TextInputAction.done,
              inputFormatters: [
                FilteringTextInputFormatter.digitsOnly,
                LengthLimitingTextInputFormatter(11),
              ],
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
              decoration: InputDecoration(
                isCollapsed: true,
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                disabledBorder: InputBorder.none,
                filled: false,
                hintText: '请输入手机号',
                hintStyle: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: palette.muted,
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                    ),
              ),
              onSubmitted: (_) => onSubmitted(),
            ),
          ),
          const SizedBox(width: 10),
          IconButton(
            tooltip: '检测手机号',
            onPressed: detecting ? null : onDetect,
            icon: detecting
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(Icons.manage_search_outlined, color: palette.muted),
          ),
        ],
      ),
    );
  }
}

class _PhoneLoginFlow extends StatelessWidget {
  const _PhoneLoginFlow();

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.5;
    const firstStep = _PhoneFlowStep(
      icon: Icons.fact_check_outlined,
      title: '检测手机号',
      subtitle: '识别已有账号',
    );
    const secondStep = _PhoneFlowStep(
      icon: Icons.verified_user_outlined,
      title: '账号登录',
      subtitle: '切换后继续',
    );
    return Container(
      key: const ValueKey('phone-login-flow'),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: largeText
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                firstStep,
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Icon(
                    Icons.arrow_downward,
                    color: palette.muted,
                    size: 20,
                  ),
                ),
                secondStep,
              ],
            )
          : Row(
              children: [
                const Expanded(child: firstStep),
                const SizedBox(width: 12),
                Icon(
                  Icons.arrow_forward_ios,
                  color: palette.muted,
                  size: 16,
                ),
                const SizedBox(width: 12),
                const Expanded(child: secondStep),
              ],
            ),
    );
  }
}

class _PhoneFlowStep extends StatelessWidget {
  const _PhoneFlowStep({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.5;
    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxWidth < 150;
        return Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: palette.primarySoft,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(icon, color: palette.primary, size: 20),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    maxLines: largeText ? 2 : 1,
                    overflow: largeText
                        ? TextOverflow.visible
                        : TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    maxLines: largeText || compact ? 2 : 1,
                    overflow: largeText
                        ? TextOverflow.visible
                        : TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}
