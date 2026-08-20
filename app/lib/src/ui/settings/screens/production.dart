import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/settings/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/reminder_preference.dart';

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
        fallbackBackLocation: '/houses/$targetHouseId',
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: () {
              ref.invalidate(houseSettingProvider(targetHouseId));
              ref.invalidate(reminderPreferenceProvider(targetHouseId));
            },
            icon: const Icon(Icons.refresh),
          ),
        ],
        child: setting.when(
          data: (data) => _ProductionSettingsForm(
            setting: data.setting,
            houseId: targetHouseId,
            houseName: houseName,
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
  });

  final GlobalSetting setting;
  final int? houseId;
  final String? houseName;

  @override
  ConsumerState<_ProductionSettingsForm> createState() =>
      _ProductionSettingsFormState();
}

class _ProductionSettingsFormState
    extends ConsumerState<_ProductionSettingsForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _aphrodisiacController;
  late final TextEditingController _palpationController;
  late final TextEditingController _gestationController;
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
    _gestationController =
        TextEditingController(text: '${widget.setting.gestationDays}');
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
    _gestationController.dispose();
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
                        _isHouseSetting ? '当前兔舍独立配置' : '新建兔场默认配置',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _isHouseSetting
                            ? '仅影响 ${widget.houseName ?? '当前兔舍'}，状态转换和事件表单会用这些天数预填日期。'
                            : '创建兔场时复制这些天数；已创建兔场不会随此处变化。',
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
                  fieldKey: const ValueKey('production-aphrodisiac-days'),
                  label: '催情时长',
                  controller: _aphrodisiacController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-palpation-days'),
                  label: '待摸胎时长',
                  controller: _palpationController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-gestation-days'),
                  label: '妊娠参考天数',
                  controller: _gestationController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-prepartum-days'),
                  label: '待备产时长',
                  controller: _prepartumController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-weaning-days'),
                  label: '断奶时长',
                  controller: _weaningController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-postpartum-days'),
                  label: '产后恢复时长',
                  controller: _postpartumController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-sale-days'),
                  label: '出售天数',
                  controller: _saleController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-replacement-days'),
                  label: '后备成熟天数',
                  controller: _replacementController,
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SectionCard(
            child: TextFormField(
              key: const ValueKey('production-remark'),
              controller: _remarkController,
              minLines: 2,
              maxLines: 4,
              decoration: const InputDecoration(labelText: '备注'),
            ),
          ),
          const SizedBox(height: 18),
          ElevatedButton.icon(
            key: const ValueKey('production-settings-save'),
            onPressed: _saving ? null : _save,
            icon: _saving
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_outlined),
            label: const Text('保存生产设置'),
          ),
          if (_isHouseSetting) ...[
            const SizedBox(height: 28),
            _HouseReminderPreferenceSection(
              houseId: widget.houseId!,
              houseName: widget.houseName,
            ),
          ],
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
        gestationDays: _intValue(_gestationController),
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

class _HouseReminderPreferenceSection extends ConsumerWidget {
  const _HouseReminderPreferenceSection({
    required this.houseId,
    required this.houseName,
  });

  final int houseId;
  final String? houseName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preference = ref.watch(reminderPreferenceProvider(houseId));
    final targetName = houseName?.trim();
    return Column(
      key: const ValueKey('house-production-reminders'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.notifications_active_outlined, size: 22),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '当前兔舍提醒',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${targetName == null || targetName.isEmpty ? '当前兔舍' : targetName}的提醒单独配置；这些选项只影响你的首页提醒。',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        preference.when(
          loading: () => const Padding(
            padding: EdgeInsets.symmetric(vertical: 28),
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (error, _) => ErrorState(
            message: error.toString(),
            onRetry: () => ref.invalidate(reminderPreferenceProvider(houseId)),
          ),
          data: (value) => ReminderPreferenceForm(
            key: ValueKey('house-reminder-form-$houseId-${value.id}'),
            houseId: houseId,
            preference: value,
          ),
        ),
      ],
    );
  }
}

class _DayField extends StatelessWidget {
  const _DayField({
    required this.fieldKey,
    required this.label,
    required this.controller,
  });

  final Key fieldKey;
  final String label;
  final TextEditingController controller;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: fieldKey,
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
