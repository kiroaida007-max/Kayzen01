import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../data/search_request.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../../widgets/states.dart';
import '../search/pickers.dart';
import '../search/search_card.dart';

class ResultsPage extends ConsumerStatefulWidget {
  const ResultsPage({super.key});

  @override
  ConsumerState<ResultsPage> createState() => _ResultsPageState();
}

class _ResultsPageState extends ConsumerState<ResultsPage> {
  bool _inbound = false;
  final Set<String> _hiddenOperators = {};

  @override
  void initState() {
    super.initState();
    // Deep link or refresh: run the search if nothing is loaded yet.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final results = ref.read(searchResultsProvider);
      if (!results.isLoading && results.value == null) ref.read(searchResultsProvider.notifier).run();
    });
  }

  void _modify() {
    showDialog<void>(
      context: context,
      builder: (ctx) => Dialog(
        insetPadding: const EdgeInsets.all(16),
        backgroundColor: Colors.transparent,
        child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1240), child: const SingleChildScrollView(child: SearchCard())),
      ),
    ).then((_) => setState(() => _inbound = false));
  }

  void _pickDay(DateTime day) {
    final notifier = ref.read(searchQueryProvider.notifier);
    if (_inbound) {
      notifier.update((q) => q.copyWith(returnDate: day));
    } else {
      notifier.setDeparture(day);
    }
    ref.read(searchResultsProvider.notifier).run();
  }

  void _select(SailingOffer offer, String tariff) {
    final query = ref.read(searchQueryProvider);
    final selection = ref.read(selectionProvider.notifier);
    if (_inbound) {
      selection.selectInbound(offer, tariff);
    } else {
      selection.selectOutbound(offer, tariff);
      if (query.isRoundTrip) {
        setState(() => _inbound = true);
        return;
      }
    }
    final s = ref.read(selectionProvider);
    if (!query.isRoundTrip || s.inbound != null) context.go('/booking');
  }

  @override
  Widget build(BuildContext context) {
    final results = ref.watch(searchResultsProvider);
    final query = ref.watch(searchQueryProvider);
    final selection = ref.watch(selectionProvider);
    final meta = ref.watch(metaProvider).value;
    final mobile = Breakpoints.isMobile(context);
    final lang = context.lang;

    return Column(children: [
      const WaveHeader(),
      _Summary(query: query, meta: meta, onModify: _modify),
      Expanded(
        child: results.when(
          loading: () => const _Skeleton(),
          error: (e, _) => ErrorView(error: e, onRetry: () => ref.read(searchResultsProvider.notifier).run()),
          data: (response) {
            if (response == null) return const _Skeleton();
            final leg = _inbound && response.inbound != null ? response.inbound! : response.outbound;
            final operators = {for (final o in leg.offers) o.operatorCode: o.operatorName};
            final offers = leg.offers.where((o) => !_hiddenOperators.contains(o.operatorCode)).toList();
            final selectedId = _inbound ? selection.inbound?.sailingId : selection.outbound?.sailingId;

            final list = ListView(
              padding: EdgeInsets.fromLTRB(mobile ? 12 : 0, 16, mobile ? 12 : 0, 120),
              children: [
                if (query.isRoundTrip) ...[
                  _LegTabs(inbound: _inbound, onChanged: (v) => setState(() => _inbound = v), outboundDone: selection.outbound != null),
                  const SizedBox(height: 12),
                ],
                _NearbyDays(days: leg.nearbyDays, selected: leg.date, onPick: _pickDay),
                const SizedBox(height: 14),
                if (operators.length > 1 || mobile)
                  _Filters(
                    operators: operators,
                    hidden: _hiddenOperators,
                    sort: query.sort,
                    onToggle: (code) => setState(() => _hiddenOperators.contains(code) ? _hiddenOperators.remove(code) : _hiddenOperators.add(code)),
                    onSort: (s) {
                      ref.read(searchQueryProvider.notifier).update((q) => q.copyWith(sort: s));
                      ref.read(searchResultsProvider.notifier).run();
                    },
                  ),
                const SizedBox(height: 8),
                if (offers.isEmpty) ...[
                  const SizedBox(height: 24),
                  Center(child: Text(context.t('results.none'), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600))),
                  const SizedBox(height: 12),
                  for (final n in response.notices.where((n) => n.code == 'NO_SAILING_ON_DATE' || n.code == 'SCHEDULE_NOT_PUBLISHED'))
                    Padding(padding: const EdgeInsets.only(bottom: 8), child: NoticeBox(message: n.message, severity: n.severity)),
                ],
                for (final offer in offers)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 14),
                    child: OfferCard(
                      offer: offer,
                      selected: offer.sailingId == selectedId,
                      onSelect: (tariff) => _select(offer, tariff),
                    ),
                  ),
                if (mobile) _Notices(notices: response.notices),
              ],
            );

            return Stack(children: [
              Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 1240),
                  child: mobile
                      ? list
                      : Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 32),
                          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Expanded(child: list),
                            const SizedBox(width: 24),
                            SizedBox(
                              width: 330,
                              child: SingleChildScrollView(
                                padding: const EdgeInsets.only(top: 16, bottom: 120),
                                child: _Notices(notices: response.notices, rates: response.rates, lang: lang),
                              ),
                            ),
                          ]),
                        ),
                ),
              ),
              if (query.isRoundTrip && (selection.outbound != null || selection.inbound != null))
                Positioned(left: 0, right: 0, bottom: 0, child: _SelectionBar(selection: selection)),
            ]);
          },
        ),
      ),
    ]);
  }
}

