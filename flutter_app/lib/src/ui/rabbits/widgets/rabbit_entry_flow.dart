import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

Future<void> showRabbitEntryTypeSheet({
  required BuildContext context,
  required int houseId,
  required Cage cage,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (sheetContext) => _RabbitTypeSheet(
      hostContext: context,
      houseId: houseId,
      cage: cage,
    ),
  );
}

Future<void> showRabbitEditSheet({
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
    builder: (context) => _CreateRabbitSheet.edit(
      houseId: houseId,
      rabbit: rabbit,
      cages: cages,
    ),
  );
}

class _RabbitTypeSheet extends ConsumerStatefulWidget {
  const _RabbitTypeSheet({
    required this.hostContext,
    required this.houseId,
    required this.cage,
  });

  final BuildContext hostContext;
  final int houseId;
  final Cage cage;

  @override
  ConsumerState<_RabbitTypeSheet> createState() => _RabbitTypeSheetState();
}

class _RabbitTypeSheetState extends ConsumerState<_RabbitTypeSheet> {
  late String _type;

  String get _cageName => widget.cage.cageNumber.isEmpty
      ? '#${widget.cage.id}'
      : widget.cage.cageNumber;

  @override
  void initState() {
    super.initState();
    _type = widget.cage.preferredRabbitType;
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 18, 20, 20 + inset),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('请选择录入兔子类型', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 6),
          Text(
            '$_cageName · ${widget.cage.usageLabel} · ${widget.cage.rabbitCount} 只',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 22),
          Wrap(
            spacing: 14,
            runSpacing: 10,
            children: [
              _RabbitTypeChoice(
                value: '0',
                groupValue: _type,
                label: '种公兔/种母兔',
                onChanged: _handleTypeChanged,
              ),
              _RabbitTypeChoice(
                value: '1',
                groupValue: _type,
                label: '后备兔',
                onChanged: _handleTypeChanged,
              ),
              _RabbitTypeChoice(
                value: '2',
                groupValue: _type,
                label: '商品兔',
                onChanged: _handleTypeChanged,
              ),
            ],
          ),
          if (widget.cage.status != '0' &&
              widget.cage.preferredRabbitType != _type) ...[
            const SizedBox(height: 14),
            _CageTypeWarning(cage: widget.cage),
          ],
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('取消'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton(
                  onPressed: _continue,
                  child: const Text('确定'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  void _handleTypeChanged(String value) {
    setState(() => _type = value);
  }

  void _continue() {
    Navigator.of(context).pop();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!widget.hostContext.mounted) {
        return;
      }
      showModalBottomSheet<void>(
        context: widget.hostContext,
        isScrollControlled: true,
        useRootNavigator: true,
        useSafeArea: true,
        builder: (context) => _CreateRabbitSheet(
          houseId: widget.houseId,
          cage: widget.cage,
          initialType: _type,
        ),
      );
    });
  }
}

class _CreateRabbitSheet extends ConsumerStatefulWidget {
  const _CreateRabbitSheet({
    required this.houseId,
    required this.cage,
    required this.initialType,
  })  : rabbit = null,
        cages = const <Cage>[];

  const _CreateRabbitSheet.edit({
    required this.houseId,
    required this.rabbit,
    required this.cages,
  })  : cage = null,
        initialType = '';

  final int houseId;
  final Cage? cage;
  final String initialType;
  final Rabbit? rabbit;
  final List<Cage> cages;

  @override
  ConsumerState<_CreateRabbitSheet> createState() => _CreateRabbitSheetState();
}

class _CreateRabbitSheetState extends ConsumerState<_CreateRabbitSheet> {
  final _formKey = GlobalKey<FormState>();
  final _breedController = TextEditingController();
  final _colorController = TextEditingController();
  final _weightController = TextEditingController();
  final _remarkController = TextEditingController();
  late String _type;
  late int _selectedCageId;
  var _gender = '0';
  var _arrivalMethod = '0';
  var _health = 'normal';
  var _saving = false;

