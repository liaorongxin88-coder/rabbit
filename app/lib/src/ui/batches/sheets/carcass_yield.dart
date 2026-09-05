import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/carcass_yield.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';

Future<bool> showBatchCarcassYieldSheet({
  required BuildContext context,
  required int houseId,
  required Batch batch,
  required bool hasExistingValue,
}) async {
  return await showAppModalSheet<bool>(
        context: context,
        builder: (context) => _CarcassYieldSheet(
          houseId: houseId,
          batch: batch,
          hasExistingValue: hasExistingValue,
        ),
      ) ??
      false;
}

Future<void> showBatchCarcassYieldHistorySheet({
  required BuildContext context,
  required int houseId,
  required Batch batch,
}) {
  return showAppModalSheet<void>(
    context: context,
    builder: (context) => _CarcassYieldHistorySheet(
      houseId: houseId,
      batch: batch,
    ),
  );
}

class _CarcassYieldSheet extends ConsumerStatefulWidget {
  const _CarcassYieldSheet({
    required this.houseId,
    required this.batch,
    required this.hasExistingValue,
  });

  final int houseId;
  final Batch batch;
  final bool hasExistingValue;

  @override
  ConsumerState<_CarcassYieldSheet> createState() => _CarcassYieldSheetState();
}

class _CarcassYieldSheetState extends ConsumerState<_CarcassYieldSheet> {
  final _request = BatchWriteRequestController();
  final _yieldController = TextEditingController();
  final _sourceController = TextEditingController();
  final _reportController = TextEditingController();
  final _reasonController = TextEditingController();
  final _remarkController = TextEditingController();

