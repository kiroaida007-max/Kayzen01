import '../core/formatters.dart';
import '../core/l10n.dart';

typedef Json = Map<String, dynamic>;

List<T> _list<T>(Object? raw, T Function(Json) f) =>
    (raw as List<dynamic>? ?? const []).map((e) => f(e as Json)).toList(growable: false);

Currency currencyOf(String code) => Currency.values.firstWhere((c) => c.name == code, orElse: () => Currency.DZD);

class Money {
  const Money(this.minor, this.currency);
  final int minor;
  final Currency currency;

  factory Money.fromJson(Json j) => Money((j['minor'] as num).toInt(), currencyOf(j['currency'] as String));
  Json toJson() => {'minor': minor, 'currency': currency.name};

  String format(AppLang lang) => Fmt.money(minor, currency, lang);
  bool get isNegative => minor < 0;

  @override
  bool operator ==(Object other) => other is Money && other.minor == minor && other.currency == currency;
  @override
  int get hashCode => Object.hash(minor, currency);
}

class LocalizedText {
  const LocalizedText(this.fr, this.en, this.ar);
  final String fr;
  final String en;
  final String ar;

  factory LocalizedText.fromJson(Json j) => LocalizedText(j['fr'] as String, j['en'] as String, j['ar'] as String);
  String of(AppLang lang) => switch (lang) { AppLang.fr => fr, AppLang.en => en, AppLang.ar => ar };
}

class Violation {
  const Violation(this.code, this.severity, this.message, this.field);
  final String code;
  final String severity;
  final String message;
  final String? field;

  factory Violation.fromJson(Json j) =>
      Violation(j['code'] as String, j['severity'] as String, j['message'] as String, j['field'] as String?);
  bool get isError => severity == 'ERROR';
  bool get isWarning => severity == 'WARNING';
}

class Port {
  const Port({required this.code, required this.shortCode, required this.name, required this.country, required this.lat, required this.lon, required this.zone, this.terminal, this.destinations = const []});
  final String code;
  final String shortCode;
  final LocalizedText name;
  final String country;
  final double lat;
  final double lon;
  final String zone;
  final String? terminal;
  final List<String> destinations;

  factory Port.fromJson(Json j) => Port(
        code: j['code'] as String,
        shortCode: j['shortCode'] as String,
        name: LocalizedText.fromJson(j['name'] as Json),
        country: j['country'] as String,
        lat: (j['lat'] as num).toDouble(),
        lon: (j['lon'] as num).toDouble(),
        zone: j['zone'] as String,
        terminal: j['terminal'] as String?,
        destinations: (j['destinations'] as List<dynamic>? ?? const []).cast<String>(),
      );
}

class Tariff {
  const Tariff(this.code, this.name, this.refundable, this.modifiable, this.conditions);
  final String code;
  final LocalizedText name;
  final bool refundable;
  final bool modifiable;
  final LocalizedText conditions;

  factory Tariff.fromJson(Json j) => Tariff(j['code'] as String, LocalizedText.fromJson(j['name'] as Json), j['refundable'] as bool,
      j['modifiable'] as bool, LocalizedText.fromJson(j['conditions'] as Json));
}

class Operator {
  const Operator({required this.code, required this.name, required this.color, required this.website, required this.active, required this.markets, required this.description, required this.tariffs, required this.routes, required this.vessels, required this.infantMaxAge, required this.childMaxAge, this.seniorMinAge, required this.baggageSeatKg, required this.baggageCabinKg, this.publishedUntil});
  final String code;
  final String name;
  final String color;
  final String website;
  final bool active;
  final LocalizedText markets;
  final LocalizedText description;
  final List<Tariff> tariffs;
  final int routes;
  final List<String> vessels;
  final int infantMaxAge;
  final int childMaxAge;
  final int? seniorMinAge;
  final int baggageSeatKg;
  final int baggageCabinKg;
  final String? publishedUntil;

