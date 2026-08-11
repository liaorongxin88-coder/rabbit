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
  SessionSnapshot? _cachedSession;
  Future<SessionSnapshot>? _pendingRead;
  var _cacheGeneration = 0;

  Future<SessionSnapshot> readSession() {
    final cached = _cachedSession;
    if (cached != null) {
      return Future.value(cached);
    }
    final pending = _pendingRead;
    if (pending != null) {
      return pending;
    }

    final generation = _cacheGeneration;
    late final Future<SessionSnapshot> read;
    read = _readSessionFromDisk().then((snapshot) {
      if (_cacheGeneration == generation) {
        _cachedSession = snapshot;
        return snapshot;
      }
      return _cachedSession ?? snapshot;
    }).whenComplete(() {
      if (identical(_pendingRead, read)) {
        _pendingRead = null;
      }
    });
    _pendingRead = read;
    return read;
  }

  Future<SessionSnapshot> _readSessionFromDisk() async {
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
    _replaceCache(
      SessionSnapshot(
        token: token,
        userId: userId,
        userName: userName,
        houseId: prefs.getInt(_houseIdKeyFor(userId)) ??
            prefs.getInt(_houseIdKey) ??
            0,
      ),
    );
  }

  Future<void> saveHouseId(int userId, int houseId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_houseIdKeyFor(userId), houseId);
    await prefs.setInt(_houseIdKey, houseId);
    final cached = _cachedSession;
    if (cached != null && cached.userId == userId) {
      _replaceCache(cached.copyWith(houseId: houseId));
    }
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
    _replaceCache(SessionSnapshot.empty);
  }

  Future<void> saveUserName(String userName) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_userNameKey, userName);
    final cached = _cachedSession;
    if (cached != null) {
      _replaceCache(cached.copyWith(userName: userName));
    }
  }

  void _replaceCache(SessionSnapshot snapshot) {
    _cacheGeneration += 1;
    _cachedSession = snapshot;
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

  static const empty = SessionSnapshot(
    token: null,
    userId: null,
    userName: null,
    houseId: 0,
  );

  bool get isAuthenticated => token != null && token!.isNotEmpty;

  SessionSnapshot copyWith({
    String? token,
    int? userId,
    String? userName,
    int? houseId,
  }) {
    return SessionSnapshot(
      token: token ?? this.token,
      userId: userId ?? this.userId,
      userName: userName ?? this.userName,
      houseId: houseId ?? this.houseId,
    );
  }
}