class _Summary extends ConsumerWidget {
  const _Summary({required this.query, required this.meta, required this.onModify});
  final SearchQuery query;
  final Meta? meta;
  final VoidCallback onModify;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = context.lang;
    final mobile = Breakpoints.isMobile(context);
    final settings = ref.watch(settingsProvider);
    final details = [
      Fmt.dayShort(query.departureDate, lang) + (query.isRoundTrip && query.returnDate != null ? ' – ${Fmt.dayShort(query.returnDate!, lang)}' : ''),
      query.passengers.summary(lang),
      if (query.vehicle != null) vehicleLabel(context, query.vehicle),
      accommodationLabel(context, query.accommodation),
    ].join('  ·  ');
    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(gradient: WaveColors.navyGradient),
      padding: EdgeInsets.fromLTRB(mobile ? 16 : 40, 4, mobile ? 16 : 40, 18),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1180),
          child: Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            runSpacing: 12,
            spacing: 16,
            children: [
              Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
                DefaultTextStyle.merge(
                  style: const TextStyle(color: Colors.white),
                  child: IconTheme.merge(
                    data: const IconThemeData(color: WaveColors.goldLight),
                    child: _WhiteRouteTitle(from: portLabel(meta, query.from, lang), to: portLabel(meta, query.to, lang), roundTrip: query.isRoundTrip, size: mobile ? 19 : 24),
                  ),
                ),
                const SizedBox(height: 4),
                Text(details, style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontSize: 13.5)),
              ]),
              Row(mainAxisSize: MainAxisSize.min, children: [
                SegmentedButton<Currency>(
                  style: SegmentedButton.styleFrom(
                    backgroundColor: Colors.white.withValues(alpha: 0.1),
                    foregroundColor: Colors.white,
                    selectedBackgroundColor: WaveColors.gold,
                    selectedForegroundColor: WaveColors.navyDeep,
                    side: BorderSide(color: Colors.white.withValues(alpha: 0.4)),
                    visualDensity: VisualDensity.compact,
                  ),
                  showSelectedIcon: false,
                  segments: [for (final c in Currency.values) ButtonSegment(value: c, label: Text(c.symbol))],
                  selected: {settings.currency},
                  onSelectionChanged: (s) {
                    ref.read(settingsProvider.notifier).setCurrency(s.first);
                    ref.read(searchResultsProvider.notifier).run();
                  },
                ),
                const SizedBox(width: 12),
                OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(foregroundColor: Colors.white, side: BorderSide(color: Colors.white.withValues(alpha: 0.6))),
                  onPressed: onModify,
                  icon: const Icon(Icons.tune_rounded, size: 18),
                  label: Text(context.t('results.modify')),
                ),
              ]),
            ],
          ),
        ),
      ),
    );
  }
}

class _WhiteRouteTitle extends StatelessWidget {
  const _WhiteRouteTitle({required this.from, required this.to, required this.roundTrip, required this.size});
  final String from;
  final String to;
  final bool roundTrip;
  final double size;

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(
      fontFamily: context.lang == AppLang.ar ? WaveFonts.arabic : WaveFonts.display,
      fontWeight: FontWeight.w700,
      fontSize: size,
      color: Colors.white,
    );
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Text(from, style: style),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10),
        child: Icon(roundTrip ? Icons.sync_alt_rounded : Icons.arrow_forward_rounded, color: WaveColors.goldLight, size: size),
      ),
      Text(to, style: style),
    ]);
  }
}

