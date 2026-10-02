import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/api_client.dart';
import '../../data/models.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../../widgets/states.dart';
import '../search/search_card.dart' show portLabel;
import 'trip_summary.dart';

/// Booking after creation: payment while the places are held, then the e-ticket.
class ConfirmationPage extends ConsumerStatefulWidget {
  const ConfirmationPage({super.key, required this.reference, this.lastName});
  final String reference;
  final String? lastName;

  @override
  ConsumerState<ConfirmationPage> createState() => _ConfirmationPageState();
}

class _ConfirmationPageState extends ConsumerState<ConfirmationPage> with WidgetsBindingObserver {
  BookingView? _booking;
  Object? _error;
  String? _lastName;
  bool _needsName = false;
  String? _paying;
  bool _awaitingBank = false;
  String? _agencyInstructions;
  String? _paymentMessage;
  Timer? _ticker;
  Timer? _poll;
  final _nameController = TextEditingController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _resolveName();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _ticker?.cancel();
    _poll?.cancel();
    _nameController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Coming back from the bank's page in the browser: check the payment straight away.
    if (state == AppLifecycleState.resumed && _awaitingBank) _load(silent: true);
  }

  Future<void> _resolveName() async {
    var name = widget.lastName;
    if (name == null || name.isEmpty) {
      final trips = await ref.read(tripsProvider.future);
      name = trips.where((t) => t.reference == widget.reference.toUpperCase()).firstOrNull?.lastName;
    }
    if (!mounted) return;
    if (name == null || name.isEmpty || widget.reference.isEmpty) {
      setState(() => _needsName = true);
      return;
    }
    _lastName = name;
    await _load();
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent) setState(() => _error = null);
    try {
      final booking = await ref.read(apiProvider).booking(widget.reference, _lastName!);
      if (!mounted) return;
      setState(() {
        _booking = booking;
        _needsName = false;
      });
      _afterLoad(booking);
    } catch (e) {
      if (!mounted) return;
      if (!silent) {
        setState(() {
          _error = e;
          if (e is ApiException && e.code == 'BOOKING_NOT_FOUND') _needsName = true;
        });
      }
    }
  }

  void _afterLoad(BookingView b) {
    _ticker?.cancel();
    if (b.status == 'HELD' && b.holdExpiresAt != null) {
      _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
        if (!mounted) return;
        setState(() {});
        if (DateTime.now().isAfter(b.holdExpiresAt!)) {
          _ticker?.cancel();
          _load(silent: true);
        }
      });
    }
    if (b.isConfirmed) {
      _poll?.cancel();
      _awaitingBank = false;
    }
    // Keep the booking in "Ma vague" (reference + name stay in the device keystore).
    final trips = ref.read(tripsProvider).value ?? const [];
    if (!trips.any((t) => t.reference == b.reference) && _lastName != null) {
      final meta = ref.read(metaProvider).value;
      final lang = ref.read(settingsProvider).lang;
      final leg = b.legs.first;
      ref.read(tripsProvider.notifier).save(SavedTrip(
            b.reference,
            _lastName!,
            '${portLabel(meta, leg.from, lang)} → ${portLabel(meta, leg.to, lang)}',
            Fmt.iso(leg.departure.local),
          ));
    }
  }

  Future<void> _pay(String method) async {
    final b = _booking!;
    final lang = ref.read(settingsProvider).lang;
    setState(() {
      _paying = method;
      _paymentMessage = null;
    });
    try {
      final start = await ref.read(apiProvider).pay(b.reference, _lastName!, method, lang);
      if (!mounted) return;
      final url = start.redirectUrl;
      if (url == null) {
        // Agency: the hold is extended to 24 h and the instructions tell where to pay.
        setState(() => _agencyInstructions = start.instructions);
        await _load(silent: true);
      } else if (url.contains('sandbox=approved')) {
        final approve = await _sandboxDialog(start.amount);
        if (approve == null) return;
        final target = approve ? url : url.replaceFirst('outcome=success', 'outcome=failure');
        final updated = await ref.read(apiProvider).followPaymentReturn(target);
        if (!mounted) return;
        setState(() {
          _booking = updated;
          if (!updated.isConfirmed) _paymentMessage = context.t('confirm.declined');
        });
        _afterLoad(updated);
      } else {
        await _openBank(Uri.parse(url));
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _paymentMessage = e.isOffline ? context.t('common.offline') : e.message);
    } catch (_) {
      if (mounted) setState(() => _paymentMessage = context.t('common.error'));
    } finally {
      if (mounted) setState(() => _paying = null);
    }
  }

  /// SATIM (CIB / Edahabia) and Stripe pages must run in a real browser, never in a WebView.
  Future<void> _openBank(Uri uri) async {
    if (kIsWeb) {
      await launchUrl(uri, webOnlyWindowName: '_self');
      return;
    }
    setState(() => _awaitingBank = true);
    await launchUrl(uri, mode: LaunchMode.externalApplication);
    _poll?.cancel();
    var ticks = 0;
    _poll = Timer.periodic(const Duration(seconds: 5), (timer) {
      if (++ticks > 180 || !mounted) {
        timer.cancel();
        return;
      }
      _load(silent: true);
    });
  }

  Future<bool?> _sandboxDialog(Money amount) => showDialog<bool>(
        context: context,
        barrierDismissible: false,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.account_balance_rounded, color: WaveColors.navy, size: 36),
          title: Text(ctx.t('confirm.sandboxTitle')),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            Text(amount.format(ctx.lang), style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: WaveColors.navy)),
            const SizedBox(height: 10),
            Text(ctx.t('confirm.sandboxText'), textAlign: TextAlign.center),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: Text(ctx.t('common.close'))),
            OutlinedButton(onPressed: () => Navigator.pop(ctx, false), child: Text(ctx.t('confirm.decline'))),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(ctx.t('confirm.approve'))),
          ],
        ),
      );

  Future<void> _cancel() async {
    final b = _booking!;
    final api = ref.read(apiProvider);
    final lang = ref.read(settingsProvider).lang;
    try {
      final preview = await api.cancel(b.reference, _lastName!, dryRun: true, lang: lang);
      if (!mounted) return;
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text(ctx.t('booking.cancel')),
          content: Text(ctx.t('booking.cancelPreview', {'fee': preview.fee.format(ctx.lang), 'refund': preview.refund.format(ctx.lang)})),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(ctx.t('confirm.keep'))),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: WaveColors.danger),
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(ctx.t('confirm.cancelConfirm')),
            ),
          ],
        ),
      );
      if (ok != true) return;
      await api.cancel(b.reference, _lastName!, dryRun: false, lang: lang);
      await _load();
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final mobile = Breakpoints.isMobile(context);
    final b = _booking;
    Widget body;
    if (_needsName) {
      body = _LookupForm(
        controller: _nameController,
        error: _error,
        onSubmit: () {
          _lastName = _nameController.text.trim();
          if (_lastName!.isNotEmpty) _load();
        },
      );
    } else if (b == null) {
      body = _error != null ? ErrorView(error: _error!, onRetry: _load) : const SizedBox(height: 300, child: LoadingView());
    } else {
      body = _content(context, b, mobile);
    }
    return Column(children: [
      const WaveHeader(),
      Expanded(
        child: SingleChildScrollView(
          padding: EdgeInsets.symmetric(horizontal: mobile ? 12 : 32, vertical: 20),
          child: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1080), child: body)),
        ),
      ),
    ]);
  }

  Widget _content(BuildContext context, BookingView b, bool mobile) {
    final meta = ref.watch(metaProvider).value;
    final left = Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      _StatusBanner(booking: b),
      const SizedBox(height: 16),
      if (b.status == 'HELD') ...[
        _PaymentCard(
          booking: b,
          methods: meta?.paymentMethods ?? const ['CIB', 'EDAHABIA', 'AGENCY'],
          paying: _paying,
          awaitingBank: _awaitingBank,
          message: _paymentMessage,
          agencyInstructions: _agencyInstructions,
          onPay: _pay,
          onCheck: () => _load(silent: true),
        ),
        const SizedBox(height: 16),
      ],
      if (b.isConfirmed && b.ticketPayload != null) ...[
        _TicketCard(booking: b),
        const SizedBox(height: 16),
      ],
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Text(context.t('booking.travellers'), style: const TextStyle(fontSize: 16.5, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            for (final t in b.travellers)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const CircleAvatar(radius: 16, backgroundColor: WaveColors.sky, child: Icon(Icons.person_outline, size: 18, color: WaveColors.navy)),
                title: Text('${t.firstName} ${t.lastName}', style: const TextStyle(fontWeight: FontWeight.w600)),
                subtitle: Text('${context.t('booking.passport')} ${t.documentNumber} · ${Fmt.dateInput(DateTime.parse(t.dateOfBirth))}'),
              ),
          ]),
        ),
      ),
      if (b.isConfirmed) ...[
        const SizedBox(height: 16),
        NoticeBox(title: context.t('confirm.checkinTitle'), message: context.t('confirm.checkin')),
      ],
    ]);
    final right = Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          for (final (i, leg) in b.legs.indexed) ...[
            LegSummary(leg: leg, meta: meta, inbound: i > 0),
            const Divider(height: 26),
          ],
          PriceSummary(legs: b.legs, adjustments: b.adjustments, total: b.total, payable: b.payable, meta: meta, expanded: !b.isConfirmed),
          for (final p in b.payments.where((p) => p.status == 'APPROVED' || p.status == 'REFUND_PENDING'))
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(children: [
                Icon(p.status == 'APPROVED' ? Icons.check_circle_rounded : Icons.replay_rounded, size: 18, color: WaveColors.success),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    p.status == 'APPROVED'
                        ? '${context.t('confirm.paid')} · ${context.t('booking.method.${p.method}')}'
                        : context.t('confirm.refund', {'amount': p.amount.format(context.lang)}),
                    style: const TextStyle(fontSize: 13),
                  ),
                ),
                Text(p.amount.format(context.lang), style: const TextStyle(fontWeight: FontWeight.w600)),
              ]),
            ),
          if (b.status == 'HELD' || b.isConfirmed) ...[
            const SizedBox(height: 14),
            TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: WaveColors.danger),
              onPressed: _cancel,
              icon: const Icon(Icons.cancel_outlined),
              label: Text(context.t('booking.cancel')),
            ),
          ],
        ]),
      ),
    );
    if (mobile) return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [left, const SizedBox(height: 16), right]);
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Expanded(flex: 11, child: left),
      const SizedBox(width: 20),
      Expanded(flex: 9, child: right),
    ]);
  }
}

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.booking});
  final BookingView booking;

  @override
  Widget build(BuildContext context) {
    final b = booking;
    final (Color color, IconData icon) = switch (b.status) {
      'CONFIRMED' || 'TICKETED' => (WaveColors.success, Icons.verified_rounded),
      'HELD' => (WaveColors.warning, Icons.hourglass_top_rounded),
      'REFUNDED' => (WaveColors.muted, Icons.replay_rounded),
      _ => (WaveColors.danger, Icons.cancel_rounded),
    };
    String? countdown;
    if (b.status == 'HELD' && b.holdExpiresAt != null) {
      final left = b.holdExpiresAt!.difference(DateTime.now());
      if (left.isNegative) {
        countdown = context.t('confirm.holdExpired');
      } else {
        final h = left.inHours;
        final m = left.inMinutes.remainder(60).toString().padLeft(2, '0');
        final s = left.inSeconds.remainder(60).toString().padLeft(2, '0');
        countdown = context.t('confirm.timeLeft', {'time': h > 0 ? '$h:$m:$s' : '$m:$s'});
      }
    }
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: WaveColors.navyGradient,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(color: color.withValues(alpha: 0.18), shape: BoxShape.circle, border: Border.all(color: color, width: 2)),
          child: Icon(icon, color: color == WaveColors.muted ? Colors.white : color, size: 30),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(
              b.isConfirmed ? context.t('booking.confirmed') : context.t('booking.status.${b.status}'),
              style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 4),
            SelectableText.rich(TextSpan(children: [
              TextSpan(text: '${context.t('booking.reference')} ', style: TextStyle(color: Colors.white.withValues(alpha: 0.8))),
              TextSpan(text: b.reference, style: const TextStyle(color: WaveColors.goldLight, fontWeight: FontWeight.w800, fontSize: 18, letterSpacing: 2)),
            ])),
            if (countdown != null) ...[
              const SizedBox(height: 4),
              Text(countdown, style: const TextStyle(color: WaveColors.goldLight, fontWeight: FontWeight.w600)),
            ],
          ]),
        ),
      ]),
    );
  }
}

