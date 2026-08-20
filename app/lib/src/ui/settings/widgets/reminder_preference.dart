import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/settings/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/reproduction/reminder_preference.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/providers.dart';

class ReminderPreferenceForm extends ConsumerStatefulWidget {
  const ReminderPreferenceForm({
    super.key,
    required this.houseId,
    required this.preference,
  });

  final int houseId;
  final ReminderPreference preference;

  @override
  ConsumerState<ReminderPreferenceForm> createState() =>
      _ReminderPreferenceFormState();
}

class _ReminderPreferenceFormState
    extends ConsumerState<ReminderPreferenceForm> {
  late bool _enabled;
  late bool _notifyOverdue;
  late int _advanceDays;
  late Set<String> _selectedTypes;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _enabled = widget.preference.enabled;
    _notifyOverdue = widget.preference.notifyOverdue;
    _advanceDays = widget.preference.advanceDays;
    _selectedTypes = widget.preference.includesAll
        ? ReminderPreference.supportedTypes.keys.toSet()
        : widget.preference.taskTypes.toSet();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionCard(
          child: Column(
            children: [
              SwitchListTile(
                key: const ValueKey('reminder-enabled'),
                contentPadding: EdgeInsets.zero,
                title: const Text('应用内事件提醒'),
                subtitle: const Text('关闭后，该兔舍的事件不会出现在首页提醒中'),
                value: _enabled,
                onChanged: _saving
                    ? null
                    : (value) => setState(() => _enabled = value),
              ),
              const Divider(height: 1),
              SwitchListTile(
                key: const ValueKey('reminder-overdue'),
                contentPadding: EdgeInsets.zero,
                title: const Text('显示逾期事件'),
                value: _notifyOverdue,
                onChanged: !_enabled || _saving
                    ? null
                    : (value) => setState(() => _notifyOverdue = value),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                '提前 $_advanceDays 天显示',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              Slider(
                key: const ValueKey('reminder-advance-days'),
                value: _advanceDays.toDouble(),
                min: 0,
                max: 14,
                divisions: 14,
                label: '$_advanceDays 天',
                onChanged: !_enabled || _saving
                    ? null
                    : (value) => setState(() => _advanceDays = value.round()),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('提醒类型', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              for (final entry in ReminderPreference.supportedTypes.entries)
                CheckboxListTile(
                  key: ValueKey('reminder-type-${entry.key}'),
                  contentPadding: EdgeInsets.zero,
                  title: Text(entry.value),
                  value: _selectedTypes.contains(entry.key),
                  onChanged: !_enabled || _saving
                      ? null
                      : (selected) => _toggleType(entry.key, selected == true),
                ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        FilledButton.icon(
          key: const ValueKey('reminder-save'),
          onPressed: _saving ? null : _save,
          icon: _saving
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Colors.white,
                  ),
                )
              : const Icon(Icons.save_outlined),
          label: const Text('保存提醒设置'),
        ),
      ],
    );
  }

  void _toggleType(String type, bool selected) {
    setState(() {
      if (selected) {
        _selectedTypes.add(type);
      } else if (_selectedTypes.length > 1) {
        _selectedTypes.remove(type);
      }
    });
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final allSelected =
          _selectedTypes.length == ReminderPreference.supportedTypes.length;
      final preference = widget.preference.copyWith(
        enabled: _enabled,
        advanceDays: _advanceDays,
        notifyOverdue: _notifyOverdue,
        taskTypes: allSelected ? {'ALL'} : _selectedTypes,
      );
      await ref.read(settingsRepositoryProvider).updateReminderPreference(
            houseId: widget.houseId,
            preference: preference,
          );
      ref.invalidate(reminderPreferenceProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      _showMessage('事件提醒设置已保存');
    } catch (error) {
      _showMessage(error is ApiException ? error.message : error.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}
