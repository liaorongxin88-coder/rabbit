import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';

Future<Rabbit?> showCageRabbitPickerSheet({
  required BuildContext context,
  required List<Rabbit> rabbits,
}) {
  return showAppModalSheet<Rabbit>(
    context: context,
    builder: (context) => _CageRabbitPickerSheet(rabbits: rabbits),
  );
}

class _CageRabbitPickerSheet extends StatelessWidget {
  const _CageRabbitPickerSheet({required this.rabbits});

  final List<Rabbit> rabbits;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * .78,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
              child: Row(
                children: [
                  const Icon(Icons.pets_outlined),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '选择异常兔只',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '当前笼位有 ${rabbits.length} 只在栏兔只',
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: '关闭',
                    onPressed: () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            Flexible(
              child: ListView.separated(
                key: const ValueKey('cage-abnormal-rabbit-picker'),
                padding: const EdgeInsets.fromLTRB(12, 0, 12, 20),
                itemCount: rabbits.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, index) {
                  final rabbit = rabbits[index];
                  return ListTile(
                    key: ValueKey('cage-abnormal-rabbit-${rabbit.id}'),
                    leading: const Icon(Icons.pets_outlined),
                    title: Text('兔 #${rabbit.id} · ${rabbit.typeLabel}'),
                    subtitle: Text(
                      '${rabbit.genderLabel} · ${rabbit.breed.isEmpty ? '品种未填' : rabbit.breed}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.of(context).pop(rabbit),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
