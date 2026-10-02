import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/countries.dart';
import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../data/search_request.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/flag.dart';
import '../../widgets/states.dart';

/// Dialog on wide screens, bottom sheet on phones.
Future<T?> showWavePicker<T>(BuildContext context, {required String title, required Widget Function(BuildContext) builder, double width = 460}) {
  if (Breakpoints.isMobile(context)) {
    return showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.8,
        maxChildSize: 0.95,
        builder: (ctx, controller) => Column(children: [
          const SizedBox(height: 8),
          Container(width: 40, height: 4, decoration: BoxDecoration(color: WaveColors.line, borderRadius: BorderRadius.circular(2))),
          _PickerTitle(title),
          Expanded(child: SingleChildScrollView(controller: controller, padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), child: builder(ctx))),
        ]),
      ),
    );
  }
  return showDialog<T>(
    context: context,
    builder: (ctx) => Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      insetPadding: const EdgeInsets.all(24),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: width, maxHeight: 640),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          _PickerTitle(title),
          Flexible(child: SingleChildScrollView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 20), child: builder(ctx))),
        ]),
      ),
    ),
  );
}

class _PickerTitle extends StatelessWidget {
  const _PickerTitle(this.title);
  final String title;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 14, 8, 8),
        child: Row(children: [
          Expanded(child: Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: WaveColors.navyInk))),
          IconButton(onPressed: () => Navigator.of(context).pop(), icon: const Icon(Icons.close_rounded), tooltip: context.t('common.close')),
        ]),
      );
}



// ------------------------------------------------------------------ ports

Future<String?> pickPort(BuildContext context, {required Meta meta, String? from, required bool destination, String? selected}) {
  final title = context.t(destination ? 'search.to' : 'search.from');
  return showWavePicker<String>(context, title: title, builder: (ctx) => _PortList(meta: meta, from: from, destination: destination, selected: selected));
}

class _PortList extends StatefulWidget {
  const _PortList({required this.meta, required this.from, required this.destination, required this.selected});
  final Meta meta;
  final String? from;
  final bool destination;
  final String? selected;

  @override
  State<_PortList> createState() => _PortListState();
}

