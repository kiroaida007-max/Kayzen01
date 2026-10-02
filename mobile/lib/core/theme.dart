import 'package:flutter/material.dart';

/// Palette sampled from the WAVE mockup: deep Mediterranean navy and warm gold.
class WaveColors {
  WaveColors._();

  static const navy = Color(0xFF0A2A5E);
  static const navyDeep = Color(0xFF061B3E);
  static const navyInk = Color(0xFF0E2246);
  static const blue = Color(0xFF1F5FBF);
  static const sky = Color(0xFFE8F1FB);
  static const gold = Color(0xFFE2B85C);
  static const goldLight = Color(0xFFF4D78C);
  static const goldDeep = Color(0xFFC8973A);
  static const sand = Color(0xFFFFF8EA);
  static const surface = Color(0xFFFFFFFF);
  static const background = Color(0xFFF3F7FC);
  static const line = Color(0xFFDCE4EF);
  static const muted = Color(0xFF5B6B82);
  static const success = Color(0xFF138A55);
  static const warning = Color(0xFFB7791F);
  static const danger = Color(0xFFC0392B);

  static const goldGradient = LinearGradient(
    colors: [goldLight, gold, goldDeep],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const buttonGradient = LinearGradient(
    colors: [Color(0xFFF6DC95), Color(0xFFE7BF67), Color(0xFFD9AA4E)],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );

  static const navyGradient = LinearGradient(
    colors: [Color(0xFF0D3270), navyDeep],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}

class WaveFonts {
  WaveFonts._();

  static const display = 'PlayfairDisplay';
  static const body = 'Poppins';
  static const arabic = 'Cairo';
}

class WaveTheme {
  WaveTheme._();

  static ThemeData light({bool arabic = false}) {
    final bodyFont = arabic ? WaveFonts.arabic : WaveFonts.body;
    final base = ThemeData(
      useMaterial3: true,
      fontFamily: bodyFont,
      // Poppins has no arrows (→ ↔) and no Arabic; Playfair and Cairo cover them offline.
      fontFamilyFallback: const [WaveFonts.display, WaveFonts.arabic],
      colorScheme: ColorScheme.fromSeed(
        seedColor: WaveColors.navy,
        primary: WaveColors.navy,
        secondary: WaveColors.gold,
        surface: WaveColors.surface,
      ),
      scaffoldBackgroundColor: WaveColors.background,
    );
    final text = base.textTheme.apply(
      bodyColor: WaveColors.navyInk,
      displayColor: WaveColors.navyInk,
      fontFamily: bodyFont,
      fontFamilyFallback: const [WaveFonts.display, WaveFonts.arabic],
    );
    final display = arabic ? WaveFonts.arabic : WaveFonts.display;
    return base.copyWith(
      textTheme: text.copyWith(
        displayLarge: text.displayLarge?.copyWith(fontFamily: display, fontWeight: FontWeight.w800),
        displayMedium: text.displayMedium?.copyWith(fontFamily: display, fontWeight: FontWeight.w800),
        headlineLarge: text.headlineLarge?.copyWith(fontFamily: display, fontWeight: FontWeight.w700),
        headlineMedium: text.headlineMedium?.copyWith(fontFamily: display, fontWeight: FontWeight.w700),
        headlineSmall: text.headlineSmall?.copyWith(fontFamily: display, fontWeight: FontWeight.w700),
        titleLarge: text.titleLarge?.copyWith(fontWeight: FontWeight.w600),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: WaveColors.navy,
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: WaveColors.surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: WaveColors.line),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: WaveColors.line),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: WaveColors.line),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: WaveColors.blue, width: 1.6),
        ),
      ),
      chipTheme: base.chipTheme.copyWith(
        backgroundColor: Colors.white,
        selectedColor: WaveColors.navy,
        side: const BorderSide(color: WaveColors.line),
        // A bare TextStyle here would drop the app fonts (and Arabic coverage) for every chip.
        labelStyle: text.labelLarge?.copyWith(color: WaveColors.navyInk),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: WaveColors.navy,
          foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          // Buttons replace the inherited text style: derive it from the theme to keep the fonts.
          textStyle: text.labelLarge?.copyWith(fontWeight: FontWeight.w600),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: WaveColors.navy,
          side: const BorderSide(color: WaveColors.line),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
      ),
      dividerTheme: const DividerThemeData(color: WaveColors.line, space: 1),
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
    );
  }
}

/// Layout breakpoints used across pages.
class Breakpoints {
  Breakpoints._();

  static const double mobile = 720;
  static const double desktop = 1120;

  static bool isMobile(BuildContext c) => MediaQuery.sizeOf(c).width < mobile;
  static bool isDesktop(BuildContext c) => MediaQuery.sizeOf(c).width >= desktop;
}