  bool get _isEdit => widget.rabbit != null;

  Rabbit get _rabbit => widget.rabbit!;

  Cage get _createCage => widget.cage!;

  @override
  void initState() {
    super.initState();
    final rabbit = widget.rabbit;
    if (rabbit == null) {
      _type = widget.initialType;
      _selectedCageId = _createCage.id;
    } else {
      _type = rabbit.type;
      _gender = rabbit.gender;
      _selectedCageId = rabbit.cageId;
      _arrivalMethod =
          rabbit.arrivalMethod.isEmpty ? '0' : rabbit.arrivalMethod;
      _breedController.text = rabbit.breed;
      final weight = rabbit.weight;
      if (weight != null && weight > 0) {
        _weightController.text = weight.toStringAsFixed(2);
      }
    }
    if (!_isEdit && _type == '0') {
      _gender = '0';
    }
  }

  @override
  void dispose() {
    _breedController.dispose();
    _colorController.dispose();
    _weightController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: mediaQuery.size.height * 0.9),
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildHeader(context),
                Flexible(
                  fit: FlexFit.loose,
                  child: SingleChildScrollView(
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
                    child: _buildFormBody(context),
                  ),
                ),
                _buildActionBar(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _isEdit ? '编辑兔子信息' : '新增兔子信息',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 4),
                Text(
                  _isEdit
                      ? '兔 #${_rabbit.id} · 当前笼位 #${_rabbit.cageId}'
                      : '笼位 ${_createCage.cageNumber.isEmpty ? '#${_createCage.id}' : _createCage.cageNumber}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: '关闭',
            onPressed: _saving ? null : () => Navigator.of(context).pop(),
            icon: const Icon(Icons.close),
          ),
        ],
      ),
    );
  }

  Widget _buildFormBody(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_isEdit) ...[
          const _SectionLabel('当前档案'),
          const SizedBox(height: 8),
          _ReadOnlyInfoBox(
            icon: Icons.info_outline,
            text:
                '${_rabbit.typeLabel} · ${_rabbit.genderLabel}。类型和性别涉及生产流程，需通过对应业务动作调整。',
          ),
          const SizedBox(height: 18),
        ] else ...[
          const _SectionLabel('性别'),
          const SizedBox(height: 8),
          Wrap(
            spacing: 16,
            runSpacing: 8,
            children: [
              _RadioChoice(
                value: '1',
                groupValue: _gender,
                label: '公',
                onChanged: (value) => setState(() => _gender = value),
              ),
              _RadioChoice(
                value: '0',
                groupValue: _gender,
                label: '母',
                onChanged: (value) => setState(() => _gender = value),
              ),
            ],
          ),
          const SizedBox(height: 18),
          const _RequiredLabel('生育状态'),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: _type,
            isExpanded: true,
            decoration: const InputDecoration(
              hintText: '请选择生育状态',
            ),
            items: const [
              DropdownMenuItem(value: '0', child: Text('待配种')),
              DropdownMenuItem(value: '1', child: Text('后备兔')),
              DropdownMenuItem(value: '2', child: Text('商品兔')),
            ],
            onChanged: _saving
                ? null
                : (value) => setState(() => _type = value ?? _type),
          ),
          const SizedBox(height: 18),
        ],
        if (_isEdit) ...[
          const _RequiredLabel('笼位'),
          const SizedBox(height: 8),
          DropdownButtonFormField<int>(
            value: _selectedCageId,
            isExpanded: true,
            decoration: const InputDecoration(hintText: '请选择笼位'),
            items: [
              for (final cage in _editableCages())
                DropdownMenuItem(
                  value: cage.id,
                  child: Text(cage.label),
                ),
            ],
            onChanged: _saving
                ? null
                : (value) => setState(
                      () => _selectedCageId = value ?? _selectedCageId,
                    ),
          ),
          const SizedBox(height: 18),
        ],
        if (!_isEdit) ...[
          const _SectionLabel('健康状况'),
          const SizedBox(height: 8),
          Wrap(
            spacing: 16,
            runSpacing: 8,
            children: [
              _RadioChoice(
                value: 'excellent',
                groupValue: _health,
                label: '优秀',
                onChanged: (value) => setState(() => _health = value),
              ),
              _RadioChoice(
                value: 'normal',
                groupValue: _health,
                label: '正常',
                onChanged: (value) => setState(() => _health = value),
              ),
              _RadioChoice(
                value: 'poor',
                groupValue: _health,
                label: '差',
                onChanged: (value) => setState(() => _health = value),
              ),
            ],
          ),
          const SizedBox(height: 18),
        ],
        _ResponsiveFieldRow(
          children: [
            TextFormField(
              controller: _breedController,
              decoration: const InputDecoration(
                labelText: '品种',
                hintText: '请输入品种',
                counterText: '',
              ),
              maxLength: 100,
              textInputAction:
                  _isEdit ? TextInputAction.done : TextInputAction.next,
              validator: (value) {
                final text = value?.trim() ?? '';
                if (text.length > 100) {
                  return '品种不能超过 100 字';
                }
                return null;
              },
            ),
            if (!_isEdit)
              TextFormField(
                controller: _colorController,
                decoration: const InputDecoration(
                  labelText: '颜色',
                  hintText: '请输入颜色',
                ),
                textInputAction: TextInputAction.next,
              ),
          ],
        ),
        const SizedBox(height: 12),
        _ResponsiveFieldRow(
          children: [
            DropdownButtonFormField<String>(
              value: _arrivalMethod,
              isExpanded: true,
              decoration: const InputDecoration(labelText: '来源'),
              items: const [
                DropdownMenuItem(value: '0', child: Text('购入')),
                DropdownMenuItem(value: '1', child: Text('出生')),
              ],
              onChanged: _saving
                  ? null
                  : (value) => setState(
                        () => _arrivalMethod = value ?? _arrivalMethod,
                      ),
            ),
            TextFormField(
              controller: _weightController,
              decoration: const InputDecoration(
                labelText: '体重',
                suffixText: 'kg',
              ),
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              textInputAction: TextInputAction.next,
              inputFormatters: [
                FilteringTextInputFormatter.allow(
                  RegExp(r'^\d*\.?\d{0,2}'),
                ),
              ],
            ),
          ],
        ),
        if (!_isEdit) ...[
          const SizedBox(height: 18),
          const _SectionLabel('备注'),
          const SizedBox(height: 8),
          TextFormField(
            controller: _remarkController,
            decoration: const InputDecoration(hintText: '请输入备注'),
            minLines: 3,
            maxLines: 4,
          ),
          const SizedBox(height: 8),
          Text(
            '颜色、健康状况和备注为界面预留项，当前后端兔只档案暂未落库。',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ],
    );
  }

  Widget _buildActionBar(BuildContext context) {
    final palette = AppPalette.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: palette.surface,
        border: Border(top: BorderSide(color: palette.line)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
        child: Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: _saving ? null : () => Navigator.of(context).pop(),
                child: const Text('取消'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: ElevatedButton(
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : Text(_isEdit ? '保存' : '确定'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() => _saving = true);
    try {
      if (_isEdit) {
        await ref.read(rabbitRepositoryProvider).updateRabbit(
              houseId: widget.houseId,
              rabbitId: _rabbit.id,
              cageId: _selectedCageId,
              motherId: _rabbit.motherId,
              breed: _breedController.text,
              arrivalMethod: _arrivalMethod,
              arrivalDate: _rabbit.arrivalDate,
              weight: double.tryParse(_weightController.text.trim()),
            );
      } else {
        await ref.read(rabbitRepositoryProvider).createRabbit(
              houseId: widget.houseId,
              cageId: _createCage.id,
              type: _type,
              gender: _gender,
              breed: _breedController.text,
              arrivalMethod: _arrivalMethod,
              weight: double.tryParse(_weightController.text.trim()),
            );
      }
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _isEdit ? '已更新兔 #${_rabbit.id}' : '已录入到 ${_createCageName()}',
            ),
          ),
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

  List<Cage> _editableCages() {
    final cages = widget.cages
        .where((cage) => cage.id == _selectedCageId || _cageFitsRabbit(cage))
        .toList();
    if (cages.every((cage) => cage.id != _selectedCageId)) {
      cages.insert(
        0,
        Cage(
          id: _selectedCageId,
          houseId: widget.houseId,
          cageNumber: '#$_selectedCageId',
          status: '',
          rabbitCount: 0,
          isEnabled: true,
        ),
      );
    }
    return cages;
  }

  bool _cageFitsRabbit(Cage cage) {
    return cage.status == '0' || cage.status == _cageStatusForType(_type);
  }

  String _cageStatusForType(String type) {
    switch (type) {
      case '0':
        return '1';
      case '1':
        return '2';
      case '2':
        return '3';
      default:
        return '';
    }
  }

  String _createCageName() {
    final cage = _createCage;
    return cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;
  }
}

class _RabbitTypeChoice extends StatelessWidget {
  const _RabbitTypeChoice({
    required this.value,
    required this.groupValue,
    required this.label,
    required this.onChanged,
  });

  final String value;
  final String groupValue;
  final String label;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return InkWell(
      onTap: () => onChanged(value),
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Radio<String>(
              value: value,
              groupValue: groupValue,
              onChanged: (value) {
                if (value != null) {
                  onChanged(value);
                }
              },
              activeColor: palette.success,
            ),
            Text(label, style: Theme.of(context).textTheme.titleMedium),
          ],
        ),
      ),
    );
  }
}

