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
      fallbackBackLocation: '/profile',
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
  late final TextEditingController _prepartumController;
  late final TextEditingController _weaningController;
  late final TextEditingController _postpartumController;
  late final TextEditingController _adaptationController;
  late final TextEditingController _growingController;
  late final TextEditingController _fatteningController;
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
    _adaptationController =
        TextEditingController(text: '${widget.setting.adaptationDays}');
    _growingController =
        TextEditingController(text: '${widget.setting.growingDays}');
    _fatteningController =
        TextEditingController(text: '${widget.setting.fatteningDays}');
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
    _adaptationController.dispose();
    _growingController.dispose();
    _fatteningController.dispose();
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
                        _isHouseSetting ? '当前兔舍独立配置' : '新建兔舍默认配置',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _isHouseSetting
                            ? '仅影响 ${widget.houseName ?? '当前兔舍'}，状态转换和事件表单会用这些天数预填日期。'
                            : '创建兔舍时复制这些天数；已创建兔舍不会随此处变化。',
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
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('母兔繁育参数', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-aphrodisiac-days'),
                  label: '催情至配种时长',
                  helperText: '执行催情后开始计算，到期提醒配种。',
                  controller: _aphrodisiacController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-palpation-days'),
                  label: '配种至摸胎时长',
                  helperText: '完成配种后开始计算，到期提醒摸胎。',
                  controller: _palpationController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-prepartum-days'),
                  label: '备产提前天数',
                  helperText: '按预产期提前设置的天数，到期提醒备产。',
                  controller: _prepartumController,
                  min: 1,
                  max: 29,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-weaning-days'),
                  label: '分娩至分笼时长',
                  helperText: '完成接产后开始计算，到期提醒断奶分笼。',
                  controller: _weaningController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-postpartum-days'),
                  label: '休养恢复时长',
                  helperText: '接产后开始计算休养到期；空怀、流产或分娩失败后也用这一天数安排下一轮催情。',
                  controller: _postpartumController,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-replacement-days'),
                  label: '后备成熟天数',
                  helperText: '进入后备阶段后开始计算，到期提醒转为种兔。',
                  controller: _replacementController,
                ),
                const SizedBox(height: 20),
                Text('商品兔生长参数', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-adaptation-days'),
                  label: '幼兔适应期时长',
                  helperText: '从进入幼兔适应期起计入成熟日期；适应期内生成对应的日常观察提醒。',
                  controller: _adaptationController,
                  min: 2,
                  max: 3,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-growing-days'),
                  label: '成长期时长',
                  helperText: '从进入生长期起计入剩余成熟天数；生长期内生成对应的日常观察提醒。',
                  controller: _growingController,
                  min: 15,
                  max: 18,
                ),
                const SizedBox(height: 12),
                _DayField(
                  fieldKey: const ValueKey('production-fattening-days'),
                  label: '育肥期时长',
                  helperText: '从进入育肥期起计算成熟日期，到期生成可出售提醒。',
                  controller: _fatteningController,
                  min: 12,
                  max: 15,
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
        prepartumDays: _intValue(_prepartumController),
        weaningDays: _intValue(_weaningController),
        postpartumDays: _intValue(_postpartumController),
        adaptationDays: _intValue(_adaptationController),
        growingDays: _intValue(_growingController),
        fatteningDays: _intValue(_fatteningController),
        saleDays: _intValue(_adaptationController) +
            _intValue(_growingController) +
            _intValue(_fatteningController),
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
    required this.helperText,
    required this.controller,
    this.min,
    this.max,
  });

  final Key fieldKey;
  final String label;
  final String helperText;
  final TextEditingController controller;
  final int? min;
  final int? max;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: fieldKey,
      controller: controller,
      decoration: InputDecoration(
        labelText: label,
        helperText: helperText,
        helperMaxLines: 3,
        suffixText: '天',
      ),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      validator: (value) {
        if (value == null || value.isEmpty) {
          return '请输入天数';
        }
        final parsed = int.tryParse(value);
        if (parsed == null) {
          return '请输入有效天数';
        }
        if (min != null && parsed < min!) {
          return '不能少于 $min 天';
        }
        if (max != null && parsed > max!) {
          return '不能多于 $max 天';
        }
        return null;
      },
    );
  }
}