class _PortListState extends State<_PortList> {
  String _filter = '';

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    final reachable = widget.destination && widget.from != null ? widget.meta.port(widget.from!)?.destinations ?? const <String>[] : null;
    final ports = widget.meta.ports.where((p) {
      if (widget.destination && p.code == widget.from) return false;
      if (_filter.isEmpty) return true;
      final f = _filter.toLowerCase();
      return p.name.of(lang).toLowerCase().contains(f) || p.name.fr.toLowerCase().contains(f) || p.code.toLowerCase().contains(f);
    }).toList()
      ..sort((a, b) {
        final ar = reachable == null || reachable.contains(a.code) ? 0 : 1;
        final br = reachable == null || reachable.contains(b.code) ? 0 : 1;
        if (ar != br) return ar - br;
        const order = ['DZ', 'FR', 'ES', 'IT'];
        final c = order.indexOf(a.country) - order.indexOf(b.country);
        return c != 0 ? c : a.name.of(lang).compareTo(b.name.of(lang));
      });

    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      TextField(
        autofocus: !Breakpoints.isMobile(context),
        decoration: InputDecoration(prefixIcon: const Icon(Icons.search_rounded), hintText: context.t('search.searchPort')),
        onChanged: (v) => setState(() => _filter = v.trim()),
      ),
      const SizedBox(height: 8),
      for (final port in ports) _portTile(context, port, reachable == null || reachable.contains(port.code), lang),
    ]);
  }

  Widget _portTile(BuildContext context, Port port, bool enabled, AppLang lang) {
    final operators = widget.destination && widget.from != null
        ? widget.meta.routes.where((r) => r.from == widget.from && r.to == port.code).map((r) => r.operator).toSet()
        : widget.meta.routes.where((r) => r.from == port.code).map((r) => r.operator).toSet();
    final selected = port.code == widget.selected;
    return Opacity(
      opacity: enabled ? 1 : 0.45,
      child: ListTile(
        enabled: enabled,
        selected: selected,
        selectedTileColor: WaveColors.sky,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        leading: FlagIcon(port.country, width: 28),
        title: Text('${port.name.of(lang)}  ·  ${port.shortCode}', style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: enabled
            ? Wrap(spacing: 8, runSpacing: 2, crossAxisAlignment: WrapCrossAlignment.center, children: [
                Text(countryName(port.country, lang), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
                for (final op in operators.take(4)) OperatorLogo(op, size: 13),
              ])
            : Text(const ['Pas de liaison directe', 'No direct crossing', 'لا توجد رحلة مباشرة'][lang.index],
                style: const TextStyle(fontSize: 12.5)),
        trailing: selected ? const Icon(Icons.check_circle, color: WaveColors.navy) : null,
        onTap: enabled ? () => Navigator.of(context).pop(port.code) : null,
      ),
    );
  }
}

// ------------------------------------------------------------------ passengers

Future<Passengers?> pickPassengers(BuildContext context, Passengers initial, int maxPassengers) {
  return showWavePicker<Passengers>(context, title: context.t('search.passengers'), builder: (ctx) => _PassengersEditor(initial: initial, max: maxPassengers));
}

class _PassengersEditor extends StatefulWidget {
  const _PassengersEditor({required this.initial, required this.max});
  final Passengers initial;
  final int max;

  @override
  State<_PassengersEditor> createState() => _PassengersEditorState();
}

class _PassengersEditorState extends State<_PassengersEditor> {
  late Passengers _p = widget.initial;

  @override
  Widget build(BuildContext context) {
    final full = _p.total >= widget.max;
    final infants = _p.childrenAges.where((a) => a < 2).length;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      _Stepper(
        label: context.t('search.adults'),
        hint: context.t('search.adultsHint'),
        value: _p.adults,
        min: _p.seniors > 0 ? 0 : 1,
        canIncrement: !full,
        onChanged: (v) => setState(() => _p = _p.copyWith(adults: v)),
      ),
      _Stepper(
        label: context.t('search.seniors'),
        hint: context.t('search.seniorsHint'),
        value: _p.seniors,
        min: _p.adults > 0 ? 0 : 1,
        canIncrement: !full,
        onChanged: (v) => setState(() => _p = _p.copyWith(seniors: v)),
      ),
      _Stepper(
        label: context.t('search.children'),
        hint: context.t('search.childrenHint'),
        value: _p.childrenAges.length,
        min: 0,
        canIncrement: !full,
        onChanged: (v) => setState(() {
          final ages = [..._p.childrenAges];
          if (v > ages.length) {
            ages.add(8);
          } else {
            ages.removeLast();
          }
          _p = _p.copyWith(childrenAges: ages);
        }),
      ),
      for (var i = 0; i < _p.childrenAges.length; i++)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: DropdownButtonFormField<int>(
            initialValue: _p.childrenAges[i],
            decoration: InputDecoration(labelText: context.t('search.childAge', {'n': i + 1}), isDense: true),
            items: [
              for (var age = 0; age <= 17; age++)
                DropdownMenuItem(value: age, child: Text(age == 0 ? context.t('search.infant') : context.t('search.years', {'n': age}))),
            ],
            onChanged: (age) => setState(() {
              final ages = [..._p.childrenAges]..[i] = age ?? 8;
              _p = _p.copyWith(childrenAges: ages);
            }),
          ),
        ),
      if (infants > _p.grownUps) ...[
        const SizedBox(height: 12),
        NoticeBox(severity: 'ERROR', message: const [
          'Chaque bébé de moins de 2 ans doit être accompagné de son propre adulte.',
          'Each infant under 2 must be accompanied by their own adult.',
          'يجب أن يرافق كل رضيع دون السنتين شخصٌ بالغ خاص به.',
        ][context.lang.index]),
      ],
      const SizedBox(height: 18),
      GoldButton(
        label: context.t('search.done'),
        icon: Icons.check_rounded,
        expand: true,
        onPressed: infants > _p.grownUps || _p.grownUps == 0 ? null : () => Navigator.of(context).pop(_p),
      ),
    ]);
  }
}

