import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_entry_flow.dart';

class CageManagementSection extends ConsumerStatefulWidget {
  const CageManagementSection({
    super.key,
    required this.house,
  });

  final RabbitHouse house;

  @override
  ConsumerState<CageManagementSection> createState() =>
      _CageManagementSectionState();
}

class _CageManagementSectionState extends ConsumerState<CageManagementSection> {
  final _searchController = TextEditingController();
  var _keyword = '';

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_handleSearchChanged);
  }

  @override
  void dispose() {
    _searchController
      ..removeListener(_handleSearchChanged)
      ..dispose();
    super.dispose();
  }

  void _handleSearchChanged() {
    final next = _searchController.text.trim();
    if (next == _keyword) {
      return;
    }
    setState(() => _keyword = next);
  }

  @override
  Widget build(BuildContext context) {
    final houseId = widget.house.id;
    final cages = ref.watch(houseCagesProvider(houseId));

    return SectionCard(
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
            child: _CageHeader(
              house: widget.house,
              onCreate: () => _showCreateCagesSheet(context),
              onRefresh: () => ref.invalidate(houseCagesProvider(houseId)),
            ),
          ),
          const Divider(height: 1, color: AppColors.line),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                  controller: _searchController,
                  decoration: InputDecoration(
                    hintText: '请输入兔子位置进行搜索',
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: _keyword.isEmpty
                        ? null
                        : IconButton(
                            tooltip: '清空搜索',
                            icon: const Icon(Icons.close),
                            onPressed: _searchController.clear,
                          ),
                  ),
                ),
                const SizedBox(height: 14),
                cages.when(
                  data: _buildCageGrid,
                  loading: () => const _CageLoading(),
                  error: (error, _) => _InlineSectionError(
                    message: error.toString(),
                    onRetry: () => ref.invalidate(houseCagesProvider(houseId)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCageGrid(List<Cage> cages) {
    final keyword = _keyword.toLowerCase();
    final filtered = keyword.isEmpty
        ? cages
        : cages
            .where((cage) => cage.cageNumber.toLowerCase().contains(keyword))
            .toList();

    if (cages.isEmpty) {
      return _CageEmptyState(
        title: '暂无笼位',
        message: '点击“新增笼位”，按整排编号、层数和每排位置批量生成。',
        actionLabel: '新增笼位',
        onAction: () => _showCreateCagesSheet(context),
      );
    }

    if (filtered.isEmpty) {
      return const _CageEmptyState(
        title: '没有匹配笼位',
        message: '换一个位置编号试试，或清空搜索查看全部笼位。',
      );
    }

    final occupied = cages.where((cage) => cage.rabbitCount > 0).length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _CageMetricChip(label: '总笼位', value: '${cages.length}'),
            _CageMetricChip(label: '空笼', value: '${cages.length - occupied}'),
            _CageMetricChip(label: '有兔', value: '$occupied'),
          ],
        ),
        const SizedBox(height: 14),
        LayoutBuilder(
          builder: (context, constraints) {
            final columns = constraints.maxWidth >= 640 ? 4 : 3;
            return GridView.builder(
              itemCount: filtered.length,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: columns,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: columns >= 4 ? 1.45 : 1.18,
              ),
              itemBuilder: (context, index) {
                return _CageTile(
                  cage: filtered[index],
                  onTap: () => showRabbitEntryTypeSheet(
                    context: context,
                    houseId: widget.house.id,
                    cage: filtered[index],
                  ),
                );
              },
            );
          },
        ),
      ],
    );
  }

  Future<void> _showCreateCagesSheet(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) => _CreateCagesSheet(houseId: widget.house.id),
    );
  }
}

class _CageHeader extends StatelessWidget {
  const _CageHeader({
    required this.house,
    required this.onCreate,
    required this.onRefresh,
  });