class _PaymentCard extends StatelessWidget {
  const _PaymentCard({required this.booking, required this.methods, required this.paying, required this.awaitingBank, required this.message, required this.agencyInstructions, required this.onPay, required this.onCheck});
  final BookingView booking;
  final List<String> methods;
  final String? paying;
  final bool awaitingBank;
  final String? message;
  final String? agencyInstructions;
  final ValueChanged<String> onPay;
  final VoidCallback onCheck;

  Money? _amount(String method) {
    final p = booking.payable;
    return switch (method) {
      'CIB' || 'EDAHABIA' || 'AGENCY' => p[Currency.DZD],
      'CARD' => booking.total.currency == Currency.DZD ? p[Currency.EUR] : booking.total,
      _ => booking.total,
    };
  }

  @override
  Widget build(BuildContext context) {
    final sandbox = methods.contains('SANDBOX');
    final shown = methods.where((m) => m != 'SANDBOX' || methods.length == 1).toList();
    final expired = booking.holdExpiresAt != null && DateTime.now().isAfter(booking.holdExpiresAt!);
    final agencyPending = booking.payments.any((p) => p.method == 'AGENCY' && p.status == 'PENDING');
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Expanded(child: Text(context.t('confirm.payTitle'), style: const TextStyle(fontSize: 16.5, fontWeight: FontWeight.w700))),
            if (sandbox)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(color: WaveColors.sand, borderRadius: BorderRadius.circular(6), border: Border.all(color: WaveColors.gold)),
                child: Text(context.t('confirm.test'), style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: WaveColors.goldDeep)),
              ),
          ]),
          const SizedBox(height: 4),
          Text(context.t('confirm.dzdNote'), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
          const SizedBox(height: 12),
          for (final m in shown)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _MethodTile(
                method: m,
                amount: _amount(m),
                loading: paying == m,
                enabled: paying == null && !expired,
                onTap: () => onPay(m),
              ),
            ),
          if (awaitingBank) ...[
            const SizedBox(height: 6),
            Row(children: [
              const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
              const SizedBox(width: 10),
              Expanded(child: Text(context.t('confirm.waitingBank'))),
              TextButton(onPressed: onCheck, child: Text(context.t('confirm.check'))),
            ]),
          ],
          if (agencyInstructions != null || agencyPending) ...[
            const SizedBox(height: 6),
            NoticeBox(
              title: context.t('confirm.agency'),
              message: agencyInstructions ?? booking.payments.lastWhere((p) => p.method == 'AGENCY').message ?? '',
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(onPressed: () => openWhatsApp(context), icon: const Icon(Icons.chat_outlined), label: Text(context.t('common.whatsapp'))),
          ],
          if (message != null) ...[
            const SizedBox(height: 8),
            NoticeBox(message: message!, severity: 'ERROR'),
          ],
        ]),
      ),
    );
  }
}

