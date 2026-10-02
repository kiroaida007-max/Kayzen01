import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/config.dart';
import '../../core/formatters.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../data/models.dart';
import '../../state/providers.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';
import '../search/search_card.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mobile = Breakpoints.isMobile(context);
    final desktop = Breakpoints.isDesktop(context);
    final pad = mobile ? 16.0 : (desktop ? 96.0 : 32.0);
    final heroHeight = mobile ? 430.0 : (desktop ? 560.0 : 520.0);

    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFFE6F0FA), Color(0xFFF4F8FC), Color(0xFFEAF2FA)],
          stops: [0.3, 0.6, 1.0],
        ),
      ),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Stack(children: [
            Positioned(top: 0, left: 0, right: 0, height: heroHeight, child: const _HeroBackground()),
            Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const WaveHeader(transparent: true),
              Padding(
                padding: EdgeInsets.fromLTRB(pad, mobile ? 12 : 26, pad, 0),
                child: const _HeroText(),
              ),
              SizedBox(height: mobile ? 22 : 30),
              Padding(padding: EdgeInsets.symmetric(horizontal: mobile ? 12 : pad - 12), child: const SearchCard()),
            ]),
          ]),
          SizedBox(height: mobile ? 28 : 36),
          Padding(padding: EdgeInsets.symmetric(horizontal: pad - (mobile ? 0 : 12)), child: const _PopularDestinations()),
          SizedBox(height: mobile ? 28 : 34),
          Padding(padding: EdgeInsets.symmetric(horizontal: pad - (mobile ? 0 : 12)), child: const _Partners()),
          SizedBox(height: mobile ? 28 : 34),
          const _StatsBand(),
          const _Footer(),
        ]),
      ),
    );
  }
}

class _HeroBackground extends StatelessWidget {
  const _HeroBackground();

  /// hero_ship.jpg is 1672×594.
  static const _imageAspect = 1672 / 594;

  @override
  Widget build(BuildContext context) {
    final mobile = Breakpoints.isMobile(context);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return LayoutBuilder(builder: (context, constraints) {
      final imageWidth = mobile ? constraints.maxWidth * 1.9 : constraints.maxWidth * 0.68;
      final imageHeight = imageWidth / _imageAspect;
      // On the first web frame the viewport can be 0×0: 0/0 is NaN, and Skia rejects NaN stops.
      final ratio = constraints.maxHeight > 0 ? (imageHeight + (mobile ? 40 : 52)) / constraints.maxHeight : double.nan;
      final seaStop = ratio.isFinite ? ratio.clamp(0.3, 0.95) : 0.66;
      return Stack(fit: StackFit.expand, clipBehavior: Clip.hardEdge, children: [
        // Sky → horizon → sea (sampled from the photo's edge), then the page colour.
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: const [
                Color(0xFF4F73A3), Color(0xFF8FA2BD), Color(0xFFB9AEB6), Color(0xFF6A6070),
                Color(0xFF3E4A63), Color(0xFF1A416E), Color(0xFF2F6E9E), Color(0xFFE6F0FA),
              ],
              stops: [0.0, seaStop * 0.2, seaStop * 0.36, seaStop * 0.5, seaStop * 0.6, seaStop * 0.75, seaStop, 1.0],
            ),
          ),
        ),
        Positioned(
          // Below the navigation bar, as in the mockup; the sky gradient fills the strip above.
          top: mobile ? 40 : 52,
          right: rtl ? null : (mobile ? -imageWidth * 0.28 : 0),
          left: rtl ? (mobile ? -imageWidth * 0.28 : 0) : null,
          width: imageWidth,
          height: imageHeight,
          child: ShaderMask(
            blendMode: BlendMode.dstIn,
            shaderCallback: (rect) => LinearGradient(
              begin: rtl ? Alignment.centerRight : Alignment.centerLeft,
              end: rtl ? Alignment.centerLeft : Alignment.centerRight,
              colors: const [Colors.transparent, Colors.black, Colors.black],
              stops: [0.0, mobile ? 0.05 : 0.2, 1.0],
            ).createShader(rect),
            child: ShaderMask(
              blendMode: BlendMode.dstIn,
              shaderCallback: (rect) => const LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Colors.black, Colors.black, Colors.transparent],
                stops: [0.0, 0.86, 1.0],
              ).createShader(rect),
              child: Transform.flip(
                flipX: rtl,
                child: Image.asset('assets/images/hero_ship.jpg', fit: BoxFit.cover, filterQuality: FilterQuality.medium),
              ),
            ),
          ),
        ),
        // Navy veil behind the title for contrast.
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: rtl ? Alignment.centerRight : Alignment.centerLeft,
              end: rtl ? Alignment.centerLeft : Alignment.centerRight,
              colors: [
                WaveColors.navyDeep.withValues(alpha: mobile ? 0.70 : 0.55),
                WaveColors.navyDeep.withValues(alpha: mobile ? 0.45 : 0.22),
                WaveColors.navyDeep.withValues(alpha: 0),
                WaveColors.navyDeep.withValues(alpha: 0),
              ],
              stops: const [0.0, 0.4, 0.6, 1.0],
            ),
          ),
        ),
        // Top veil keeps the navigation readable.
        const DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [Color(0x99061B3E), Color(0x00061B3E), Color(0x00061B3E)],
              stops: [0.0, 0.18, 1.0],
            ),
          ),
        ),
      ]);
    });
  }
}

