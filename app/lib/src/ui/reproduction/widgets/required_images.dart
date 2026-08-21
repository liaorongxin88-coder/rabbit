import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

typedef PickReproImage = Future<XFile?> Function(ImageSource source);

class RequiredImagesField extends StatelessWidget {
  const RequiredImagesField({
    super.key,
    required this.files,
    required this.onChanged,
    this.enabled = true,
    this.pickImage,
  });

  final List<XFile> files;
  final ValueChanged<List<XFile>> onChanged;
  final bool enabled;
  final PickReproImage? pickImage;

  Future<void> _add(BuildContext context) async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('拍照'),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('从相册选择'),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null || !context.mounted) {
      return;
    }
    final selected = pickImage == null
        ? await ImagePicker().pickImage(
            source: source,
            imageQuality: 85,
            maxWidth: 2048,
          )
        : await pickImage!(source);
    if (selected != null) {
      onChanged([...files, selected]);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                '相关图片 *',
                style: Theme.of(context).textTheme.titleSmall,
              ),
            ),
            SizedBox(
              width: 112,
              child: OutlinedButton.icon(
                key: const ValueKey('required-images-add'),
                onPressed:
                    enabled && files.length < 6 ? () => _add(context) : null,
                icon: const Icon(Icons.add_photo_alternate_outlined),
                label: const Text('添加'),
              ),
            ),
          ],
        ),
        if (files.isEmpty)
          const Padding(
            padding: EdgeInsets.only(top: 6),
            child: Text('至少上传 1 张，最多 6 张'),
          )
        else
          ...files.indexed.map(
            (entry) => ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.image_outlined),
              title: Text(
                entry.$2.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: IconButton(
                tooltip: '移除图片',
                onPressed: enabled
                    ? () => onChanged([
                          ...files.take(entry.$1),
                          ...files.skip(entry.$1 + 1),
                        ])
                    : null,
                icon: const Icon(Icons.close),
              ),
            ),
          ),
      ],
    );
  }
}
