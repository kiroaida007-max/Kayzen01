import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../../widgets/states.dart';

const companyImages = {'BAL': 'company_bal', 'CL': 'company_cl', 'GNV': 'company_gnv', 'NE': 'company_ne'};

/// Each company's markets, age bands, baggage, fares and fleet, as the operators publish them.
class CompaniesPage extends ConsumerWidget {
  const CompaniesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final meta = ref.watch(metaProvider);
    return WavePage(
      title: context.t('companies.title'),
      child: meta.when(
        loading: () => const SizedBox(height: 300, child: LoadingView()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(metaProvider)),
        data: (m) {
          final ops = [...m.operators]..sort((a, b) => a.active == b.active ? b.routes.compareTo(a.routes) : (a.active ? -1 : 1));
          return LayoutBuilder(builder: (context, c) {
            final columns = c.maxWidth >= 1000 ? 2 : 1;
            final width = (c.maxWidth - (columns - 1) * 18) / columns;
            return Wrap(spacing: 18, runSpacing: 18, children: [for (final o in ops) SizedBox(width: width, child: _CompanyCard(op: o, meta: m))]);
          });
        },
      ),
    );
  }
}

class _CompanyCard extends StatelessWidget {
  const _CompanyCard({required this.op, required this.meta});
  final Operator op;
  final Meta meta;

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    final image = companyImages[op.code];
    final muted = const TextStyle(fontSize: 13, color: WaveColors.muted, height: 1.4);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Stack(children: [
          if (image != null)
            Image.asset('assets/images/$image.jpg', height: 120, width: double.infinity, fit: BoxFit.cover)
          else
            ShipArt(color: hexColor(op.color), height: 120),
          Positioned(
            left: 14,
            bottom: 12,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(10)),
              child: OperatorLogo(op.code, size: 20),
            ),
          ),
          if (!op.active)
            Positioned(
              right: 12,
              top: 12,
              child: Chip(
                backgroundColor: WaveColors.danger,
                side: BorderSide.none,
                label: Text(context.t('companies.inactive'), style: const TextStyle(color: Colors.white, fontSize: 12)),
              ),
            ),
        ]),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(op.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 2),
            Text(op.markets.of(lang), style: const TextStyle(fontSize: 13, color: WaveColors.blue, fontWeight: FontWeight.w500)),
            const SizedBox(height: 8),
            Text(op.description.of(lang), style: const TextStyle(fontSize: 13.5, height: 1.45)),
            const SizedBox(height: 12),
            Wrap(spacing: 8, runSpacing: 6, children: [
              _Fact(icon: Icons.route_outlined, text: context.t('companies.count', {'routes': op.routes, 'ships': op.vessels.length})),
              _Fact(icon: Icons.child_care_rounded, text: context.t('companies.ages', {'infant': op.infantMaxAge + 1, 'from': op.infantMaxAge + 1, 'child': op.childMaxAge})),
              if (op.seniorMinAge != null) _Fact(icon: Icons.elderly_rounded, text: context.t('companies.seniors', {'n': op.seniorMinAge!})),
              _Fact(icon: Icons.luggage_outlined, text: context.t('companies.baggage', {'seat': op.baggageSeatKg, 'cabin': op.baggageCabinKg})),
              if (op.publishedUntil != null)
                _Fact(icon: Icons.event_available_outlined, text: context.t('companies.published', {'date': Fmt.dayLong(DateTime.parse(op.publishedUntil!), lang)})),
            ]),
            if (op.tariffs.isNotEmpty) ...[
              const SizedBox(height: 10),
              Theme(
                data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                child: ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  childrenPadding: const EdgeInsets.only(bottom: 8),
                  title: Text(context.t('companies.fares'), style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14.5)),
                  children: [
                    for (final t in op.tariffs)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Row(children: [
                            Text(t.name.of(lang), style: const TextStyle(fontWeight: FontWeight.w600)),
                            const SizedBox(width: 8),
                            if (t.refundable) const Icon(Icons.currency_exchange_rounded, size: 15, color: WaveColors.success),
                            if (t.modifiable) const Padding(padding: EdgeInsets.only(left: 4), child: Icon(Icons.edit_calendar_outlined, size: 15, color: WaveColors.blue)),
                          ]),
                          Text(t.conditions.of(lang), style: muted),
                        ]),
                      ),
                  ],
                ),
              ),
            ],
            if (op.vessels.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(context.t('companies.fleet'), style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14.5)),
              const SizedBox(height: 6),
              Wrap(spacing: 6, runSpacing: 6, children: [
                for (final code in op.vessels)
                  ActionChip(
                    avatar: const Icon(Icons.directions_boat_outlined, size: 16),
                    label: Text(meta.vessel(code)?.name ?? code, style: const TextStyle(fontSize: 12.5)),
                    onPressed: () => context.go('/live?vessel=$code'),
                  ),
              ]),
            ],
            const SizedBox(height: 10),
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: TextButton.icon(
                onPressed: () => launchUrl(Uri.parse(op.website), mode: LaunchMode.externalApplication),
                icon: const Icon(Icons.open_in_new_rounded, size: 18),
                label: Text(context.t('common.website')),
              ),
            ),
          ]),
        ),
      ]),
    );
  }
}

class _Fact extends StatelessWidget {
  const _Fact({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
        decoration: BoxDecoration(color: WaveColors.background, borderRadius: BorderRadius.circular(8)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, size: 15, color: WaveColors.navy),
          const SizedBox(width: 6),
          Flexible(child: Text(text, style: const TextStyle(fontSize: 12.5))),
        ]),
      );
}