class _Stepper extends StatelessWidget {
  const _Stepper({required this.label, required this.hint, required this.value, required this.min, required this.canIncrement, required this.onChanged});
  final String label;
  final String hint;
  final int value;
  final int min;
  final bool canIncrement;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
              Text(hint, style: const TextStyle(color: WaveColors.muted, fontSize: 12.5)),
            ]),
          ),
          _RoundIcon(icon: Icons.remove, onTap: value > min ? () => onChanged(value - 1) : null, label: '-'),
          SizedBox(width: 36, child: Text('$value', textAlign: TextAlign.center, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600))),
          _RoundIcon(icon: Icons.add, onTap: canIncrement ? () => onChanged(value + 1) : null, label: '+'),
        ]),
      );
}

class _RoundIcon extends StatelessWidget {
  const _RoundIcon({required this.icon, required this.onTap, required this.label});
  final IconData icon;
  final VoidCallback? onTap;
  final String label;

  @override
  Widget build(BuildContext context) => Semantics(
        button: true,
        label: label,
        child: InkResponse(
          onTap: onTap,
          radius: 22,
          child: Container(
            width: 34,
            height: 34,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: onTap == null ? WaveColors.line : WaveColors.navy, width: 1.4),
            ),
            child: Icon(icon, size: 18, color: onTap == null ? WaveColors.line : WaveColors.navy),
          ),
        ),
      );
}

// ------------------------------------------------------------------ vehicle

String vehicleLabel(BuildContext context, Vehicle? v) {
  if (v == null) return context.t('search.none');
  final base = context.t(switch (v.type) {
    VehicleType.car => 'search.car',
    VehicleType.suv => 'search.suv',
    VehicleType.motorcycle => 'search.motorcycle',
    VehicleType.camper => 'search.camper',
    VehicleType.van => 'search.van',
  });
  return v.withTrailer ? '$base + ${context.t('search.trailerShort')}' : base;
}

/// Distinguishes "no vehicle" (a choice) from a dismissed picker (null result).
class VehicleChoice {
  const VehicleChoice(this.vehicle);
  final Vehicle? vehicle;
}

Future<VehicleChoice?> pickVehicle(BuildContext context, Vehicle? initial) {
  return showWavePicker<VehicleChoice>(
    context,
    title: context.t('search.vehicle'),
    builder: (ctx) => _VehicleEditor(initial: initial),
  );
}

class _VehicleEditor extends StatefulWidget {
  const _VehicleEditor({required this.initial});
  final Vehicle? initial;
  @override
  State<_VehicleEditor> createState() => _VehicleEditorState();
}

class _VehicleEditorState extends State<_VehicleEditor> {
  late VehicleType? _type = widget.initial?.type;
  late bool _trailer = widget.initial?.withTrailer ?? false;
  late bool _roofBox = widget.initial?.roofBox ?? false;
  late final _height = TextEditingController(text: widget.initial?.heightM?.toString() ?? '');
  late final _length = TextEditingController(text: widget.initial?.lengthM?.toString() ?? '');
  late final _year = TextEditingController(text: widget.initial?.registrationYear?.toString() ?? '');