  final RabbitHouse house;
  final VoidCallback onCreate;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: AppColors.softBlue,
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.grid_view_rounded, color: AppColors.blue),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('笼位列表', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 3),
              Text(
                '${house.name} · ${house.layoutLabel}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
          ),
        ),
        IconButton(
          tooltip: '刷新笼位',
          onPressed: onRefresh,
          icon: const Icon(Icons.refresh),
        ),
        const SizedBox(width: 4),
        FilledButton.icon(
          onPressed: onCreate,
          icon: const Icon(Icons.add),
          label: const Text('新增'),
        ),
      ],
    );
  }
}

class _CageTile extends StatelessWidget {
  const _CageTile({
    required this.cage,
    required this.onTap,
  });

  final Cage cage;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final occupied = cage.rabbitCount > 0;
    final title = occupied ? '在栏 ${cage.rabbitCount} 只' : '空笼';
    final titleColor = occupied ? AppColors.green : AppColors.ink;

    return Material(
      color: AppColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: const BorderSide(color: AppColors.line),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
                child: Align(
                  alignment: Alignment.topLeft,
                  child: Text(
                    title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          color: titleColor,
                          fontSize: 17,
                          fontWeight: FontWeight.w900,
                        ),
                  ),
                ),
              ),
            ),
            const Divider(height: 1, color: AppColors.line),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 9, 12, 10),
              child: Text(
                cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: AppColors.muted,
                  fontSize: 18,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CreateCagesSheet extends ConsumerStatefulWidget {
  const _CreateCagesSheet({required this.houseId});

  final int houseId;

  @override
  ConsumerState<_CreateCagesSheet> createState() => _CreateCagesSheetState();
}

class _CreateCagesSheetState extends ConsumerState<_CreateCagesSheet> {
  final _formKey = GlobalKey<FormState>();
  final _rowController = TextEditingController();
  final _layersController = TextEditingController(text: '3');
  final _positionsController = TextEditingController(text: '3');
  var _saving = false;

  @override
  void dispose() {
    _rowController.dispose();
    _layersController.dispose();
    _positionsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.of(context).viewInsets.bottom;
    final preview = _previewLabels();

    return Padding(
      padding: EdgeInsets.fromLTRB(20, 18, 20, 20 + inset),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '新增笼位',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  IconButton(
                    tooltip: '关闭',
                    onPressed:
                        _saving ? null : () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _RequiredNumberField(
                controller: _rowController,
                label: '整排编号',
                hintText: '例如 1',
                allowText: true,
                onChanged: _refreshPreview,
              ),
              const SizedBox(height: 14),
              _RequiredNumberField(
                controller: _layersController,
                label: '笼子高几层',
                hintText: '例如 3',
                onChanged: _refreshPreview,
              ),
              const SizedBox(height: 7),
              const _FieldHelpText(
                '如下:根据自己兔场的情况填写,一层就写1,二层就写2,三层就写3',
              ),
              const SizedBox(height: 14),
              _RequiredNumberField(
                controller: _positionsController,
                label: '每排几个位置',
                hintText: '例如 3',
                onChanged: _refreshPreview,
              ),
              const SizedBox(height: 7),
              const _FieldHelpText('如下:每排有多少个就写多少'),
              if (preview.isNotEmpty) ...[
                const SizedBox(height: 16),
                Text('生成预览', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final label in preview.take(9))
                      _PreviewLabel(label: label),
                    if (preview.length > 9)
                      _PreviewLabel(label: '+${preview.length - 9}'),
                  ],
                ),
              ],
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed:
                          _saving ? null : () => Navigator.of(context).pop(),
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
                          : const Text('确定'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  List<String> _previewLabels() {
    final row = _rowController.text.trim();
    final layers = int.tryParse(_layersController.text.trim()) ?? 0;
    final positions = int.tryParse(_positionsController.text.trim()) ?? 0;
    if (row.isEmpty || layers <= 0 || positions <= 0) {
      return const <String>[];
    }
    return _buildCageNumbers(row: row, layers: layers, positions: positions);
  }

  void _refreshPreview(String _) {
    setState(() {});
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final row = _rowController.text.trim();
    final layers = int.parse(_layersController.text.trim());
    final positions = int.parse(_positionsController.text.trim());
    final labels = _buildCageNumbers(
      row: row,
      layers: layers,
      positions: positions,
    );

    setState(() => _saving = true);
    var created = 0;
    try {
      final repository = ref.read(rabbitRepositoryProvider);
      for (final label in labels) {
        await repository.createCage(
          houseId: widget.houseId,
          cageNumber: label,
          remark: '客户端批量新增',
        );
        created++;
      }
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已新增 $created 个笼位')),
        );
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      final message = error is ApiException ? error.message : error.toString();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text(created > 0 ? '已新增 $created 个，$message' : message)),
      );
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  List<String> _buildCageNumbers({
    required String row,
    required int layers,
    required int positions,
  }) {
    final labels = <String>[];
    var serial = 1;
    for (var position = 1; position <= positions; position++) {
      for (var layer = 1; layer <= layers; layer++) {
        labels.add('$row(${_layerLabel(layer, layers)})$serial');
        serial++;
      }
    }
    return labels;
  }

  String _layerLabel(int layer, int totalLayers) {
    if (totalLayers == 1) {
      return '上';
    }
    if (totalLayers == 2) {
      return layer == 1 ? '上' : '下';
    }
    if (totalLayers == 3) {
      return const ['上', '中', '下'][layer - 1];
    }
    return '第$layer层';
  }
}

class _RequiredNumberField extends StatelessWidget {
  const _RequiredNumberField({
    required this.controller,
    required this.label,
    required this.hintText,
    this.allowText = false,
    this.onChanged,
  });

  final TextEditingController controller;
  final String label;
  final String hintText;
  final bool allowText;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      enabled: true,
      decoration: InputDecoration(
        label: Text.rich(
          TextSpan(
            children: [
              const TextSpan(
                text: '* ',
                style: TextStyle(color: AppColors.red),
              ),
              TextSpan(text: label),
            ],
          ),
        ),
        hintText: hintText,
      ),
      keyboardType: allowText ? TextInputType.text : TextInputType.number,
      inputFormatters: allowText
          ? const <TextInputFormatter>[]
          : [FilteringTextInputFormatter.digitsOnly],
      validator: (value) {
        final text = value?.trim() ?? '';
        if (text.isEmpty) {
          return '请填写$label';
        }
        if (!allowText) {
          final number = int.tryParse(text) ?? 0;
          if (number <= 0) {
            return '$label必须大于 0';
          }
          if (number > 50) {
            return '$label不能超过 50';
          }
        }
        return null;
      },
      onChanged: (value) {
        final formState = Form.maybeOf(context);
        formState?.validate();
        onChanged?.call(value);
      },
    );
  }
}