class _MethodTile extends StatelessWidget {
  const _MethodTile({required this.method, required this.amount, required this.loading, required this.enabled, required this.onTap});
  final String method;
  final Money? amount;
  final bool loading;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final (IconData icon, Color color) = switch (method) {
      'CIB' => (Icons.credit_card_rounded, const Color(0xFF0B7A4B)),
      'EDAHABIA' => (Icons.credit_card_rounded, const Color(0xFFC8973A)),
      'CARD' => (Icons.public_rounded, WaveColors.blue),
      'AGENCY' => (Icons.storefront_rounded, WaveColors.navy),
      _ => (Icons.science_outlined, WaveColors.muted),
    };
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12), side: const BorderSide(color: WaveColors.line)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: enabled ? onTap : null,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(color: color.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(10)),
              child: Icon(icon, color: color),
            ),
            const SizedBox(width: 12),
            Expanded(child: Text(context.t('booking.method.$method'), style: const TextStyle(fontWeight: FontWeight.w600))),
            if (amount != null) Text(amount!.format(context.lang), style: const TextStyle(fontWeight: FontWeight.w700, color: WaveColors.navy)),
            const SizedBox(width: 10),
            loading
                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : Icon(Directionality.of(context) == TextDirection.rtl ? Icons.chevron_left_rounded : Icons.chevron_right_rounded, color: WaveColors.muted),
          ]),
        ),
      ),
    );
  }
}

