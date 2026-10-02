// Currency codes mirror ISO 4217 / the API, hence upper case.
// ignore_for_file: constant_identifier_names

import 'package:intl/intl.dart';

import 'l10n.dart';

/// Currencies the traveller can display prices in.
enum Currency { DZD, EUR, USD }

extension CurrencyX on Currency {
  String get symbol => switch (this) {
        Currency.DZD => 'DA',
        Currency.EUR => '€',
        Currency.USD => '\$',
      };
}

/// A timestamp as published by the operator, in the port's own time zone.
///
/// `DateTime.parse` would convert `2026-10-14T15:00+02:00` to UTC or device time and lose the
/// port-local wall clock, so we keep both the wall clock and the absolute instant.
class PortTime {
  PortTime(this.local, this.instant, this.offset);

  final DateTime local;
  final DateTime instant;
  final Duration offset;

  static final _offsetPattern = RegExp(r'([+-])(\d{2}):?(\d{2})$');

  factory PortTime.parse(String iso) {
    final instant = DateTime.parse(iso).toUtc();
    if (iso.endsWith('Z')) return PortTime(DateTime.parse(iso.substring(0, iso.length - 1)), instant, Duration.zero);
    final m = _offsetPattern.firstMatch(iso);
    if (m == null) return PortTime(DateTime.parse(iso), instant, Duration.zero);
    final sign = m.group(1) == '-' ? -1 : 1;
    final offset = Duration(hours: int.parse(m.group(2)!), minutes: int.parse(m.group(3)!)) * sign;
    return PortTime(DateTime.parse(iso.substring(0, m.start)), instant, offset);
  }

  /// "UTC+1" style label, used next to times to avoid any time-zone confusion.
  String get utcLabel {
    final h = offset.inHours;
    final m = offset.inMinutes.remainder(60).abs();
    final sign = offset.isNegative ? '-' : '+';
    return m == 0 ? 'UTC$sign${h.abs()}' : 'UTC$sign${h.abs()}:${m.toString().padLeft(2, '0')}';
  }
}

class Fmt {
  Fmt._();

  /// Algeria groups thousands with spaces in French and Arabic alike ("144 136 DA" / "144 136 د.ج").
  static String _numberLocale(AppLang lang) => lang == AppLang.en ? 'en' : 'fr';
  static String _dateLocale(AppLang lang) => switch (lang) {
        AppLang.fr => 'fr',
        AppLang.en => 'en',
        AppLang.ar => 'ar_DZ',
      };

  /// Prices: whole dinars; euros and dollars keep cents only when there are some.
  static String money(int minor, Currency currency, AppLang lang) {
    final value = minor / 100.0;
    final hasCents = minor % 100 != 0 && currency != Currency.DZD;
    final pattern = hasCents ? '#,##0.00' : '#,##0';
    // No-break space: keeps "144 136" on one line and, being a number separator for the bidi
    // algorithm, stops right-to-left text from reordering the groups into "136 144".
    final number = NumberFormat(pattern, _numberLocale(lang)).format(value).replaceAll(RegExp('[ \u202f]'), '\u00a0');
    return switch ((currency, lang)) {
      (Currency.DZD, AppLang.ar) => '$number د.ج',
      (Currency.DZD, AppLang.en) => 'DZD $number',
      (Currency.DZD, _) => '$number DA',
      (Currency.EUR, AppLang.en) => '€$number',
      (Currency.USD, AppLang.en) => '\$$number',
      (Currency.EUR, _) => '$number €',
      (Currency.USD, _) => '$number \$',
    };
  }

  static String time(DateTime local) => DateFormat('HH:mm').format(local);

  static String dayShort(DateTime date, AppLang lang) => DateFormat('EEE d MMM', _dateLocale(lang)).format(date);

  static String dayLong(DateTime date, AppLang lang) => DateFormat('EEEE d MMMM y', _dateLocale(lang)).format(date);

  static String dateInput(DateTime date) => DateFormat('dd/MM/yyyy').format(date);

  static String iso(DateTime date) => DateFormat('yyyy-MM-dd').format(date);

  /// Mockup style: "20h", "23h30".
  static String duration(int minutes) {
    final h = minutes ~/ 60;
    final m = minutes % 60;
    return m == 0 ? '${h}h' : '${h}h${m.toString().padLeft(2, '0')}';
  }

  static String durationRange(int? min, int? max) {
    if (min == null) return '—';
    if (max == null || (max - min).abs() < 60) return duration(min);
    return '${duration(min)} - ${duration(max)}';
  }

  static String passengers(int adults, int seniors, int children, AppLang lang) {
    final grown = adults + seniors;
    final parts = <String>[];
    switch (lang) {
      case AppLang.fr:
        if (grown > 0) parts.add('$grown Adulte${grown > 1 ? 's' : ''}');
        if (children > 0) parts.add('$children Enfant${children > 1 ? 's' : ''}');
      case AppLang.en:
        if (grown > 0) parts.add('$grown Adult${grown > 1 ? 's' : ''}');
        if (children > 0) parts.add('$children Child${children > 1 ? 'ren' : ''}');
      case AppLang.ar:
        if (grown > 0) parts.add('$grown ${grown > 1 ? 'بالغين' : 'بالغ'}');
        if (children > 0) parts.add('$children ${children > 1 ? 'أطفال' : 'طفل'}');
    }
    return parts.join(', ');
  }

  static int dayDiff(DateTime fromLocal, DateTime toLocal) =>
      DateTime(toLocal.year, toLocal.month, toLocal.day).difference(DateTime(fromLocal.year, fromLocal.month, fromLocal.day)).inDays;
}
