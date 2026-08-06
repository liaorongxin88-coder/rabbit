import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/services/session_store.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
      'houseId.7': 11,
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'first-token'});
  });

  test('readSession caches platform storage until a session mutation',
      () async {
    final store = SessionStore();

    final first = await store.readSession();
    FlutterSecureStorage.setMockInitialValues({'token': 'changed-on-disk'});
    final cached = await store.readSession();

    expect(first.token, 'first-token');
    expect(identical(first, cached), isTrue);

    await store.saveHouseId(7, 22);
    final updated = await store.readSession();
    expect(updated.token, 'first-token');
    expect(updated.houseId, 22);

    await store.clear();
    expect((await store.readSession()).isAuthenticated, isFalse);
  });
}