class _TicketCard extends StatelessWidget {
  const _TicketCard({required this.booking});
  final BookingView booking;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(children: [
            Row(children: [
              const Icon(Icons.qr_code_2_rounded, color: WaveColors.navy),
              const SizedBox(width: 8),
              Text(context.t('booking.ticket'), style: const TextStyle(fontSize: 16.5, fontWeight: FontWeight.w700)),
              const Spacer(),
              for (final op in booking.legs.map((l) => l.operator).toSet()) Padding(padding: const EdgeInsetsDirectional.only(start: 8), child: OperatorLogo(op, size: 14)),
            ]),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: WaveColors.line)),
              child: QrImageView(
                data: booking.ticketPayload!,
                size: 210,
                backgroundColor: Colors.white,
                eyeStyle: const QrEyeStyle(eyeShape: QrEyeShape.square, color: WaveColors.navyDeep),
                dataModuleStyle: const QrDataModuleStyle(dataModuleShape: QrDataModuleShape.square, color: WaveColors.navyDeep),
              ),
            ),
            const SizedBox(height: 10),
            Text(booking.reference, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, letterSpacing: 4, color: WaveColors.navy)),
            const SizedBox(height: 6),
            Text(context.t('booking.showAtPort'), textAlign: TextAlign.center, style: const TextStyle(color: WaveColors.muted)),
          ]),
        ),
      );
}

class _LookupForm extends StatelessWidget {
  const _LookupForm({required this.controller, required this.error, required this.onSubmit});
  final TextEditingController controller;
  final Object? error;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) => Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 460),
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(22),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Text(context.t('trips.find'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                Text(context.t('confirm.lookup'), style: const TextStyle(color: WaveColors.muted)),
                const SizedBox(height: 16),
                TextField(
                  controller: controller,
                  textCapitalization: TextCapitalization.characters,
                  decoration: InputDecoration(labelText: context.t('booking.lastName')),
                  onSubmitted: (_) => onSubmit(),
                ),
                if (error != null) ...[
                  const SizedBox(height: 10),
                  NoticeBox(message: error is ApiException ? (error as ApiException).message : context.t('common.error'), severity: 'ERROR'),
                ],
                const SizedBox(height: 16),
                GoldButton(label: context.t('confirm.show'), height: 48, expand: true, onPressed: onSubmit),
                const SizedBox(height: 8),
                TextButton(onPressed: () => context.go('/trips'), child: Text(context.t('trips.title'))),
              ]),
            ),
          ),
        ),
      );
}
