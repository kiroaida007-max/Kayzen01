import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/config.dart';
import '../core/theme.dart';

/// WAVE wordmark with its Arabic name and the gold plane swoosh from the mockup.
class WaveLogo extends StatelessWidget {
  const WaveLogo({super.key, this.color = Colors.white, this.scale = 1.0, this.showTagline = true});

  final Color color;
  final double scale;
  final bool showTagline;

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.ltr,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Text(
                'WAVE',
                style: TextStyle(
                  fontFamily: WaveFonts.display,
                  fontWeight: FontWeight.w700,
                  fontSize: 30 * scale,
                  letterSpacing: 1.2 * scale,
                  color: color,
                  height: 1.0,
                ),
              ),
              SizedBox(width: 10 * scale),
              Text(
                'موجة',
                style: TextStyle(fontFamily: WaveFonts.arabic, fontWeight: FontWeight.w700, fontSize: 30 * scale, color: color, height: 1.0),
              ),
            ],
          ),
          SizedBox(height: 2 * scale),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                width: 44 * scale,
                height: 18 * scale,
                child: CustomPaint(painter: _SwooshPainter()),
              ),
              if (showTagline && AppConfig.poweredBy.isNotEmpty) ...[
                SizedBox(width: 6 * scale),
                Text(
                  AppConfig.poweredBy,
                  style: TextStyle(color: WaveColors.goldLight, fontSize: 11.5 * scale, fontWeight: FontWeight.w500),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

/// Gold plane taking off on a curved trail.
class _SwooshPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final trail = Path()
      ..moveTo(0, size.height * 0.85)
      ..quadraticBezierTo(size.width * 0.45, size.height * 1.05, size.width * 0.78, size.height * 0.35);
    canvas.drawPath(
      trail,
      Paint()
        ..shader = WaveColors.goldGradient.createShader(Offset.zero & size)
        ..style = PaintingStyle.stroke
        ..strokeWidth = size.height * 0.12
        ..strokeCap = StrokeCap.round,
    );
    final icon = Icons.flight;
    final painter = TextPainter(
      text: TextSpan(
        text: String.fromCharCode(icon.codePoint),
        style: TextStyle(fontFamily: icon.fontFamily, package: icon.fontPackage, fontSize: size.height * 0.95, color: WaveColors.gold),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    canvas.save();
    canvas.translate(size.width * 0.78, size.height * 0.30);
    canvas.rotate(math.pi / 4);
    painter.paint(canvas, Offset(-painter.width / 2, -painter.height / 2));
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Gold underline flourish next to section titles ("Destinations populaires ~~").
class GoldFlourish extends StatelessWidget {
  const GoldFlourish({super.key, this.width = 64, this.height = 14});
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) => SizedBox(width: width, height: height, child: CustomPaint(painter: _FlourishPainter()));
}

class _FlourishPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..shader = WaveColors.goldGradient.createShader(Offset.zero & size)
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 2; i++) {
      final y = size.height * (0.45 + i * 0.28);
      final path = Path()
        ..moveTo(size.width * (0.05 + i * 0.12), y)
        ..cubicTo(size.width * 0.35, y - size.height * 0.55, size.width * 0.6, y + size.height * 0.35, size.width * (0.95 - i * 0.1), y - size.height * 0.2);
      canvas.drawPath(path, paint..strokeWidth = i == 0 ? 2.4 : 1.6);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key, this.trailing, this.color = WaveColors.navyInk, this.size = 26});
  final String text;
  final Widget? trailing;
  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    final arabic = Directionality.of(context) == TextDirection.rtl;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Flexible(
          child: Text(
            text,
            style: TextStyle(
              fontFamily: arabic ? WaveFonts.arabic : WaveFonts.display,
              fontWeight: FontWeight.w700,
              fontSize: size,
              color: color,
              height: 1.15,
            ),
          ),
        ),
        const SizedBox(width: 12),
        const GoldFlourish(),
        const Spacer(),
        ?trailing,
      ],
    );
  }
}

