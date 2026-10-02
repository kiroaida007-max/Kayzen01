import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/config.dart';
import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../search/search_card.dart' show portLabel;

/// Bundled coastline (Natural Earth, public domain) drawn under the tiles: the map stays readable
/// offline, on flaky port Wi-Fi, or if the tile server is down.
final coastlineProvider = FutureProvider<List<List<LatLng>>>((ref) async {
  final raw = jsonDecode(await rootBundle.loadString('assets/geo/med_land.json')) as Map<String, dynamic>;
  return [
    for (final polygon in raw['polygons'] as List<dynamic>)
      [for (final p in polygon as List<dynamic>) LatLng(((p as List<dynamic>)[0] as num).toDouble(), (p[1] as num).toDouble())],
  ];
});

class LiveMapPage extends ConsumerStatefulWidget {
  const LiveMapPage({super.key, this.focusVessel});
  final String? focusVessel;

  @override
  ConsumerState<LiveMapPage> createState() => _LiveMapPageState();
}

class _LiveMapPageState extends ConsumerState<LiveMapPage> {
  final _map = MapController();
  String? _selected;
  bool _focused = false;
  String _filter = '';

  @override
  void initState() {
    super.initState();
    _selected = widget.focusVessel;
  }

  void _select(VesselPosition v, {bool move = true}) {
    setState(() => _selected = v.vessel);
    if (move) _map.move(LatLng(v.lat, v.lon), math.max(_map.camera.zoom, 6));
  }

  @override
  Widget build(BuildContext context) {
    final positions = ref.watch(livePositionsProvider);
    final meta = ref.watch(metaProvider).value;
    final desktop = Breakpoints.isDesktop(context);
    final vessels = positions.value ?? const <VesselPosition>[];

    if (!_focused && widget.focusVessel != null) {
      final target = vessels.where((v) => v.vessel == widget.focusVessel).firstOrNull;
      if (target != null) {
        _focused = true;
        WidgetsBinding.instance.addPostFrameCallback((_) => _map.move(LatLng(target.lat, target.lon), 6.5));
      }
    }

    final selected = vessels.where((v) => v.vessel == _selected).firstOrNull;
    final map = _LiveMap(
      controller: _map,
      meta: meta,
      vessels: vessels,
      selected: selected,
      onSelect: (v) => _select(v, move: false),
      onClear: () => setState(() => _selected = null),
    );

    final list = _VesselList(
      vessels: vessels,
      meta: meta,
      selected: _selected,
      filter: _filter,
      loading: positions.isLoading && vessels.isEmpty,
      onFilter: (f) => setState(() => _filter = f),
      onSelect: _select,
    );

    return Column(children: [
      const WaveHeader(),
      Expanded(
        child: desktop
            ? Row(children: [
                SizedBox(width: 360, child: Material(color: Colors.white, elevation: 2, child: list)),
                Expanded(
                  child: Stack(children: [
                    map,
                    Positioned(left: 16, top: 16, child: _Legend(vessels: vessels)),
                    if (selected != null) Positioned(right: 16, top: 16, width: 360, child: _VesselCard(vessel: selected, meta: meta, onClose: () => setState(() => _selected = null))),
                  ]),
                ),
              ])
            : Stack(children: [
                map,
                Positioned(left: 12, top: 12, right: 12, child: _Legend(vessels: vessels)),
                if (selected != null)
                  Positioned(left: 12, right: 12, bottom: 12, child: _VesselCard(vessel: selected, meta: meta, onClose: () => setState(() => _selected = null)))
                else
                  DraggableScrollableSheet(
                    initialChildSize: 0.28,
                    minChildSize: 0.12,
                    maxChildSize: 0.85,
                    builder: (context, scroll) => Material(
                      color: Colors.white,
                      elevation: 8,
                      borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
                      child: _VesselList(
                        vessels: vessels,
                        meta: meta,
                        selected: _selected,
                        filter: _filter,
                        loading: positions.isLoading && vessels.isEmpty,
                        onFilter: (f) => setState(() => _filter = f),
                        onSelect: _select,
                        scrollController: scroll,
                        handle: true,
                      ),
                    ),
                  ),
              ]),
      ),
    ]);
  }
}

class _LiveMap extends ConsumerWidget {
  const _LiveMap({required this.controller, required this.meta, required this.vessels, required this.selected, required this.onSelect, required this.onClear});
  final MapController controller;
  final Meta? meta;
  final List<VesselPosition> vessels;
  final VesselPosition? selected;
  final ValueChanged<VesselPosition> onSelect;
  final VoidCallback onClear;

