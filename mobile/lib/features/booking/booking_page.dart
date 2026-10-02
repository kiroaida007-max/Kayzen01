import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/countries.dart';
import '../../core/formatters.dart';
import '../../core/secure_screen.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/api_client.dart';
import '../../data/models.dart';
import '../../data/search_request.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../../widgets/states.dart';
import '../search/pickers.dart' show vehicleLabel;
import '../search/search_card.dart' show portLabel;
import 'trip_summary.dart';

/// Mirrors the server rules (BookingService.validateTravellers) so most mistakes are caught before the request.
final _nameRe = RegExp(r"^(?=.*\p{L})[\p{L}' -]{1,50}$", unicode: true);
final _docRe = RegExp(r'^[A-Z0-9]{5,20}$');
final _emailRe = RegExp(r'^[^@\s]+@[^@\s]+\.[A-Za-z]{2,}$');
final _plateRe = RegExp(r'^[A-Z0-9][A-Z0-9 -]{1,14}$');
final _dateFmt = DateFormat('dd/MM/yyyy');

bool validPhone(String raw) {
  final compact = raw.replaceAll(RegExp(r'[\s.()-]'), '');
  return RegExp(r'^0[567]\d{8}$').hasMatch(compact) || RegExp(r'^\+\d{8,15}$').hasMatch(compact) || RegExp(r'^00\d{8,15}$').hasMatch(compact);
}

DateTime? parseDate(String text) {
  try {
    return _dateFmt.parseStrict(text.trim());
  } catch (_) {
    return null;
  }
}

int ageOn(DateTime dob, DateTime day) {
  var age = day.year - dob.year;
  if (day.month < dob.month || (day.month == dob.month && day.day < dob.day)) age--;
  return age;
}