  factory Operator.fromJson(Json j) {
    final bands = j['ageBands'] as Json;
    final baggage = j['baggage'] as Json;
    return Operator(
      code: j['code'] as String,
      name: j['name'] as String,
      color: j['color'] as String,
      website: j['website'] as String,
      active: j['active'] as bool,
      markets: LocalizedText.fromJson(j['markets'] as Json),
      description: LocalizedText.fromJson(j['description'] as Json),
      tariffs: _list(j['tariffs'], Tariff.fromJson),
      routes: (j['routes'] as num).toInt(),
      vessels: (j['vessels'] as List<dynamic>).cast<String>(),
      infantMaxAge: (bands['infantMaxAge'] as num).toInt(),
      childMaxAge: (bands['childMaxAge'] as num).toInt(),
      seniorMinAge: (bands['seniorMinAge'] as num?)?.toInt(),
      baggageSeatKg: (baggage['seatKg'] as num).toInt(),
      baggageCabinKg: (baggage['cabinKg'] as num).toInt(),
      publishedUntil: j['publishedUntil'] as String?,
    );
  }
}

class Vessel {
  const Vessel({required this.code, required this.name, required this.operator, this.imo, this.mmsi, this.built, this.lengthM, required this.speedKn, required this.passengers, required this.vehicles, required this.accommodations, required this.petFriendly, required this.amenities, required this.verified});
  final String code;
  final String name;
  final String operator;
  final String? imo;
  final String? mmsi;
  final int? built;
  final double? lengthM;
  final double speedKn;
  final int passengers;
  final int vehicles;
  final List<String> accommodations;
  final bool petFriendly;
  final List<String> amenities;
  final bool verified;

  factory Vessel.fromJson(Json j) {
    final cap = j['capacity'] as Json;
    return Vessel(
      code: j['code'] as String,
      name: j['name'] as String,
      operator: j['operator'] as String,
      imo: j['imo'] as String?,
      mmsi: j['mmsi'] as String?,
      built: (j['built'] as num?)?.toInt(),
      lengthM: (j['lengthM'] as num?)?.toDouble(),
      speedKn: (j['speedKn'] as num).toDouble(),
      passengers: (cap['passengers'] as num).toInt(),
      vehicles: (cap['vehicles'] as num).toInt(),
      accommodations: (j['accommodations'] as List<dynamic>).cast<String>(),
      petFriendly: j['petFriendly'] as bool,
      amenities: (j['amenities'] as List<dynamic>).cast<String>(),
      verified: j['verified'] as bool,
    );
  }
}

class RouteSummary {
  const RouteSummary({required this.id, required this.operator, required this.from, required this.to, this.typicalDurationMin, required this.departuresPerWeek, this.nextDeparture, required this.fromPrice, required this.fromPriceDzd, required this.popular, required this.polyline});
  final String id;
  final String operator;
  final String from;
  final String to;
  final int? typicalDurationMin;
  final double departuresPerWeek;
  final PortTime? nextDeparture;
  final Money fromPrice;
  final Money fromPriceDzd;
  final bool popular;
  final List<List<double>> polyline;

  factory RouteSummary.fromJson(Json j) => RouteSummary(
        id: j['id'] as String,
        operator: j['operator'] as String,
        from: j['from'] as String,
        to: j['to'] as String,
        typicalDurationMin: (j['typicalDurationMin'] as num?)?.toInt(),
        departuresPerWeek: (j['departuresPerWeek'] as num).toDouble(),
        nextDeparture: j['nextDeparture'] == null ? null : PortTime.parse(j['nextDeparture'] as String),
        fromPrice: Money.fromJson(j['fromPrice'] as Json),
        fromPriceDzd: Money.fromJson(j['fromPriceDzd'] as Json),
        popular: j['popular'] as bool,
        polyline: (j['polyline'] as List<dynamic>).map((p) => (p as List<dynamic>).map((v) => (v as num).toDouble()).toList()).toList(),
      );
}

class PopularRoute {
  const PopularRoute({required this.from, required this.to, required this.image, required this.operators, this.minDurationMin, this.maxDurationMin, this.fromPriceDzd});
  final String from;
  final String to;
  final String image;
  final List<String> operators;
  final int? minDurationMin;
  final int? maxDurationMin;
  final Money? fromPriceDzd;

  factory PopularRoute.fromJson(Json j) => PopularRoute(
        from: j['from'] as String,
        to: j['to'] as String,
        image: j['image'] as String,
        operators: (j['operators'] as List<dynamic>).cast<String>(),
        minDurationMin: (j['minDurationMin'] as num?)?.toInt(),
        maxDurationMin: (j['maxDurationMin'] as num?)?.toInt(),
        fromPriceDzd: j['fromPriceDzd'] == null ? null : Money.fromJson(j['fromPriceDzd'] as Json),
      );
}

