import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/api_client.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../../widgets/states.dart';
import '../home/home_page.dart' show RouteTitle;
import '../search/search_card.dart' show openRouteSearch, portLabel;

/// "Ma vague": saved bookings (kept in the device keystore), booking lookup and favourite routes.
class TripsPage extends ConsumerStatefulWidget {
  const TripsPage({super.key});

  @override
  ConsumerState<TripsPage> createState() => _TripsPageState();
}

class _TripsPageState extends ConsumerState<TripsPage> {
  final _reference = TextEditingController();
  final _name = TextEditingController();
  bool _searching = false;
  String? _error;

  @override
  void dispose() {
    _reference.dispose();
    _name.dispose();
    super.dispose();
  }

  Future<void> _find() async {
    final reference = _reference.text.trim().toUpperCase();
    final name = _name.text.trim();
    if (reference.isEmpty || name.isEmpty) {
      setState(() => _error = context.t('booking.required'));
      return;
    }
    setState(() {
      _searching = true;
      _error = null;
    });
    try {
      final booking = await ref.read(apiProvider).booking(reference, name);
      final meta = ref.read(metaProvider).value;
      final lang = ref.read(settingsProvider).lang;
      final leg = booking.legs.first;
      await ref.read(tripsProvider.notifier).save(SavedTrip(
            booking.reference,
            name,
            '${portLabel(meta, leg.from, lang)} → ${portLabel(meta, leg.to, lang)}',
            Fmt.iso(leg.departure.local),
          ));
      if (mounted) context.go('/booking/${booking.reference}');
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.isOffline ? context.t('common.offline') : e.message);
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final trips = ref.watch(tripsProvider);
    final favorites = ref.watch(favoritesProvider);
    final meta = ref.watch(metaProvider).value;
    final lang = context.lang;
    final wide = MediaQuery.sizeOf(context).width >= 900;

    final tripList = Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      SectionTitle(context.t('trips.upcoming'), size: 22),
      const SizedBox(height: 12),
      trips.when(
        loading: () => const SizedBox(height: 120, child: LoadingView()),
        error: (e, _) => ErrorView(error: e),
        data: (list) => list.isEmpty
            ? Card(
                child: Padding(
                  padding: const EdgeInsets.all(22),
                  child: Column(children: [
                    const Icon(Icons.waves_rounded, size: 40, color: WaveColors.muted),
                    const SizedBox(height: 8),
                    Text(context.t('trips.empty'), textAlign: TextAlign.center),
                    const SizedBox(height: 12),
                    GoldButton(label: context.t('booking.newSearch'), height: 44, fontSize: 14, onPressed: () => context.go('/')),
                  ]),
                ),
              )
            : Column(children: [
                for (final t in list)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Card(
                      child: ListTile(
                        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                        leading: const CircleAvatar(backgroundColor: WaveColors.sky, child: Icon(Icons.confirmation_number_outlined, color: WaveColors.navy)),
                        title: Text(t.title, style: const TextStyle(fontWeight: FontWeight.w600)),
                        subtitle: Text('${Fmt.dayLong(DateTime.parse(t.date), lang)} · ${t.reference}'),
                        onTap: () => context.go('/booking/${t.reference}'),
                        trailing: IconButton(
                          tooltip: context.t('trips.remove'),
                          icon: const Icon(Icons.delete_outline_rounded),
                          onPressed: () => ref.read(tripsProvider.notifier).remove(t.reference),
                        ),
                      ),
                    ),
                  ),
              ]),
      ),
    ]);

    final finder = Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text(context.t('trips.find'), style: const TextStyle(fontSize: 16.5, fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          TextField(
            controller: _reference,
            textCapitalization: TextCapitalization.characters,
            decoration: InputDecoration(labelText: context.t('trips.reference')),
          ),
          const SizedBox(height: 10),
          TextField(
            controller: _name,
            textCapitalization: TextCapitalization.characters,
            decoration: InputDecoration(labelText: context.t('booking.lastName')),
            onSubmitted: (_) => _find(),
          ),
          if (_error != null) ...[const SizedBox(height: 10), NoticeBox(message: _error!, severity: 'ERROR')],
          const SizedBox(height: 14),
          GoldButton(label: context.t('trips.open'), icon: Icons.search_rounded, height: 46, fontSize: 15, expand: true, loading: _searching, onPressed: _find),
        ]),
      ),
    );

    final favoriteList = Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      SectionTitle(context.t('trips.favorites'), size: 22),
      const SizedBox(height: 12),
      if (favorites.isEmpty)
        Text(context.t('trips.noFavorites'), style: const TextStyle(color: WaveColors.muted))
      else
        for (final key in favorites)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                child: Row(children: [
                  Expanded(child: RouteTitle(from: portLabel(meta, key.split('-').first, lang), to: portLabel(meta, key.split('-').last, lang))),
                  IconButton(
                    tooltip: context.t('trips.search'),
                    icon: const Icon(Icons.search_rounded, color: WaveColors.navy),
                    onPressed: () => openRouteSearch(context, ref, key.split('-').first, key.split('-').last),
                  ),
                  IconButton(
                    tooltip: context.t('trips.remove'),
                    icon: const Icon(Icons.favorite_rounded, color: WaveColors.danger),
                    onPressed: () => ref.read(favoritesProvider.notifier).toggle(key),
                  ),
                ]),
              ),
            ),
          ),
    ]);

    return WavePage(
      title: context.t('trips.title'),
      subtitle: context.t('trips.subtitle'),
      child: wide
          ? Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(flex: 3, child: Column(children: [tripList, const SizedBox(height: 26), favoriteList])),
              const SizedBox(width: 24),
              Expanded(flex: 2, child: finder),
            ])
          : Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [finder, const SizedBox(height: 24), tripList, const SizedBox(height: 24), favoriteList]),
    );
  }
}