String newIdempotencyKey() {
  final random = Random.secure();
  return List.generate(16, (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
}

enum _Kind { adult, senior, child }

class _TravellerForm {
  _TravellerForm(this.kind, this.searchedAge);
  final _Kind kind;
  final int? searchedAge;
  final first = TextEditingController();
  final last = TextEditingController();
  final dob = TextEditingController();
  final passport = TextEditingController();
  final expiry = TextEditingController();
  String? sex;
  String nationality = 'DZ';
  String? issuing;

  void dispose() {
    for (final c in [first, last, dob, passport, expiry]) {
      c.dispose();
    }
  }

  Map<String, dynamic> toJson() => {
        'firstName': first.text.trim(),
        'lastName': last.text.trim(),
        'sex': sex,
        'dateOfBirth': Fmt.iso(parseDate(dob.text)!),
        'nationality': nationality,
        'document': {
          'type': 'PASSPORT',
          'number': passport.text.toUpperCase().replaceAll(' ', ''),
          'expiry': Fmt.iso(parseDate(expiry.text)!),
          'issuingCountry': issuing ?? nationality,
        },
      };
}

class BookingPage extends ConsumerStatefulWidget {
  const BookingPage({super.key});

  @override
  ConsumerState<BookingPage> createState() => _BookingPageState();
}

class _BookingPageState extends ConsumerState<BookingPage> {
  final _formKey = GlobalKey<FormState>();
  late final List<_TravellerForm> _travellers;
  final _plate = TextEditingController();
  final _make = TextEditingController();
  final _model = TextEditingController();
  String _regCountry = 'DZ';
  final _email = TextEditingController();
  final _phone = TextEditingController();
  bool _accept = false;
  bool _acceptError = false;

  Quote? _quote;
  Object? _quoteError;
  bool _submitting = false;
  final String _idempotencyKey = newIdempotencyKey();
  Map<String, String> _serverErrors = {};
  List<Violation> _formErrors = const [];

  @override
  void initState() {
    super.initState();
    final p = ref.read(searchQueryProvider).passengers;
    _travellers = [
      for (var i = 0; i < p.adults; i++) _TravellerForm(_Kind.adult, null),
      for (var i = 0; i < p.seniors; i++) _TravellerForm(_Kind.senior, null),
      for (final age in p.childrenAges) _TravellerForm(_Kind.child, age),
    ];
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadQuote());
    SecureScreen.set(true);
  }

  @override
  void dispose() {
    SecureScreen.set(false);
    for (final t in _travellers) {
      t.dispose();
    }
    for (final c in [_plate, _make, _model, _email, _phone]) {
      c.dispose();
    }
    super.dispose();
  }

  Map<String, dynamic>? _selectionJson() {
    final s = ref.read(selectionProvider);
    final q = ref.read(searchQueryProvider);
    final settings = ref.read(settingsProvider);
    if (s.outbound == null) return null;
    final accommodation = apiName(q.accommodation);
    return {
      'outbound': {'sailingId': s.outbound!.sailingId, 'accommodation': accommodation, 'tariff': s.outboundTariff},
      if (q.isRoundTrip && s.inbound != null)
        'inbound': {'sailingId': s.inbound!.sailingId, 'accommodation': accommodation, 'tariff': s.inboundTariff},
      'passengers': q.passengers.toJson(),
      if (q.vehicle != null) 'vehicle': q.vehicle!.toJson(),
      'pets': q.pets.map((p) => p.toJson()).toList(),
      'accessibility': q.accessibility.toJson(),
      'currency': settings.currency.name,
      'lang': settings.lang.name,
    };
  }

  Future<void> _loadQuote() async {
    final selection = _selectionJson();
    if (selection == null) return;
    setState(() => _quoteError = null);
    try {
      final quote = await ref.read(apiProvider).quote(selection);
      if (mounted) setState(() => _quote = quote);
    } catch (e) {
      if (mounted) setState(() => _quoteError = e);
    }
  }

  DateTime get _departureDay => _quote?.legs.first.departure.local ?? ref.read(selectionProvider).outbound!.departure.local;

  DateTime get _tripEnd {
    final last = _quote?.legs.last.arrival.local ?? ref.read(selectionProvider).inbound?.arrival.local ?? _departureDay;
    return DateTime(last.year, last.month, last.day).add(const Duration(days: 2));
  }

  String? _serverError(String field) => _serverErrors[field];

  void _clearServerError(String field) {
    if (_serverErrors.containsKey(field)) setState(() => _serverErrors = {..._serverErrors}..remove(field));
  }

  Future<void> _submit({Money? expected}) async {
    final valid = _formKey.currentState!.validate();
    setState(() => _acceptError = !_accept);
    if (!valid || !_accept) {
      setState(() => _formErrors = [Violation('FORM', 'ERROR', context.t('booking.fixErrors'), null)]);
      return;
    }
    final selection = _selectionJson();
    final quote = _quote;
    if (selection == null || quote == null) return;
    final q = ref.read(searchQueryProvider);
    final body = {
      'selection': selection,
      'travellers': _travellers.map((t) => t.toJson()).toList(),
      if (q.vehicle != null)
        'vehicleDetails': {'plate': _plate.text.trim().toUpperCase(), 'make': _make.text.trim(), 'model': _model.text.trim(), 'registrationCountry': _regCountry},
      'contact': {'email': _email.text.trim(), 'phone': _phone.text.trim()},
      'acceptTerms': _accept,
      'expectedTotal': (expected ?? quote.total).toJson(),
    };
    setState(() {
      _submitting = true;
      _formErrors = const [];
      _serverErrors = {};
    });
    try {
      final booking = await ref.read(apiProvider).createBooking(body, _idempotencyKey);
      final meta = ref.read(metaProvider).value;
      final lang = ref.read(settingsProvider).lang;
      final first = booking.legs.first;
      await ref.read(tripsProvider.notifier).save(SavedTrip(
            booking.reference,
            _travellers.first.last.text.trim(),
            '${portLabel(meta, first.from, lang)} → ${portLabel(meta, first.to, lang)}',
            Fmt.iso(first.departure.local),
          ));
      ref.read(selectionProvider.notifier).clear();
      if (mounted) context.go('/booking/${booking.reference}');
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _submitting = false);
      if (e.code == 'PRICE_CHANGED' && e.details != null) {
        final fresh = Quote.fromJson(e.details!['quote'] as Map<String, dynamic>);
        setState(() => _quote = fresh);
        final accept = await _confirmNewPrice(fresh.total);
        if (accept == true) await _submit(expected: fresh.total);
        return;
      }
      setState(() {
        _formErrors = e.violations.isEmpty ? [Violation(e.code, 'ERROR', e.isOffline ? context.t('common.offline') : e.message, null)] : e.violations;
        _serverErrors = {for (final v in e.violations.where((v) => v.field != null)) v.field!: v.message};
      });
      _formKey.currentState!.validate();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _submitting = false;
        _formErrors = [Violation('ERROR', 'ERROR', context.t('common.error'), null)];
      });
    }
  }

  Future<bool?> _confirmNewPrice(Money total) => showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text(ctx.t('booking.priceChanged')),
          content: Text(ctx.t('booking.priceChangedText', {'total': total.format(ctx.lang)})),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(ctx.t('booking.back'))),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(ctx.t('booking.acceptNewPrice'))),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    final selection = ref.watch(selectionProvider);
    final meta = ref.watch(metaProvider).value;
    final mobile = Breakpoints.isMobile(context);
    ref.listen(settingsProvider.select((s) => s.currency), (_, _) => _loadQuote());

    if (selection.outbound == null) {
      return Column(children: [
        const WaveHeader(),
        Expanded(
          child: Center(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.directions_boat_outlined, size: 48, color: WaveColors.muted),
              const SizedBox(height: 12),
              Text(context.t('booking.noSelection'), style: const TextStyle(fontSize: 16)),
              const SizedBox(height: 16),
              GoldButton(label: context.t('booking.newSearch'), height: 48, onPressed: () => context.go('/')),
            ]),
          ),
        ),
      ]);
    }

    final summary = _SummaryCard(quote: _quote, error: _quoteError, meta: meta, onRetry: _loadQuote);
    final form = Form(
      key: _formKey,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        NoticeBox(message: context.t('booking.passportOnly'), severity: 'WARNING'),
        const SizedBox(height: 16),
        for (final (i, t) in _travellers.indexed) ...[
          _TravellerCard(
            index: i,
            form: t,
            departure: _departureDay,
            tripEnd: _tripEnd,
            serverError: _serverError,
            clearServerError: _clearServerError,
            onChanged: () => setState(() {}),
          ),
          const SizedBox(height: 16),
        ],
        if (ref.read(searchQueryProvider).vehicle != null) ...[
          _Section(
            title: context.t('booking.vehicleDetails'),
            icon: Icons.directions_car_outlined,
            subtitle: context.t('booking.vehicleOf', {'v': vehicleLabel(context, ref.read(searchQueryProvider).vehicle)}),
            child: _Grid(children: [
              TextFormField(
                controller: _plate,
                textCapitalization: TextCapitalization.characters,
                decoration: InputDecoration(labelText: context.t('booking.plate'), hintText: '01234 120 16'),
                onChanged: (_) => _clearServerError('vehicleDetails.plate'),
                validator: (v) {
                  final value = (v ?? '').trim().toUpperCase();
                  if (value.isEmpty) return context.t('booking.required');
                  if (!_plateRe.hasMatch(value)) return context.t('booking.invalid');
                  return _serverError('vehicleDetails.plate');
                },
              ),
              _CountryField(label: context.t('booking.regCountry'), value: _regCountry, onChanged: (c) => setState(() => _regCountry = c)),
              TextFormField(
                controller: _make,
                decoration: InputDecoration(labelText: context.t('booking.make'), hintText: 'Renault, Hyundai…'),
                validator: (v) => (v ?? '').trim().isEmpty ? context.t('booking.required') : _serverError('vehicleDetails'),
              ),
              TextFormField(
                controller: _model,
                decoration: InputDecoration(labelText: context.t('booking.model'), hintText: 'Clio, Tucson…'),
                validator: (v) => (v ?? '').trim().isEmpty ? context.t('booking.required') : null,
              ),
            ]),
          ),
          const SizedBox(height: 16),
        ],
        _Section(
          title: context.t('booking.contact'),
          icon: Icons.mail_outline_rounded,
          child: _Grid(children: [
            TextFormField(
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              autofillHints: const [AutofillHints.email],
              decoration: InputDecoration(labelText: context.t('booking.email'), helperText: context.t('booking.emailHint')),
              onChanged: (_) => _clearServerError('contact.email'),
              validator: (v) {
                final value = (v ?? '').trim();
                if (value.isEmpty) return context.t('booking.required');
                if (!_emailRe.hasMatch(value) || value.length > 254) return context.t('booking.invalid');
                return _serverError('contact.email');
              },
            ),
            TextFormField(
              controller: _phone,
              keyboardType: TextInputType.phone,
              autofillHints: const [AutofillHints.telephoneNumber],
              decoration: InputDecoration(labelText: context.t('booking.phone'), helperText: context.t('booking.phoneHint')),
              onChanged: (_) => _clearServerError('contact.phone'),
              validator: (v) {
                final value = (v ?? '').trim();
                if (value.isEmpty) return context.t('booking.required');
                if (!validPhone(value)) return context.t('booking.invalid');
                return _serverError('contact.phone');
              },
            ),
          ]),
        ),
        const SizedBox(height: 16),
        _Section(
          title: context.t('booking.review'),
          icon: Icons.fact_check_outlined,
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            NoticeBox(message: context.t('booking.holdInfo')),
            const SizedBox(height: 10),
            CheckboxListTile(
              value: _accept,
              contentPadding: EdgeInsets.zero,
              controlAffinity: ListTileControlAffinity.leading,
              onChanged: (v) => setState(() {
                _accept = v ?? false;
                _acceptError = false;
              }),
              title: Text(context.t('booking.accept'), style: const TextStyle(fontSize: 13.5)),
              subtitle: _acceptError ? Text(context.t('booking.required'), style: const TextStyle(color: WaveColors.danger)) : null,
            ),
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: TextButton.icon(
                onPressed: () => _showConditions(context, meta),
                icon: const Icon(Icons.policy_outlined, size: 18),
                label: Text(context.t('booking.conditions')),
              ),
            ),
            for (final v in _formErrors) Padding(padding: const EdgeInsets.only(top: 8), child: NoticeBox(message: v.message, severity: 'ERROR')),
            const SizedBox(height: 14),
            GoldButton(
              label: _quote == null ? context.t('booking.book') : '${context.t('booking.book')} · ${_quote!.total.format(context.lang)}',
              icon: Icons.lock_outline_rounded,
              loading: _submitting,
              expand: true,
              onPressed: _quote == null ? null : () => _submit(),
            ),
            const SizedBox(height: 8),
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Icon(Icons.verified_user_outlined, size: 16, color: WaveColors.success),
              const SizedBox(width: 6),
              Text(context.t('booking.secure'), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
            ]),
          ]),
        ),
      ]),
    );

    return Column(children: [
      const WaveHeader(),
      Expanded(
        child: SingleChildScrollView(
          child: Column(children: [
            _TitleBand(title: context.t('booking.title')),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: mobile ? 12 : 32, vertical: 20),
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 1180),
                  child: mobile
                      ? Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [summary, const SizedBox(height: 16), form])
                      : Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Expanded(child: form),
                          const SizedBox(width: 24),
                          SizedBox(width: 380, child: summary),
                        ]),
                ),
              ),
            ),
          ]),
        ),
      ),
    ]);
  }

  void _showConditions(BuildContext context, Meta? meta) {
    final lang = context.lang;
    final legs = _quote?.legs ?? const <LegQuote>[];
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(ctx.t('booking.conditions')),
        content: SizedBox(
          width: 520,
          child: SingleChildScrollView(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
              for (final leg in legs) ...[
                Row(children: [OperatorLogo(leg.operator, size: 15), const SizedBox(width: 8), Text(leg.tariffName.of(lang), style: const TextStyle(fontWeight: FontWeight.w600))]),
                const SizedBox(height: 6),
                Text(
                  meta?.operator(leg.operator)?.tariffs.where((t) => t.code == leg.tariffCode).firstOrNull?.conditions.of(lang) ?? '',
                  style: const TextStyle(fontSize: 13.5, height: 1.45),
                ),
                const SizedBox(height: 14),
              ],
            ]),
          ),
        ),
        actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: Text(ctx.t('common.close')))],
      ),
    );
  }
}

