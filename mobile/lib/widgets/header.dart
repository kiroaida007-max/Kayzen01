import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/config.dart';
import '../core/formatters.dart';
import '../core/l10n.dart';
import '../core/theme.dart';
import '../state/providers.dart';
import 'brand.dart';
import 'flag.dart';

class NavItem {
  const NavItem(this.labelKey, this.path, this.icon);
  final String labelKey;
  final String path;
  final IconData icon;
}

const navItems = [
  NavItem('nav.ferries', '/', Icons.directions_boat_filled_outlined),
  NavItem('nav.routes', '/routes', Icons.route_outlined),
  NavItem('nav.companies', '/companies', Icons.business_outlined),
  NavItem('nav.ships', '/ships', Icons.sailing_outlined),
  NavItem('nav.deals', '/deals', Icons.local_offer_outlined),
  NavItem('nav.guides', '/guides', Icons.menu_book_outlined),
  NavItem('nav.tv', '/tv', Icons.smart_display_outlined),
  NavItem('nav.community', '/community', Icons.forum_outlined),
];

Future<void> openWhatsApp(BuildContext context) async {
  final number = AppConfig.whatsappNumbers.first.replaceAll('+', '');
  final text = Uri.encodeComponent(context.t('common.whatsapp'));
  await launchUrl(Uri.parse('https://wa.me/$number?text=$text'), mode: LaunchMode.externalApplication);
}

/// Top navigation. Transparent over the home hero, navy everywhere else.
class WaveHeader extends ConsumerWidget {
  const WaveHeader({super.key, this.transparent = false});
  final bool transparent;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final desktop = Breakpoints.isDesktop(context);
    final location = GoRouterState.of(context).uri.path;
    final trips = ref.watch(tripsProvider).value?.length ?? 0;

    final bar = Padding(
      padding: EdgeInsets.symmetric(horizontal: desktop ? 40 : 16, vertical: desktop ? 14 : 10),
      child: Row(
        children: [
          InkWell(onTap: () => context.go('/'), child: WaveLogo(scale: desktop ? 1.0 : 0.78)),
          if (desktop) ...[
            const SizedBox(width: 40),
            Expanded(child: _NavBar(location: location)),
            const _LangCurrencyButton(),
            const SizedBox(width: 14),
            IconButton(
              tooltip: context.t('trips.favorites'),
              onPressed: () => context.go('/trips'),
              icon: const Icon(Icons.favorite_border_rounded, color: Colors.white),
            ),
            const SizedBox(width: 10),
            _MyWaveButton(count: trips),
            const SizedBox(width: 14),
            _ContactButton(onPressed: () => openWhatsApp(context)),
          ] else ...[
            const Spacer(),
            const _LangCurrencyButton(compact: true),
            IconButton(
              tooltip: context.t('header.contact'),
              onPressed: () => openWhatsApp(context),
              icon: const Icon(Icons.support_agent_rounded, color: WaveColors.goldLight),
            ),
          ],
        ],
      ),
    );

    if (transparent) return SafeArea(bottom: false, child: bar);
    return Container(
      decoration: const BoxDecoration(gradient: WaveColors.navyGradient),
      child: SafeArea(bottom: false, child: bar),
    );
  }

  static bool _isActive(String location, String path) => path == '/' ? location == '/' || location == '/results' : location.startsWith(path);
}

/// Shows as many links as fit and folds the rest into a "Plus" menu, so the bar never overflows.
class _NavBar extends StatelessWidget {
  const _NavBar({required this.location});
  final String location;