class _HeroText extends StatelessWidget {
  const _HeroText();

  @override
  Widget build(BuildContext context) {
    final mobile = Breakpoints.isMobile(context);
    final desktop = Breakpoints.isDesktop(context);
    final arabic = context.lang == AppLang.ar;
    final titleSize = mobile ? 38.0 : (desktop ? 64.0 : 54.0);
    final titleFont = arabic ? WaveFonts.arabic : WaveFonts.display;
    final titleStyle = TextStyle(fontFamily: titleFont, fontWeight: FontWeight.w800, fontSize: titleSize, height: 1.04, color: Colors.white);

    final features = [
      (Icons.directions_car_outlined, 'hero.f1'),
      (Icons.bed_outlined, 'hero.f2'),
      (Icons.verified_user_outlined, 'hero.f3'),
      (Icons.headset_mic_outlined, 'hero.f4'),
    ];

    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(
        context.t('hero.kicker'),
        style: TextStyle(color: WaveColors.goldLight, fontSize: mobile ? 11.5 : 13, letterSpacing: arabic ? 0 : 3.2, fontWeight: FontWeight.w600),
      ),
      const SizedBox(height: 10),
      Text(context.t('hero.title1'), style: titleStyle),
      ShaderMask(
        blendMode: BlendMode.srcIn,
        shaderCallback: (rect) => WaveColors.goldGradient.createShader(rect),
        child: Text(context.t('hero.title2'), style: titleStyle),
      ),
      SizedBox(
        width: titleSize * 5.2,
        height: titleSize * 0.32,
        child: CustomPaint(painter: _UnderlinePainter()),
      ),
      const SizedBox(height: 10),
      ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: Text(
          context.t('hero.subtitle'),
          style: TextStyle(color: Colors.white.withValues(alpha: 0.95), fontSize: mobile ? 15 : 19.5, height: 1.4),
        ),
      ),
      SizedBox(height: mobile ? 18 : 28),
      Wrap(
        spacing: mobile ? 14 : 30,
        runSpacing: 12,
        children: [
          for (final (icon, key) in features)
            Row(mainAxisSize: MainAxisSize.min, children: [
              Container(
                width: mobile ? 36 : 46,
                height: mobile ? 36 : 46,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: Colors.white.withValues(alpha: 0.06),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.85), width: 1.3),
                ),
                child: Icon(icon, color: Colors.white, size: mobile ? 18 : 22),
              ),
              const SizedBox(width: 10),
              Text(context.t(key), style: TextStyle(color: Colors.white, fontSize: mobile ? 11.5 : 13, height: 1.25)),
            ]),
        ],
      ),
    ]);
  }
}

