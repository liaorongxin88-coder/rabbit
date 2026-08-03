import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/settings_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/global_setting.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

class ProductionSettingsScreen extends ConsumerWidget {
  const ProductionSettingsScreen({
    super.key,
    this.houseId,
    this.houseName,
  });

  final int? houseId;
  final String? houseName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final targetHouseId = houseId;
    final isHouseSetting = targetHouseId != null && targetHouseId > 0;
    if (isHouseSetting) {
      final setting = ref.watch(houseSettingProvider(targetHouseId));
      return AppPage(
        title: '兔舍生产设置',
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: () =>
                ref.invalidate(houseSettingProvider(targetHouseId)),
            icon: const Icon(Icons.refresh),
          ),
        ],
        child: setting.when(
          data: (data) => _ProductionSettingsForm(
            setting: data.setting,
            houseId: targetHouseId,
            houseName: houseName,
            customized: data.customized,
          ),
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ErrorState(
            message: error.toString(),
            onRetry: () => ref.invalidate(houseSettingProvider(targetHouseId)),
          ),
        ),
      );
    }

    final setting = ref.watch(userSettingProvider);
    return AppPage(
      title: '默认生产设置',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: () => ref.invalidate(userSettingProvider),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: setting.when(
        data: (data) => _ProductionSettingsForm(setting: data),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(userSettingProvider),
        ),
      ),
    );
  }
}

class _ProductionSettingsForm extends ConsumerStatefulWidget {
  const _ProductionSettingsForm({
    required this.setting,
    this.houseId,
    this.houseName,
    this.customized = false,
  });

  final GlobalSetting setting;
  final int? houseId;
  final String? houseName;
  final bool customized;

  @override
  ConsumerState<_ProductionSettingsForm> createState() =>
      _ProductionSettingsFormState();
}

class _ProductionSettingsFormState
    extends ConsumerState<_ProductionSettingsForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _aphrodisiacController;
  late final TextEditingController _palpationController;
  late final TextEditingController _prepartumController;
  late final TextEditingController _weaningController;
  late final TextEditingController _postpartumController;
  late final TextEditingController _saleController;
  late final TextEditingController _replacementController;
  late final TextEditingController _remarkController;
  var _saving = false;

  bool get _isHouseSetting => widget.houseId != null && widget.houseId! > 0;

  @override
  void initState() {
    super.initState();
    _aphrodisiacController =
        TextEditingController(text: '${widget.setting.aphrodisiacDays}');
    _palpationController =
        TextEditingController(text: '${widget.setting.palpationDays}');
    _prepartumController =
        TextEditingController(text: '${widget.setting.prepartumDays}');
    _weaningController =
        TextEditingController(text: '${widget.setting.weaningDays}');
    _postpartumController =
        TextEditingController(text: '${widget.setting.postpartumDays}');
    _saleController = TextEditingController(text: '${widget.setting.saleDays}');
    _replacementController =
        TextEditingController(text: '${widget.setting.replacementDays}');
    _remarkController = TextEditingController(text: widget.setting.remark);
  }

  @override
  void dispose() {
    _aphrodisiacController.dispose();
    _palpationController.dispose();
    _prepartumController.dispose();
    _weaningController.dispose();
    _postpartumController.dispose();
    _saleController.dispose();
    _replacementController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Form(
      key: _formKey,
      child: ListView(
        padding: AppSpacing.pagePadding,
        children: [
          SectionCard(
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: palette.warningSoft,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(
                    Icons.calendar_month_outlined,
                    color: palette.warning,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _isHouseSetting
                            ? (widget.customized ? '当前兔舍单独配置' : '继承默认配置')
                            : '所有兔舍默认配置',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _isHouseSetting
                            ? '${widget.houseName ?? '当前兔舍'} 的生产周期。保存后将覆盖默认配置。'
                            : '未单独配置的兔舍会使用这些天数。',
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
            child: Column(
              children: [
                _DayField(
                  label: '催情间隔',
                  controller: _aphrodisiacController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  label: '摸胎天数',
                  controller: _palpationController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  label: '备产提前天数',
                  controller: _prepartumController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  label: '断奶天数',
                  controller: _weaningController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  label: '产后恢复天数',
                  controller: _postpartumController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  label: '出售天数',
                  controller: _saleController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  label: '后备成熟天数',
                  controller: _replacementController,
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SectionCard(
            child: TextFormField(
              controller: _remarkController,
              minLines: 2,
              maxLines: 4,
              decoration: const InputDecoration(labelText: '备注'),
            ),
          ),
          const SizedBox(height: 18),
          ElevatedButton.icon(
            onPressed: _saving ? null : _save,
            icon: _saving
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_outlined),
            label: const Text('保存生产设置'),
          ),
        ],
      ),
    );
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() => _saving = true);
    try {
      final setting = GlobalSetting(
        id: widget.setting.id,
        userId: widget.setting.userId,
        houseId: widget.setting.houseId,
        aphrodisiacDays: _intValue(_aphrodisiacController),
        palpationDays: _intValue(_palpationController),
        prepartumDays: _intValue(_prepartumController),
        weaningDays: _intValue(_weaningController),
        postpartumDays: _intValue(_postpartumController),
        saleDays: _intValue(_saleController),
        replacementDays: _intValue(_replacementController),
        remark: _remarkController.text.trim(),
      );
      if (_isHouseSetting) {
        await ref.read(settingsRepositoryProvider).updateHouseSetting(
              houseId: widget.houseId!,
              setting: setting,
            );
        ref.invalidate(houseSettingProvider(widget.houseId!));
        _showMessage('兔舍生产设置已保存');
      } else {
        await ref.read(settingsRepositoryProvider).updateSetting(
              setting: setting,
            );
        ref.invalidate(userSettingProvider);
        _showMessage('默认生产设置已保存');
      }
    } catch (error) {
      _showMessage(_errorMessage(error));
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  int _intValue(TextEditingController controller) {
    return int.tryParse(controller.text) ?? 0;
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

class _DayField extends StatelessWidget {
  const _DayField({
    required this.label,
    required this.controller,
  });

  final String label;
  final TextEditingController controller;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      decoration: InputDecoration(
        labelText: label,
        suffixText: '天',
      ),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      validator: (value) {
        if (value == null || value.isEmpty) {
          return '请输入天数';
        }
        return null;
      },
    );
  }
}