  static double _width(BuildContext context, String label, bool bold) {
    final painter = TextPainter(
      text: TextSpan(text: label, style: TextStyle(fontSize: 14, fontWeight: bold ? FontWeight.w600 : FontWeight.w400, fontFamily: DefaultTextStyle.of(context).style.fontFamily)),
      textDirection: TextDirection.ltr,
      maxLines: 1,
    )..layout();
    return painter.width + 28;
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (context, constraints) {
        const moreWidth = 84.0;
        final widths = [for (final item in navItems) _width(context, context.t(item.labelKey), WaveHeader._isActive(location, item.path))];
        final total = widths.fold<double>(0, (a, b) => a + b);
        var visible = navItems.length;
        if (total > constraints.maxWidth) {
          var used = moreWidth;
          visible = 0;
          while (visible < navItems.length && used + widths[visible] <= constraints.maxWidth) {
            used += widths[visible];
            visible++;
          }
        }
        final hidden = navItems.skip(visible).toList();
        return Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          for (final item in navItems.take(visible)) _NavLink(item: item, active: WaveHeader._isActive(location, item.path)),
          if (hidden.isNotEmpty)
            PopupMenuButton<String>(
              tooltip: context.t('nav.more'),
              offset: const Offset(0, 40),
              onSelected: (path) => context.go(path),
              itemBuilder: (context) => [
                for (final item in hidden)
                  PopupMenuItem(
                    value: item.path,
                    child: Row(children: [Icon(item.icon, size: 20, color: WaveColors.navy), const SizedBox(width: 10), Text(context.t(item.labelKey))]),
                  ),
              ],
              child: Padding(
                // Same baseline as the links, which reserve room for the active underline.
                padding: const EdgeInsets.fromLTRB(10, 8, 10, 16.5),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Text(
                    context.t('nav.more'),
                    style: TextStyle(color: Colors.white, fontSize: 14, fontWeight: hidden.any((i) => WaveHeader._isActive(location, i.path)) ? FontWeight.w600 : FontWeight.w400),
                  ),
                  const Icon(Icons.keyboard_arrow_down_rounded, color: Colors.white, size: 18),
                ]),
              ),
            ),
        ]);
      });
}

class _NavLink extends StatelessWidget {
  const _NavLink({required this.item, required this.active});
  final NavItem item;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: InkWell(
        borderRadius: BorderRadius.circular(6),
        onTap: () => context.go(item.path),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text(
              context.t(item.labelKey),
              style: TextStyle(color: Colors.white, fontSize: 14, fontWeight: active ? FontWeight.w600 : FontWeight.w400),
            ),
            const SizedBox(height: 6),
            // A gradient on a zero-width box makes Skia return no shader, so inactive links get no decoration.
            AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              height: 2.5,
              width: active ? 42 : 0,
              decoration: active ? BoxDecoration(gradient: WaveColors.goldGradient, borderRadius: BorderRadius.circular(2)) : null,
            ),
          ]),
        ),
      ),
    );
  }
}

class _LangCurrencyButton extends ConsumerWidget {
  const _LangCurrencyButton({this.compact = false});
  final bool compact;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    return PopupMenuButton<Object>(
      tooltip: '${context.t('common.language')} / ${context.t('common.currency')}',
      offset: const Offset(0, 44),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      onSelected: (value) {
        final notifier = ref.read(settingsProvider.notifier);
        if (value is AppLang) notifier.setLang(value);
        if (value is Currency) notifier.setCurrency(value);
        // Prices depend on the currency: refresh results if a search is on screen.
        if (value is Currency && ref.read(searchResultsProvider).value != null) {
          ref.read(searchResultsProvider.notifier).run();
        }
      },
      itemBuilder: (context) => [
        PopupMenuItem<Object>(enabled: false, child: Text(context.t('common.language'), style: const TextStyle(fontWeight: FontWeight.w600))),
        for (final lang in AppLang.values)
          CheckedPopupMenuItem<Object>(
            value: lang,
            checked: lang == settings.lang,
            child: Row(children: [FlagIcon(lang.flagCountry), const SizedBox(width: 10), Text(lang.label)]),
          ),
        const PopupMenuDivider(),
        PopupMenuItem<Object>(enabled: false, child: Text(context.t('common.currency'), style: const TextStyle(fontWeight: FontWeight.w600))),
        for (final c in Currency.values)
          CheckedPopupMenuItem<Object>(value: c, checked: c == settings.currency, child: Text('${c.name}  (${c.symbol})')),
      ],
      child: Container(
        padding: EdgeInsets.symmetric(horizontal: compact ? 8 : 12, vertical: 7),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.white.withValues(alpha: 0.55)),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          FlagIcon(settings.lang.flagCountry, width: 20),
          const SizedBox(width: 6),
          Text(settings.lang.short, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w500)),
          if (!compact) ...[
            const SizedBox(width: 6),
            Text('· ${settings.currency.symbol}', style: const TextStyle(color: WaveColors.goldLight, fontSize: 13)),
          ],
          const Icon(Icons.keyboard_arrow_down_rounded, color: Colors.white, size: 18),
        ]),
      ),
    );
  }
}