class _LegTabs extends StatelessWidget {
  const _LegTabs({required this.inbound, required this.onChanged, required this.outboundDone});
  final bool inbound;
  final ValueChanged<bool> onChanged;
  final bool outboundDone;

  @override
  Widget build(BuildContext context) => SegmentedButton<bool>(
        segments: [
          ButtonSegment(value: false, icon: const Icon(Icons.east_rounded), label: Text(context.t('results.chooseOutbound'))),
          ButtonSegment(value: true, icon: const Icon(Icons.west_rounded), label: Text(context.t('results.chooseInbound')), enabled: outboundDone),
        ],
        selected: {inbound},
        onSelectionChanged: (s) => onChanged(s.first),
      );
}

class _NearbyDays extends ConsumerWidget {
  const _NearbyDays({required this.days, required this.selected, required this.onPick});
  final List<DayPrice> days;
  final DateTime selected;
  final ValueChanged<DateTime> onPick;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = context.lang;
    final cheapest = days.where((d) => d.minPrice != null).map((d) => d.minPrice!.minor).fold<int?>(null, (a, b) => a == null || b < a ? b : a);
    return SizedBox(
      height: 72,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: days.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, i) {
          final d = days[i];
          final isSelected = DateUtils.isSameDay(d.date, selected);
          final isCheapest = d.minPrice != null && d.minPrice!.minor == cheapest;
          return InkWell(
            borderRadius: BorderRadius.circular(12),
            onTap: d.sailings == 0 ? null : () => onPick(d.date),
            child: Container(
              width: 116,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              decoration: BoxDecoration(
                color: isSelected ? WaveColors.navy : Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: isSelected ? WaveColors.navy : (isCheapest ? WaveColors.success : WaveColors.line)),
              ),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.center, children: [
                Text(Fmt.dayShort(d.date, lang), style: TextStyle(fontSize: 12.5, color: isSelected ? Colors.white : WaveColors.muted)),
                const SizedBox(height: 4),
                Text(
                  d.minPrice?.format(lang) ?? (d.sailings == 0 ? context.t('results.noSailing') : '—'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontWeight: d.minPrice == null ? FontWeight.w400 : FontWeight.w700,
                    fontSize: d.minPrice == null ? 12.5 : 13.5,
                    color: isSelected ? WaveColors.goldLight : (d.minPrice == null ? WaveColors.muted : (isCheapest ? WaveColors.success : WaveColors.navyInk)),
                  ),
                ),
              ]),
            ),
          );
        },
      ),
    );
  }
}

class _Filters extends StatelessWidget {
  const _Filters({required this.operators, required this.hidden, required this.sort, required this.onToggle, required this.onSort});
  final Map<String, String> operators;
  final Set<String> hidden;
  final SortOrder sort;
  final ValueChanged<String> onToggle;
  final ValueChanged<SortOrder> onSort;

  @override
  Widget build(BuildContext context) {
    return Wrap(spacing: 8, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
      for (final entry in operators.entries)
        FilterChip(
          backgroundColor: Colors.white,
          selectedColor: WaveColors.sky,
          selected: !hidden.contains(entry.key),
          showCheckmark: true,
          label: OperatorLogo(entry.key, size: 15),
          onSelected: (_) => onToggle(entry.key),
        ),
      const SizedBox(width: 12),
      SegmentedButton<SortOrder>(
        style: SegmentedButton.styleFrom(visualDensity: VisualDensity.compact),
        showSelectedIcon: false,
        segments: [
          ButtonSegment(value: SortOrder.price, label: Text(context.t('results.sortPrice'))),
          ButtonSegment(value: SortOrder.departure, label: Text(context.t('results.sortDeparture'))),
          ButtonSegment(value: SortOrder.duration, label: Text(context.t('results.sortDuration'))),
        ],
        selected: {sort},
        onSelectionChanged: (s) => onSort(s.first),
      ),
    ]);
  }
}

/// One crossing: times in port-local time, ship, availability, best fare and all open tariffs.
class OfferCard extends StatefulWidget {
  const OfferCard({super.key, required this.offer, required this.selected, required this.onSelect});
  final SailingOffer offer;
  final bool selected;
  final ValueChanged<String> onSelect;