class _UnderlinePainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final path = Path()
      ..moveTo(size.width * 0.28, size.height * 0.55)
      ..quadraticBezierTo(size.width * 0.5, size.height * 0.05, size.width * 0.82, size.height * 0.4);
    canvas.drawPath(
      path,
      Paint()
        ..shader = WaveColors.goldGradient.createShader(Offset.zero & size)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

// ------------------------------------------------------------------ destinations

/// Used until the catalog has loaded, so the page never renders empty.
const _fallbackPopular = [
  PopularRoute(from: 'DZALG', to: 'ESBCN', image: 'dest_barcelona', operators: ['BAL']),
  PopularRoute(from: 'DZALG', to: 'FRMRS', image: 'dest_marseille', operators: ['CL', 'AF', 'NE']),
  PopularRoute(from: 'DZORN', to: 'ESVLC', image: 'dest_valencia', operators: ['BAL']),
  PopularRoute(from: 'DZBJA', to: 'FRSET', image: 'dest_sete', operators: ['GNV']),
  PopularRoute(from: 'DZAAE', to: 'ITCVV', image: 'dest_civitavecchia', operators: ['GNV']),
];

const _fallbackNames = {
  'DZALG': LocalizedText('Alger', 'Algiers', 'الجزائر'),
  'DZORN': LocalizedText('Oran', 'Oran', 'وهران'),
  'DZBJA': LocalizedText('Béjaïa', 'Bejaia', 'بجاية'),
  'DZAAE': LocalizedText('Annaba', 'Annaba', 'عنابة'),
  'ESBCN': LocalizedText('Barcelone', 'Barcelona', 'برشلونة'),
  'FRMRS': LocalizedText('Marseille', 'Marseille', 'مرسيليا'),
  'ESVLC': LocalizedText('Valence', 'Valencia', 'فالنسيا'),
  'FRSET': LocalizedText('Sète', 'Sète', 'سات'),
  'ITCVV': LocalizedText('Civitavecchia', 'Civitavecchia', 'تشيفيتافيكيا'),
};

class _PopularDestinations extends ConsumerStatefulWidget {
  const _PopularDestinations();

  @override
  ConsumerState<_PopularDestinations> createState() => _PopularDestinationsState();
}

class _PopularDestinationsState extends ConsumerState<_PopularDestinations> {
  final _scroll = ScrollController();

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final meta = ref.watch(metaProvider).value;
    final lang = context.lang;
    final popular = meta?.popular ?? _fallbackPopular;
    String name(String code) => meta?.port(code)?.name.of(lang) ?? _fallbackNames[code]?.of(lang) ?? code;

    void open(PopularRoute p) => openRouteSearch(context, ref, p.from, p.to);

    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: SectionTitle(
          context.t('home.popular'),
          size: Breakpoints.isMobile(context) ? 22 : 26,
          trailing: TextButton(
            onPressed: () => context.go('/routes'),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Text(context.t('home.allRoutes'), style: const TextStyle(color: WaveColors.blue, fontWeight: FontWeight.w500)),
              const SizedBox(width: 6),
              const Icon(Icons.arrow_forward_rounded, size: 18, color: WaveColors.blue),
            ]),
          ),
        ),
      ),
      const SizedBox(height: 14),
      SizedBox(
        height: 178,
        child: Stack(children: [
          ListView.separated(
            controller: _scroll,
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: popular.length,
            separatorBuilder: (_, _) => const SizedBox(width: 12),
            itemBuilder: (context, i) => _DestinationCard(
              route: popular[i],
              from: name(popular[i].from),
              to: name(popular[i].to),
              onTap: () => open(popular[i]),
            ),
          ),
          PositionedDirectional(
            end: 4,
            top: 64,
            child: Material(
              color: Colors.white,
              shape: const CircleBorder(),
              elevation: 3,
              child: IconButton(
                icon: const Icon(Icons.chevron_right_rounded, color: WaveColors.navy),
                onPressed: () => _scroll.animateTo(
                  (_scroll.offset + 480).clamp(0, _scroll.position.maxScrollExtent),
                  duration: const Duration(milliseconds: 350),
                  curve: Curves.easeOut,
                ),
              ),
            ),
          ),
        ]),
      ),
    ]);
  }
}