  late DateTime _measuredDate;
  XFile? _evidence;
  String? _uploadedEvidencePath;
  String? _evidenceFileId;
  String? _error;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _measuredDate = farmToday();
    if (!widget.hasExistingValue) {
      _reasonController.text = '首次录入';
    }
  }

  @override
  void dispose() {
    _yieldController.dispose();
    _sourceController.dispose();
    _reportController.dispose();
    _reasonController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final date = await showDatePicker(
      context: context,
      initialDate: _measuredDate,
      firstDate: DateTime(2000),
      lastDate: farmToday(),
    );
    if (date != null && mounted) setState(() => _measuredDate = date);
  }

  Future<void> _pickEvidence() async {
    try {
      final file = await ImagePicker().pickImage(source: ImageSource.gallery);
      if (file != null && mounted) {
        setState(() {
          _evidence = file;
          if (_uploadedEvidencePath != file.path) {
            _evidenceFileId = null;
            _uploadedEvidencePath = null;
          }
        });
      }
    } catch (_) {
      if (mounted) setState(() => _error = '无法选择凭证图片，请检查系统授权');
    }
  }

  Future<void> _submit() async {
    if (_saving) return;
    final percent = double.tryParse(_yieldController.text.trim());
    if (percent == null) {
      setState(() => _error = '请填写有效的出肉率');
      return;
    }
    final fingerprint = canonicalBatchWriteFingerprint({
      'batchId': widget.batch.id,
      'yieldPercent': percent,
      'sourceUnit': _sourceController.text,
      'measuredDate': DateFormat('yyyy-MM-dd').format(_measuredDate),
      'reportNumber': _reportController.text,
      'evidencePath': _evidence?.path,
      'changeReason': _reasonController.text,
      'remark': _remarkController.text,
    });
    final requestId = _request.requestIdFor(fingerprint);
    var draft = BatchCarcassYieldDraft(
      yieldRate: percent / 100,
      sourceUnit: _sourceController.text,
      measuredDate: _measuredDate,
      reportNumber: _reportController.text,
      evidenceFileId: _evidenceFileId,
      changeReason: _reasonController.text,
      remark: _remarkController.text,
      requestId: requestId,
    );
    final validation = draft.validate();
    if (validation != null) {
      setState(() => _error = validation);
      return;
    }

    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final evidence = _evidence;
      if (evidence != null && _evidenceFileId == null) {
        _evidenceFileId = await ref.read(reproRepositoryProvider).uploadImage(
              houseId: widget.houseId,
              filePath: evidence.path,
              fileName: evidence.name,
            );
        _uploadedEvidencePath = evidence.path;
      }
      draft = BatchCarcassYieldDraft(
        yieldRate: percent / 100,
        sourceUnit: _sourceController.text,
        measuredDate: _measuredDate,
        reportNumber: _reportController.text,
        evidenceFileId: _evidenceFileId,
        changeReason: _reasonController.text,
        remark: _remarkController.text,
        requestId: requestId,
      );
      await ref.read(batchRepositoryProvider).createCarcassYield(
            houseId: widget.houseId,
            batchId: widget.batch.id,
            draft: draft,
          );
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error is ApiException
              ? error.message
              : error is ArgumentError
                  ? '${error.message}'
                  : '出肉率保存失败，请检查网络后重试';
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: media.size.height * 0.9),
        child: Padding(
          padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                title: Text(widget.hasExistingValue ? '修正出肉率' : '录入出肉率'),
                subtitle: Text('批次 ${widget.batch.batchCode} · 每次保存都会新增版本'),
                trailing: IconButton(
                  tooltip: '关闭',
                  onPressed: _saving ? null : () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close),
                ),
              ),
              Flexible(
                child: ListView(
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
                  children: [
                    TextField(
                      key: const ValueKey('carcass-yield-percent'),
                      controller: _yieldController,
                      enabled: !_saving,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: '出肉率（%）*'),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      key: const ValueKey('carcass-yield-source'),
                      controller: _sourceController,
                      enabled: !_saving,
                      maxLength: 100,
                      decoration: const InputDecoration(labelText: '来源单位*'),
                    ),
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.event_outlined),
                      title: const Text('检测或屠宰日期'),
                      subtitle:
                          Text(DateFormat('yyyy-MM-dd').format(_measuredDate)),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: _saving ? null : _pickDate,
                    ),
                    TextField(
                      key: const ValueKey('carcass-yield-report'),
                      controller: _reportController,
                      enabled: !_saving,
                      maxLength: 100,
                      decoration: const InputDecoration(labelText: '报告编号（可选）'),
                    ),
                    OutlinedButton.icon(
                      key: const ValueKey('carcass-yield-evidence'),
                      onPressed: _saving ? null : _pickEvidence,
                      icon: const Icon(Icons.add_photo_alternate_outlined),
                      label: Text(
                          _evidence == null ? '选择凭证图片（可选）' : _evidence!.name),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      key: const ValueKey('carcass-yield-reason'),
                      controller: _reasonController,
                      enabled: !_saving,
                      maxLength: 300,
                      decoration: const InputDecoration(labelText: '修改说明*'),
                    ),
                    TextField(
                      key: const ValueKey('carcass-yield-remark'),
                      controller: _remarkController,
                      enabled: !_saving,
                      maxLength: 2000,
                      maxLines: 3,
                      decoration: const InputDecoration(labelText: '备注（可选）'),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 10, 20, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (_error != null) ...[
                      Text(
                        _error!,
                        key: const ValueKey('carcass-yield-error'),
                        style: TextStyle(
                          color: AppPalette.of(context).danger,
                        ),
                      ),
                      const SizedBox(height: 8),
                    ],
                    FilledButton.icon(
                      key: const ValueKey('carcass-yield-submit'),
                      onPressed: _saving ? null : _submit,
                      icon: _saving
                          ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.save_outlined),
                      label: Text(_saving ? '正在保存' : '保存版本'),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CarcassYieldHistorySheet extends ConsumerStatefulWidget {
  const _CarcassYieldHistorySheet({
    required this.houseId,
    required this.batch,
  });

  final int houseId;
  final Batch batch;

  @override
  ConsumerState<_CarcassYieldHistorySheet> createState() =>
      _CarcassYieldHistorySheetState();
}

class _CarcassYieldHistorySheetState
    extends ConsumerState<_CarcassYieldHistorySheet> {
  BatchCarcassYieldPage? _page;
  Object? _error;
  var _pageNumber = 1;
  var _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await ref.read(batchRepositoryProvider).listCarcassYields(
            houseId: widget.houseId,
            batchId: widget.batch.id,
            page: _pageNumber,
          );
      if (mounted) setState(() => _page = page);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final page = _page;
    final pageCount = page == null || page.pageSize <= 0
        ? 1
        : (page.total / page.pageSize).ceil().clamp(1, 1 << 20);
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints:
            BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.88),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: const Text('出肉率版本历史'),
              subtitle: Text('批次 ${widget.batch.batchCode} · 最近版本在前'),
              trailing: IconButton(
                tooltip: '关闭',
                onPressed: () => Navigator.of(context).pop(),
                icon: const Icon(Icons.close),
              ),
            ),
            Flexible(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null
                      ? Center(
                          child: OutlinedButton.icon(
                            onPressed: _load,
                            icon: const Icon(Icons.refresh),
                            label: const Text('版本历史读取失败，重试'),
                          ),
                        )
                      : page == null || page.items.isEmpty
                          ? const Center(child: Text('还没有出肉率版本'))
                          : ListView.separated(
                              padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
                              itemCount: page.items.length,
                              separatorBuilder: (_, __) => const Divider(),
                              itemBuilder: (context, index) {
                                final item = page.items[index];
                                return ListTile(
                                  contentPadding: EdgeInsets.zero,
                                  title: Text(
                                      '${(item.yieldRate * 100).toStringAsFixed(2)}% · ${item.sourceUnit}'),
                                  subtitle: Text([
                                    DateFormat('yyyy-MM-dd')
                                        .format(item.measuredDate),
                                    item.changeReason,
                                    item.createdByName ??
                                        '用户 #${item.createdBy}',
                                    DateFormat('yyyy-MM-dd HH:mm').format(
                                      farmLocalDateTime(item.createdAt),
                                    ),
                                    if (item.reportNumber != null)
                                      '报告 ${item.reportNumber}',
                                    if (item.evidenceFileId != null) '已保存凭证',
                                    if (item.remark != null) item.remark!,
                                  ].join('\n')),
                                );
                              },
                            ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
              child: Row(
                children: [
                  Expanded(
                      child: Text(
                          '第 $_pageNumber/$pageCount 页 · 共 ${page?.total ?? 0} 条')),
                  IconButton(
                    tooltip: '上一页',
                    onPressed: _loading || _pageNumber <= 1
                        ? null
                        : () {
                            setState(() => _pageNumber--);
                            _load();
                          },
                    icon: const Icon(Icons.chevron_left),
                  ),
                  IconButton(
                    tooltip: '下一页',
                    onPressed: _loading || _pageNumber >= pageCount
                        ? null
                        : () {
                            setState(() => _pageNumber++);
                            _load();
                          },
                    icon: const Icon(Icons.chevron_right),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
