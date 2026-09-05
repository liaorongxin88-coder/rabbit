import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:share_plus/share_plus.dart' show Share, XFile;

final fileShareServiceProvider = Provider<FileShareService>(
  (_) => const SystemFileShareService(),
);

abstract interface class FileShareService {
  Future<void> shareSpreadsheet(File file);
}

class SystemFileShareService implements FileShareService {
  const SystemFileShareService();

  @override
  Future<void> shareSpreadsheet(File file) async {
    await Share.shareXFiles(
      [
        XFile(
          file.path,
          mimeType:
              'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        ),
      ],
      subject: '批次统计 Excel',
    );
  }
}