class _DestinationCard extends ConsumerWidget {
  const _DestinationCard({required this.route, required this.from, required this.to, required this.onTap});
  final PopularRoute route;
  final String from;
  final String to;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final favorites = ref.watch(favoritesProvider);
    final key = '${route.from}-${route.to}';
    final operator = route.operators.isEmpty ? null : route.operators.first;
    final meta = ref.watch(metaProvider).value;
    final featured = meta?.routes.where((r) => r.from == route.from && r.to == route.to && r.operator == operator).firstOrNull;
    final duration = featured?.typicalDurationMin != null
        ? Fmt.duration(featured!.typicalDurationMin!)
        : Fmt.durationRange(route.minDurationMin, route.maxDurationMin);
    return SizedBox(
      width: 246,
      child: Material(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        elevation: 0,
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              boxShadow: [BoxShadow(color: WaveColors.navyDeep.withValues(alpha: 0.08), blurRadius: 14, offset: const Offset(0, 6))],
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Stack(children: [
                ClipRRect(
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
                  child: Image.asset('assets/images/${route.image}.jpg', height: 100, width: 246, fit: BoxFit.cover),
                ),
                PositionedDirectional(
                  top: 6,
                  end: 6,
                  child: InkResponse(
                    onTap: () => ref.read(favoritesProvider.notifier).toggle(key),
                    child: CircleAvatar(
                      radius: 14,
                      backgroundColor: Colors.white.withValues(alpha: 0.9),
                      child: Icon(favorites.contains(key) ? Icons.favorite_rounded : Icons.favorite_border_rounded, size: 16, color: WaveColors.danger),
                    ),
                  ),
                ),
              ]),
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 10, 10, 10),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  RouteTitle(from: from, to: to),
                  const SizedBox(height: 8),
                  Row(children: [
                    if (operator != null) OperatorLogo(operator, size: 16),
                    const SizedBox(width: 8),
                    const Icon(Icons.schedule_rounded, size: 16, color: WaveColors.muted),
                    const SizedBox(width: 4),
                    Flexible(
                      child: Text(
                        duration,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 12.5, color: WaveColors.navyInk),
                      ),
                    ),
                    const Spacer(),
                    Container(
                      width: 30,
                      height: 30,
                      decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: WaveColors.line)),
                      child: Icon(Directionality.of(context) == TextDirection.rtl ? Icons.arrow_back_rounded : Icons.arrow_forward_rounded, size: 16, color: WaveColors.navy),
                    ),
                  ]),
                ]),
              ),
            ]),
          ),
        ),
      ),
    );
  }
}

// ------------------------------------------------------------------ partners

class _Partners extends ConsumerWidget {
  const _Partners();

  static const _cards = [
    ('BAL', 'company_bal'),
    ('CL', 'company_cl'),
    ('NE', 'company_ne'),
    ('GNV', 'company_gnv'),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final meta = ref.watch(metaProvider).value;
    final lang = context.lang;
    final mobile = Breakpoints.isMobile(context);
    final desktop = Breakpoints.isDesktop(context);
    final others = meta?.operators.where((o) => o.active && !_cards.any((c) => c.$1 == o.code)).toList() ?? const <Operator>[];

    final cards = [
      for (final (code, image) in _cards)
        _PartnerCard(code: code, image: image, markets: meta?.operator(code)?.markets.of(lang) ?? '', onTap: () => context.go('/companies')),
    ];

    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Padding(padding: const EdgeInsets.symmetric(horizontal: 12), child: SectionTitle(context.t('home.partners'), size: mobile ? 22 : 26)),
      const SizedBox(height: 14),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: desktop
            // Fixed height: a stretched Row inside a scroll view would otherwise get an unbounded height.
            ? SizedBox(
                height: 164,
                child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  for (final c in cards) ...[Expanded(flex: 10, child: c), const SizedBox(width: 14)],
                  const Expanded(flex: 19, child: _CabinsPromo()),
                ]),
              )
            : Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                GridView.count(
                  crossAxisCount: mobile ? 2 : 4,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  crossAxisSpacing: 12,
                  mainAxisSpacing: 12,
                  childAspectRatio: mobile ? 1.15 : 1.35,
                  children: cards,
                ),
                const SizedBox(height: 12),
                const SizedBox(height: 176, child: _CabinsPromo()),
              ]),
      ),
      if (others.isNotEmpty) ...[
        const SizedBox(height: 14),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Wrap(spacing: 10, runSpacing: 10, crossAxisAlignment: WrapCrossAlignment.center, children: [
            Text(const ['Et aussi :', 'Also:', 'وأيضا:'][lang.index], style: const TextStyle(color: WaveColors.muted)),
            for (final o in others)
              ActionChip(
                backgroundColor: Colors.white,
                label: OperatorLogo(o.code, size: 18),
                onPressed: () => context.go('/companies'),
              ),
          ]),
        ),
      ],
    ]);
  }
}