class RateInfo {
  const RateInfo(this.dzdPerUnit, this.asOf, this.source);
  final Map<Currency, double> dzdPerUnit;
  final String asOf;
  final String source;

  factory RateInfo.fromJson(Json j) => RateInfo(
        (j['dzdPerUnit'] as Json).map((k, v) => MapEntry(currencyOf(k), double.parse(v as String))),
        j['asOf'] as String,
        j['source'] as String,
      );

  /// Client-side conversion only for indicative figures (static content); real prices come from the API.
  int convertMinor(int minor, Currency from, Currency to) {
    if (from == to) return minor;
    final dzd = minor * (dzdPerUnit[from] ?? 1);
    final value = dzd / (dzdPerUnit[to] ?? 1);
    return to == Currency.DZD ? (value / 100).round() * 100 : value.round();
  }
}

class Regulation {
  const Regulation(this.id, this.title, this.description, this.source);
  final String id;
  final LocalizedText title;
  final LocalizedText description;
  final String source;
  factory Regulation.fromJson(Json j) =>
      Regulation(j['id'] as String, LocalizedText.fromJson(j['title'] as Json), LocalizedText.fromJson(j['description'] as Json), j['source'] as String);
}

class Meta {
  const Meta({required this.version, required this.ports, required this.operators, required this.vessels, required this.routes, required this.popular, required this.rates, required this.maxPassengers, required this.maxPets, required this.paymentMethods, required this.regulations});
  final String version;
  final List<Port> ports;
  final List<Operator> operators;
  final List<Vessel> vessels;
  final List<RouteSummary> routes;
  final List<PopularRoute> popular;
  final RateInfo rates;
  final int maxPassengers;
  final int maxPets;
  final List<String> paymentMethods;
  final List<Regulation> regulations;

  factory Meta.fromJson(Json j) => Meta(
        version: j['version'] as String,
        ports: _list(j['ports'], Port.fromJson),
        operators: _list(j['operators'], Operator.fromJson),
        vessels: _list(j['vessels'], Vessel.fromJson),
        routes: _list(j['routes'], RouteSummary.fromJson),
        popular: _list(j['popular'], PopularRoute.fromJson),
        rates: RateInfo.fromJson(j['rates'] as Json),
        maxPassengers: ((j['limits'] as Json)['maxPassengers'] as num).toInt(),
        maxPets: ((j['limits'] as Json)['maxPets'] as num).toInt(),
        paymentMethods: (j['paymentMethods'] as List<dynamic>).cast<String>(),
        regulations: _list(j['regulations'], Regulation.fromJson),
      );

  Port? port(String code) => ports.where((p) => p.code == code).firstOrNull;
  Operator? operator(String code) => operators.where((o) => o.code == code).firstOrNull;
  Vessel? vessel(String code) => vessels.where((v) => v.code == code).firstOrNull;
}

// ------------------------------------------------------------------ search

class PriceLine {
  const PriceLine(this.kind, this.label, this.quantity, this.unit, this.total);
  final String kind;
  final String label;
  final int quantity;
  final Money unit;
  final Money total;
  factory PriceLine.fromJson(Json j) => PriceLine(j['kind'] as String, j['label'] as String, (j['quantity'] as num).toInt(),
      Money.fromJson(j['unit'] as Json), Money.fromJson(j['total'] as Json));
}

class TariffOffer {
  const TariffOffer(this.code, this.name, this.total, this.totalNative, this.refundable, this.modifiable, this.conditions);
  final String code;
  final String name;
  final Money total;
  final Money totalNative;
  final bool refundable;
  final bool modifiable;
  final String conditions;
  factory TariffOffer.fromJson(Json j) => TariffOffer(j['code'] as String, j['name'] as String, Money.fromJson(j['total'] as Json),
      Money.fromJson(j['totalNative'] as Json), j['refundable'] as bool, j['modifiable'] as bool, j['conditions'] as String);
}

class CabinAllocation {
  const CabinAllocation(this.type, this.count);
  final String type;
  final int count;
  factory CabinAllocation.fromJson(Json j) => CabinAllocation(j['type'] as String, (j['count'] as num).toInt());
}