  /// Ships berthed in the same port share coordinates: fan them out so every marker stays tappable.
  Map<String, LatLng> _spread(List<VesselPosition> list) {
    final groups = <String, List<VesselPosition>>{};
    for (final v in list) {
      groups.putIfAbsent('${v.lat.toStringAsFixed(3)},${v.lon.toStringAsFixed(3)}', () => []).add(v);
    }
    final out = <String, LatLng>{};
    for (final group in groups.values) {
      if (group.length == 1) {
        out[group.first.vessel] = LatLng(group.first.lat, group.first.lon);
        continue;
      }
      for (final (i, v) in group.indexed) {
        final angle = 2 * math.pi * i / group.length - math.pi / 2;
        out[v.vessel] = LatLng(v.lat + 0.09 * math.sin(angle), v.lon + 0.12 * math.cos(angle));
      }
    }
    return out;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = context.lang;
    final coastline = ref.watch(coastlineProvider).value ?? const <List<LatLng>>[];
    final routes = meta?.routes ?? const <RouteSummary>[];
    final operatorColors = {for (final o in meta?.operators ?? const <Operator>[]) o.code: hexColor(o.color)};
    final points = _spread(vessels);
    bool highlighted(RouteSummary r) =>
        selected != null && ((r.from == selected!.from && r.to == selected!.to) || (r.from == selected!.to && r.to == selected!.from)) && r.operator == selected!.operator;

    return FlutterMap(
      mapController: controller,
      options: MapOptions(
        // Frame every route on any screen size, from phones to 4K monitors.
        initialCameraFit: CameraFit.bounds(
          bounds: LatLngBounds(const LatLng(35.0, -3.5), const LatLng(43.6, 12.3)),
          padding: const EdgeInsets.all(28),
        ),
        minZoom: 4,
        maxZoom: 13,
        // Keep the view on the western Mediterranean (and the bundled coastline's edges away).
        cameraConstraint: CameraConstraint.containCenter(bounds: LatLngBounds(const LatLng(28, -12), const LatLng(48, 24))),
        backgroundColor: const Color(0xFFAAD3DF),
        onTap: (_, _) => onClear(),
        interactionOptions: const InteractionOptions(flags: InteractiveFlag.all & ~InteractiveFlag.rotate),
      ),
      children: [
        PolygonLayer(polygons: [
          for (final land in coastline)
            Polygon(points: land, color: const Color(0xFFF2EFE6), borderColor: const Color(0xFFB9C7D3), borderStrokeWidth: 1),
        ]),
        // Failed tiles are retried when they scroll back into view; the coastline above covers the gap.
        TileLayer(
          urlTemplate: AppConfig.tileUrl,
          userAgentPackageName: 'dz.wave.app',
          maxNativeZoom: 18,
          evictErrorTileStrategy: EvictErrorTileStrategy.notVisibleRespectMargin,
          errorTileCallback: (_, _, _) {},
        ),
        TileLayer(
          urlTemplate: AppConfig.seamarkTileUrl,
          userAgentPackageName: 'dz.wave.app',
          maxNativeZoom: 18,
          evictErrorTileStrategy: EvictErrorTileStrategy.notVisibleRespectMargin,
          errorTileCallback: (_, _, _) {},
        ),
        PolylineLayer(polylines: [
          for (final r in routes.where((r) => !highlighted(r)))
            Polyline(
              points: [for (final p in r.polyline) LatLng(p[0], p[1])],
              color: (operatorColors[r.operator] ?? WaveColors.navy).withValues(alpha: 0.45),
              strokeWidth: 2,
              pattern: StrokePattern.dashed(segments: const [8, 6]),
            ),
          for (final r in routes.where(highlighted))
            Polyline(points: [for (final p in r.polyline) LatLng(p[0], p[1])], color: operatorColors[r.operator] ?? WaveColors.navy, strokeWidth: 4.5),
        ]),
        MarkerLayer(markers: [
          for (final port in meta?.ports ?? const <Port>[])
            Marker(
              point: LatLng(port.lat, port.lon),
              width: 120,
              height: 34,
              child: IgnorePointer(
                child: Column(children: [
                  Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(color: Colors.white, shape: BoxShape.circle, border: Border.all(color: WaveColors.navy, width: 2.5)),
                  ),
                  const SizedBox(height: 2),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.85), borderRadius: BorderRadius.circular(4)),
                    child: Text(port.name.of(lang), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: WaveColors.navyInk)),
                  ),
                ]),
              ),
            ),
          for (final v in vessels)
            Marker(
              point: points[v.vessel]!,
              width: v.vessel == selected?.vessel ? 150 : 44,
              height: v.vessel == selected?.vessel ? 74 : 44,
              child: _ShipMarker(vessel: v, selected: v.vessel == selected?.vessel, onTap: () => onSelect(v)),
            ),
        ]),
        RichAttributionWidget(
          alignment: AttributionAlignment.bottomLeft,
          attributions: [
            TextSourceAttribution('OpenStreetMap', onTap: () => launchUrl(Uri.parse('https://www.openstreetmap.org/copyright'))),
            TextSourceAttribution('OpenSeaMap', onTap: () => launchUrl(Uri.parse('https://www.openseamap.org'))),
          ],
        ),
      ],
    );
  }
}

