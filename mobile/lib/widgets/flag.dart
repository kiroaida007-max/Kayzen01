import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Small painted flags. Emoji flags need a colour-emoji font download on the web and render
/// inconsistently across Android versions, so we draw the few flags we need.
class FlagIcon extends StatelessWidget {
  const FlagIcon(this.code, {super.key, this.width = 22});
  final String code;
  final double width;

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: BorderRadius.circular(2.5),
        child: SizedBox(width: width, height: width * 2 / 3, child: CustomPaint(painter: _FlagPainter(code.toUpperCase()))),
      );
}

class _FlagPainter extends CustomPainter {
  _FlagPainter(this.code);
  final String code;

  @override
  void paint(Canvas canvas, Size s) {
    final p = Paint();
    void rect(double x, double y, double w, double h, Color c) => canvas.drawRect(Rect.fromLTWH(x, y, w, h), p..color = c);
    switch (code) {
      case 'FR':
        rect(0, 0, s.width / 3, s.height, const Color(0xFF0055A4));
        rect(s.width / 3, 0, s.width / 3, s.height, Colors.white);
        rect(2 * s.width / 3, 0, s.width / 3, s.height, const Color(0xFFEF4135));
      case 'IT':
        rect(0, 0, s.width / 3, s.height, const Color(0xFF009246));
        rect(s.width / 3, 0, s.width / 3, s.height, Colors.white);
        rect(2 * s.width / 3, 0, s.width / 3, s.height, const Color(0xFFCE2B37));
      case 'ES':
        rect(0, 0, s.width, s.height, const Color(0xFFAA151B));
        rect(0, s.height / 4, s.width, s.height / 2, const Color(0xFFF1BF00));
      case 'DZ':
        rect(0, 0, s.width / 2, s.height, const Color(0xFF006233));
        rect(s.width / 2, 0, s.width / 2, s.height, Colors.white);
        final c = Offset(s.width / 2, s.height / 2);
        final r = s.height * 0.3;
        final crescent = Path.combine(
          PathOperation.difference,
          Path()..addOval(Rect.fromCircle(center: c, radius: r)),
          Path()..addOval(Rect.fromCircle(center: c + Offset(r * 0.32, 0), radius: r * 0.8)),
        );
        canvas.drawPath(crescent, p..color = const Color(0xFFD21034));
        canvas.drawPath(_star(c + Offset(r * 0.55, 0), r * 0.42), p);
      case 'GB':
        rect(0, 0, s.width, s.height, const Color(0xFF012169));
        final white = Paint()
          ..color = Colors.white
          ..strokeWidth = s.height * 0.2;
        final red = Paint()
          ..color = const Color(0xFFC8102E)
          ..strokeWidth = s.height * 0.08;
        canvas.drawLine(Offset.zero, Offset(s.width, s.height), white);
        canvas.drawLine(Offset(s.width, 0), Offset(0, s.height), white);
        canvas.drawLine(Offset.zero, Offset(s.width, s.height), red);
        canvas.drawLine(Offset(s.width, 0), Offset(0, s.height), red);
        rect(s.width * 0.4, 0, s.width * 0.2, s.height, Colors.white);
        rect(0, s.height * 0.35, s.width, s.height * 0.3, Colors.white);
        rect(s.width * 0.44, 0, s.width * 0.12, s.height, const Color(0xFFC8102E));
        rect(0, s.height * 0.41, s.width, s.height * 0.18, const Color(0xFFC8102E));
      default:
        rect(0, 0, s.width, s.height, const Color(0xFFB0BEC5));
    }
  }

  Path _star(Offset c, double r) {
    final path = Path();
    for (var i = 0; i < 10; i++) {
      final radius = i.isEven ? r : r * 0.45;
      final a = -math.pi / 2 + i * math.pi / 5;
      final pt = c + Offset(math.cos(a) * radius, math.sin(a) * radius);
      i == 0 ? path.moveTo(pt.dx, pt.dy) : path.lineTo(pt.dx, pt.dy);
    }
    return path..close();
  }

  @override
  bool shouldRepaint(covariant _FlagPainter old) => old.code != code;
}
