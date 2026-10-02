import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/l10n.dart';
import '../core/theme.dart';
import 'header.dart';

/// Scaffold shared by every route: bottom navigation on phones, header navigation on wide screens.
class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.child});
  final Widget child;

  static const _tabs = [
    ('/', 'nav.home', Icons.home_outlined, Icons.home_rounded),
    ('/routes', 'nav.routes', Icons.route_outlined, Icons.route),
    ('/live', 'nav.live', Icons.map_outlined, Icons.map_rounded),
    ('/trips', 'header.myWave', Icons.waves_outlined, Icons.waves_rounded),
  ];

  int _index(String location) {
    for (var i = _tabs.length - 1; i >= 0; i--) {
      final path = _tabs[i].$1;
      if (path == '/' ? (location == '/' || location == '/results' || location.startsWith('/booking')) : location.startsWith(path)) return i;
    }
    return 4;
  }

  @override
  Widget build(BuildContext context) {
    final mobile = Breakpoints.isMobile(context);
    final location = GoRouterState.of(context).uri.path;
    return Scaffold(
      body: child,
      bottomNavigationBar: mobile
          ? NavigationBar(
              height: 64,
              backgroundColor: Colors.white,
              indicatorColor: WaveColors.gold.withValues(alpha: 0.35),
              selectedIndex: _index(location),
              onDestinationSelected: (i) {
                if (i < _tabs.length) {
                  context.go(_tabs[i].$1);
                } else {
                  _showMore(context);
                }
              },
              destinations: [
                for (final (_, key, icon, selected) in _tabs)
                  NavigationDestination(icon: Icon(icon), selectedIcon: Icon(selected, color: WaveColors.navy), label: context.t(key)),
                NavigationDestination(icon: const Icon(Icons.menu_rounded), label: context.t('nav.more')),
              ],
            )
          : null,
    );
  }

  void _showMore(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Wrap(children: [
          for (final item in navItems.skip(1))
            ListTile(
              leading: Icon(item.icon, color: WaveColors.navy),
              title: Text(ctx.t(item.labelKey)),
              onTap: () {
                Navigator.of(ctx).pop();
                context.go(item.path);
              },
            ),
          ListTile(
            leading: const Icon(Icons.support_agent_rounded, color: WaveColors.navy),
            title: Text(ctx.t('header.contact')),
            onTap: () {
              Navigator.of(ctx).pop();
              openWhatsApp(context);
            },
          ),
        ]),
      ),
    );
  }
}
