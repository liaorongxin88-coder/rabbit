import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/house_member.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_members_screen.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

void main() {
  testWidgets('invites an exact phone without searching platform accounts',
      (tester) async {
    final repository = _InvitationHouseRepository();
    await _pumpMembersScreen(tester, repository, const <HouseMember>[]);

    expect(find.text('查找账号'), findsNothing);
    expect(find.textContaining('同商户'), findsNothing);
    await tester.enterText(
      find.byKey(const ValueKey('house-invitation-phone-field')),
      '13800138000',
    );
    await tester.tap(find.byKey(const ValueKey('submit-house-invitation')));
    await tester.pumpAndSettle();

    expect(repository.invitedHouseId, 8);
    expect(repository.invitedPhone, '13800138000');
    expect(repository.invitedRole, 'STAFF');
    expect(find.text('邀请已提交'), findsOneWidget);
  });

  testWidgets('adds a co-owner without demoting the current owner',
      (tester) async {
    final repository = _InvitationHouseRepository();
    await _pumpMembersScreen(
      tester,
      repository,
      const [
        HouseMember(
          userId: 9,
          userName: '王场长',
          perms: 'control',
          isAdmin: false,
        ),
      ],
    );

    await tester.scrollUntilVisible(
      find.text('王场长'),
      240,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('王场长'));
    await tester.pumpAndSettle();

    expect(find.text('设为所有者'), findsOneWidget);
    expect(find.textContaining('转让'), findsNothing);
    await tester.tap(find.text('设为所有者'));
    await tester.pumpAndSettle();

    expect(find.text('新增共同所有者'), findsOneWidget);
    expect(
      find.textContaining('您和 王场长 都将保留所有者权限'),
      findsOneWidget,
    );
    await tester.tap(find.widgetWithText(FilledButton, '设为所有者'));
    await tester.pumpAndSettle();

    expect(repository.updatedHouseId, 8);
    expect(repository.updatedMemberUserId, 9);
    expect(repository.updatedPerms, 'control');
    expect(repository.updatedIsAdmin, isTrue);
  });

  testWidgets('allows removing an owner and displays the last-owner conflict',
      (tester) async {
    final repository = _InvitationHouseRepository()
      ..removeError = const ApiException(
        '兔舍必须保留至少一名所有者',
        businessCode: 409,
      );
    await _pumpMembersScreen(
      tester,
      repository,
      const [
        HouseMember(
          userId: 10,
          userName: '李场长',
          perms: 'control',
          isAdmin: true,
        ),
      ],
    );

    await tester.scrollUntilVisible(
      find.text('李场长'),
      240,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('李场长'));
    await tester.pumpAndSettle();

    final removeTile = tester.widget<ListTile>(
      find.ancestor(
        of: find.text('移除所有者'),
        matching: find.byType(ListTile),
      ),
    );
    expect(removeTile.onTap, isNotNull);
    await tester.tap(find.text('移除所有者'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, '移除'));
    await tester.pumpAndSettle();

    expect(repository.removedHouseId, 8);
    expect(repository.removedMemberUserId, 10);
    expect(find.text('兔舍必须保留至少一名所有者'), findsOneWidget);
  });

  testWidgets('house detail describes the co-owner model', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          houseCagesProvider(8).overrideWith((_) async => const []),
          houseRabbitsProvider(8).overrideWith((_) async => const []),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
              role: 'OWNER',
              permissions: ['rabbit:house-members:list'],
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseDetailScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('我的角色：所有者'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('人员管理'),
      240,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.textContaining('新增共同所有者'), findsOneWidget);
    expect(find.textContaining('转让管理员'), findsNothing);
  });
}

Future<void> _pumpMembersScreen(
  WidgetTester tester,
  _InvitationHouseRepository repository,
  List<HouseMember> members,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        houseRepositoryProvider.overrideWithValue(repository),
        housePermissionProvider(8).overrideWith(
          (_) async => const HousePermission(
            perms: 'control',
            isAdmin: true,
            role: 'OWNER',
            permissions: ['rabbit:house-members:list'],
          ),
        ),
        houseMembersProvider(8).overrideWith((_) async => members),
      ],
      child: MaterialApp(
        theme: buildAppTheme(),
        home: const HouseMembersScreen(
          houseId: 8,
          houseName: '测试兔舍',
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

class _InvitationHouseRepository extends HouseRepository {
  _InvitationHouseRepository() : super(ApiClient(SessionStore()));

  int? invitedHouseId;
  String? invitedPhone;
  String? invitedRole;
  int? updatedHouseId;
  int? updatedMemberUserId;
  String? updatedPerms;
  bool? updatedIsAdmin;
  int? removedHouseId;
  int? removedMemberUserId;
  ApiException? removeError;

  @override
  Future<void> inviteMember({
    required int houseId,
    required String phone,
    required String role,
  }) async {
    invitedHouseId = houseId;
    invitedPhone = phone;
    invitedRole = role;
  }

  @override
  Future<void> updateMember({
    required int houseId,
    required int memberUserId,
    String? perms,
    bool? isAdmin,
  }) async {
    updatedHouseId = houseId;
    updatedMemberUserId = memberUserId;
    updatedPerms = perms;
    updatedIsAdmin = isAdmin;
  }

  @override
  Future<void> removeMember({
    required int houseId,
    required int memberUserId,
  }) async {
    removedHouseId = houseId;
    removedMemberUserId = memberUserId;
    final error = removeError;
    if (error != null) {
      throw error;
    }
  }
}

const _house = RabbitHouse(
  id: 8,
  name: '测试兔舍',
  remark: '',
  layoutRows: 1,
  layoutCols: 1,
  layoutLayers: 1,
);
