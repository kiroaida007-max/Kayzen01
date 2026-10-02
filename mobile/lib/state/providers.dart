import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/formatters.dart';
import '../core/l10n.dart';
import '../data/api_client.dart';
import '../data/models.dart';
import '../data/search_request.dart';

final apiProvider = Provider<ApiClient>((ref) => ApiClient());

/// Overridden in `main()` once SharedPreferences is loaded.
final prefsProvider = Provider<SharedPreferences>((ref) => throw UnimplementedError('prefsProvider not initialised'));

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) => const FlutterSecureStorage());

// ------------------------------------------------------------------ settings

class Settings {
  const Settings({this.lang = AppLang.fr, this.currency = Currency.DZD});
  final AppLang lang;
  final Currency currency;
  Settings copyWith({AppLang? lang, Currency? currency}) => Settings(lang: lang ?? this.lang, currency: currency ?? this.currency);
}

class SettingsNotifier extends Notifier<Settings> {
  @override
  Settings build() {
    final prefs = ref.watch(prefsProvider);
    final lang = AppLang.values.where((l) => l.name == prefs.getString('lang')).firstOrNull ?? AppLang.fr;
    final currency = Currency.values.where((c) => c.name == prefs.getString('currency')).firstOrNull ?? Currency.DZD;
    return Settings(lang: lang, currency: currency);
  }

  void setLang(AppLang lang) {
    ref.read(prefsProvider).setString('lang', lang.name);
    state = state.copyWith(lang: lang);
  }

  void setCurrency(Currency currency) {
    ref.read(prefsProvider).setString('currency', currency.name);
    state = state.copyWith(currency: currency);
  }
}

final settingsProvider = NotifierProvider<SettingsNotifier, Settings>(SettingsNotifier.new);

// ------------------------------------------------------------------ catalog

final metaProvider = FutureProvider<Meta>((ref) => ref.watch(apiProvider).meta());

final guidesProvider = FutureProvider<List<Guide>>((ref) => ref.watch(apiProvider).guides());

final dealsProvider = FutureProvider<List<Deal>>((ref) => ref.watch(apiProvider).deals());

/// REST snapshot first (instant map), then WebSocket updates.
final livePositionsProvider = StreamProvider<List<VesselPosition>>((ref) async* {
  final api = ref.watch(apiProvider);
  try {
    yield await api.vessels();
  } catch (_) {
    // The socket below will deliver positions as soon as the server is reachable.
  }
  yield* api.liveStream();
});

// ------------------------------------------------------------------ search form

DateTime _today() {
  final now = DateTime.now();
  return DateTime(now.year, now.month, now.day);
}

class SearchQueryNotifier extends Notifier<SearchQuery> {
  @override
  SearchQuery build() => SearchQuery(
        from: 'DZALG',
        to: 'ESBCN',
        departureDate: _today().add(const Duration(days: 8)),
        passengers: const Passengers(adults: 2, childrenAges: [8]),
        vehicle: const Vehicle(type: VehicleType.car),
        accommodation: AccommodationPref.cabinAny,
      );

  void update(SearchQuery Function(SearchQuery) change) => state = change(state);

  void setFrom(String code, Meta? meta) {
    final destinations = meta?.port(code)?.destinations ?? const [];
    final keepTo = state.to != null && destinations.contains(state.to);
    state = state.copyWith(from: code, clearTo: !keepTo);
  }

  void swap(Meta? meta) {
    if (state.from == null || state.to == null) return;
    final canReverse = meta?.port(state.to!)?.destinations.contains(state.from) ?? true;
    if (canReverse) state = state.copyWith(from: state.to, to: state.from);
  }

  void setDeparture(DateTime date) {
    final ret = state.returnDate;
    state = state.copyWith(departureDate: date, clearReturn: ret != null && ret.isBefore(date));
  }

  void setTripType(TripType type) {
    state = state.copyWith(
      tripType: type,
      returnDate: type == TripType.roundTrip ? (state.returnDate ?? state.departureDate.add(const Duration(days: 14))) : null,
      clearReturn: type == TripType.oneWay,
    );
  }

  void prefill(String from, String to) {
    state = state.copyWith(from: from, to: to);
  }
}