  @override
  void dispose() {
    _height.dispose();
    _length.dispose();
    _year.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final options = <(VehicleType?, String, IconData)>[
      (null, 'search.none', Icons.directions_walk_rounded),
      (VehicleType.car, 'search.car', Icons.directions_car_filled_outlined),
      (VehicleType.suv, 'search.suv', Icons.airport_shuttle_outlined),
      (VehicleType.motorcycle, 'search.motorcycle', Icons.two_wheeler_rounded),
      (VehicleType.camper, 'search.camper', Icons.rv_hookup_outlined),
      (VehicleType.van, 'search.van', Icons.local_shipping_outlined),
    ];
    final carLike = _type == VehicleType.car || _type == VehicleType.suv;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Wrap(spacing: 8, runSpacing: 8, children: [
        for (final (type, key, icon) in options)
          ChoiceChip(
            avatar: Icon(icon, size: 18, color: _type == type ? Colors.white : WaveColors.navy),
            label: Text(context.t(key)),
            selected: _type == type,
            labelStyle: TextStyle(color: _type == type ? Colors.white : WaveColors.navyInk),
            selectedColor: WaveColors.navy,
            showCheckmark: false,
            onSelected: (_) => setState(() => _type = type),
          ),
      ]),
      if (_type != null) ...[
        const SizedBox(height: 14),
        if (carLike) ...[
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _roofBox,
            title: Text(context.t('search.roofBox')),
            onChanged: (v) => setState(() => _roofBox = v),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _trailer,
            title: Text(context.t('search.trailer')),
            onChanged: (v) => setState(() => _trailer = v),
          ),
        ],
        if (_type != VehicleType.motorcycle)
          Row(children: [
            Expanded(child: _numberField(_height, context.t('search.height'))),
            const SizedBox(width: 10),
            Expanded(child: _numberField(_length, context.t('search.length'))),
          ]),
        const SizedBox(height: 10),
        _numberField(_year, context.t('search.regYear'), integer: true),
        const SizedBox(height: 10),
        NoticeBox(message: context.t('search.vehicleHelp')),
        if (_type == VehicleType.van) ...[
          const SizedBox(height: 8),
          NoticeBox(
            severity: 'WARNING',
            message: const [
              'Du 15 juin au 15 septembre, les fourgons sont interdits sur les navires de passagers vers l\'Algérie.',
              'From 15 June to 15 September, vans are banned on passenger ships to Algeria.',
              'من 15 جوان إلى 15 سبتمبر، يُمنع نقل الشاحنات الصغيرة على سفن المسافرين نحو الجزائر.',
            ][context.lang.index],
          ),
        ],
      ],
      const SizedBox(height: 18),
      GoldButton(
        label: context.t('search.done'),
        icon: Icons.check_rounded,
        expand: true,
        onPressed: () {
          final type = _type;
          Navigator.of(context).pop(VehicleChoice(type == null
              ? null
              : Vehicle(
                  type: type,
                  heightM: double.tryParse(_height.text.replaceAll(',', '.')),
                  lengthM: double.tryParse(_length.text.replaceAll(',', '.')),
                  withTrailer: _trailer && (type == VehicleType.car || type == VehicleType.suv),
                  roofBox: _roofBox,
                  registrationYear: int.tryParse(_year.text),
                )));
        },
      ),
    ]);
  }

  Widget _numberField(TextEditingController c, String label, {bool integer = false}) => TextField(
        controller: c,
        keyboardType: TextInputType.numberWithOptions(decimal: !integer),
        decoration: InputDecoration(labelText: label, isDense: true),
      );
}

// ------------------------------------------------------------------ accommodation

String accommodationLabel(BuildContext context, AccommodationPref pref) => context.t(switch (pref) {
      AccommodationPref.seat => 'search.seat',
      AccommodationPref.cabinAny => 'search.cabinAny',
      AccommodationPref.cabinInterior => 'search.cabinInterior',
      AccommodationPref.cabinExterior => 'search.cabinExterior',
      AccommodationPref.suite => 'search.suite',
    });

Future<AccommodationPref?> pickAccommodation(BuildContext context, AccommodationPref current) {
  final items = <(AccommodationPref, IconData, List<String>)>[
    (AccommodationPref.seat, Icons.event_seat_outlined, const ['Fauteuil inclinable en salon', 'Reclining seat in a lounge', 'مقعد قابل للإمالة في الصالة']),
    (AccommodationPref.cabinAny, Icons.bed_outlined, const ['La cabine la moins chère pour votre groupe', 'Cheapest cabin for your group', 'أرخص مقصورة لمجموعتك']),
    (AccommodationPref.cabinInterior, Icons.king_bed_outlined, const ['Sans hublot, 2 ou 4 lits', 'No window, 2 or 4 berths', 'بدون نافذة، سريران أو أربعة']),
    (AccommodationPref.cabinExterior, Icons.waves_outlined, const ['Vue sur mer, 2 ou 4 lits', 'Sea view, 2 or 4 berths', 'إطلالة على البحر، سريران أو أربعة']),
    (AccommodationPref.suite, Icons.star_outline_rounded, const ['Le confort maximal', 'Maximum comfort', 'أقصى درجات الراحة']),
  ];
  return showWavePicker<AccommodationPref>(
    context,
    title: context.t('search.accommodation'),
    builder: (ctx) => Column(children: [
      for (final (pref, icon, desc) in items)
        ListTile(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          selected: pref == current,
          selectedTileColor: WaveColors.sky,
          leading: Icon(icon, color: WaveColors.navy),
          title: Text(accommodationLabel(ctx, pref), style: const TextStyle(fontWeight: FontWeight.w600)),
          subtitle: Text(desc[ctx.lang.index]),
          trailing: pref == current ? const Icon(Icons.check_circle, color: WaveColors.navy) : null,
          onTap: () => Navigator.of(ctx).pop(pref),
        ),
    ]),
  );
}

