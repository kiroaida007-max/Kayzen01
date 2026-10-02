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
import 'pickers.dart';

/// The booking form from the mockup: trip type, ports, dates, passengers, vehicle,
/// accommodation, pets and accessibility. Every input opens a picker that only offers valid choices.
class SearchCard extends ConsumerWidget {
  const SearchCard({super.key, this.compactAfterSearch = false});
  final bool compactAfterSearch;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final query = ref.watch(searchQueryProvider);
    final meta = ref.watch(metaProvider).value;
    final mobile = Breakpoints.isMobile(context);
    final desktop = Breakpoints.isDesktop(context);
    final lang = context.lang;
    final notifier = ref.read(searchQueryProvider.notifier);

    String portName(String? code) => code == null ? context.t('search.choosePort') : (meta?.port(code)?.name.of(lang) ?? code);

    Future<void> chooseFrom() async {
      if (meta == null) return;
      final code = await pickPort(context, meta: meta, destination: false, selected: query.from);
      if (code != null) notifier.setFrom(code, meta);
    }

    Future<void> chooseTo() async {
      if (meta == null) return;
      final code = await pickPort(context, meta: meta, from: query.from, destination: true, selected: query.to);
      if (code != null) notifier.update((q) => q.copyWith(to: code));
    }

    Future<void> chooseDeparture() async {
      final today = DateUtils.dateOnly(DateTime.now());
      final date = await pickDate(context, initial: query.departureDate, firstDate: today, query: query, forReturn: false);
      if (date != null) notifier.setDeparture(date);
    }

    Future<void> chooseReturn() async {
      if (!query.isRoundTrip) notifier.setTripType(TripType.roundTrip);
      final current = ref.read(searchQueryProvider);
      final date = await pickDate(
        context,
        initial: current.returnDate ?? current.departureDate.add(const Duration(days: 14)),
        firstDate: current.departureDate,
        query: current,
        forReturn: true,
      );
      if (date != null) notifier.update((q) => q.copyWith(returnDate: date));
    }

    Future<void> choosePassengers() async {
      final p = await pickPassengers(context, query.passengers, meta?.maxPassengers ?? 9);
      if (p != null) notifier.update((q) => q.copyWith(passengers: p));
    }

    Future<void> chooseVehicle() async {
      final choice = await pickVehicle(context, query.vehicle);
      if (choice == null) return;
      final vehicle = choice.vehicle;
      notifier.update((q) => vehicle == null ? q.copyWith(clearVehicle: true) : q.copyWith(vehicle: vehicle));
    }

    Future<void> chooseAccommodation() async {
      final a = await pickAccommodation(context, query.accommodation);
      if (a != null) notifier.update((q) => q.copyWith(accommodation: a));
    }

    Future<void> choosePets() async {
      final pets = await pickPets(context, query.pets, meta?.maxPets ?? 4);
      if (pets != null) notifier.update((q) => q.copyWith(pets: pets));
    }

    Future<void> chooseAccessibility() async {
      final a = await pickAccessibility(context, query.accessibility);
      if (a != null) notifier.update((q) => q.copyWith(accessibility: a));
    }

    void submit() {
      if (!query.isComplete) {
        final msg = query.from == null || query.to == null ? context.t('search.choosePort') : context.t('search.noReturnDate');
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
        return;
      }
      ref.read(selectionProvider.notifier).clear();
      ref.read(searchResultsProvider.notifier).run();
      context.go('/results');
    }

    final petsLabel = query.pets.isEmpty ? context.t('search.no') : context.t('search.withPet', {'n': query.pets.fold<int>(0, (a, p) => a + p.count)});
    final accessLabel = query.accessibility.any ? context.t('search.pmrSet') : context.t('search.no');
    final returnLabel = query.returnDate == null ? context.t('search.addReturn') : Fmt.dateInput(query.returnDate!);

    final tripToggle = _TripToggle(
      value: query.tripType,
      onChanged: notifier.setTripType,
    );