class _TitleBand extends StatelessWidget {
  const _TitleBand({required this.title});
  final String title;

  @override
  Widget build(BuildContext context) {
    final mobile = Breakpoints.isMobile(context);
    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(gradient: WaveColors.navyGradient),
      padding: EdgeInsets.fromLTRB(mobile ? 16 : 40, 4, mobile ? 16 : 40, 20),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1180),
          child: Row(children: [
            Flexible(child: Text(title, style: Theme.of(context).textTheme.headlineMedium?.copyWith(color: Colors.white, fontSize: mobile ? 24 : 30))),
            const SizedBox(width: 12),
            const GoldFlourish(),
          ]),
        ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.icon, required this.child, this.subtitle});
  final String title;
  final String? subtitle;
  final IconData icon;
  final Widget child;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Row(children: [
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(color: WaveColors.sky, borderRadius: BorderRadius.circular(8)),
                child: Icon(icon, size: 20, color: WaveColors.navy),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(title, style: const TextStyle(fontSize: 16.5, fontWeight: FontWeight.w700)),
                  if (subtitle != null) Text(subtitle!, style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
                ]),
              ),
            ]),
            const SizedBox(height: 16),
            child,
          ]),
        ),
      );
}

/// Two columns on wide screens, one on phones.
class _Grid extends StatelessWidget {
  const _Grid({required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (context, c) {
        final two = c.maxWidth >= 520;
        final width = two ? (c.maxWidth - 14) / 2 : c.maxWidth;
        return Wrap(spacing: 14, runSpacing: 14, children: [for (final w in children) SizedBox(width: width, child: w)]);
      });
}

class _TravellerCard extends StatelessWidget {
  const _TravellerCard({required this.index, required this.form, required this.departure, required this.tripEnd, required this.serverError, required this.clearServerError, required this.onChanged});
  final int index;
  final _TravellerForm form;
  final DateTime departure;
  final DateTime tripEnd;
  final String? Function(String) serverError;
  final void Function(String) clearServerError;
  final VoidCallback onChanged;

