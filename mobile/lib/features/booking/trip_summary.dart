import 'package:flutter/material.dart';

import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../data/search_request.dart';
import '../../widgets/brand.dart';
import '../results/results_page.dart' show cabinLabel;
import '../search/pickers.dart' show accommodationLabel;
import '../search/search_card.dart' show portLabel;

AccommodationPref accommodationFromApi(String value) =>
    AccommodationPref.values.where((a) => apiName(a) == value).firstOrNull ?? AccommodationPref.seat;

/// One crossing of a quote or booking: company, ports, port-local times, ship, fare and berths.
class LegSummary extends StatelessWidget {
  const LegSummary({super.key, required this.leg, required this.meta, required this.inbound});
  final LegQuote leg;
  final Meta? meta;
  final bool inbound;

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    final vessel = leg.vessel == null ? null : meta?.vessel(leg.vessel!)?.name;
    final dayShift = Fmt.dayDiff(leg.departure.local, leg.arrival.local);
    final muted = const TextStyle(fontSize: 12.5, color: WaveColors.muted);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(color: WaveColors.sky, borderRadius: BorderRadius.circular(6)),
          child: Text(context.t(inbound ? 'results.inbound' : 'results.outbound'),
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: WaveColors.navy)),
        ),
        const SizedBox(width: 10),
        Expanded(child: Text(Fmt.dayLong(leg.departure.local, lang), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500))),
        OperatorLogo(leg.operator, size: 15),
      ]),
      const SizedBox(height: 10),
      Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(Fmt.time(leg.departure.local), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            Text(portLabel(meta, leg.from, lang), style: const TextStyle(fontWeight: FontWeight.w600)),
            Text(leg.departure.utcLabel, style: muted),
          ]),
        ),
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Icon(Directionality.of(context) == TextDirection.rtl ? Icons.west_rounded : Icons.east_rounded, color: WaveColors.goldDeep),
        ),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text('${Fmt.time(leg.arrival.local)}${dayShift > 0 ? ' +$dayShift' : ''}', textDirection: TextDirection.ltr, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            Text(portLabel(meta, leg.to, lang), style: const TextStyle(fontWeight: FontWeight.w600)),
            Text(leg.arrival.utcLabel, style: muted),
          ]),
        ),
      ]),
      const SizedBox(height: 8),
      Wrap(spacing: 6, runSpacing: 6, children: [
        if (vessel != null) _Tag(icon: Icons.directions_boat_outlined, text: vessel),
        _Tag(icon: Icons.sell_outlined, text: leg.tariffName.of(lang)),
        if (leg.cabins.isEmpty) _Tag(icon: Icons.event_seat_outlined, text: accommodationLabel(context, accommodationFromApi(leg.accommodation))),
        for (final c in leg.cabins) _Tag(icon: Icons.bed_outlined, text: '${c.count} × ${cabinLabel(context, c.type)}'),
        if (leg.priceSource != 'LIVE') _Tag(icon: Icons.info_outline_rounded, text: context.t('results.reference')),
      ]),
    ]);
  }
}

class _Tag extends StatelessWidget {
  const _Tag({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(border: Border.all(color: WaveColors.line), borderRadius: BorderRadius.circular(6)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, size: 14, color: WaveColors.muted),
          const SizedBox(width: 4),
          Flexible(child: Text(text, style: const TextStyle(fontSize: 12), overflow: TextOverflow.ellipsis)),
        ]),
      );
}

/// Every price line of the quote, then the total and the dinar amount charged by CIB / Edahabia.
class PriceSummary extends StatelessWidget {
  const PriceSummary({super.key, required this.legs, required this.adjustments, required this.total, required this.payable, this.meta, this.expanded = true});
  final Meta? meta;
  final List<LegQuote> legs;
  final List<PriceLine> adjustments;
  final Money total;
  final Map<Currency, Money> payable;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    final dzd = payable[Currency.DZD];
    Widget line(String label, Money amount, {bool strong = false}) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 3),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(
              child: Text(label,
                  style: TextStyle(fontSize: 13, fontWeight: strong ? FontWeight.w600 : FontWeight.w400, color: amount.isNegative ? WaveColors.success : WaveColors.navyInk)),
            ),
            const SizedBox(width: 12),
            Text(amount.format(lang),
                style: TextStyle(fontSize: 13, fontWeight: strong ? FontWeight.w700 : FontWeight.w500, color: amount.isNegative ? WaveColors.success : WaveColors.navyInk)),
          ]),
        );
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      if (expanded)
        for (final (i, leg) in legs.indexed) ...[
          Padding(
            padding: const EdgeInsets.only(top: 6, bottom: 2),
            child: Text('${context.t(i == 0 ? 'results.outbound' : 'results.inbound')} · ${portLabel(meta, leg.from, lang)} → ${portLabel(meta, leg.to, lang)}',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: WaveColors.muted)),
          ),
          for (final l in leg.lines) line(l.quantity > 1 ? '${l.label} × ${l.quantity}' : l.label, l.total),
        ],
      if (expanded) for (final a in adjustments) line(a.label, a.total),
      const Divider(height: 22),
      Row(children: [
        Expanded(child: Text(context.t('results.total'), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700))),
        Text(total.format(lang), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: WaveColors.navy)),
      ]),
      if (dzd != null && total.currency != Currency.DZD)
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text(
            '${const ['Soit', 'That is', 'أي'][lang.index]} ${dzd.format(lang)} '
            '${const ['débités en dinars par CIB / Edahabia', 'charged in dinars by CIB / Edahabia', 'تُخصم بالدينار عبر CIB / الذهبية'][lang.index]}',
            textAlign: TextAlign.end,
            style: const TextStyle(fontSize: 12, color: WaveColors.muted),
          ),
        ),
    ]);
  }
}
