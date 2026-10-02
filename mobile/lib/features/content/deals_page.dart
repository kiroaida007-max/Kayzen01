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
import 'companies_page.dart' show companyImages;

/// "Bons plans": the operators' current promotions. The search applies them automatically when eligible.
class DealsPage extends ConsumerWidget {
  const DealsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final deals = ref.watch(dealsProvider);
    return WavePage(
      title: context.t('deals.title'),
      subtitle: context.t('deals.subtitle'),
      child: deals.when(
        loading: () => const SizedBox(height: 300, child: LoadingView()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(dealsProvider)),
        data: (list) => LayoutBuilder(builder: (context, c) {
          final columns = c.maxWidth >= 1000 ? 3 : (c.maxWidth >= 640 ? 2 : 1);
          final width = (c.maxWidth - (columns - 1) * 16) / columns;
          return Wrap(spacing: 16, runSpacing: 16, children: [for (final d in list) SizedBox(width: width, child: _DealCard(deal: d))]);
        }),
      ),
    );
  }
}

class _DealCard extends ConsumerWidget {
  const _DealCard({required this.deal});
  final Deal deal;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = context.lang;
    final d = deal;
    final image = companyImages[d.operator];
    final color = d.operator == null ? null : ref.watch(metaProvider).value?.operator(d.operator!)?.color;
    final badge = d.badge ?? (d.discount > 0 ? '-${(d.discount * 100).round()} %' : null);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Stack(children: [
          image != null
              ? Image.asset('assets/images/$image.jpg', height: 120, width: double.infinity, fit: BoxFit.cover)
              : color != null
                  ? ShipArt(color: hexColor(color), height: 120)
                  : Image.asset('assets/images/cabin.jpg', height: 120, width: double.infinity, fit: BoxFit.cover),
          if (badge != null)
            Positioned(
              left: 12,
              top: 12,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(gradient: WaveColors.buttonGradient, borderRadius: BorderRadius.circular(8)),
                child: Text(badge, style: const TextStyle(fontWeight: FontWeight.w800, color: WaveColors.navyDeep)),
              ),
            ),
        ]),
        Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (d.operator != null) OperatorLogo(d.operator!, size: 15),
            const SizedBox(height: 8),
            Text(d.title.of(lang), style: const TextStyle(fontSize: 16.5, fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            Text(d.description.of(lang), style: const TextStyle(fontSize: 13, height: 1.45)),
            const SizedBox(height: 10),
            Wrap(spacing: 10, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
              if (d.bookTo != null)
                Row(mainAxisSize: MainAxisSize.min, children: [
                  const Icon(Icons.event_outlined, size: 15, color: WaveColors.muted),
                  const SizedBox(width: 4),
                  Text(context.t('deals.until', {'date': Fmt.dayLong(DateTime.parse(d.bookTo!), lang)}), style: const TextStyle(fontSize: 12, color: WaveColors.muted)),
                ]),
              if (d.estimated) Text(context.t('deals.estimated'), style: const TextStyle(fontSize: 12, color: WaveColors.warning)),
            ]),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(child: GoldButton(label: context.t('deals.book'), height: 42, fontSize: 14, onPressed: () => context.go('/'))),
              if (d.source != null) ...[
                const SizedBox(width: 8),
                IconButton(
                  tooltip: context.t('common.source'),
                  onPressed: () => launchUrl(Uri.parse(d.source!), mode: LaunchMode.externalApplication),
                  icon: const Icon(Icons.open_in_new_rounded, color: WaveColors.muted),
                ),
              ],
            ]),
          ]),
        ),
      ]),
    );
  }
}