    final from = _Field(label: context.t('search.from'), icon: Icons.anchor_rounded, value: portName(query.from), onTap: chooseFrom, dropdown: true);
    final to = _Field(label: context.t('search.to'), icon: Icons.anchor_rounded, value: portName(query.to), onTap: chooseTo, dropdown: true);
    final swap = _SwapButton(onTap: () => notifier.swap(meta), vertical: mobile);
    final departure = _Field(label: context.t('search.departure'), icon: Icons.calendar_month_outlined, value: Fmt.dateInput(query.departureDate), onTap: chooseDeparture);
    final ret = _Field(
      label: context.t('search.return'),
      icon: Icons.calendar_month_outlined,
      value: query.isRoundTrip ? returnLabel : context.t('search.addReturn'),
      onTap: chooseReturn,
      muted: !query.isRoundTrip,
    );
    final passengers = _Field(label: context.t('search.passengers'), icon: Icons.groups_rounded, value: query.passengers.summary(lang), onTap: choosePassengers, dropdown: true);
    final vehicle = _Field(label: context.t('search.vehicle'), icon: Icons.directions_car_outlined, value: vehicleLabel(context, query.vehicle), onTap: chooseVehicle, dropdown: true, labelInside: true);
    final accommodation = _Field(label: context.t('search.accommodation'), icon: Icons.bed_outlined, value: accommodationLabel(context, query.accommodation), onTap: chooseAccommodation, dropdown: true, labelInside: true);
    final pets = _Field(label: context.t('search.pets'), icon: Icons.pets_outlined, value: petsLabel, onTap: choosePets, dropdown: true, labelInside: true);
    final access = _Field(label: context.t('search.accessibility'), icon: Icons.accessible_forward_rounded, value: accessLabel, onTap: chooseAccessibility, dropdown: true, labelInside: true);
    final button = GoldButton(label: context.t('search.submit'), onPressed: submit, height: 58, fontSize: desktop ? 16 : 15.5, expand: true, padding: 14);

    final Widget body;
    if (mobile) {
      body = Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        tripToggle,
        const SizedBox(height: 14),
        Stack(clipBehavior: Clip.none, children: [
          Column(children: [from, const SizedBox(height: 10), to]),
          PositionedDirectional(end: 12, top: 44, child: swap),
        ]),
        const SizedBox(height: 10),
        Row(children: [Expanded(child: departure), const SizedBox(width: 10), Expanded(child: ret)]),
        const SizedBox(height: 10),
        passengers,
        const SizedBox(height: 10),
        Row(children: [Expanded(child: vehicle), const SizedBox(width: 10), Expanded(child: accommodation)]),
        const SizedBox(height: 10),
        Row(children: [Expanded(child: pets), const SizedBox(width: 10), Expanded(child: access)]),
        const SizedBox(height: 16),
        button,
      ]);
    } else if (!desktop) {
      body = Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Align(alignment: AlignmentDirectional.centerStart, child: tripToggle),
        const SizedBox(height: 16),
        Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Expanded(child: from),
          Padding(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 10), child: swap),
          Expanded(child: to),
        ]),
        const SizedBox(height: 12),
        Row(children: [Expanded(child: departure), const SizedBox(width: 12), Expanded(child: ret), const SizedBox(width: 12), Expanded(child: passengers)]),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(child: vehicle),
          const SizedBox(width: 12),
          Expanded(child: accommodation),
          const SizedBox(width: 12),
          Expanded(child: pets),
          const SizedBox(width: 12),
          Expanded(child: access),
        ]),
        const SizedBox(height: 16),
        button,
      ]);
    } else {
      body = Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Align(alignment: AlignmentDirectional.centerStart, child: tripToggle),
        const SizedBox(height: 18),
        Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Expanded(flex: 30, child: from),
          Padding(padding: const EdgeInsets.fromLTRB(6, 0, 6, 10), child: swap),
          Expanded(flex: 30, child: to),
          const SizedBox(width: 14),
          Expanded(flex: 26, child: departure),
          const SizedBox(width: 14),
          Expanded(flex: 26, child: ret),
          const SizedBox(width: 14),
          Expanded(flex: 33, child: passengers),
          const Spacer(flex: 6),
        ]),
        const SizedBox(height: 14),
        Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Expanded(flex: 33, child: vehicle),
          const SizedBox(width: 14),
          Expanded(flex: 36, child: accommodation),
          const SizedBox(width: 14),
          Expanded(flex: 33, child: pets),
          const SizedBox(width: 14),
          Expanded(flex: 33, child: access),
          const SizedBox(width: 16),
          Expanded(flex: 50, child: button),
        ]),
      ]);
    }

    return Container(
      padding: EdgeInsets.all(mobile ? 16 : 22),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        boxShadow: [
          BoxShadow(color: WaveColors.navyDeep.withValues(alpha: 0.16), blurRadius: 30, offset: const Offset(0, 14)),
          BoxShadow(color: WaveColors.navyDeep.withValues(alpha: 0.05), blurRadius: 4, offset: const Offset(0, 1)),
        ],
      ),
      child: body,
    );
  }
}