class _ShipMarker extends StatelessWidget {
  const _ShipMarker({required this.vessel, required this.selected, required this.onTap});
  final VesselPosition vessel;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = hexColor(vessel.operatorColor);
    final size = selected ? 40.0 : 32.0;
    final icon = Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(color: selected ? WaveColors.gold : Colors.white, width: selected ? 3 : 2),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.25), blurRadius: 6, offset: const Offset(0, 2))],
      ),
      child: vessel.underway
          ? Transform.rotate(angle: vessel.courseDeg * math.pi / 180, child: Icon(Icons.navigation_rounded, color: Colors.white, size: size * 0.6))
          : Icon(Icons.anchor_rounded, color: Colors.white, size: size * 0.5),
    );
    return GestureDetector(
      onTap: onTap,
      child: Tooltip(
        message: vessel.name,
        child: selected
            ? Column(mainAxisSize: MainAxisSize.min, children: [
                icon,
                const SizedBox(height: 3),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: WaveColors.navyDeep, borderRadius: BorderRadius.circular(6)),
                  child: Text(vessel.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600)),
                ),
              ])
            : Center(child: icon),
      ),
    );
  }
}

class _Legend extends StatelessWidget {
  const _Legend({required this.vessels});
  final List<VesselPosition> vessels;

  @override
  Widget build(BuildContext context) {
    final atSea = vessels.where((v) => v.underway).length;
    final ais = vessels.where((v) => v.isAis).length;
    return Align(
      alignment: AlignmentDirectional.topStart,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.95),
          borderRadius: BorderRadius.circular(10),
          boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.12), blurRadius: 10)],
        ),
        child: Wrap(spacing: 14, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
          Row(mainAxisSize: MainAxisSize.min, children: [
            const _Pulse(),
            const SizedBox(width: 6),
            Text(context.t('live.ships', {'n': vessels.length}), style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
          ]),
          Text('${context.t('live.underway')} : $atSea', style: const TextStyle(fontSize: 12.5)),
          Text(ais > 0 ? '${context.t('live.ais')} : $ais' : context.t('live.estimated'), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
        ]),
      ),
    );
  }
}

class _Pulse extends StatefulWidget {
  const _Pulse();

  @override
  State<_Pulse> createState() => _PulseState();
}

class _PulseState extends State<_Pulse> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1400))..repeat();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: _c,
        builder: (_, _) => Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(
            color: WaveColors.success,
            shape: BoxShape.circle,
            boxShadow: [BoxShadow(color: WaveColors.success.withValues(alpha: 1 - _c.value), blurRadius: 0, spreadRadius: 6 * _c.value)],
          ),
        ),
      );
}

