import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/phone_number_detector.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

enum _LoginMode { phone, account }

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _accountFormKey = GlobalKey<FormState>();
  late final PageController _modePageController;
  final _phoneController = TextEditingController();
  final _userNameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _phoneFocusNode = FocusNode();
  var _mode = _LoginMode.phone;
  var _registerMode = false;
  var _submitting = false;
  var _detectingPhone = false;
  var _phoneFocused = false;

  @override
  void initState() {
    super.initState();
    _modePageController = PageController(initialPage: _mode.index);
    _phoneFocusNode.addListener(() {
      if (mounted) {
        setState(() => _phoneFocused = _phoneFocusNode.hasFocus);
      }
    });
  }

  @override
  void dispose() {
    _modePageController.dispose();
    _phoneFocusNode.dispose();
    _phoneController.dispose();
    _userNameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 34, 24, 24),
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: Container(
                width: 54,
                height: 54,
                decoration: BoxDecoration(
                  color: palette.successSoft,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: palette.line),
                ),
                child: Icon(
                  Icons.agriculture_outlined,
                  color: palette.success,
                  size: 30,
                ),
              ),
            ),
            const SizedBox(height: 28),
            Text('智能兔管家', style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 10),
            Text(
              '登录后管理兔舍、预警和生产流程。',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 24),
            SegmentedButton<_LoginMode>(
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
              onSelectionChanged:
                  _submitting ? null : (next) => _selectMode(next.first),
            ),
            const SizedBox(height: 18),
            SizedBox(
              height: 320,
              child: PageView(
                controller: _modePageController,
                physics: _submitting
                    ? const NeverScrollableScrollPhysics()
                    : const PageScrollPhysics(),
                onPageChanged: (index) {
                  final next = _LoginMode.values[index];
                  if (_mode != next) {
                    setState(() => _mode = next);
                  }
                },
                children: [
                  _buildPhoneLogin(context),
                  _buildAccountLogin(context),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _selectMode(_LoginMode mode) {
    if (_mode != mode) {
      setState(() => _mode = mode);
    }
    _modePageController.animateToPage(
      mode.index,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
    );
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
                controller: _passwordController,
                obscureText: true,
                decoration: const InputDecoration(
                  hintText: '密码',
                  prefixIcon: Icon(Icons.lock_outline),
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
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _submitting ? null : _submitAccount,
            child: _submitting
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(_registerMode ? '注册并进入' : '登录'),
          ),
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton(
            onPressed: _submitting
                ? null
                : () => setState(() => _registerMode = !_registerMode),
            child: Text(_registerMode ? '已有账号，去登录' : '创建新账号'),
          ),
        ),
      ],
    );
  }

  void _showPhonePlaceholder() {
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
    if (!_accountFormKey.currentState!.validate()) {
      return;
    }
    final controller = ref.read(authControllerProvider.notifier);
    await _runAuth(() {
      if (_registerMode) {
        return controller.register(
          _userNameController.text.trim(),
          _passwordController.text.trim(),
        );
      }
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
    return AnimatedContainer(
      duration: const Duration(milliseconds: 160),
      curve: Curves.easeOut,
      height: 64,
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
          Icon(Icons.phone_android_outlined, color: palette.muted, size: 25),
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
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
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
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
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
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        children: [
          const Expanded(
            child: _PhoneFlowStep(
              icon: Icons.fact_check_outlined,
              title: '检测手机号',
              subtitle: '识别已有账号',
            ),
          ),
          const SizedBox(width: 12),
          Icon(Icons.arrow_forward_ios, color: palette.muted, size: 16),
          const SizedBox(width: 12),
          const Expanded(
            child: _PhoneFlowStep(
              icon: Icons.verified_user_outlined,
              title: '一键进入',
              subtitle: '未注册自动创建',
            ),
          ),
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
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    maxLines: compact ? 2 : 1,
                    overflow: TextOverflow.ellipsis,
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