  @override
  State<OfferCard> createState() => _OfferCardState();
}

class _OfferCardState extends State<OfferCard> {
  bool _expanded = false;
  String? _tariff;

  @override
  Widget build(BuildContext context) {
    final o = widget.offer;
    final lang = context.lang;
    final mobile = Breakpoints.isMobile(context);
    final tariff = _tariff ?? o.cheapest?.code;
    final chosen = o.tariffs.where((t) => t.code == tariff).firstOrNull ?? o.cheapest;
    final dayShift = Fmt.dayDiff(o.departure.local, o.arrival.local);

    final timeline = Row(children: [
      _TimeBlock(time: o.departure, port: o.fromName.of(lang), lang: lang),
      Expanded(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Column(children: [
            Text(Fmt.duration(o.durationMin), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted, fontWeight: FontWeight.w500)),
            const SizedBox(height: 4),
            Row(children: [
              const Expanded(child: Divider(thickness: 1.4, color: WaveColors.line)),
              Icon(Icons.directions_boat_rounded, size: 18, color: hexColor(o.operatorColor)),
              const Expanded(child: Divider(thickness: 1.4, color: WaveColors.line)),
            ]),
            const SizedBox(height: 4),
            Text(o.vesselName ?? context.t('results.shipTba'), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: WaveColors.muted)),
          ]),
        ),
      ),
      _TimeBlock(time: o.arrival, port: o.toName.of(lang), lang: lang, alignEnd: true, dayShift: dayShift),
    ]);

    final priceBlock = Column(crossAxisAlignment: mobile ? CrossAxisAlignment.start : CrossAxisAlignment.end, children: [
      if (chosen != null) ...[
        Text(context.t('results.forGroup'), style: const TextStyle(fontSize: 11.5, color: WaveColors.muted)),
        Text(chosen.total.format(lang), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: WaveColors.navyInk)),
        const SizedBox(height: 2),
        _PriceSourceChip(live: o.isLive),
      ] else
        Text(context.t('results.unavailable'), style: const TextStyle(fontWeight: FontWeight.w600, color: WaveColors.danger)),
    ]);

    final button = o.bookable && chosen != null
        ? (widget.selected
            ? FilledButton.icon(
                onPressed: () => widget.onSelect(chosen.code),
                icon: const Icon(Icons.check_rounded),
                label: Text(context.t('results.selected')),
                style: FilledButton.styleFrom(backgroundColor: WaveColors.success),
              )
            : SizedBox(
                width: mobile ? 128 : 150,
                child: GoldButton(
                  label: context.t('results.choose'),
                  icon: mobile ? null : Icons.arrow_forward,
                  height: 46,
                  fontSize: 15,
                  padding: mobile ? 12 : 24,
                  onPressed: () => widget.onSelect(chosen.code),
                ),
              ))
        : const SizedBox.shrink();

    return Opacity(
      opacity: o.bookable ? 1 : 0.62,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: widget.selected ? WaveColors.success : WaveColors.line, width: widget.selected ? 2 : 1),
          boxShadow: [BoxShadow(color: WaveColors.navyDeep.withValues(alpha: 0.05), blurRadius: 12, offset: const Offset(0, 4))],
        ),
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            OperatorLogo(o.operatorCode, size: 18),
            const Spacer(),
            _AvailabilityChip(level: o.availability, seatsLeft: o.seatsLeft, cabinsLeft: o.cabinsLeft),
          ]),
          const SizedBox(height: 14),
          if (mobile) ...[
            timeline,
            const SizedBox(height: 14),
            Row(crossAxisAlignment: CrossAxisAlignment.end, children: [Expanded(child: priceBlock), button]),
          ] else
            Row(children: [
              Expanded(flex: 5, child: timeline),
              const SizedBox(width: 24),
              Expanded(flex: 2, child: priceBlock),
              const SizedBox(width: 16),
              button,
            ]),
          if (o.cabins.isNotEmpty) ...[
            const SizedBox(height: 10),
            Wrap(spacing: 8, children: [
              for (final c in o.cabins)
                Chip(
                  visualDensity: VisualDensity.compact,
                  avatar: const Icon(Icons.bed_outlined, size: 16),
                  label: Text('${c.count} × ${cabinLabel(context, c.type)}', style: const TextStyle(fontSize: 12.5)),
                ),
            ]),
          ],
          for (final r in o.reasons) Padding(padding: const EdgeInsets.only(top: 8), child: NoticeBox(message: r.message, severity: 'ERROR')),
          for (final n in o.notes.where((n) => n.isWarning)) Padding(padding: const EdgeInsets.only(top: 8), child: NoticeBox(message: n.message, severity: 'WARNING')),
          if (o.bookable && o.tariffs.isNotEmpty) ...[
            const SizedBox(height: 6),
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: TextButton.icon(
                onPressed: () => setState(() => _expanded = !_expanded),
                icon: Icon(_expanded ? Icons.expand_less_rounded : Icons.expand_more_rounded),
                label: Text('${context.t('results.tariffs')} · ${context.t('results.details')}'),
              ),
            ),
            if (_expanded) ...[
              RadioGroup<String>(
                groupValue: tariff,
                onChanged: (v) => setState(() => _tariff = v),
                child: Column(children: [
                  for (final t in o.tariffs)
                    RadioListTile<String>(
                      contentPadding: EdgeInsets.zero,
                      value: t.code,
                      title: Row(children: [
                        Expanded(child: Text(t.name, style: const TextStyle(fontWeight: FontWeight.w600))),
                        Text(t.total.format(lang), style: const TextStyle(fontWeight: FontWeight.w700)),
                      ]),
                      subtitle: Text(t.conditions, style: const TextStyle(fontSize: 12.5)),
                    ),
                ]),
              ),
              const Divider(height: 20),
              for (final line in o.breakdown) _BreakdownRow(line: line, lang: lang),
              if (o.tariffs.length > 1)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text(
                    const ['Détail calculé pour le tarif le moins cher.', 'Breakdown shown for the cheapest fare.', 'التفاصيل محسوبة لأرخص تعريفة.'][lang.index],
                    style: const TextStyle(fontSize: 12, color: WaveColors.muted),
                  ),
                ),
            ],
          ],
        ]),
      ),
    );
  }
}