class _MyWaveButton extends StatelessWidget {
  const _MyWaveButton({required this.count});
  final int count;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: () => context.go('/trips'),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: WaveColors.navyDeep.withValues(alpha: 0.55),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.white.withValues(alpha: 0.7)),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Text(context.t('header.myWave'), style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w500)),
          const SizedBox(width: 8),
          Container(
            width: 20,
            height: 20,
            alignment: Alignment.center,
            decoration: const BoxDecoration(gradient: WaveColors.goldGradient, shape: BoxShape.circle),
            child: Text('$count', style: const TextStyle(color: WaveColors.navyDeep, fontSize: 11, fontWeight: FontWeight.w700)),
          ),
        ]),
      ),
    );
  }
}

class _ContactButton extends StatelessWidget {
  const _ContactButton({required this.onPressed});
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Ink(
        decoration: BoxDecoration(gradient: WaveColors.buttonGradient, borderRadius: BorderRadius.circular(8)),
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: onPressed,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Container(
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: WaveColors.navyDeep, width: 1.4)),
                child: const Icon(Icons.call, size: 12, color: WaveColors.navyDeep),
              ),
              const SizedBox(width: 8),
              Text(context.t('header.contact'), style: const TextStyle(color: WaveColors.navyDeep, fontWeight: FontWeight.w600, fontSize: 13.5)),
            ]),
          ),
        ),
      ),
    );
  }
}

/// Standard page frame for every screen except the home page.
class WavePage extends StatelessWidget {
  const WavePage({super.key, required this.title, required this.child, this.subtitle, this.maxWidth = 1180, this.scrollable = true});
  final String title;
  final String? subtitle;
  final Widget child;
  final double maxWidth;
  final bool scrollable;

  @override
  Widget build(BuildContext context) {
    final mobile = Breakpoints.isMobile(context);
    final heading = Container(
      width: double.infinity,
      decoration: const BoxDecoration(gradient: WaveColors.navyGradient),
      padding: EdgeInsets.fromLTRB(mobile ? 16 : 40, 6, mobile ? 16 : 40, 26),
      child: Center(
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxWidth),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Flexible(
                child: Text(title,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(color: Colors.white, fontSize: mobile ? 26 : 32)),
              ),
              const SizedBox(width: 12),
              const GoldFlourish(),
            ]),
            if (subtitle != null) ...[
              const SizedBox(height: 6),
              Text(subtitle!, style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontSize: 15)),
            ],
          ]),
        ),
      ),
    );
    final body = Padding(
      padding: EdgeInsets.symmetric(horizontal: mobile ? 16 : 40, vertical: 24),
      child: Center(child: ConstrainedBox(constraints: BoxConstraints(maxWidth: maxWidth), child: child)),
    );
    return Column(children: [
      const WaveHeader(),
      Expanded(
        child: scrollable
            ? SingleChildScrollView(child: Column(children: [heading, body]))
            : Column(children: [heading, Expanded(child: body)]),
      ),
    ]);
  }
}