// ------------------------------------------------------------------ pets

Future<List<Pet>?> pickPets(BuildContext context, List<Pet> initial, int maxPets) {
  return showWavePicker<List<Pet>>(context, title: context.t('search.pets'), builder: (ctx) => _PetsEditor(initial: initial, max: maxPets));
}

class _PetsEditor extends StatefulWidget {
  const _PetsEditor({required this.initial, required this.max});
  final List<Pet> initial;
  final int max;
  @override
  State<_PetsEditor> createState() => _PetsEditorState();
}

class _PetsEditorState extends State<_PetsEditor> {
  late bool _enabled = widget.initial.isNotEmpty;
  late PetType _type = widget.initial.firstOrNull?.type ?? PetType.dog;
  late int _count = widget.initial.firstOrNull?.count ?? 1;
  late PetPlacement _placement = widget.initial.firstOrNull?.placement ?? PetPlacement.any;

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      SegmentedButton<bool>(
        segments: [
          ButtonSegment(value: false, label: Text(context.t('search.no'))),
          ButtonSegment(value: true, label: Text(context.t('search.yes')), icon: const Icon(Icons.pets)),
        ],
        selected: {_enabled},
        onSelectionChanged: (s) => setState(() => _enabled = s.first),
      ),
      if (_enabled) ...[
        const SizedBox(height: 14),
        SegmentedButton<PetType>(
          segments: [
            ButtonSegment(value: PetType.dog, label: Text(context.t('search.dog'))),
            ButtonSegment(value: PetType.cat, label: Text(context.t('search.cat'))),
            ButtonSegment(value: PetType.other, label: Text(context.t('search.otherPet'))),
          ],
          selected: {_type},
          onSelectionChanged: (s) => setState(() => _type = s.first),
        ),
        _Stepper(
          label: context.t('search.pets'),
          hint: '1 – ${widget.max}',
          value: _count,
          min: 1,
          canIncrement: _count < widget.max,
          onChanged: (v) => setState(() => _count = v),
        ),
        RadioGroup<PetPlacement>(
          groupValue: _placement,
          onChanged: (v) => setState(() => _placement = v ?? PetPlacement.any),
          child: Column(children: [
            for (final (p, key) in const [(PetPlacement.any, 'search.anyPlacement'), (PetPlacement.kennel, 'search.kennel'), (PetPlacement.cabin, 'search.petCabin')])
              RadioListTile<PetPlacement>(contentPadding: EdgeInsets.zero, value: p, title: Text(context.t(key))),
          ]),
        ),
        NoticeBox(
          message: const [
            'Puce, vaccin antirabique et certificat sanitaire obligatoires. Retour vers l\'UE : titrage antirabique 3 mois avant.',
            'Microchip, rabies vaccine and health certificate required. Back to the EU: rabies titration 3 months before.',
            'الشريحة والتلقيح ضد داء الكلب والشهادة الصحية إلزامية. للعودة إلى أوروبا: معايرة قبل 3 أشهر.',
          ][context.lang.index],
        ),
      ],
      const SizedBox(height: 18),
      GoldButton(
        label: context.t('search.done'),
        icon: Icons.check_rounded,
        expand: true,
        onPressed: () => Navigator.of(context).pop(_enabled ? [Pet(type: _type, count: _count, placement: _placement)] : <Pet>[]),
      ),
    ]);
  }
}

// ------------------------------------------------------------------ accessibility