String cabinLabel(BuildContext context, String type) => switch (type) {
      'CABIN_INT_2' => const ['Cabine intérieure 2 lits', 'Inside cabin, 2 berths', 'مقصورة داخلية بسريرين'][context.lang.index],
      'CABIN_INT_4' => const ['Cabine intérieure 4 lits', 'Inside cabin, 4 berths', 'مقصورة داخلية بأربعة أسرّة'][context.lang.index],
      'CABIN_EXT_2' => const ['Cabine extérieure 2 lits', 'Outside cabin, 2 berths', 'مقصورة خارجية بسريرين'][context.lang.index],
      'CABIN_EXT_4' => const ['Cabine extérieure 4 lits', 'Outside cabin, 4 berths', 'مقصورة خارجية بأربعة أسرّة'][context.lang.index],
      'SUITE' => const ['Suite', 'Suite', 'جناح'][context.lang.index],
      'CABIN_PET' => const ['Cabine animaux admis', 'Pet-friendly cabin', 'مقصورة للحيوانات'][context.lang.index],
      'CABIN_PMR' => const ['Cabine adaptée PMR', 'Accessible cabin', 'مقصورة مهيأة'][context.lang.index],
      _ => type,
    };

class _TimeBlock extends StatelessWidget {
  const _TimeBlock({required this.time, required this.port, required this.lang, this.alignEnd = false, this.dayShift = 0});
  final PortTime time;
  final String port;
  final AppLang lang;
  final bool alignEnd;
  final int dayShift;

  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: alignEnd ? CrossAxisAlignment.end : CrossAxisAlignment.start, children: [
        Row(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(Fmt.time(time.local), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: WaveColors.navyInk, height: 1.1)),
          if (dayShift > 0)
            Padding(
              padding: const EdgeInsetsDirectional.only(start: 3),
              child: Text('+$dayShift', textDirection: TextDirection.ltr, style: const TextStyle(fontSize: 11, color: WaveColors.danger, fontWeight: FontWeight.w700)),
            ),
        ]),
        Text(Fmt.dayShort(time.local, lang), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
        Text(port, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600)),
        Text('${context.t('results.localTime')} (${time.utcLabel})', style: const TextStyle(fontSize: 10.5, color: WaveColors.muted)),
      ]);
}