class _RadioChoice extends StatelessWidget {
  const _RadioChoice({
    required this.value,
    required this.groupValue,
    required this.label,
    required this.onChanged,
  });

  final String value;
  final String groupValue;
  final String label;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return InkWell(
      onTap: () => onChanged(value),
      borderRadius: BorderRadius.circular(8),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Radio<String>(
            value: value,
            groupValue: groupValue,
            onChanged: (value) {
              if (value != null) {
                onChanged(value);
              }
            },
            activeColor: palette.success,
          ),
          Text(label, style: Theme.of(context).textTheme.titleMedium),
        ],
      ),
    );
  }
}

class _ResponsiveFieldRow extends StatelessWidget {
  const _ResponsiveFieldRow({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 420) {
          return Column(
            children: [
              for (var i = 0; i < children.length; i++) ...[
                if (i > 0) const SizedBox(height: 12),
                children[i],
              ],
            ],
          );
        }

        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var i = 0; i < children.length; i++) ...[
              if (i > 0) const SizedBox(width: 10),
              Expanded(child: children[i]),
            ],
          ],
        );
      },
    );
  }
}

class _ReadOnlyInfoBox extends StatelessWidget {
  const _ReadOnlyInfoBox({
    required this.icon,
    required this.text,
  });

  final IconData icon;
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
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: palette.muted),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
        ],
      ),
    );
  }
}

class _CageTypeWarning extends StatelessWidget {
  const _CageTypeWarning({required this.cage});

  final Cage cage;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.warningSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Text(
        '当前笼位用途为${cage.usageLabel}，选择其他类型时后端会拒绝提交。',
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: palette.warning,
              fontWeight: FontWeight.w800,
            ),
      ),
    );
  }
}

class _RequiredLabel extends StatelessWidget {
  const _RequiredLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text.rich(
      TextSpan(
        children: [
          const TextSpan(
            text: '* ',
            style: TextStyle(color: AppColors.red),
          ),
          TextSpan(text: label),
        ],
      ),
      style: Theme.of(context).textTheme.titleMedium,
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(label, style: Theme.of(context).textTheme.titleMedium);
  }
}
