import '../core/formatters.dart';
import '../core/l10n.dart';

enum TripType { oneWay, roundTrip }

enum VehicleType { car, suv, motorcycle, camper, van }

enum AccommodationPref { seat, cabinAny, cabinInterior, cabinExterior, suite }

enum PetType { dog, cat, other }

enum PetPlacement { any, kennel, cabin }

enum SortOrder { price, departure, duration }

extension on Enum {
  /// API uses SCREAMING_SNAKE_CASE (`cabinAny` → `CABIN_ANY`).
  String get api => name.replaceAllMapped(RegExp('[A-Z]'), (m) => '_${m[0]}').toUpperCase();
}

class Passengers {
  const Passengers({this.adults = 1, this.seniors = 0, this.childrenAges = const []});
  final int adults;
  final int seniors;
  final List<int> childrenAges;

  int get total => adults + seniors + childrenAges.length;
  int get grownUps => adults + seniors;

  Passengers copyWith({int? adults, int? seniors, List<int>? childrenAges}) =>
      Passengers(adults: adults ?? this.adults, seniors: seniors ?? this.seniors, childrenAges: childrenAges ?? this.childrenAges);

  Map<String, dynamic> toJson() => {'adults': adults, 'seniors': seniors, 'childrenAges': childrenAges};

  String summary(AppLang lang) => Fmt.passengers(adults, seniors, childrenAges.length, lang);
}

class Vehicle {
  const Vehicle({required this.type, this.lengthM, this.heightM, this.withTrailer = false, this.roofBox = false, this.registrationYear});
  final VehicleType type;
  final double? lengthM;
  final double? heightM;
  final bool withTrailer;
  final bool roofBox;
  final int? registrationYear;

  Map<String, dynamic> toJson() => {
        'type': type.api,
        if (lengthM != null) 'lengthM': lengthM,
        if (heightM != null) 'heightM': heightM,
        'withTrailer': withTrailer,
        'roofBox': roofBox,
        if (registrationYear != null) 'registrationYear': registrationYear,
      };
}

class Pet {
  const Pet({required this.type, this.count = 1, this.placement = PetPlacement.any});
  final PetType type;
  final int count;
  final PetPlacement placement;
  Map<String, dynamic> toJson() => {'type': type.api, 'count': count, 'placement': placement.api};
}

class Accessibility {
  const Accessibility({this.wheelchair = false, this.reducedMobility = false, this.assistance = false});
  final bool wheelchair;
  final bool reducedMobility;
  final bool assistance;
  bool get any => wheelchair || reducedMobility || assistance;
  Map<String, dynamic> toJson() => {'wheelchair': wheelchair, 'reducedMobility': reducedMobility, 'assistance': assistance};
}

/// Everything the search form collects; serialised exactly as the backend expects.
class SearchQuery {
  const SearchQuery({
    this.tripType = TripType.oneWay,
    this.from,
    this.to,
    required this.departureDate,
    this.returnDate,
    this.passengers = const Passengers(),
    this.vehicle,
    this.accommodation = AccommodationPref.seat,
    this.pets = const [],
    this.accessibility = const Accessibility(),
    this.flexDays = 0,
    this.sort = SortOrder.price,
  });

  final TripType tripType;
  final String? from;
  final String? to;
  final DateTime departureDate;
  final DateTime? returnDate;
  final Passengers passengers;
  final Vehicle? vehicle;
  final AccommodationPref accommodation;
  final List<Pet> pets;
  final Accessibility accessibility;
  final int flexDays;
  final SortOrder sort;

  bool get isRoundTrip => tripType == TripType.roundTrip;
  bool get isComplete => from != null && to != null && (!isRoundTrip || returnDate != null);

  SearchQuery copyWith({
    TripType? tripType,
    String? from,
    String? to,
    DateTime? departureDate,
    DateTime? returnDate,
    bool clearReturn = false,
    Passengers? passengers,
    Vehicle? vehicle,
    bool clearVehicle = false,
    AccommodationPref? accommodation,
    List<Pet>? pets,
    Accessibility? accessibility,
    int? flexDays,
    SortOrder? sort,
    bool clearTo = false,
  }) =>
      SearchQuery(
        tripType: tripType ?? this.tripType,
        from: from ?? this.from,
        to: clearTo ? null : (to ?? this.to),
        departureDate: departureDate ?? this.departureDate,
        returnDate: clearReturn ? null : (returnDate ?? this.returnDate),
        passengers: passengers ?? this.passengers,
        vehicle: clearVehicle ? null : (vehicle ?? this.vehicle),
        accommodation: accommodation ?? this.accommodation,
        pets: pets ?? this.pets,
        accessibility: accessibility ?? this.accessibility,
        flexDays: flexDays ?? this.flexDays,
        sort: sort ?? this.sort,
      );

  Map<String, dynamic> toJson(Currency currency, AppLang lang) => {
        'tripType': tripType == TripType.roundTrip ? 'ROUND_TRIP' : 'ONE_WAY',
        'from': from,
        'to': to,
        'departureDate': Fmt.iso(departureDate),
        if (isRoundTrip && returnDate != null) 'returnDate': Fmt.iso(returnDate!),
        'passengers': passengers.toJson(),
        if (vehicle != null) 'vehicle': vehicle!.toJson(),
        'accommodation': accommodation.api,
        'pets': pets.map((p) => p.toJson()).toList(),
        'accessibility': accessibility.toJson(),
        'currency': currency.name,
        'flexDays': flexDays,
        'sort': sort.api,
        'lang': lang.name,
      };
}

String apiName(Enum value) => value.api;
