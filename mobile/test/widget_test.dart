import 'package:flutter_test/flutter_test.dart';
import 'package:wave_app/core/formatters.dart';
import 'package:wave_app/core/l10n.dart';
import 'package:wave_app/data/models.dart';
import 'package:wave_app/data/search_request.dart';
import 'package:wave_app/features/booking/booking_page.dart' show ageOn, parseDate, validPhone;

void main() {
  test('port-local time keeps the published wall clock', () {
    final t = PortTime.parse('2026-10-08T20:00:00+01:00');
    expect(t.local.hour, 20);
    expect(t.instant.toUtc().hour, 19);
    expect(t.utcLabel, 'UTC+1');
    final summer = PortTime.parse('2026-07-01T09:30:00+02:00');
    expect(summer.utcLabel, 'UTC+2');
    expect(Fmt.time(summer.local), '09:30');
  });

  test('money formatting per currency and language', () {
    expect(Fmt.money(12874200, Currency.DZD, AppLang.fr), '128\u00a0742 DA');
    expect(Fmt.money(28826, Currency.EUR, AppLang.fr), '288,26 €');
    expect(Fmt.money(28800, Currency.EUR, AppLang.en), '€288');
    expect(Fmt.money(14413600, Currency.DZD, AppLang.ar), '144\u00a0136 د.ج');
    expect(Fmt.money(14413600, Currency.DZD, AppLang.en), 'DZD 144,136');
  });

  test('durations use the mockup style', () {
    expect(Fmt.duration(1200), '20h');
    expect(Fmt.duration(1410), '23h30');
    expect(Fmt.durationRange(1200, 1440), '20h - 24h');
  });

  test('search request matches the API contract', () {
    final q = SearchQuery(
      from: 'DZALG',
      to: 'FRMRS',
      departureDate: DateTime(2026, 10, 14),
      passengers: const Passengers(adults: 2, childrenAges: [8, 1]),
      vehicle: const Vehicle(type: VehicleType.car, roofBox: true),
      accommodation: AccommodationPref.cabinExterior,
      pets: const [Pet(type: PetType.dog, placement: PetPlacement.kennel)],
    );
    final json = q.toJson(Currency.EUR, AppLang.ar);
    expect(json['tripType'], 'ONE_WAY');
    expect(json['departureDate'], '2026-10-14');
    expect(json['accommodation'], 'CABIN_EXTERIOR');
    expect((json['vehicle'] as Map)['type'], 'CAR');
    expect((json['pets'] as List).first, {'type': 'DOG', 'count': 1, 'placement': 'KENNEL'});
    expect(json['currency'], 'EUR');
    expect(json['lang'], 'ar');
    expect(json.containsKey('returnDate'), isFalse);
  });

  test('client-side checks mirror the server rules', () {
    expect(validPhone('0550 12 34 56'), isTrue);
    expect(validPhone('+33 6 12 34 56 78'), isTrue);
    expect(validPhone('0033612345678'), isTrue);
    expect(validPhone('0450123456'), isFalse, reason: 'Algerian landlines are not accepted for WhatsApp');
    expect(parseDate('31/02/2026'), isNull);
    expect(parseDate('05/06/2018'), DateTime(2018, 6, 5));
    expect(ageOn(DateTime(2018, 6, 5), DateTime(2026, 10, 10)), 8);
    expect(ageOn(DateTime(2008, 10, 11), DateTime(2026, 10, 10)), 17, reason: '18th birthday is the day after departure');
  });

  test('money JSON round-trips', () {
    final m = Money.fromJson(const {'minor': 954170, 'currency': 'EUR'});
    expect(m.toJson(), {'minor': 954170, 'currency': 'EUR'});
    expect(m.format(AppLang.fr), '9\u00a0541,70 €');
  });
}
