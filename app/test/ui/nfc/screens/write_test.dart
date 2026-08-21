import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/writer.dart';
import 'package:rabbit_flutter/src/ui/nfc/screens/write.dart';

void main() {
  const item = NfcWriteSessionItem(
    queueItem: NfcCageQueueItem(
      cageId: 10,
      cageNumber: '一号兔舍东排超长笼位编号(上)100',
      bindingStatus: 'UNBOUND',
      tagUid: null,
      payload: 'r1.8.a.1.signature',
    ),
  );
  const activeSession = NfcWriteSession(
    houseId: 8,
    items: [item],
    currentIndex: 0,
    updatedAt: 1,
  );
  const completedSession = NfcWriteSession(
    houseId: 8,
    items: [
      NfcWriteSessionItem(
        queueItem: NfcCageQueueItem(
          cageId: 10,
          cageNumber: '1-1-1',
          bindingStatus: 'UNBOUND',
          tagUid: null,
          payload: 'r1.8.a.1.signature',
        ),
        status: NfcWriteItemStatus.completed,
        writtenTagUid: '04AABBCC',
      ),
    ],
    currentIndex: 1,
    updatedAt: 2,
  );

  for (final size in [const Size(360, 800), const Size(412, 915)]) {
    testWidgets(
        'writer states fit ${size.width.toInt()}x${size.height.toInt()}',
        (tester) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final states = <NfcWriteState>[
        const NfcWriteState(
          phase: NfcWritePhase.waiting,
          session: activeSession,
          message: '等待标签',
        ),
        const NfcWriteState(
          phase: NfcWritePhase.binding,
          session: activeSession,
          message: '正在绑定',
        ),
        const NfcWriteState(
          phase: NfcWritePhase.success,
          session: activeSession,
          message: '写入成功',
        ),
        const NfcWriteState(
          phase: NfcWritePhase.confirmOverwrite,
          session: activeSession,
          message: '标签已写入其他笼位',
          conflict: NfcWriteException(
            NfcWriteError.existingManagedPayload,
            '标签已写入其他笼位',
            existingPayload: 'r1.9.b.1.signature',
          ),
        ),
        const NfcWriteState(
          phase: NfcWritePhase.paused,
          session: activeSession,
          message: '写入已暂停',
        ),
        const NfcWriteState(
          phase: NfcWritePhase.error,
          session: activeSession,
          message: '标签容量不足，需要至少 80 字节',
        ),
        const NfcWriteState(
          phase: NfcWritePhase.completed,
          session: completedSession,
          message: '本次写入已完成',
        ),
      ];

      for (final state in states) {
        await tester.pumpWidget(
          MaterialApp(
            theme: buildAppTheme(),
            home: NfcWriteView(
              state: state,
              onExit: _noop,
              onPause: _noop,
              onResume: _noop,
              onPrevious: _noop,
              onSkip: _noop,
              onRetry: _noop,
              onConfirmOverwrite: _noop,
              onConfirmBinding: _noop,
              onDone: _noop,
            ),
          ),
        );
        await tester.pump();
        expect(tester.takeException(), isNull, reason: state.phase.name);
      }
    });
  }
}

void _noop() {}