  String get _field => 'travellers[$index]';

  @override
  Widget build(BuildContext context) {
    final kind = switch (form.kind) {
      _Kind.adult => context.t('booking.adult'),
      _Kind.senior => context.t('booking.senior'),
      _Kind.child => form.searchedAge == 0 ? context.t('booking.infant') : context.t('booking.child', {'n': form.searchedAge!}),
    };
    final dob = parseDate(form.dob.text);
    final age = dob == null ? null : ageOn(dob, departure);
    final mismatch = age != null &&
        switch (form.kind) {
          _Kind.adult => age < 18 || age >= 60,
          _Kind.senior => age < 60,
          _Kind.child => age != form.searchedAge,
        };
    final expiry = parseDate(form.expiry.text);
    final expiresSoon = expiry != null && expiry.isAfter(tripEnd) && expiry.isBefore(DateTime(tripEnd.year, tripEnd.month + 6, tripEnd.day));

    return _Section(
      title: '${context.t('booking.traveller', {'n': index + 1})} · $kind',
      icon: form.kind == _Kind.child ? Icons.child_care_rounded : Icons.person_outline_rounded,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        _Grid(children: [
          TextFormField(
            controller: form.first,
            textCapitalization: TextCapitalization.words,
            autofillHints: index == 0 ? const [AutofillHints.givenName] : null,
            decoration: InputDecoration(labelText: context.t('booking.firstName')),
            onChanged: (_) => clearServerError(_field),
            validator: (v) {
              final value = (v ?? '').trim();
              if (value.isEmpty) return context.t('booking.required');
              if (!_nameRe.hasMatch(value)) return context.t('booking.invalid');
              return serverError(_field);
            },
          ),
          TextFormField(
            controller: form.last,
            textCapitalization: TextCapitalization.characters,
            autofillHints: index == 0 ? const [AutofillHints.familyName] : null,
            decoration: InputDecoration(labelText: context.t('booking.lastName')),
            onChanged: (_) => clearServerError(_field),
            validator: (v) {
              final value = (v ?? '').trim();
              if (value.isEmpty) return context.t('booking.required');
              if (!_nameRe.hasMatch(value)) return context.t('booking.invalid');
              return null;
            },
          ),
          FormField<String>(
            initialValue: form.sex,
            validator: (_) => form.sex == null ? context.t('booking.required') : null,
            builder: (state) => InputDecorator(
              decoration: InputDecoration(labelText: context.t('booking.sex'), errorText: state.errorText, contentPadding: const EdgeInsets.fromLTRB(12, 8, 12, 8)),
              child: SegmentedButton<String>(
                showSelectedIcon: false,
                emptySelectionAllowed: true,
                style: SegmentedButton.styleFrom(visualDensity: VisualDensity.compact),
                segments: [
                  ButtonSegment(value: 'F', label: Text(context.t('booking.female'))),
                  ButtonSegment(value: 'M', label: Text(context.t('booking.male'))),
                ],
                selected: {?form.sex},
                onSelectionChanged: (s) {
                  form.sex = s.firstOrNull;
                  state.didChange(form.sex);
                  onChanged();
                },
              ),
            ),
          ),
          _DateField(
            controller: form.dob,
            label: context.t('booking.dob'),
            first: DateTime(departure.year - 110),
            last: DateTime.now(),
            initial: DateTime(departure.year - (form.searchedAge ?? 35), departure.month, departure.day),
            helper: mismatch ? context.t('booking.ageMismatch') : null,
            onChanged: () {
              clearServerError('$_field.dateOfBirth');
              onChanged();
            },
            validator: (d) {
              if (d == null) return context.t('booking.invalid');
              if (d.isAfter(DateTime.now()) || d.isBefore(DateTime(departure.year - 120))) return context.t('booking.dobFuture');
              return serverError('$_field.dateOfBirth');
            },
          ),
          _CountryField(
            label: context.t('booking.nationality'),
            value: form.nationality,
            onChanged: (c) {
              form.nationality = c;
              onChanged();
            },
          ),
          TextFormField(
            controller: form.passport,
            textCapitalization: TextCapitalization.characters,
            inputFormatters: [FilteringTextInputFormatter.allow(RegExp('[A-Za-z0-9 ]')), LengthLimitingTextInputFormatter(24)],
            decoration: InputDecoration(labelText: context.t('booking.passport')),
            onChanged: (_) => clearServerError('$_field.document.number'),
            validator: (v) {
              final value = (v ?? '').toUpperCase().replaceAll(' ', '');
              if (value.isEmpty) return context.t('booking.required');
              if (!_docRe.hasMatch(value)) return context.t('booking.invalid');
              return serverError('$_field.document.number');
            },
          ),
          _DateField(
            controller: form.expiry,
            label: context.t('booking.expiry'),
            first: DateTime.now(),
            last: DateTime(DateTime.now().year + 15),
            initial: DateTime(tripEnd.year + 5, tripEnd.month, tripEnd.day),
            helper: expiresSoon ? context.t('booking.passportSoon') : null,
            onChanged: () {
              clearServerError('$_field.document.expiry');
              onChanged();
            },
            validator: (d) {
              if (d == null) return context.t('booking.invalid');
              if (!d.isAfter(tripEnd)) return context.t('booking.passportExpired');
              return serverError('$_field.document.expiry');
            },
          ),
          _CountryField(
            label: context.t('booking.issuing'),
            value: form.issuing ?? form.nationality,
            onChanged: (c) {
              form.issuing = c;
              onChanged();
            },
          ),
        ]),
      ]),
    );
  }
}

