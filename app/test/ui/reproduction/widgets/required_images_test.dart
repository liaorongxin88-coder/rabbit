import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';

import 'package:rabbit_flutter/src/ui/reproduction/widgets/required_images.dart';

void main() {
  testWidgets('required image field adds and removes a selected image',
      (tester) async {
    var files = <XFile>[];

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) => RequiredImagesField(
              files: files,
              pickImage: (_) async =>
                  XFile('/tmp/abortion.png', name: 'abortion.png'),
              onChanged: (value) => setState(() => files = value),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('required-images-add')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('从相册选择'));
    await tester.pumpAndSettle();

    expect(find.text('abortion.png'), findsOneWidget);
    await tester.tap(find.byTooltip('移除图片'));
    await tester.pump();
    expect(find.text('abortion.png'), findsNothing);
  });
}
