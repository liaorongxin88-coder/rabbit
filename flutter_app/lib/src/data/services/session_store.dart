import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

final sessionStoreProvider = Provider<SessionStore>((ref) => SessionStore());

class SessionStore {
  static const _tokenKey = 'token';
  static const _userIdKey = 'userId';
  static const _userNameKey = 'userName';
  static const _houseIdKey = 'houseId';
  static const _secureStorage = FlutterSecureStorage();

  Future<SessionSnapshot> readSession() async {
    final prefs = await SharedPreferences.getInstance();
    final userId = prefs.getInt(_userIdKey);
    return SessionSnapshot(
      token: await _secureStorage.read(key: _tokenKey),
      userId: userId,
      userName: prefs.getString(_userNameKey),
      houseId: userId == null
          ? 0
          : prefs.getInt(_houseIdKeyFor(userId)) ??
              prefs.getInt(_houseIdKey) ??
              0,
    );
  }

  Future<void> saveAuth({
    required String token,
    required int userId,
    required String userName,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await _secureStorage.write(key: _tokenKey, value: token);
    await prefs.setInt(_userIdKey, userId);
    await prefs.setString(_userNameKey, userName);
  }

  Future<void> saveHouseId(int userId, int houseId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_houseIdKeyFor(userId), houseId);
    await prefs.setInt(_houseIdKey, houseId);
  }

  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    final userId = prefs.getInt(_userIdKey);
    await _secureStorage.delete(key: _tokenKey);
    await prefs.remove(_userIdKey);
    await prefs.remove(_userNameKey);
    await prefs.remove(_houseIdKey);
    if (userId != null) {
      await prefs.remove(_houseIdKeyFor(userId));
    }
  }

  Future<void> saveUserName(String userName) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_userNameKey, userName);
  }

  static String _houseIdKeyFor(int userId) => '$_houseIdKey.$userId';
}

class SessionSnapshot {
  const SessionSnapshot({
    required this.token,
    required this.userId,
    required this.userName,
    required this.houseId,
  });

  final String? token;
  final int? userId;
  final String? userName;
  final int houseId;

  bool get isAuthenticated => token != null && token!.isNotEmpty;
}
