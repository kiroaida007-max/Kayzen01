import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../../widgets/states.dart';
import '../results/results_page.dart' show cabinLabel;
import 'companies_page.dart' show companyImages;

String amenityLabel(String a, AppLang lang) => switch (a) {
      'RESTAURANT' => const ['Restaurant', 'Restaurant', 'مطعم'],
      'CAFETERIA' => const ['Cafétéria', 'Cafeteria', 'كافيتيريا'],
      'SHOP' => const ['Boutique', 'Shop', 'متجر'],
      'PRAYER_ROOM' => const ['Salle de prière', 'Prayer room', 'مصلى'],
      'WIFI' => const ['Wi-Fi', 'Wi-Fi', 'واي فاي'],
      'KIDS_AREA' => const ['Espace enfants', 'Kids area', 'فضاء الأطفال'],
      'PMR_ACCESS' => const ['Accès PMR', 'Step-free access', 'ولوج ذوي الاحتياجات'],
      'INFIRMARY' => const ['Infirmerie', 'Infirmary', 'عيادة'],
      'PET_AREA' => const ['Espace animaux', 'Pet area', 'فضاء الحيوانات'],
      'CINEMA' => const ['Cinéma', 'Cinema', 'سينما'],
      'POOL' => const ['Piscine', 'Pool', 'مسبح'],
      'HALAL_FOOD' => const ['Repas halal', 'Halal food', 'طعام حلال'],
      'LOUNGE' => const ['Salon', 'Lounge', 'صالون'],
      'ELEVATOR' => const ['Ascenseurs', 'Lifts', 'مصاعد'],
      'CUSTOMS_ON_BOARD' => const ['Police & douane à bord', 'Border control on board', 'الشرطة والجمارك على متن السفينة'],
      _ => [a, a, a],
    }[lang.index];

IconData amenityIcon(String a) => switch (a) {
      'RESTAURANT' => Icons.restaurant_rounded,
      'CAFETERIA' => Icons.local_cafe_outlined,
      'SHOP' => Icons.shopping_bag_outlined,
      'PRAYER_ROOM' => Icons.mosque_outlined,
      'WIFI' => Icons.wifi_rounded,
      'KIDS_AREA' => Icons.toys_outlined,
      'PMR_ACCESS' => Icons.accessible_rounded,
      'INFIRMARY' => Icons.medical_services_outlined,
      'PET_AREA' => Icons.pets_rounded,
      'CINEMA' => Icons.movie_outlined,
      'POOL' => Icons.pool_rounded,
      'HALAL_FOOD' => Icons.lunch_dining_outlined,
      'LOUNGE' => Icons.weekend_outlined,
      'ELEVATOR' => Icons.elevator_outlined,
      'CUSTOMS_ON_BOARD' => Icons.badge_outlined,
      _ => Icons.check_circle_outline,
    };

class ShipsPage extends ConsumerWidget {
  const ShipsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final meta = ref.watch(metaProvider);
    return WavePage(
      title: context.t('ships.title'),
      subtitle: context.t('ships.subtitle'),
      child: meta.when(
        loading: () => const SizedBox(height: 300, child: LoadingView()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(metaProvider)),
        data: (m) {
          final active = {for (final o in m.operators) o.code: o.active};
          final ships = m.vessels.where((v) => active[v.operator] ?? false).toList()..sort((a, b) => (b.built ?? 0).compareTo(a.built ?? 0));
          return LayoutBuilder(builder: (context, c) {
            final columns = c.maxWidth >= 1080 ? 3 : (c.maxWidth >= 680 ? 2 : 1);
            final width = (c.maxWidth - (columns - 1) * 16) / columns;
            return Wrap(spacing: 16, runSpacing: 16, children: [for (final v in ships) SizedBox(width: width, child: _ShipCard(vessel: v, operatorColor: m.operator(v.operator)?.color))]);
          });
        },
      ),
    );
  }
}

class _ShipCard extends StatelessWidget {
  const _ShipCard({required this.vessel, this.operatorColor});
  final Vessel vessel;
  final String? operatorColor;

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    final v = vessel;
    final image = companyImages[v.operator];
    final cabins = v.accommodations.where((a) => a != 'SEAT').toList();
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Stack(children: [
          image == null
              ? ShipArt(color: hexColor(operatorColor ?? '#0A2A5E'))
              : Image.asset('assets/images/$image.jpg', height: 130, width: double.infinity, fit: BoxFit.cover),
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Colors.transparent, WaveColors.navyDeep.withValues(alpha: 0.85)],
                  stops: const [0.35, 1.0],
                ),
              ),
            ),
          ),
          Positioned(
            left: 14,
            right: 14,
            bottom: 10,
            child: Text(v.name, style: const TextStyle(color: Colors.white, fontSize: 19, fontWeight: FontWeight.w700)),
          ),
          Positioned(
            right: 10,
            top: 10,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.92), borderRadius: BorderRadius.circular(6)),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                Icon(v.verified ? Icons.verified_rounded : Icons.info_outline_rounded, size: 14, color: v.verified ? WaveColors.success : WaveColors.muted),
                const SizedBox(width: 4),
                Text(context.t(v.verified ? 'ships.verified' : 'ships.indicative'), style: const TextStyle(fontSize: 11)),
              ]),
            ),
          ),
        ]),
        Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              OperatorLogo(v.operator, size: 15),
              const Spacer(),
              if (v.built != null) Text(context.t('ships.built', {'year': v.built!}), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
            ]),
            const SizedBox(height: 10),
            Text(context.t('ships.capacity', {'pax': v.passengers, 'veh': v.vehicles}), style: const TextStyle(fontWeight: FontWeight.w600)),
            if (v.lengthM != null)
              Text(context.t('ships.length', {'m': v.lengthM!.round(), 'kn': v.speedKn.round()}), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
            const SizedBox(height: 10),
            Wrap(spacing: 6, runSpacing: 6, children: [
              for (final c in cabins)
                Chip(
                  visualDensity: VisualDensity.compact,
                  avatar: const Icon(Icons.bed_outlined, size: 15),
                  label: Text(cabinLabel(context, c), style: const TextStyle(fontSize: 11.5)),
                ),
              if (v.petFriendly)
                Chip(visualDensity: VisualDensity.compact, avatar: const Icon(Icons.pets_rounded, size: 15), label: Text(context.t('ships.pets'), style: const TextStyle(fontSize: 11.5))),
            ]),
            const SizedBox(height: 8),
            Wrap(spacing: 10, runSpacing: 6, children: [
              for (final a in v.amenities)
                Tooltip(
                  message: amenityLabel(a, lang),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    Icon(amenityIcon(a), size: 16, color: WaveColors.navy),
                    const SizedBox(width: 3),
                    Text(amenityLabel(a, lang), style: const TextStyle(fontSize: 11.5, color: WaveColors.muted)),
                  ]),
                ),
            ]),
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: () => context.go('/live?vessel=${v.code}'),
              icon: const Icon(Icons.map_outlined, size: 18),
              label: Text(context.t('ships.onMap')),
            ),
          ]),
        ),
      ]),
    );
  }
}