class SailingOffer {
  const SailingOffer({required this.sailingId, required this.routeId, required this.operatorCode, required this.operatorName, required this.operatorColor, this.vesselCode, this.vesselName, this.amenities = const [], required this.from, required this.to, required this.fromName, required this.toName, required this.departure, required this.arrival, required this.durationMin, required this.status, required this.priceSource, required this.bookable, required this.reasons, required this.notes, required this.availability, this.seatsLeft, this.cabinsLeft, required this.accommodation, required this.cabins, this.cheapest, required this.tariffs, required this.breakdown, required this.promotions});
  final String sailingId;
  final String routeId;
  final String operatorCode;
  final String operatorName;
  final String operatorColor;
  final String? vesselCode;
  final String? vesselName;
  final List<String> amenities;
  final String from;
  final String to;
  final LocalizedText fromName;
  final LocalizedText toName;
  final PortTime departure;
  final PortTime arrival;
  final int durationMin;
  final String status;
  final String priceSource;
  final bool bookable;
  final List<Violation> reasons;
  final List<Violation> notes;
  final String availability;
  final int? seatsLeft;
  final int? cabinsLeft;
  final String accommodation;
  final List<CabinAllocation> cabins;
  final TariffOffer? cheapest;
  final List<TariffOffer> tariffs;
  final List<PriceLine> breakdown;
  final List<String> promotions;

  bool get isLive => priceSource == 'LIVE';

  factory SailingOffer.fromJson(Json j) {
    final op = j['operator'] as Json;
    final vessel = j['vessel'] as Json?;
    final avail = j['availability'] as Json;
    final from = j['from'] as Json;
    final to = j['to'] as Json;
    return SailingOffer(
      sailingId: j['sailingId'] as String,
      routeId: j['routeId'] as String,
      operatorCode: op['code'] as String,
      operatorName: op['name'] as String,
      operatorColor: op['color'] as String,
      vesselCode: vessel?['code'] as String?,
      vesselName: vessel?['name'] as String?,
      amenities: (vessel?['amenities'] as List<dynamic>? ?? const []).cast<String>(),
      from: from['code'] as String,
      to: to['code'] as String,
      fromName: LocalizedText.fromJson(from['name'] as Json),
      toName: LocalizedText.fromJson(to['name'] as Json),
      departure: PortTime.parse(j['departure'] as String),
      arrival: PortTime.parse(j['arrival'] as String),
      durationMin: (j['durationMin'] as num).toInt(),
      status: j['status'] as String,
      priceSource: j['priceSource'] as String,
      bookable: j['bookable'] as bool,
      reasons: _list(j['reasons'], Violation.fromJson),
      notes: _list(j['notes'], Violation.fromJson),
      availability: avail['level'] as String,
      seatsLeft: (avail['seatsLeft'] as num?)?.toInt(),
      cabinsLeft: (avail['cabinsLeft'] as num?)?.toInt(),
      accommodation: j['accommodation'] as String,
      cabins: _list(j['cabins'], CabinAllocation.fromJson),
      cheapest: j['cheapest'] == null ? null : TariffOffer.fromJson(j['cheapest'] as Json),
      tariffs: _list(j['tariffs'], TariffOffer.fromJson),
      breakdown: _list(j['breakdown'], PriceLine.fromJson),
      promotions: (j['promotions'] as List<dynamic>? ?? const []).cast<String>(),
    );
  }
}

class DayPrice {
  const DayPrice(this.date, this.minPrice, this.sailings);
  final DateTime date;
  final Money? minPrice;
  final int sailings;
  factory DayPrice.fromJson(Json j) => DayPrice(DateTime.parse(j['date'] as String),
      j['minPrice'] == null ? null : Money.fromJson(j['minPrice'] as Json), (j['sailings'] as num).toInt());
}

class LegResults {
  const LegResults(this.date, this.offers, this.nearbyDays);
  final DateTime date;
  final List<SailingOffer> offers;
  final List<DayPrice> nearbyDays;
  factory LegResults.fromJson(Json j) =>
      LegResults(DateTime.parse(j['date'] as String), _list(j['offers'], SailingOffer.fromJson), _list(j['nearbyDays'], DayPrice.fromJson));
}

class SearchResponse {
  const SearchResponse(this.currency, this.outbound, this.inbound, this.notices, this.rates);
  final Currency currency;
  final LegResults outbound;
  final LegResults? inbound;
  final List<Violation> notices;
  final RateInfo rates;
  factory SearchResponse.fromJson(Json j) => SearchResponse(
        currencyOf(j['currency'] as String),
        LegResults.fromJson(j['outbound'] as Json),
        j['inbound'] == null ? null : LegResults.fromJson(j['inbound'] as Json),
        _list(j['notices'], Violation.fromJson),
        RateInfo.fromJson(j['rates'] as Json),
      );
}