class _FieldHelpText extends StatelessWidget {
  const _FieldHelpText(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: AppColors.muted,
            fontWeight: FontWeight.w700,
          ),
    );
  }
}

class _PreviewLabel extends StatelessWidget {
  const _PreviewLabel({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.line),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: AppColors.ink,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _CageMetricChip extends StatelessWidget {
  const _CageMetricChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.line),
      ),
      child: Text(
        '$label $value',
        style: const TextStyle(
          color: AppColors.ink,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _CageEmptyState extends StatelessWidget {
  const _CageEmptyState({
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.line),
      ),
      child: Column(
        children: [
          const Icon(Icons.grid_view_outlined, color: AppColors.muted),
          const SizedBox(height: 8),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: onAction,
              icon: const Icon(Icons.add),
              label: Text(actionLabel!),
            ),
          ],
        ],
      ),
    );
  }
}

class _CageLoading extends StatelessWidget {
  const _CageLoading();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        const SizedBox(width: 10),
        Text('加载笼位中...', style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _InlineSectionError extends StatelessWidget {
  const _InlineSectionError({
    required this.message,
    required this.onRetry,
  });

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.softRed,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.line),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline, color: AppColors.red),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          TextButton(onPressed: onRetry, child: const Text('重试')),
        ],
      ),
    );
  }
}