final searchQueryProvider = NotifierProvider<SearchQueryNotifier, SearchQuery>(SearchQueryNotifier.new);

/// Results of the last submitted search (null until the user searches).
class SearchResultsNotifier extends AsyncNotifier<SearchResponse?> {
  @override
  Future<SearchResponse?> build() async => null;

  Future<void> run() async {
    final query = ref.read(searchQueryProvider);
    final settings = ref.read(settingsProvider);
    state = const AsyncLoading();
    state = await AsyncValue.guard(() => ref.read(apiProvider).search(query, settings.currency, settings.lang));
  }
}

final searchResultsProvider = AsyncNotifierProvider<SearchResultsNotifier, SearchResponse?>(SearchResultsNotifier.new);

// ------------------------------------------------------------------ selection & booking

class Selection {
  const Selection({this.outbound, this.outboundTariff, this.inbound, this.inboundTariff});
  final SailingOffer? outbound;
  final String? outboundTariff;
  final SailingOffer? inbound;
  final String? inboundTariff;

  Selection copyWith({SailingOffer? outbound, String? outboundTariff, SailingOffer? inbound, String? inboundTariff, bool clearInbound = false}) => Selection(
        outbound: outbound ?? this.outbound,
        outboundTariff: outboundTariff ?? this.outboundTariff,
        inbound: clearInbound ? null : (inbound ?? this.inbound),
        inboundTariff: clearInbound ? null : (inboundTariff ?? this.inboundTariff),
      );
}

class SelectionNotifier extends Notifier<Selection> {
  @override
  Selection build() => const Selection();

  void selectOutbound(SailingOffer offer, String tariff) => state = state.copyWith(outbound: offer, outboundTariff: tariff);
  void selectInbound(SailingOffer offer, String tariff) => state = state.copyWith(inbound: offer, inboundTariff: tariff);
  void clear() => state = const Selection();
}

final selectionProvider = NotifierProvider<SelectionNotifier, Selection>(SelectionNotifier.new);

// ------------------------------------------------------------------ "Ma vague"

class SavedTrip {
  const SavedTrip(this.reference, this.lastName, this.title, this.date);
  final String reference;
  final String lastName;
  final String title;
  final String date;

  Map<String, dynamic> toJson() => {'reference': reference, 'lastName': lastName, 'title': title, 'date': date};
  factory SavedTrip.fromJson(Map<String, dynamic> j) =>
      SavedTrip(j['reference'] as String, j['lastName'] as String, j['title'] as String, j['date'] as String);
}

/// Booking references live in the platform keystore (Keychain / Android Keystore).
class TripsNotifier extends AsyncNotifier<List<SavedTrip>> {
  static const _key = 'wave.trips';

  @override
  Future<List<SavedTrip>> build() async {
    try {
      final raw = await ref.read(secureStorageProvider).read(key: _key);
      if (raw == null) return const [];
      return (jsonDecode(raw) as List<dynamic>).map((e) => SavedTrip.fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {
      return const [];
    }
  }

  Future<void> save(SavedTrip trip) async {
    final current = [...(state.value ?? const <SavedTrip>[])]..removeWhere((t) => t.reference == trip.reference);
    final next = [trip, ...current];
    state = AsyncData(next);
    await ref.read(secureStorageProvider).write(key: _key, value: jsonEncode(next.map((t) => t.toJson()).toList()));
  }

  Future<void> remove(String reference) async {
    final next = [...(state.value ?? const <SavedTrip>[])]..removeWhere((t) => t.reference == reference);
    state = AsyncData(next);
    await ref.read(secureStorageProvider).write(key: _key, value: jsonEncode(next.map((t) => t.toJson()).toList()));
  }
}

final tripsProvider = AsyncNotifierProvider<TripsNotifier, List<SavedTrip>>(TripsNotifier.new);

class FavoritesNotifier extends Notifier<Set<String>> {
  @override
  Set<String> build() => (ref.watch(prefsProvider).getStringList('favorites') ?? const []).toSet();

  void toggle(String key) {
    final next = {...state};
    if (!next.remove(key)) next.add(key);
    ref.read(prefsProvider).setStringList('favorites', next.toList());
    state = next;
  }
}

final favoritesProvider = NotifierProvider<FavoritesNotifier, Set<String>>(FavoritesNotifier.new);