// ------------------------------------------------------------------ booking

class LegQuote {
  const LegQuote({required this.sailingId, required this.operator, this.vessel, required this.from, required this.to, required this.departure, required this.arrival, required this.tariffCode, required this.tariffName, required this.accommodation, required this.cabins, required this.lines, required this.total, required this.priceSource});
  final String sailingId;
  final String operator;
  final String? vessel;
  final String from;
  final String to;
  final PortTime departure;
  final PortTime arrival;
  final String tariffCode;
  final LocalizedText tariffName;
  final String accommodation;
  final List<CabinAllocation> cabins;
  final List<PriceLine> lines;
  final Money total;
  final String priceSource;

  factory LegQuote.fromJson(Json j) {
    final tariff = j['tariff'] as Json;
    return LegQuote(
      sailingId: j['sailingId'] as String,
      operator: j['operator'] as String,
      vessel: j['vessel'] as String?,
      from: j['from'] as String,
      to: j['to'] as String,
      departure: PortTime.parse(j['departure'] as String),
      arrival: PortTime.parse(j['arrival'] as String),
      tariffCode: tariff['code'] as String,
      tariffName: LocalizedText.fromJson(tariff['name'] as Json),
      accommodation: j['accommodation'] as String,
      cabins: _list(j['cabins'], CabinAllocation.fromJson),
      lines: _list(j['lines'], PriceLine.fromJson),
      total: Money.fromJson(j['total'] as Json),
      priceSource: j['priceSource'] as String,
    );
  }
}

class Quote {
  const Quote(this.legs, this.adjustments, this.total, this.payable, this.notices);
  final List<LegQuote> legs;
  final List<PriceLine> adjustments;
  final Money total;
  final Map<Currency, Money> payable;
  final List<Violation> notices;
  factory Quote.fromJson(Json j) => Quote(
        _list(j['legs'], LegQuote.fromJson),
        _list(j['adjustments'], PriceLine.fromJson),
        Money.fromJson(j['total'] as Json),
        (j['payable'] as Json? ?? const {}).map((k, v) => MapEntry(currencyOf(k), Money.fromJson(v as Json))),
        _list(j['notices'], Violation.fromJson),
      );
}

class TravellerView {
  const TravellerView(this.firstName, this.lastName, this.documentNumber, this.dateOfBirth);
  final String firstName;
  final String lastName;
  final String documentNumber;
  final String dateOfBirth;
  factory TravellerView.fromJson(Json j) =>
      TravellerView(j['firstName'] as String, j['lastName'] as String, j['documentNumber'] as String, j['dateOfBirth'] as String);
}

class PaymentRecord {
  const PaymentRecord(this.id, this.method, this.status, this.amount, this.message);
  final String id;
  final String method;
  final String status;
  final Money amount;
  final String? message;
  factory PaymentRecord.fromJson(Json j) =>
      PaymentRecord(j['id'] as String, j['method'] as String, j['status'] as String, Money.fromJson(j['amount'] as Json), j['message'] as String?);
}

class BookingView {
  const BookingView({required this.reference, required this.status, required this.email, required this.phone, required this.legs, required this.adjustments, required this.total, required this.payable, required this.travellers, this.holdExpiresAt, required this.payments, this.ticketPayload, required this.notices});
  final String reference;
  final String status;
  final String email;
  final String phone;
  final List<LegQuote> legs;
  final List<PriceLine> adjustments;
  final Money total;
  final Map<Currency, Money> payable;
  final List<TravellerView> travellers;
  final DateTime? holdExpiresAt;
  final List<PaymentRecord> payments;
  final String? ticketPayload;
  final List<Violation> notices;

  factory BookingView.fromJson(Json j) {
    final contact = j['contact'] as Json;
    return BookingView(
      reference: j['reference'] as String,
      status: j['status'] as String,
      email: contact['email'] as String,
      phone: contact['phone'] as String,
      legs: _list(j['legs'], LegQuote.fromJson),
      adjustments: _list(j['adjustments'], PriceLine.fromJson),
      total: Money.fromJson(j['total'] as Json),
      payable: (j['payable'] as Json? ?? const {}).map((k, v) => MapEntry(currencyOf(k), Money.fromJson(v as Json))),
      travellers: _list(j['travellers'], TravellerView.fromJson),
      holdExpiresAt: j['holdExpiresAt'] == null ? null : DateTime.parse(j['holdExpiresAt'] as String).toLocal(),
      payments: _list(j['payments'], PaymentRecord.fromJson),
      ticketPayload: (j['ticket'] as Json?)?['qrPayload'] as String?,
      notices: _list(j['notices'], Violation.fromJson),
    );
  }

