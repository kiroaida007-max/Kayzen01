import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/providers.dart';
import '../../widgets/header.dart';
import '../../widgets/states.dart';

IconData guideIcon(String name) => switch (name) {
      'passport' => Icons.badge_outlined,
      'child' => Icons.child_care_rounded,
      'car' => Icons.directions_car_outlined,
      'pets' => Icons.pets_rounded,
      'schedule' => Icons.schedule_rounded,
      'luggage' => Icons.luggage_outlined,
      'policy' => Icons.policy_outlined,
      'gavel' => Icons.gavel_rounded,
      'payments' => Icons.payments_outlined,
      'accessible' => Icons.accessible_rounded,
      _ => Icons.menu_book_outlined,
    };

class GuidesPage extends ConsumerWidget {
  const GuidesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final guides = ref.watch(guidesProvider);
    final lang = context.lang;
    return WavePage(
      title: context.t('guides.title'),
      subtitle: context.t('guides.subtitle'),
      child: guides.when(
        loading: () => const SizedBox(height: 300, child: LoadingView()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(guidesProvider)),
        data: (list) => LayoutBuilder(builder: (context, c) {
          final columns = c.maxWidth >= 1000 ? 3 : (c.maxWidth >= 640 ? 2 : 1);
          final width = (c.maxWidth - (columns - 1) * 16) / columns;
          return Wrap(spacing: 16, runSpacing: 16, children: [
            for (final g in list)
              SizedBox(
                width: width,
                child: Card(
                  clipBehavior: Clip.antiAlias,
                  child: InkWell(
                    onTap: () => context.go('/guides/${g.id}'),
                    child: Padding(
                      padding: const EdgeInsets.all(18),
                      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(gradient: WaveColors.goldGradient, borderRadius: BorderRadius.circular(12)),
                          child: Icon(guideIcon(g.icon), color: WaveColors.navyDeep),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(g.title.of(lang), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                            const SizedBox(height: 6),
                            Text(g.summary.of(lang), maxLines: 3, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, color: WaveColors.muted, height: 1.4)),
                            const SizedBox(height: 8),
                            Text(context.t('guides.read'), style: const TextStyle(color: WaveColors.blue, fontWeight: FontWeight.w600, fontSize: 13)),
                          ]),
                        ),
                      ]),
                    ),
                  ),
                ),
              ),
          ]);
        }),
      ),
    );
  }
}

class GuideDetailPage extends ConsumerWidget {
  const GuideDetailPage({super.key, required this.id});
  final String id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final guides = ref.watch(guidesProvider);
    final lang = context.lang;
    final guide = guides.value?.where((g) => g.id == id).firstOrNull;
    return WavePage(
      title: guide?.title.of(lang) ?? context.t('guides.title'),
      subtitle: guide?.summary.of(lang),
      maxWidth: 860,
      child: guides.when(
        loading: () => const SizedBox(height: 300, child: LoadingView()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(guidesProvider)),
        data: (_) => guide == null
            ? Center(child: TextButton(onPressed: () => context.go('/guides'), child: Text(context.t('guides.title'))))
            : Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: TextButton.icon(
                    onPressed: () => context.go('/guides'),
                    icon: Icon(Directionality.of(context) == TextDirection.rtl ? Icons.arrow_forward_rounded : Icons.arrow_back_rounded, size: 18),
                    label: Text(context.t('guides.title')),
                  ),
                ),
                const SizedBox(height: 8),
                for (final s in guide.sections) ...[_SectionCard(section: s), const SizedBox(height: 14)],
              ]),
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.section});
  final GuideSection section;

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(section.title.of(lang), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
          const SizedBox(height: 10),
          SelectableText(section.body.of(lang), style: const TextStyle(fontSize: 14.5, height: 1.6)),
          if (section.source != null) ...[
            const SizedBox(height: 10),
            InkWell(
              onTap: () => launchUrl(Uri.parse(section.source!), mode: LaunchMode.externalApplication),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                const Icon(Icons.link_rounded, size: 16, color: WaveColors.blue),
                const SizedBox(width: 4),
                Flexible(
                  child: Text('${context.t('common.source')} : ${Uri.parse(section.source!).host}',
                      style: const TextStyle(color: WaveColors.blue, fontSize: 12.5)),
                ),
              ]),
            ),
          ],
        ]),
      ),
    );
  }
}
