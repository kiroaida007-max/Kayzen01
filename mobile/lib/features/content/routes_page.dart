import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../../widgets/states.dart';
import '../home/home_page.dart' show RouteTitle;
import '../search/search_card.dart' show openRouteSearch, portLabel;

enum _Direction { all, fromAlgeria, toAlgeria }

/// Every port pair with the companies serving it, so travellers compare at a glance.
class RoutesPage extends ConsumerStatefulWidget {
  const RoutesPage({super.key});

  @override
  ConsumerState<RoutesPage> createState() => _RoutesPageState();
}

class _RoutesPageState extends ConsumerState<RoutesPage> {
  _Direction _direction = _Direction.fromAlgeria;
  String _query = '';
  final Set<String> _operators = {};

  @override
  Widget build(BuildContext context) {
    final meta = ref.watch(metaProvider);
    return WavePage(
      title: context.t('routes.title'),
      subtitle: context.t('routes.subtitle'),
      child: meta.when(
        loading: () => const SizedBox(height: 300, child: LoadingView()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(metaProvider)),
        data: (m) => _body(context, m),
      ),
    );
  }

  Widget _body(BuildContext context, Meta meta) {
    final lang = context.lang;
    bool algerian(String code) => meta.port(code)?.country == 'DZ';
    final q = _query.trim().toLowerCase();
    final routes = meta.routes.where((r) {
      if (_direction == _Direction.fromAlgeria && !algerian(r.from)) return false;
      if (_direction == _Direction.toAlgeria && algerian(r.from)) return false;
      if (_operators.isNotEmpty && !_operators.contains(r.operator)) return false;
      if (q.isNotEmpty && !portLabel(meta, r.from, lang).toLowerCase().contains(q) && !portLabel(meta, r.to, lang).toLowerCase().contains(q)) return false;
      return true;
    });
    final pairs = <String, List<RouteSummary>>{};
    for (final r in routes) {
      pairs.putIfAbsent('${r.from}|${r.to}', () => []).add(r);
    }
    final keys = pairs.keys.toList()
      ..sort((a, b) {
        final ra = pairs[a]!.first, rb = pairs[b]!.first;
        final byFrom = portLabel(meta, ra.from, lang).compareTo(portLabel(meta, rb.from, lang));
        return byFrom != 0 ? byFrom : portLabel(meta, ra.to, lang).compareTo(portLabel(meta, rb.to, lang));
      });
    final activeOperators = meta.operators.where((o) => o.active && o.routes > 0).toList();

    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Wrap(spacing: 10, runSpacing: 10, crossAxisAlignment: WrapCrossAlignment.center, children: [
        SegmentedButton<_Direction>(
          showSelectedIcon: false,
          segments: [
            ButtonSegment(value: _Direction.fromAlgeria, label: Text(context.t('routes.fromAlgeria'))),
            ButtonSegment(value: _Direction.toAlgeria, label: Text(context.t('routes.toAlgeria'))),
            ButtonSegment(value: _Direction.all, label: Text(context.t('routes.all'))),
          ],
          selected: {_direction},
          onSelectionChanged: (s) => setState(() => _direction = s.first),
        ),
        SizedBox(
          width: 280,
          child: TextField(
            onChanged: (v) => setState(() => _query = v),
            decoration: InputDecoration(isDense: true, prefixIcon: const Icon(Icons.search_rounded), hintText: context.t('routes.filter')),
          ),
        ),
      ]),
      const SizedBox(height: 12),
      Wrap(spacing: 8, runSpacing: 8, children: [
        for (final o in activeOperators)
          FilterChip(
            backgroundColor: Colors.white,
            selectedColor: WaveColors.sky,
            selected: _operators.contains(o.code),
            label: OperatorLogo(o.code, size: 15),
            onSelected: (v) => setState(() => v ? _operators.add(o.code) : _operators.remove(o.code)),
          ),
      ]),
      const SizedBox(height: 18),
      LayoutBuilder(builder: (context, c) {
        final columns = c.maxWidth >= 1000 ? 2 : 1;
        final width = (c.maxWidth - (columns - 1) * 16) / columns;
        return Wrap(spacing: 16, runSpacing: 16, children: [
          for (final k in keys) SizedBox(width: width, child: _PairCard(meta: meta, routes: pairs[k]!)),
        ]);
      }),
    ]);
  }
}

class _PairCard extends ConsumerWidget {
  const _PairCard({required this.meta, required this.routes});
  final Meta meta;
  final List<RouteSummary> routes;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = context.lang;
    final currency = ref.watch(settingsProvider).currency;
    final first = routes.first;
    final sorted = [...routes]..sort((a, b) => a.fromPriceDzd.minor.compareTo(b.fromPriceDzd.minor));
    Money price(RouteSummary r) => Money(meta.rates.convertMinor(r.fromPriceDzd.minor, Currency.DZD, currency), currency);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Expanded(child: RouteTitle(from: portLabel(meta, first.from, lang), to: portLabel(meta, first.to, lang))),
            TextButton(
              onPressed: () => openRouteSearch(context, ref, first.from, first.to),
              child: Text(context.t('routes.see')),
            ),
          ]),
          const SizedBox(height: 8),
          for (final r in sorted)
            Container(
              margin: const EdgeInsets.only(top: 8),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(color: WaveColors.background, borderRadius: BorderRadius.circular(10)),
              child: Row(children: [
                SizedBox(width: 130, child: Align(alignment: AlignmentDirectional.centerStart, child: OperatorLogo(r.operator, size: 15))),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(
                      '${r.typicalDurationMin == null ? '—' : Fmt.duration(r.typicalDurationMin!)} · ${context.t('common.perWeek', {'n': _perWeek(r.departuresPerWeek)})}',
                      style: const TextStyle(fontSize: 12.5),
                    ),
                    if (r.nextDeparture != null)
                      Text(
                        '${context.t('routes.next')} : ${Fmt.dayShort(r.nextDeparture!.local, lang)} ${Fmt.time(r.nextDeparture!.local)}',
                        style: const TextStyle(fontSize: 12, color: WaveColors.muted),
                      ),
                  ]),
                ),
                Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
                  Text(context.t('results.from'), style: const TextStyle(fontSize: 11, color: WaveColors.muted)),
                  Text(price(r).format(lang), style: const TextStyle(fontWeight: FontWeight.w700, color: WaveColors.navy)),
                ]),
              ]),
            ),
        ]),
      ),
    );
  }

  static String _perWeek(double n) => n == n.roundToDouble() ? n.toInt().toString() : n.toStringAsFixed(1);
}
