import 'package:flutter/foundation.dart';

/// Build-time configuration (`--dart-define=KEY=value`), so the same code ships to
/// staging and production without edits.
class AppConfig {
  AppConfig._();

  static const String _apiUrl = String.fromEnvironment('WAVE_API_URL');

  /// Empty on web means "same origin": nginx serves the app and proxies `/api`.
  static String get apiBaseUrl {
    if (_apiUrl.isNotEmpty) return _apiUrl;
    if (kIsWeb) return '';
    // Android emulator reaches the host machine through 10.0.2.2.
    return defaultTargetPlatform == TargetPlatform.android
        ? 'http://10.0.2.2:8080'
        : 'http://localhost:8080';
  }

  static String get wsBaseUrl {
    final base = apiBaseUrl.isEmpty ? Uri.base.origin : apiBaseUrl;
    return base.replaceFirst(RegExp('^http'), 'ws');
  }

  /// Agency WhatsApp numbers shown on "Nous contacter".
  static const List<String> whatsappNumbers = [
    String.fromEnvironment('WAVE_WHATSAPP_1', defaultValue: '+213549705582'),
    String.fromEnvironment('WAVE_WHATSAPP_2', defaultValue: '+213776167407'),
  ];

  /// Optional "Powered by …" line under the logo; hidden unless configured.
  static const String poweredBy = String.fromEnvironment('WAVE_POWERED_BY');

  /// Map tiles. OpenStreetMap's public servers are fine for development only;
  /// production should point to a commercial or self-hosted tile service.
  static const String tileUrl = String.fromEnvironment(
    'WAVE_TILE_URL',
    defaultValue: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  );
  static const String seamarkTileUrl = String.fromEnvironment(
    'WAVE_SEAMARK_URL',
    defaultValue: 'https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png',
  );
}