class _PartnerCard extends StatelessWidget {
  const _PartnerCard({required this.code, required this.image, required this.markets, required this.onTap});
  final String code;
  final String image;
  final String markets;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const SizedBox(height: 14),
          Center(child: OperatorLogo(code, size: 24)),
          const SizedBox(height: 8),
          Text(markets, textAlign: TextAlign.center, style: const TextStyle(fontSize: 12, color: WaveColors.navyInk)),
          const Spacer(),
          Image.asset('assets/images/$image.jpg', height: 56, fit: BoxFit.cover),
        ]),
      ),
    );
  }
}

class _CabinsPromo extends StatelessWidget {
  const _CabinsPromo();

  @override
  Widget build(BuildContext context) {
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Stack(fit: StackFit.expand, children: [
        Align(
          alignment: rtl ? Alignment.centerLeft : Alignment.centerRight,
          child: FractionallySizedBox(widthFactor: 0.55, heightFactor: 1, child: Image.asset('assets/images/cabin.jpg', fit: BoxFit.cover)),
        ),
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: rtl ? Alignment.centerRight : Alignment.centerLeft,
              end: rtl ? Alignment.centerLeft : Alignment.centerRight,
              colors: const [WaveColors.navyDeep, WaveColors.navyDeep, Color(0x00061B3E), Color(0x00061B3E)],
              stops: const [0.0, 0.45, 0.62, 1.0],
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: FractionallySizedBox(
            alignment: AlignmentDirectional.centerStart,
            widthFactor: 0.55,
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.center, children: [
              Text(context.t('home.cabinsKicker'), style: const TextStyle(color: WaveColors.goldLight, fontSize: 10.5, letterSpacing: 2, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              Text(
                context.t('home.cabinsTitle'),
                maxLines: 2,
                style: TextStyle(
                  fontFamily: context.lang == AppLang.ar ? WaveFonts.arabic : WaveFonts.display,
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                  fontSize: 18,
                  height: 1.1,
                ),
              ),
              const SizedBox(height: 5),
              Flexible(
                child: Text(context.t('home.cabinsText'),
                    maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontSize: 11, height: 1.3)),
              ),
              const SizedBox(height: 8),
              SizedBox(
                height: 32,
                child: GoldButton(label: context.t('home.cabinsCta'), height: 32, fontSize: 12, onPressed: () => context.go('/ships')),
              ),
            ]),
          ),
        ),
      ]),
    );
  }
}

// ------------------------------------------------------------------ stats & footer

class _StatsBand extends ConsumerWidget {
  const _StatsBand();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final meta = ref.watch(metaProvider).value;
    final mobile = Breakpoints.isMobile(context);
    final ports = meta?.ports.length ?? 14;
    final operators = meta?.operators.where((o) => o.active).length ?? 6;
    final routes = meta?.routes.length ?? 44;
    final perWeek = meta?.routes.fold<double>(0, (a, r) => a + r.departuresPerWeek).round() ?? 70;
    final lang = context.lang;

    final stats = [
      (Icons.beach_access_outlined, '$ports', const ['Ports\nen Méditerranée', 'Mediterranean\nports', 'موانئ\nفي المتوسط'][lang.index]),
      (Icons.directions_boat_outlined, '$operators', context.t('stats.companies')),
      (Icons.route_outlined, '$routes', const ['Lignes\ndirectes', 'Direct\nroutes', 'خطوط\nمباشرة'][lang.index]),
      (Icons.event_available_outlined, '$perWeek', const ['Départs\npar semaine', 'Departures\nper week', 'رحلات\nفي الأسبوع'][lang.index]),
      (Icons.shield_outlined, '100%', context.t('stats.assistance')),
    ];