class _VesselList extends StatelessWidget {
  const _VesselList({required this.vessels, required this.meta, required this.selected, required this.filter, required this.loading, required this.onFilter, required this.onSelect, this.scrollController, this.handle = false});
  final List<VesselPosition> vessels;
  final Meta? meta;
  final String? selected;
  final String filter;
  final bool loading;
  final ValueChanged<String> onFilter;
  final ValueChanged<VesselPosition> onSelect;
  final ScrollController? scrollController;
  final bool handle;

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    final query = filter.trim().toLowerCase();
    final shown = vessels
        .where((v) =>
            query.isEmpty ||
            v.name.toLowerCase().contains(query) ||
            portLabel(meta, v.from, lang).toLowerCase().contains(query) ||
            portLabel(meta, v.to, lang).toLowerCase().contains(query) ||
            (meta?.operator(v.operator)?.name.toLowerCase().contains(query) ?? false))
        .toList()
      ..sort((a, b) => a.underway == b.underway ? a.name.compareTo(b.name) : (a.underway ? -1 : 1));
    return ListView(
      controller: scrollController,
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 24),
      children: [
        if (handle)
          Center(
            child: Container(width: 40, height: 4, margin: const EdgeInsets.only(bottom: 10), decoration: BoxDecoration(color: WaveColors.line, borderRadius: BorderRadius.circular(2))),
          ),
        Text(context.t('live.title'), style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        Text(context.t('live.estimated'), style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
        const SizedBox(height: 12),
        TextField(
          onChanged: onFilter,
          decoration: InputDecoration(
            isDense: true,
            prefixIcon: const Icon(Icons.search_rounded),
            hintText: const ['Navire, port ou compagnie', 'Ship, port or company', 'سفينة أو ميناء أو شركة'][lang.index],
          ),
        ),
        const SizedBox(height: 10),
        if (loading) const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())),
        for (final v in shown)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Material(
              color: v.vessel == selected ? WaveColors.sky : Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
                side: BorderSide(color: v.vessel == selected ? WaveColors.blue : WaveColors.line),
              ),
              child: InkWell(
                borderRadius: BorderRadius.circular(12),
                onTap: () => onSelect(v),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Icon(v.underway ? Icons.navigation_rounded : Icons.anchor_rounded, size: 18, color: hexColor(v.operatorColor)),
                      const SizedBox(width: 6),
                      Expanded(child: Text(v.name, style: const TextStyle(fontWeight: FontWeight.w700))),
                      _StatusChip(underway: v.underway),
                    ]),
                    const SizedBox(height: 6),
                    Row(children: [
                      OperatorLogo(v.operator, size: 13),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          v.underway
                              ? '${portLabel(meta, v.from, lang)} → ${portLabel(meta, v.to, lang)}'
                              : '${portLabel(meta, v.from, lang)}${v.to != null ? ' → ${portLabel(meta, v.to, lang)}' : ''}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 12.5, color: WaveColors.muted),
                        ),
                      ),
                    ]),
                    if (v.underway && v.progress != null) ...[
                      const SizedBox(height: 8),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(value: v.progress, minHeight: 5, color: hexColor(v.operatorColor), backgroundColor: WaveColors.line),
                      ),
                    ],
                  ]),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.underway});
  final bool underway;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: (underway ? WaveColors.success : WaveColors.muted).withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(context.t(underway ? 'live.underway' : 'live.inPort'),
            style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: underway ? WaveColors.success : WaveColors.muted)),
      );
}

class _VesselCard extends ConsumerWidget {
  const _VesselCard({required this.vessel, required this.meta, required this.onClose});
  final VesselPosition vessel;
  final Meta? meta;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = context.lang;
    final v = vessel;
    final info = meta?.vessel(v.vessel);
    final muted = const TextStyle(fontSize: 12.5, color: WaveColors.muted);
    String when(PortTime t) => '${Fmt.dayShort(t.local, lang)} ${Fmt.time(t.local)} (${t.utcLabel})';
    return Material(
      elevation: 8,
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Expanded(child: Text(v.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800))),
            IconButton(onPressed: onClose, icon: const Icon(Icons.close_rounded), visualDensity: VisualDensity.compact),
          ]),
          Row(children: [OperatorLogo(v.operator, size: 15), const Spacer(), _StatusChip(underway: v.underway)]),
          const SizedBox(height: 12),
          if (v.underway) ...[
            Text('${portLabel(meta, v.from, lang)} → ${portLabel(meta, v.to, lang)}', style: const TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(value: v.progress ?? 0, minHeight: 6, color: hexColor(v.operatorColor), backgroundColor: WaveColors.line),
            ),
            const SizedBox(height: 8),
            if (v.departure != null) Text('${context.t('search.departure')} : ${when(v.departure!)}', style: muted),
            if (v.eta != null) Text('${context.t('live.eta')} : ${when(v.eta!)}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
            Text('${context.t('live.speed')} : ${v.speedKn.toStringAsFixed(1)} kn · ${v.courseDeg.round()}°', style: muted),
          ] else ...[
            Text('${context.t('live.inPort')} · ${portLabel(meta, v.from, lang)}', style: const TextStyle(fontWeight: FontWeight.w600)),
            if (v.departure != null && v.to != null) ...[
              const SizedBox(height: 6),
              Text('${context.t('live.next')} : ${when(v.departure!)} → ${portLabel(meta, v.to, lang)}', style: const TextStyle(fontSize: 13)),
            ],
          ],
          if (info != null) ...[
            const Divider(height: 22),
            Text(context.t('ships.capacity', {'pax': info.passengers, 'veh': info.vehicles}), style: muted),
            if (info.built != null) Text(context.t('ships.built', {'year': info.built!}), style: muted),
          ],
          const SizedBox(height: 8),
          Row(children: [
            Icon(v.isAis ? Icons.satellite_alt_rounded : Icons.schedule_rounded, size: 15, color: WaveColors.muted),
            const SizedBox(width: 6),
            Expanded(child: Text(context.t(v.isAis ? 'live.ais' : 'live.estimated'), style: muted)),
          ]),
          if (v.from != null && v.to != null) ...[
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: () {
                ref.read(searchQueryProvider.notifier).prefill(v.from!, v.to!);
                context.go('/');
              },
              icon: const Icon(Icons.search_rounded, size: 18),
              label: Text(context.t('trips.search')),
            ),
          ],
        ]),
      ),
    );
  }
}