/// dd/MM/yyyy text entry (fast on phones) with an optional calendar.
class _DateField extends StatelessWidget {
  const _DateField({required this.controller, required this.label, required this.first, required this.last, required this.initial, required this.validator, required this.onChanged, this.helper});
  final TextEditingController controller;
  final String label;
  final DateTime first;
  final DateTime last;
  final DateTime initial;
  final String? helper;
  final String? Function(DateTime?) validator;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) => TextFormField(
        controller: controller,
        keyboardType: TextInputType.datetime,
        inputFormatters: [_DateMask()],
        decoration: InputDecoration(
          labelText: label,
          hintText: context.t('booking.dateHint'),
          helperText: helper,
          helperMaxLines: 3,
          helperStyle: const TextStyle(color: WaveColors.warning),
          suffixIcon: IconButton(
            tooltip: const ['Calendrier', 'Calendar', 'التقويم'][context.lang.index],
            icon: const Icon(Icons.calendar_month_outlined),
            onPressed: () async {
              final current = parseDate(controller.text);
              final start = current ?? initial;
              final picked = await showDatePicker(
                context: context,
                initialDate: start.isBefore(first) ? first : (start.isAfter(last) ? last : start),
                firstDate: first,
                lastDate: last,
                initialEntryMode: DatePickerEntryMode.calendar,
              );
              if (picked != null) {
                controller.text = Fmt.dateInput(picked);
                onChanged();
              }
            },
          ),
        ),
        onChanged: (_) => onChanged(),
        validator: (v) => (v ?? '').trim().isEmpty ? context.t('booking.required') : validator(parseDate(v!)),
      );
}