    Widget stat((IconData, String, String) s) => Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(s.$1, color: WaveColors.goldLight, size: mobile ? 28 : 34),
          const SizedBox(width: 10),
          Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
            Text(s.$3, style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontSize: 11, height: 1.2)),
            ShaderMask(
              blendMode: BlendMode.srcIn,
              shaderCallback: (r) => WaveColors.goldGradient.createShader(r),
              child: Text(s.$2, style: TextStyle(fontSize: mobile ? 22 : 26, fontWeight: FontWeight.w700, color: Colors.white)),
            ),
          ]),
        ]);

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFF123A73), WaveColors.navyDeep]),
      ),
      padding: EdgeInsets.symmetric(vertical: mobile ? 20 : 26, horizontal: mobile ? 16 : 60),
      child: mobile
          ? Wrap(spacing: 24, runSpacing: 18, children: stats.map(stat).toList())
          : Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                for (var i = 0; i < stats.length; i++) ...[
                  stat(stats[i]),
                  if (i < stats.length - 1) Container(width: 1, height: 44, color: Colors.white.withValues(alpha: 0.18)),
                ],
              ],
            ),
    );
  }
}

class _Footer extends StatelessWidget {
  const _Footer();

  @override
  Widget build(BuildContext context) {
    final mobile = Breakpoints.isMobile(context);
    final linkStyle = TextStyle(color: Colors.white.withValues(alpha: 0.8), fontSize: 13, height: 2);
    Widget column(String title, List<(String, String)> links) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(color: WaveColors.goldLight, fontWeight: FontWeight.w600)),
          const SizedBox(height: 6),
          for (final (label, path) in links) InkWell(onTap: () => context.go(path), child: Text(label, style: linkStyle)),
        ]);

    return Container(
      color: const Color(0xFF04132D),
      padding: EdgeInsets.symmetric(horizontal: mobile ? 16 : 96, vertical: 28),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Wrap(spacing: 60, runSpacing: 24, children: [
          const WaveLogo(scale: 0.9),
          column('WAVE', [
            (context.t('nav.routes'), '/routes'),
            (context.t('nav.companies'), '/companies'),
            (context.t('nav.ships'), '/ships'),
            (context.t('nav.live'), '/live'),
          ]),
          column(context.t('nav.guides'), [
            (context.t('nav.guides'), '/guides'),
            (context.t('nav.deals'), '/deals'),
            (context.t('nav.tv'), '/tv'),
            (context.t('nav.community'), '/community'),
          ]),
          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(context.t('header.contact'), style: const TextStyle(color: WaveColors.goldLight, fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            for (final n in AppConfig.whatsappNumbers)
              Text(n, textDirection: TextDirection.ltr, style: linkStyle),
            const SizedBox(height: 4),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(foregroundColor: Colors.white, side: BorderSide(color: Colors.white.withValues(alpha: 0.4))),
              onPressed: () => openWhatsApp(context),
              icon: const Icon(Icons.chat_rounded, size: 18),
              label: Text(context.t('common.whatsapp')),
            ),
          ]),
        ]),
        const SizedBox(height: 22),
        Divider(color: Colors.white.withValues(alpha: 0.12)),
        const SizedBox(height: 10),
        Text(context.t('footer.rights'), style: TextStyle(color: Colors.white.withValues(alpha: 0.6), fontSize: 12)),
      ]),
    );
  }
}

/// "Alger → Barcelone" with a drawn arrow (no reliance on font fallback for the glyph).
class RouteTitle extends StatelessWidget {
  const RouteTitle({super.key, required this.from, required this.to, this.size = 14});
  final String from;
  final String to;
  final double size;

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(fontWeight: FontWeight.w600, fontSize: size, color: WaveColors.navyInk);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Flexible(child: Text(from, maxLines: 1, overflow: TextOverflow.ellipsis, style: style)),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Icon(rtl ? Icons.arrow_back_rounded : Icons.arrow_forward_rounded, size: size + 2, color: WaveColors.navyInk),
      ),
      Flexible(child: Text(to, maxLines: 1, overflow: TextOverflow.ellipsis, style: style)),
    ]);
  }
}