class _PriceSourceChip extends StatelessWidget {
  const _PriceSourceChip({required this.live});
  final bool live;

  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(live ? Icons.bolt_rounded : Icons.info_outline_rounded, size: 14, color: live ? WaveColors.success : WaveColors.muted),
        const SizedBox(width: 3),
        Text(context.t(live ? 'results.live' : 'results.reference'), style: TextStyle(fontSize: 11.5, color: live ? WaveColors.success : WaveColors.muted)),
      ]);
}

class _AvailabilityChip extends StatelessWidget {
  const _AvailabilityChip({required this.level, this.seatsLeft, this.cabinsLeft});
  final String level;
  final int? seatsLeft;
  final int? cabinsLeft;

  @override
  Widget build(BuildContext context) {
    final (String key, Color color) = switch (level) {
      'HIGH' => ('results.availHigh', WaveColors.success),
      'MEDIUM' => ('results.availMedium', WaveColors.warning),
      'LOW' => ('results.availLow', WaveColors.danger),
      'SOLD_OUT' => ('results.soldOut', WaveColors.danger),
      _ => ('results.availHigh', WaveColors.muted),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(20)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(Icons.circle, size: 8, color: color),
        const SizedBox(width: 6),
        Text(context.t(key), style: TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w600)),
      ]),
    );
  }
}

class _BreakdownRow extends StatelessWidget {
  const _BreakdownRow({required this.line, required this.lang});
  final PriceLine line;
  final AppLang lang;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(
            child: Text(
              line.quantity > 1 ? '${line.label}  × ${line.quantity}' : line.label,
              style: TextStyle(fontSize: 13, color: line.total.isNegative ? WaveColors.success : WaveColors.navyInk),
            ),
          ),
          Text(line.total.format(lang), style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: line.total.isNegative ? WaveColors.success : WaveColors.navyInk)),
        ]),
      );
}

class _Notices extends StatelessWidget {
  const _Notices({required this.notices, this.rates, this.lang});
  final List<Violation> notices;
  final RateInfo? rates;
  final AppLang? lang;

  @override
  Widget build(BuildContext context) {
    final shown = notices.where((n) => n.code != 'NO_SAILING_ON_DATE').toList();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text(context.t('results.goodToKnow'), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
      const SizedBox(height: 10),
      for (final n in shown) Padding(padding: const EdgeInsets.only(bottom: 8), child: NoticeBox(message: n.message, severity: n.severity)),
      if (rates != null) ...[
        const SizedBox(height: 6),
        // Exchange rates read left to right in every language.
        Directionality(
          textDirection: TextDirection.ltr,
          child: Text(
            '1 € = ${rates!.dzdPerUnit[Currency.EUR]?.toStringAsFixed(2)} DA · 1 \$ = ${rates!.dzdPerUnit[Currency.USD]?.toStringAsFixed(2)} DA\n${rates!.source}',
            textAlign: context.lang.isRtl ? TextAlign.right : TextAlign.left,
            style: const TextStyle(fontSize: 12, color: WaveColors.muted),
          ),
        ),
      ],
    ]);
  }
}

class _SelectionBar extends ConsumerWidget {
  const _SelectionBar({required this.selection});
  final Selection selection;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = context.lang;
    String price(SailingOffer? o, String? t) => o?.tariffs.where((x) => x.code == t).firstOrNull?.total.format(lang) ?? '—';
    final ready = selection.outbound != null && selection.inbound != null;
    return Material(
      elevation: 12,
      color: Colors.white,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          child: Row(children: [
            Expanded(
              child: Wrap(spacing: 20, runSpacing: 4, children: [
                Text('${context.t('results.outbound')}: ${price(selection.outbound, selection.outboundTariff)}', style: const TextStyle(fontWeight: FontWeight.w600)),
                Text('${context.t('results.inbound')}: ${price(selection.inbound, selection.inboundTariff)}', style: const TextStyle(fontWeight: FontWeight.w600)),
              ]),
            ),
            SizedBox(
              width: 180,
              child: GoldButton(label: context.t('results.continue'), height: 46, fontSize: 15, onPressed: ready ? () => context.go('/booking') : null),
            ),
          ]),
        ),
      ),
    );
  }
}

class _Skeleton extends StatelessWidget {
  const _Skeleton();

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(24),
        children: [
          for (var i = 0; i < 3; i++)
            Container(
              height: 150,
              margin: const EdgeInsets.only(bottom: 14),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16), border: Border.all(color: WaveColors.line)),
              child: const Center(child: SizedBox(width: 28, height: 28, child: CircularProgressIndicator(strokeWidth: 2.5))),
            ),
        ],
      );
}
