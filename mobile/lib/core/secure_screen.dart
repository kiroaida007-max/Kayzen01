import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Blocks screenshots and screen recording on Android while sensitive data is on screen
/// (implemented in MainActivity.kt). A no-op on the web and on iOS.
class SecureScreen {
  SecureScreen._();

  static const _channel = MethodChannel('dz.wave/secure_screen');

  static Future<void> set(bool enabled) async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
    try {
      await _channel.invokeMethod<void>('setSecure', enabled);
    } on PlatformException {
      // Older host builds without the channel: nothing to protect with.
    } on MissingPluginException {
      // Same, e.g. in widget tests.
    }
  }
}