/// The mockup's gold call-to-action.
class GoldButton extends StatelessWidget {
  const GoldButton({super.key, required this.label, this.onPressed, this.icon = Icons.arrow_forward, this.height = 56, this.fontSize = 16, this.loading = false, this.expand = false, this.padding = 24});

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final double height;
  final double fontSize;
  final bool loading;
  final bool expand;
  final double padding;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null && !loading;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final content = Row(
      mainAxisSize: expand ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (loading)
          const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2.4, color: WaveColors.navy))
        else
          Flexible(
            child: Text(
              label,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: WaveColors.navyDeep, fontWeight: FontWeight.w600, fontSize: fontSize),
            ),
          ),
        if (icon != null && !loading) ...[
          const SizedBox(width: 10),
          Transform.flip(flipX: rtl, child: Icon(icon, color: WaveColors.navyDeep, size: fontSize + 4)),
        ],
      ],
    );
    return Semantics(
      container: true,
      button: true,
      enabled: enabled,
      label: label,
      onTap: enabled ? onPressed : null,
      excludeSemantics: true,
      child: Opacity(
        opacity: enabled || loading ? 1 : 0.55,
        child: Material(
          color: Colors.transparent,
          child: Ink(
            height: height,
            decoration: BoxDecoration(
              gradient: WaveColors.buttonGradient,
              borderRadius: BorderRadius.circular(10),
              boxShadow: [BoxShadow(color: WaveColors.goldDeep.withValues(alpha: 0.35), blurRadius: 14, offset: const Offset(0, 6))],
            ),
            child: InkWell(
              borderRadius: BorderRadius.circular(10),
              onTap: enabled ? onPressed : null,
              child: Padding(padding: EdgeInsets.symmetric(horizontal: padding), child: content),
            ),
          ),
        ),
      ),
    );
  }
}

/// Text-based operator marks in each company's colours (official logos are trademarks and
/// should be added under the partnership agreements).
class OperatorLogo extends StatelessWidget {
  const OperatorLogo(this.code, {super.key, this.size = 22});
  final String code;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.ltr,
      child: switch (code) {
        'BAL' => Text(
            'BALEARIA',
            style: TextStyle(
              fontFamily: WaveFonts.body,
              fontWeight: FontWeight.w800,
              fontStyle: FontStyle.italic,
              fontSize: size,
              letterSpacing: 0.4,
              foreground: Paint()
                ..shader = const LinearGradient(colors: [Color(0xFF0F6E84), Color(0xFF0B8FA8)]).createShader(Rect.fromLTWH(0, 0, size * 6, size)),
            ),
          ),
        'CL' => Row(mainAxisSize: MainAxisSize.min, children: [
            Text('CORSICA ', style: TextStyle(fontWeight: FontWeight.w700, fontSize: size * 0.72, color: const Color(0xFFE2231A))),
            Text('linea', style: TextStyle(fontWeight: FontWeight.w500, fontSize: size * 0.72, color: const Color(0xFFE2231A))),
            const SizedBox(width: 4),
            Container(
              width: size * 0.95,
              height: size * 0.95,
              decoration: const BoxDecoration(color: Color(0xFFE2231A), shape: BoxShape.circle),
              child: Icon(Icons.sailing, color: Colors.white, size: size * 0.6),
            ),
          ]),
        'GNV' => Row(mainAxisSize: MainAxisSize.min, children: [
            SizedBox(width: size * 1.3, height: size, child: CustomPaint(painter: _StripesPainter(const Color(0xFF00377B)))),
            const SizedBox(width: 3),
            Text('GNV', style: TextStyle(fontWeight: FontWeight.w800, fontSize: size, color: const Color(0xFF00377B), letterSpacing: -0.5)),
          ]),
        'NE' => Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(Icons.waves, color: const Color(0xFF1D7FC4), size: size),
            const SizedBox(width: 4),
            Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('NOURIS ELBAHR', style: TextStyle(fontWeight: FontWeight.w700, fontSize: size * 0.55, color: const Color(0xFF1D4F91), height: 1.1)),
              Text('Ferries', style: TextStyle(fontWeight: FontWeight.w500, fontSize: size * 0.45, color: const Color(0xFF1D4F91), height: 1.1)),
            ]),
          ]),
        'AF' => Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(Icons.anchor, color: const Color(0xFF0B7A4B), size: size * 0.9),
            const SizedBox(width: 4),
            Text('ALGÉRIE FERRIES', style: TextStyle(fontWeight: FontWeight.w800, fontSize: size * 0.62, color: const Color(0xFF0B7A4B))),
          ]),
        'ATM' => Text('ARMAS TRASMED',
            style: TextStyle(fontWeight: FontWeight.w800, fontStyle: FontStyle.italic, fontSize: size * 0.62, color: const Color(0xFF004C97))),
        _ => Text(code, style: TextStyle(fontWeight: FontWeight.w700, fontSize: size * 0.7)),
      },
    );
  }
}