Future<Accessibility?> pickAccessibility(BuildContext context, Accessibility initial) {
  return showWavePicker<Accessibility>(context, title: context.t('search.accessibility'), builder: (ctx) => _AccessibilityEditor(initial: initial));
}

class _AccessibilityEditor extends StatefulWidget {
  const _AccessibilityEditor({required this.initial});
  final Accessibility initial;
  @override
  State<_AccessibilityEditor> createState() => _AccessibilityEditorState();
}

class _AccessibilityEditorState extends State<_AccessibilityEditor> {
  late Accessibility _a = widget.initial;

  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        CheckboxListTile(
          value: _a.wheelchair,
          secondary: const Icon(Icons.accessible_rounded),
          title: Text(context.t('search.wheelchair')),
          onChanged: (v) => setState(() => _a = Accessibility(wheelchair: v ?? false, reducedMobility: _a.reducedMobility, assistance: _a.assistance)),
        ),
        CheckboxListTile(
          value: _a.reducedMobility,
          secondary: const Icon(Icons.elderly_rounded),
          title: Text(context.t('search.reducedMobility')),
          onChanged: (v) => setState(() => _a = Accessibility(wheelchair: _a.wheelchair, reducedMobility: v ?? false, assistance: _a.assistance)),
        ),
        CheckboxListTile(
          value: _a.assistance,
          secondary: const Icon(Icons.support_agent_rounded),
          title: Text(context.t('search.assistance')),
          onChanged: (v) => setState(() => _a = Accessibility(wheelchair: _a.wheelchair, reducedMobility: _a.reducedMobility, assistance: v ?? false)),
        ),
        const SizedBox(height: 8),
        NoticeBox(
          message: const [
            'Signalez vos besoins au moins 48 h avant le départ. Seules les traversées avec cabine adaptée vous seront proposées.',
            'Tell us at least 48 h before departure. Only sailings with an adapted cabin will be offered.',
            'أبلغونا قبل 48 ساعة على الأقل من المغادرة. لن تُعرض إلا الرحلات التي تتوفر على مقصورة مهيأة.',
          ][context.lang.index],
        ),
        const SizedBox(height: 18),
        GoldButton(label: context.t('search.done'), icon: Icons.check_rounded, expand: true, onPressed: () => Navigator.of(context).pop(_a)),
      ]);
}

// ------------------------------------------------------------------ dates with price calendar

Future<DateTime?> pickDate(BuildContext context, {required DateTime initial, required DateTime firstDate, required SearchQuery query, required bool forReturn}) {
  return showWavePicker<DateTime>(
    context,
    title: context.t(forReturn ? 'search.return' : 'search.departure'),
    width: 520,
    builder: (ctx) => _PriceCalendar(initial: initial, firstDate: firstDate, query: query, forReturn: forReturn),
  );
}

class _PriceCalendar extends ConsumerStatefulWidget {
  const _PriceCalendar({required this.initial, required this.firstDate, required this.query, required this.forReturn});
  final DateTime initial;
  final DateTime firstDate;
  final SearchQuery query;
  final bool forReturn;

  @override
  ConsumerState<_PriceCalendar> createState() => _PriceCalendarState();
}

class _PriceCalendarState extends ConsumerState<_PriceCalendar> {
  late DateTime _month = DateTime(widget.initial.year, widget.initial.month);
  final Map<String, Future<List<DayPrice>>> _cache = {};