  bool get isConfirmed => status == 'CONFIRMED' || status == 'TICKETED';
}

class PaymentInitiation {
  const PaymentInitiation(this.paymentId, this.method, this.amount, this.redirectUrl, this.instructions);
  final String paymentId;
  final String method;
  final Money amount;
  final String? redirectUrl;
  final String? instructions;
  factory PaymentInitiation.fromJson(Json j) => PaymentInitiation(j['paymentId'] as String, j['method'] as String,
      Money.fromJson(j['amount'] as Json), j['redirectUrl'] as String?, j['instructions'] as String?);
}

class CancellationResult {
  const CancellationResult(this.fee, this.refund);
  final Money fee;
  final Money refund;
  factory CancellationResult.fromJson(Json j) => CancellationResult(Money.fromJson(j['fee'] as Json), Money.fromJson(j['refund'] as Json));
}

// ------------------------------------------------------------------ live & content

class VesselPosition {
  const VesselPosition({required this.vessel, required this.name, required this.operator, required this.operatorColor, required this.lat, required this.lon, required this.speedKn, required this.courseDeg, required this.status, required this.source, this.from, this.to, this.departure, this.eta, this.progress});
  final String vessel;
  final String name;
  final String operator;
  final String operatorColor;
  final double lat;
  final double lon;
  final double speedKn;
  final double courseDeg;
  final String status;
  final String source;
  final String? from;
  final String? to;
  final PortTime? departure;
  final PortTime? eta;
  final double? progress;

  bool get underway => status == 'UNDERWAY';
  bool get isAis => source == 'AIS';

  factory VesselPosition.fromJson(Json j) => VesselPosition(
        vessel: j['vessel'] as String,
        name: j['name'] as String,
        operator: j['operator'] as String,
        operatorColor: j['operatorColor'] as String,
        lat: (j['lat'] as num).toDouble(),
        lon: (j['lon'] as num).toDouble(),
        speedKn: (j['speedKn'] as num).toDouble(),
        courseDeg: (j['courseDeg'] as num).toDouble(),
        status: j['status'] as String,
        source: j['source'] as String,
        from: j['from'] as String?,
        to: j['to'] as String?,
        departure: j['departure'] == null ? null : PortTime.parse(j['departure'] as String),
        eta: j['eta'] == null ? null : PortTime.parse(j['eta'] as String),
        progress: (j['progress'] as num?)?.toDouble(),
      );
}

class GuideSection {
  const GuideSection(this.title, this.body, this.source);
  final LocalizedText title;
  final LocalizedText body;
  final String? source;
  factory GuideSection.fromJson(Json j) =>
      GuideSection(LocalizedText.fromJson(j['title'] as Json), LocalizedText.fromJson(j['body'] as Json), j['source'] as String?);
}

class Guide {
  const Guide(this.id, this.icon, this.title, this.summary, this.sections);
  final String id;
  final String icon;
  final LocalizedText title;
  final LocalizedText summary;
  final List<GuideSection> sections;
  factory Guide.fromJson(Json j) => Guide(j['id'] as String, j['icon'] as String, LocalizedText.fromJson(j['title'] as Json),
      LocalizedText.fromJson(j['summary'] as Json), _list(j['sections'], GuideSection.fromJson));
}

class Deal {
  const Deal(this.id, this.operator, this.title, this.description, this.discount, this.estimated, this.badge, this.image, this.bookTo, this.source);
  final String id;
  final String? operator;
  final LocalizedText title;
  final LocalizedText description;
  final double discount;
  final bool estimated;
  final String? badge;
  final String? image;
  final String? bookTo;
  final String? source;
  factory Deal.fromJson(Json j) => Deal(
        j['id'] as String,
        j['operator'] as String?,
        LocalizedText.fromJson(j['title'] as Json),
        LocalizedText.fromJson(j['description'] as Json),
        (j['discount'] as num).toDouble(),
        j['estimated'] as bool? ?? false,
        j['badge'] as String?,
        j['image'] as String?,
        j['bookTo'] as String?,
        j['source'] as String?,
      );
}