class _StripesPainter extends CustomPainter {
  _StripesPainter(this.color);
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.height * 0.14
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 3; i++) {
      final inset = i * size.height * 0.22;
      final path = Path()
        ..moveTo(size.width * 0.05 + inset * 0.3, size.height * 0.85 - inset * 0.2)
        ..quadraticBezierTo(size.width * 0.15 + inset, size.height * 0.15 + inset * 0.6, size.width * 0.95, size.height * 0.2 + inset);
      canvas.drawPath(path, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _StripesPainter oldDelegate) => oldDelegate.color != color;
}

Color hexColor(String hex) => Color(int.parse(hex.replaceFirst('#', 'FF'), radix: 16));

/// Brand-neutral ferry illustration in the operator's colour, used where no licensed photo exists.
class ShipArt extends StatelessWidget {
  const ShipArt({super.key, required this.color, this.height = 130});
  final Color color;
  final double height;

  @override
  Widget build(BuildContext context) => SizedBox(height: height, width: double.infinity, child: CustomPaint(painter: _ShipPainter(color)));
}

class _ShipPainter extends CustomPainter {
  _ShipPainter(this.color);
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width, h = size.height;
    final horizon = h * 0.62;
    canvas.drawRect(
      Rect.fromLTWH(0, 0, w, horizon),
      Paint()..shader = const LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFF9CC7EC), Color(0xFFE9F3FB)]).createShader(Rect.fromLTWH(0, 0, w, horizon)),
    );
    canvas.drawRect(
      Rect.fromLTWH(0, horizon, w, h - horizon),
      Paint()..shader = const LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFF2F78B7), Color(0xFF0B3A6E)]).createShader(Rect.fromLTWH(0, horizon, w, h - horizon)),
    );
    // Ship sized from the height so it looks the same in every card width.
    final len = math.min(w * 0.78, h * 2.9);
    final left = (w - len) / 2;
    final waterline = horizon + h * 0.12;
    final hullTop = waterline - h * 0.17;
    final hull = Path()
      ..moveTo(left, hullTop)
      ..lineTo(left + len, hullTop - h * 0.02)
      ..lineTo(left + len * 0.95, waterline)
      ..lineTo(left + len * 0.06, waterline)
      ..close();
    canvas.drawPath(hull, Paint()..color = const Color(0xFF0E2246));
    canvas.drawRect(Rect.fromLTWH(left + len * 0.04, hullTop + h * 0.045, len * 0.93, h * 0.035), Paint()..color = color);
    final white = Paint()..color = Colors.white;
    final decks = [
      Rect.fromLTWH(left + len * 0.08, hullTop - h * 0.10, len * 0.80, h * 0.10),
      Rect.fromLTWH(left + len * 0.16, hullTop - h * 0.19, len * 0.62, h * 0.09),
      Rect.fromLTWH(left + len * 0.26, hullTop - h * 0.26, len * 0.36, h * 0.07),
    ];
    for (final d in decks) {
      canvas.drawRRect(RRect.fromRectAndCorners(d, topRight: Radius.circular(h * 0.04)), white);
      final windows = Paint()..color = const Color(0xFF26456E);
      final count = (d.width / (h * 0.06)).floor();
      for (var i = 0; i < count; i++) {
        canvas.drawRect(Rect.fromLTWH(d.left + h * 0.02 + i * h * 0.06, d.top + d.height * 0.38, h * 0.035, d.height * 0.28), windows);
      }
    }
    canvas.drawRRect(
      RRect.fromRectAndRadius(Rect.fromLTWH(left + len * 0.38, hullTop - h * 0.36, len * 0.07, h * 0.11), Radius.circular(h * 0.015)),
      Paint()..color = color,
    );
    final wave = Paint()
      ..color = Colors.white.withValues(alpha: 0.35)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4;
    for (var row = 0; row < 3; row++) {
      final y = waterline + h * (0.06 + row * 0.07);
      final path = Path()..moveTo(0, y);
      for (var x = 0.0; x < w; x += 24) {
        path.relativeQuadraticBezierTo(6, -3, 12, 0);
        path.relativeQuadraticBezierTo(6, 3, 12, 0);
      }
      canvas.drawPath(path, wave);
    }
  }

  @override
  bool shouldRepaint(covariant _ShipPainter oldDelegate) => oldDelegate.color != color;
}