  Future<List<DayPrice>> _prices(Currency currency) {
    final q = widget.forReturn ? widget.query.copyWith(from: widget.query.to, to: widget.query.from) : widget.query;
    if (q.from == null || q.to == null) return Future.value(const []);
    final key = '${_month.year}-${_month.month}-${currency.name}';
    return _cache.putIfAbsent(key, () => ref.read(apiProvider).calendar(q, _month, currency));
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    final currency = ref.watch(settingsProvider).currency;
    final first = DateTime(widget.firstDate.year, widget.firstDate.month);
    final canGoBack = _month.isAfter(first);
    final daysInMonth = DateTime(_month.year, _month.month + 1, 0).day;
    final leading = (DateTime(_month.year, _month.month, 1).weekday + 6) % 7;
    final weekdays = const [
      ['L', 'M', 'M', 'J', 'V', 'S', 'D'],
      ['M', 'T', 'W', 'T', 'F', 'S', 'S'],
      ['ن', 'ث', 'ر', 'خ', 'ج', 'س', 'ح'],
    ][lang.index];

    return FutureBuilder<List<DayPrice>>(
      future: _prices(currency),
      builder: (context, snapshot) {
        final prices = {for (final d in snapshot.data ?? const <DayPrice>[]) Fmt.iso(d.date): d};
        final loaded = snapshot.hasData && prices.isNotEmpty;
        final cheapest = prices.values.where((d) => d.minPrice != null).map((d) => d.minPrice!.minor).fold<int?>(null, (a, b) => a == null || b < a ? b : a);
        return Column(mainAxisSize: MainAxisSize.min, children: [
          Row(children: [
            IconButton(
              onPressed: canGoBack ? () => setState(() => _month = DateTime(_month.year, _month.month - 1)) : null,
              icon: const Icon(Icons.chevron_left_rounded),
            ),
            Expanded(
              child: Text(
                Fmt.dayLong(_month, lang).split(' ').skip(2).join(' '),
                textAlign: TextAlign.center,
                style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
              ),
            ),
            IconButton(onPressed: () => setState(() => _month = DateTime(_month.year, _month.month + 1)), icon: const Icon(Icons.chevron_right_rounded)),
          ]),
          if (snapshot.connectionState == ConnectionState.waiting) const LinearProgressIndicator(minHeight: 2),
          const SizedBox(height: 6),
          GridView.count(
            crossAxisCount: 7,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            childAspectRatio: 0.82,
            children: [
              for (final w in weekdays) Center(child: Text(w, style: const TextStyle(color: WaveColors.muted, fontWeight: FontWeight.w600))),
              for (var i = 0; i < leading; i++) const SizedBox.shrink(),
              for (var day = 1; day <= daysInMonth; day++)
                _dayCell(context, DateTime(_month.year, _month.month, day), prices, loaded, cheapest, currency, lang),
            ],
          ),
          const SizedBox(height: 8),
          if (loaded)
            Row(children: [
              Container(width: 12, height: 12, decoration: BoxDecoration(color: WaveColors.success.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(3))),
              const SizedBox(width: 6),
              Text(context.t('search.cheapestDay'), style: const TextStyle(fontSize: 12, color: WaveColors.muted)),
            ]),
        ]);
      },
    );
  }

  Widget _dayCell(BuildContext context, DateTime date, Map<String, DayPrice> prices, bool loaded, int? cheapest, Currency currency, AppLang lang) {
    final beforeFirst = date.isBefore(widget.firstDate);
    final info = prices[Fmt.iso(date)];
    final noSailing = loaded && (info == null || info.sailings == 0);
    final enabled = !beforeFirst && !noSailing;
    final selected = DateUtils.isSameDay(date, widget.initial);
    final isCheapest = info?.minPrice != null && info!.minPrice!.minor == cheapest;
    return Padding(
      padding: const EdgeInsets.all(2),
      child: Material(
        color: selected
            ? WaveColors.navy
            : isCheapest
                ? WaveColors.success.withValues(alpha: 0.12)
                : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: enabled ? () => Navigator.of(context).pop(date) : null,
          child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            Text(
              '${date.day}',
              style: TextStyle(
                fontWeight: FontWeight.w600,
                color: selected ? Colors.white : (enabled ? WaveColors.navyInk : WaveColors.line),
              ),
            ),
            if (info?.minPrice != null)
              Text(
                _compact(info!.minPrice!, currency),
                style: TextStyle(fontSize: 10, color: selected ? WaveColors.goldLight : (isCheapest ? WaveColors.success : WaveColors.muted)),
              ),
          ]),
        ),
      ),
    );
  }

  String _compact(Money m, Currency c) {
    final v = m.minor / 100;
    return switch (c) {
      Currency.DZD => '${(v / 1000).toStringAsFixed(v >= 100000 ? 0 : 1)}k',
      Currency.EUR => '${v.round()}€',
      Currency.USD => '\$${v.round()}',
    };
  }
}
