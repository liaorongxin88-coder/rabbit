import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/reminder_preference.dart';

class ReminderSettingsScreen extends ConsumerStatefulWidget {
  const ReminderSettingsScreen({super.key});

  @override
  ConsumerState<ReminderSettingsScreen> createState() =>
      _ReminderSettingsScreenState();
}

class _ReminderSettingsScreenState
    extends ConsumerState<ReminderSettingsScreen> {
  int? _selectedHouseId;

  @override
  Widget build(BuildContext context) {
    final houses = ref.watch(housesProvider);
    return AppPage(
      title: '我的事件提醒',
      fallbackBackLocation: '/profile',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: _selectedHouseId == null
              ? null
              : () => ref.invalidate(
                    reminderPreferenceProvider(_selectedHouseId!),
                  ),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: houses.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(housesProvider),
        ),
        data: (items) => _buildForHouses(items),
      ),
    );
  }

  Widget _buildForHouses(List<RabbitHouse> houses) {
    if (houses.isEmpty) {
      return const EmptyState(
        icon: Icons.home_work_outlined,
        title: '暂无兔舍',
        message: '加入或创建兔舍后可以配置事件提醒。',
      );
    }
    final selectedId = houses.any((house) => house.id == _selectedHouseId)
        ? _selectedHouseId!
        : houses.first.id;
    if (_selectedHouseId != selectedId) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _selectedHouseId != selectedId) {
          setState(() => _selectedHouseId = selectedId);
        }
      });
    }
    final preference = ref.watch(reminderPreferenceProvider(selectedId));
    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        SectionCard(
          child: DropdownButtonFormField<int>(
            key: const ValueKey('reminder-house'),
            value: selectedId,
            isExpanded: true,
            decoration: const InputDecoration(labelText: '兔舍'),
            items: [
              for (final house in houses)
                DropdownMenuItem<int>(
                  value: house.id,
                  child: Text(
                    house.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            ],
            onChanged: (value) {
              if (value != null) setState(() => _selectedHouseId = value);
            },
          ),
        ),
        const SizedBox(height: 12),
        preference.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ErrorState(
            message: error.toString(),
            onRetry: () =>
                ref.invalidate(reminderPreferenceProvider(selectedId)),
          ),
          data: (value) => ReminderPreferenceForm(
            key: ValueKey('reminder-form-$selectedId-${value.id}'),
            houseId: selectedId,
            preference: value,
          ),
        ),
      ],
    );
  }
}