class _TripToggle extends StatelessWidget {
  const _TripToggle({required this.value, required this.onChanged});
  final TripType value;
  final ValueChanged<TripType> onChanged;

  @override
  Widget build(BuildContext context) {
    Widget tab(TripType t, String key) {
      final selected = t == value;
      return Expanded(
        child: InkWell(
          borderRadius: BorderRadius.circular(9),
          onTap: () => onChanged(t),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            height: 38,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: selected ? WaveColors.navy : Colors.transparent,
              borderRadius: BorderRadius.circular(9),
            ),
            child: Text(
              context.t(key),
              style: TextStyle(color: selected ? Colors.white : WaveColors.navyInk, fontWeight: FontWeight.w600, fontSize: 13.5),
            ),
          ),
        ),
      );
    }

    return Container(
      width: Breakpoints.isMobile(context) ? double.infinity : 300,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(color: const Color(0xFFEDF2F9), borderRadius: BorderRadius.circular(11)),
      child: Row(children: [tab(TripType.oneWay, 'search.oneWay'), tab(TripType.roundTrip, 'search.roundTrip')]),
    );
  }
}

class _SwapButton extends StatelessWidget {
  const _SwapButton({required this.onTap, this.vertical = false});
  final VoidCallback onTap;
  final bool vertical;

  @override
  Widget build(BuildContext context) => Semantics(
        button: true,
        label: context.t('search.swap'),
        child: Material(
          color: Colors.white,
          shape: const CircleBorder(side: BorderSide(color: WaveColors.line)),
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.all(7),
              child: Icon(vertical ? Icons.swap_vert_rounded : Icons.swap_horiz_rounded, size: 20, color: WaveColors.navy),
            ),
          ),
        ),
      );
}

/// A form field that looks like the mockup: label above (or inside), icon, bold value, chevron.
class _Field extends StatelessWidget {
  const _Field({required this.label, required this.icon, required this.value, required this.onTap, this.dropdown = false, this.labelInside = false, this.muted = false});

  final String label;
  final IconData icon;
  final String value;
  final VoidCallback onTap;
  final bool dropdown;
  final bool labelInside;
  final bool muted;

  @override
  Widget build(BuildContext context) {
    final valueStyle = TextStyle(
      fontSize: labelInside ? 14 : 15,
      fontWeight: FontWeight.w600,
      color: muted ? WaveColors.muted : WaveColors.navyInk,
    );
    final box = Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap,
        child: Container(
          height: labelInside ? 52 : 46,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(8), border: Border.all(color: WaveColors.line)),
          child: Row(children: [
            Icon(icon, size: 21, color: WaveColors.navy),
            const SizedBox(width: 10),
            Expanded(
              child: labelInside
                  ? Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11.5, color: WaveColors.muted)),
                      Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: valueStyle),
                    ])
                  : Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: valueStyle),
            ),
            if (dropdown) const Icon(Icons.keyboard_arrow_down_rounded, color: WaveColors.navy),
          ]),
        ),
      ),
    );
    final field = Semantics(button: true, label: '$label: $value', child: box);
    if (labelInside) return field;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
      Text(label, style: const TextStyle(fontSize: 12.5, color: WaveColors.navyInk, fontWeight: FontWeight.w500)),
      const SizedBox(height: 6),
      field,
    ]);
  }
}

/// Port name for summaries.
String portLabel(Meta? meta, String? code, AppLang lang) => code == null ? '—' : (meta?.port(code)?.name.of(lang) ?? code);

/// Prefills the form with a route and shows its crossings (used by route cards across the app).
void openRouteSearch(BuildContext context, WidgetRef ref, String from, String to) {
  ref.read(searchQueryProvider.notifier).prefill(from, to);
  ref.read(selectionProvider.notifier).clear();
  ref.read(searchResultsProvider.notifier).run();
  context.go('/results');
}
