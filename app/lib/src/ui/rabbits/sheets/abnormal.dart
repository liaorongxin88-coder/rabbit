import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';

const _abnormalStatuses = <String>['外伤', '采食异常', '精神萎靡', '疑似疾病', '其他异常'];

typedef RabbitAbnormalImagePicker = Future<XFile?> Function(
  ImageSource source,
);

Future<bool> showRabbitAbnormalSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
  RabbitAbnormalImagePicker? pickImage,
}) async {
  final recorded = await showAppModalSheet<bool>(
    context: context,
    builder: (context) => _RabbitAbnormalSheet(
      houseId: houseId,
      rabbit: rabbit,
      pickImage: pickImage,
    ),
  );
  return recorded ?? false;
}

class _RabbitAbnormalSheet extends ConsumerStatefulWidget {
  const _RabbitAbnormalSheet({
    required this.houseId,
    required this.rabbit,
    this.pickImage,
  });

  final int houseId;
  final Rabbit rabbit;
  final RabbitAbnormalImagePicker? pickImage;

  @override
  ConsumerState<_RabbitAbnormalSheet> createState() =>
      _RabbitAbnormalSheetState();
}

class _RabbitAbnormalSheetState extends ConsumerState<_RabbitAbnormalSheet> {
  final _formKey = GlobalKey<FormState>();
  final _remarkController = TextEditingController();
  final _writeRequest = BatchWriteRequestController();

  XFile? _image;
  String? _uploadedImageFileId;
  String _warningStatus = _abnormalStatuses.first;
  String? _submitError;
  var _saving = false;

  @override
  void dispose() {
    _remarkController.dispose();
    super.dispose();
  }

