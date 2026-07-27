import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

Future<void> showRabbitMoveCageSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
  required List<Cage> cages,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _MoveCageSheet(
      houseId: houseId,
      rabbit: rabbit,
      cages: cages,
    ),
  );
}

class _MoveCageSheet extends ConsumerStatefulWidget {
  const _MoveCageSheet({
    required this.houseId,
    required this.rabbit,
    required this.cages,
  });

  final int houseId;
  final Rabbit rabbit;
  final List<Cage> cages;

  @override
  ConsumerState<_MoveCageSheet> createState() => _MoveCageSheetState();
}

class _MoveCageSheetState extends ConsumerState<_MoveCageSheet> {
  final _searchController = TextEditingController();
  late int _selectedCageId;
  var _keyword = '';
  var _saving = false;

  Rabbit get _rabbit => widget.rabbit;

  Cage? get _currentCage {
    for (final cage in widget.cages) {
      if (cage.id == _rabbit.cageId) {
        return cage;
      }
    }
    return null;
  }

  @override
  void initState() {
    super.initState();
    _selectedCageId = _rabbit.cageId;
    _searchController.addListener(() {
      final next = _searchController.text.trim();
      if (next != _keyword) {
        setState(() => _keyword = next);
      }
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<Cage> get _targetCages {
    final keyword = _keyword.toLowerCase();
    return widget.cages.where((cage) {
      if (!cage.canAcceptRabbit(_rabbit.type, exceptRabbitCageId: _rabbit.cageId)) {
        return false;
      }
      if (keyword.isEmpty) {
        return true;
      }
      final number = cage.cageNumber.toLowerCase();
      return number.contains(keyword) || '${cage.id}'.contains(keyword);
    }).toList()
      ..sort((a, b) {
        if (a.id == _rabbit.cageId) {
          return -1;
        }
        if (b.id == _rabbit.cageId) {
          return 1;
        }
        return a.cageNumber.compareTo(b.cageNumber);
      });
  }

  String _cageLabel(Cage cage) {
    final name = cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;
    if (cage.id == _rabbit.cageId) {
      return '$name（当前）';
    }
    if (cage.rabbitCount > 0) {
      return '$name · ${cage.usageLabel} · ${cage.rabbitCount} 只';
    }
    return '$name · 空笼';
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final targets = _targetCages;
    final current = _currentCage;

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: mediaQuery.size.height * 0.88),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '换笼位',
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '兔 #${_rabbit.id} · ${_rabbit.typeLabel} · ${_rabbit.genderLabel}',
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: _saving ? null : () => Navigator.pop(context),
                      icon: const Icon(Icons.close),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: _InfoBox(
                  text: current == null
                      ? '当前笼位 #${_rabbit.cageId}'
                      : '当前笼位：${_cageLabel(current)}',
                ),
              ),
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: TextField(
                  controller: _searchController,
                  decoration: const InputDecoration(
                    hintText: '搜索目标笼位编号',
                    prefixIcon: Icon(Icons.search),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Flexible(
                child: targets.isEmpty
                    ? Padding(
                        padding: const EdgeInsets.all(20),
                        child: Text(
                          '没有可用的目标笼位。种兔/后备兔笼需为空笼，商品兔需匹配商品兔笼。',
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      )
                    : ListView.builder(
                        shrinkWrap: true,
                        padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                        itemCount: targets.length,
                        itemBuilder: (context, index) {
                          final cage = targets[index];
                          final selected = cage.id == _selectedCageId;
                          return RadioListTile<int>(
                            value: cage.id,
                            groupValue: _selectedCageId,
                            onChanged: _saving
                                ? null
                                : (value) => setState(
                                      () => _selectedCageId = value ?? _selectedCageId,
                                    ),
                            title: Text(_cageLabel(cage)),
                            subtitle: Text(cage.usageLabel),
                            selected: selected,
                          );
                        },
                      ),
              ),
              DecoratedBox(
                decoration: BoxDecoration(
                  border: Border(
                    top: BorderSide(color: AppPalette.of(context).line),
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
                  child: Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: _saving ? null : () => Navigator.pop(context),
                          child: const Text('取消'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton(
                          onPressed: _saving || _selectedCageId == _rabbit.cageId
                              ? null
                              : _save,
                          child: _saving
                              ? const SizedBox.square(
                                  dimension: 20,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Text('确认换笼'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await ref.read(rabbitRepositoryProvider).moveRabbitToCage(
            houseId: widget.houseId,
            rabbit: _rabbit,
            targetCageId: _selectedCageId,
          );
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('兔 #${_rabbit.id} 已换至笼位 #$_selectedCageId')),
        );
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
        setState(() => _saving = false);
      }
    }
  }
}

class _InfoBox extends StatelessWidget {
  const _InfoBox({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Text(text, style: Theme.of(context).textTheme.bodyMedium),
    );
  }
}
