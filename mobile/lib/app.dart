import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/l10n.dart';
import 'core/theme.dart';
import 'features/booking/booking_page.dart';
import 'features/booking/confirmation_page.dart';
import 'features/content/content_pages.dart';
import 'features/home/home_page.dart';
import 'features/live/live_map_page.dart';
import 'features/results/results_page.dart';
import 'features/trips/trips_page.dart';
import 'state/providers.dart';
import 'widgets/shell.dart';

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/',
    routes: [
      ShellRoute(
        builder: (context, state, child) => AppShell(child: child),
        routes: [
          GoRoute(path: '/', pageBuilder: (c, s) => _page(s, const HomePage())),
          GoRoute(path: '/results', pageBuilder: (c, s) => _page(s, const ResultsPage())),
          GoRoute(path: '/booking', pageBuilder: (c, s) => _page(s, const BookingPage())),
          GoRoute(
            path: '/booking/:reference',
            pageBuilder: (c, s) => _page(s, ConfirmationPage(reference: s.pathParameters['reference']!, lastName: s.uri.queryParameters['name'])),
          ),
          GoRoute(path: '/routes', pageBuilder: (c, s) => _page(s, const RoutesPage())),
          GoRoute(path: '/companies', pageBuilder: (c, s) => _page(s, const CompaniesPage())),
          GoRoute(path: '/ships', pageBuilder: (c, s) => _page(s, const ShipsPage())),
          GoRoute(path: '/deals', pageBuilder: (c, s) => _page(s, const DealsPage())),
          GoRoute(path: '/guides', pageBuilder: (c, s) => _page(s, const GuidesPage())),
          GoRoute(path: '/guides/:id', pageBuilder: (c, s) => _page(s, GuideDetailPage(id: s.pathParameters['id']!))),
          GoRoute(path: '/tv', pageBuilder: (c, s) => _page(s, const WaveTvPage())),
          GoRoute(path: '/community', pageBuilder: (c, s) => _page(s, const CommunityPage())),
          GoRoute(path: '/live', pageBuilder: (c, s) => _page(s, LiveMapPage(focusVessel: s.uri.queryParameters['vessel']))),
          GoRoute(path: '/trips', pageBuilder: (c, s) => _page(s, const TripsPage())),
          // Bank / Stripe return page on the web build.
          GoRoute(
            path: '/payment-result',
            pageBuilder: (c, s) => _page(s, ConfirmationPage(reference: s.uri.queryParameters['reference'] ?? '', lastName: null)),
          ),
        ],
      ),
    ],
  );
});

CustomTransitionPage<void> _page(GoRouterState state, Widget child) => CustomTransitionPage<void>(
      key: state.pageKey,
      child: child,
      transitionDuration: const Duration(milliseconds: 220),
      transitionsBuilder: (context, animation, secondary, child) => FadeTransition(opacity: animation, child: child),
    );

class WaveApp extends ConsumerWidget {
  const WaveApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    return MaterialApp.router(
      title: 'WAVE — Ferries Algérie',
      debugShowCheckedModeBanner: false,
      theme: WaveTheme.light(arabic: settings.lang == AppLang.ar),
      locale: settings.lang.locale,
      supportedLocales: AppLang.values.map((l) => l.locale).toList(),
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      routerConfig: ref.watch(routerProvider),
    );
  }
}