class _DateMask extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    final digits = newValue.text.replaceAll(RegExp(r'\D'), '');
    final clipped = digits.length > 8 ? digits.substring(0, 8) : digits;
    final buffer = StringBuffer();
    for (var i = 0; i < clipped.length; i++) {
      if (i == 2 || i == 4) buffer.write('/');
      buffer.write(clipped[i]);
    }
    final text = buffer.toString();
    return TextEditingValue(text: text, selection: TextSelection.collapsed(offset: text.length));
  }
}

class _CountryField extends StatelessWidget {
  const _CountryField({required this.label, required this.value, required this.onChanged});
  final String label;
  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    return DropdownButtonFormField<String>(
      initialValue: value,
      isExpanded: true,
      decoration: InputDecoration(labelText: label),
      items: [for (final c in countries) DropdownMenuItem(value: c.code, child: Text('${c.name(lang)} (${c.code})', overflow: TextOverflow.ellipsis))],
      onChanged: (c) {
        if (c != null) onChanged(c);
      },
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.quote, required this.error, required this.meta, required this.onRetry});
  final Quote? quote;
  final Object? error;
  final Meta? meta;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final q = quote;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: error != null
            ? ErrorView(error: error!, onRetry: onRetry)
            : q == null
                ? const SizedBox(height: 160, child: LoadingView())
                : Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    Text(context.t('booking.review'), style: const TextStyle(fontSize: 16.5, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 12),
                    for (final (i, leg) in q.legs.indexed) ...[
                      LegSummary(leg: leg, meta: meta, inbound: i > 0),
                      const Divider(height: 26),
                    ],
                    PriceSummary(legs: q.legs, adjustments: q.adjustments, total: q.total, payable: q.payable, meta: meta),
                    for (final n in q.notices.where((n) => n.severity != 'INFO' || n.code == 'PRICE_REFERENCE'))
                      Padding(padding: const EdgeInsets.only(top: 10), child: NoticeBox(message: n.message, severity: n.severity)),
                  ]),
      ),
    );
  }
}