  Future<void> _pickImage() async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('拍照'),
              onTap: () => Navigator.of(sheetContext).pop(ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('从相册选择'),
              onTap: () => Navigator.of(sheetContext).pop(ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null || !mounted) {
      return;
    }
    try {
      final selected = await (widget.pickImage != null
          ? widget.pickImage!(source)
          : ImagePicker().pickImage(
              source: source,
              imageQuality: 85,
              maxWidth: 2048,
            ));
      if (selected != null && mounted) {
        setState(() {
          _image = selected;
          _uploadedImageFileId = null;
          _submitError = null;
          _writeRequest.startNewDraft();
        });
      }
    } on PlatformException catch (error) {
      if (mounted) {
        setState(() {
          _submitError = source == ImageSource.camera
              ? '无法使用相机：${error.message ?? '请在系统设置中允许相机权限'}'
              : '无法访问相册：${error.message ?? '请检查系统权限'}';
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _submitError = '选择图片失败，请重试');
      }
    }
  }

  Future<void> _submit() async {
    if (_saving || !(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    final image = _image;
    if (image == null) {
      setState(() => _submitError = '请拍照或从相册选择一张相关图片');
      return;
    }

    final remark = _remarkController.text.trim();
    final requestId = _writeRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'createRabbitAbnormal',
        'houseId': widget.houseId,
        'rabbitId': widget.rabbit.id,
        'warningStatus': _warningStatus,
        'remark': remark,
        'imageName': image.name,
      }),
    );
    setState(() {
      _saving = true;
      _submitError = null;
    });
    try {
      final repository = ref.read(rabbitRepositoryProvider);
      var imageFileId = _uploadedImageFileId;
      if (imageFileId == null) {
        imageFileId = await repository.uploadImage(
          houseId: widget.houseId,
          filePath: image.path,
          fileName: image.name,
        );
        _uploadedImageFileId = imageFileId;
      }
      await repository.createAbnormalCondition(
        houseId: widget.houseId,
        rabbitId: widget.rabbit.id,
        warningStatus: _warningStatus,
        imageFileId: imageFileId,
        remark: remark,
        requestId: requestId,
      );
      if (!mounted) {
        return;
      }
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(true);
      messenger?.showSnackBar(
        SnackBar(content: Text('兔 #${widget.rabbit.id} 已新增异常记录')),
      );
    } catch (error) {
      if (mounted) {
        setState(() => _submitError = _errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final palette = AppPalette.of(context);
    final availableHeight =
        mediaQuery.size.height - mediaQuery.viewInsets.bottom;

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: mediaQuery.viewInsets.bottom),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: availableHeight * .92),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
                child: Row(
                  children: [
                    Icon(Icons.report_problem_outlined, color: palette.danger),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '新增异常记录',
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '兔 #${widget.rabbit.id} · ${widget.rabbit.typeLabel}',
                            key: ValueKey(
                              'rabbit-abnormal-target-${widget.rabbit.id}',
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
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
              ),
              Flexible(
                child: Form(
                  key: _formKey,
                  child: ListView(
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 84),
                    children: [
                      if (_submitError != null) ...[
                        Container(
                          key: const ValueKey('rabbit-abnormal-error'),
                          margin: const EdgeInsets.only(top: 4, bottom: 12),
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: palette.dangerSoft,
                            borderRadius: BorderRadius.circular(8),
                            border:
                                Border.all(color: palette.danger.withAlpha(90)),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Icon(Icons.error_outline, color: palette.danger),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  _submitError!,
                                  style: TextStyle(color: palette.text),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                      DropdownButtonFormField<String>(
                        key: const ValueKey('rabbit-abnormal-status'),
                        value: _warningStatus,
                        decoration: const InputDecoration(labelText: '异常类型 *'),
                        items: _abnormalStatuses
                            .map(
                              (status) => DropdownMenuItem(
                                value: status,
                                child: Text(status),
                              ),
                            )
                            .toList(growable: false),
                        onChanged: _saving
                            ? null
                            : (value) {
                                if (value == null) return;
                                setState(() {
                                  _warningStatus = value;
                                  _writeRequest.startNewDraft();
                                });
                              },
                      ),
                      const SizedBox(height: 14),
                      Text(
                        '相关图片 *',
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      const SizedBox(height: 6),
                      if (_image == null)
                        OutlinedButton.icon(
                          key: const ValueKey('rabbit-abnormal-add-image'),
                          onPressed: _saving ? null : _pickImage,
                          icon: const Icon(Icons.add_a_photo_outlined),
                          label: const Text('添加图片'),
                        )
                      else
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.image_outlined),
                          title: Text(
                            _image!.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: const Text('已选择 1 张图片'),
                          trailing: IconButton(
                            tooltip: '更换图片',
                            onPressed: _saving ? null : _pickImage,
                            icon: const Icon(Icons.edit_outlined),
                          ),
                        ),
                      const SizedBox(height: 14),
                      TextFormField(
                        key: const ValueKey('rabbit-abnormal-remark'),
                        controller: _remarkController,
                        enabled: !_saving,
                        maxLength: 255,
                        maxLines: 4,
                        autovalidateMode: AutovalidateMode.onUserInteraction,
                        decoration: const InputDecoration(
                          labelText: '异常说明 *',
                          hintText: '记录发现的情况和已采取的处理',
                        ),
                        validator: (value) =>
                            value == null || value.trim().isEmpty
                                ? '请填写异常说明'
                                : null,
                      ),
                    ],
                  ),
                ),
              ),
              DecoratedBox(
                decoration: BoxDecoration(
                  border: Border(top: BorderSide(color: palette.line)),
                ),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      OutlinedButton(
                        onPressed:
                            _saving ? null : () => Navigator.of(context).pop(),
                        child: const Text('取消'),
                      ),
                      const SizedBox(height: 8),
                      FilledButton.icon(
                        key: const ValueKey('rabbit-abnormal-submit'),
                        onPressed: _saving ? null : _submit,
                        icon: _saving
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.report_problem_outlined),
                        label: const Text('提交异常记录'),
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
}

String _errorMessage(Object error) {
  if (error is ApiException) {
    return error.message;
  }
  return '提交失败，请检查网络后重试';
}
