import 'l10n.dart';

/// Countries offered in nationality / passport / registration pickers, most frequent first
/// for Algeria–Europe crossings. Codes are ISO 3166-1 alpha-2, as the API expects.
class Country {
  const Country(this.code, this.fr, this.en, this.ar);
  final String code;
  final String fr;
  final String en;
  final String ar;

  String name(AppLang lang) => switch (lang) { AppLang.fr => fr, AppLang.en => en, AppLang.ar => ar };
}

const countries = <Country>[
  Country('DZ', 'Algérie', 'Algeria', 'الجزائر'),
  Country('FR', 'France', 'France', 'فرنسا'),
  Country('ES', 'Espagne', 'Spain', 'إسبانيا'),
  Country('IT', 'Italie', 'Italy', 'إيطاليا'),
  Country('BE', 'Belgique', 'Belgium', 'بلجيكا'),
  Country('DE', 'Allemagne', 'Germany', 'ألمانيا'),
  Country('GB', 'Royaume-Uni', 'United Kingdom', 'المملكة المتحدة'),
  Country('CA', 'Canada', 'Canada', 'كندا'),
  Country('CH', 'Suisse', 'Switzerland', 'سويسرا'),
  Country('NL', 'Pays-Bas', 'Netherlands', 'هولندا'),
  Country('TN', 'Tunisie', 'Tunisia', 'تونس'),
  Country('MA', 'Maroc', 'Morocco', 'المغرب'),
  Country('LY', 'Libye', 'Libya', 'ليبيا'),
  Country('MR', 'Mauritanie', 'Mauritania', 'موريتانيا'),
  Country('EG', 'Égypte', 'Egypt', 'مصر'),
  Country('PT', 'Portugal', 'Portugal', 'البرتغال'),
  Country('LU', 'Luxembourg', 'Luxembourg', 'لوكسمبورغ'),
  Country('AT', 'Autriche', 'Austria', 'النمسا'),
  Country('SE', 'Suède', 'Sweden', 'السويد'),
  Country('DK', 'Danemark', 'Denmark', 'الدنمارك'),
  Country('NO', 'Norvège', 'Norway', 'النرويج'),
  Country('FI', 'Finlande', 'Finland', 'فنلندا'),
  Country('IE', 'Irlande', 'Ireland', 'أيرلندا'),
  Country('PL', 'Pologne', 'Poland', 'بولندا'),
  Country('CZ', 'Tchéquie', 'Czechia', 'التشيك'),
  Country('GR', 'Grèce', 'Greece', 'اليونان'),
  Country('MT', 'Malte', 'Malta', 'مالطا'),
  Country('TR', 'Turquie', 'Türkiye', 'تركيا'),
  Country('US', 'États-Unis', 'United States', 'الولايات المتحدة'),
  Country('SA', 'Arabie saoudite', 'Saudi Arabia', 'السعودية'),
  Country('AE', 'Émirats arabes unis', 'United Arab Emirates', 'الإمارات'),
  Country('QA', 'Qatar', 'Qatar', 'قطر'),
  Country('JO', 'Jordanie', 'Jordan', 'الأردن'),
  Country('LB', 'Liban', 'Lebanon', 'لبنان'),
  Country('SY', 'Syrie', 'Syria', 'سوريا'),
  Country('PS', 'Palestine', 'Palestine', 'فلسطين'),
  Country('IQ', 'Irak', 'Iraq', 'العراق'),
  Country('SD', 'Soudan', 'Sudan', 'السودان'),
  Country('ML', 'Mali', 'Mali', 'مالي'),
  Country('NE', 'Niger', 'Niger', 'النيجر'),
  Country('SN', 'Sénégal', 'Senegal', 'السنغال'),
  Country('CI', 'Côte d\'Ivoire', 'Côte d\'Ivoire', 'ساحل العاج'),
  Country('CM', 'Cameroun', 'Cameroon', 'الكاميرون'),
  Country('NG', 'Nigeria', 'Nigeria', 'نيجيريا'),
  Country('CN', 'Chine', 'China', 'الصين'),
  Country('JP', 'Japon', 'Japan', 'اليابان'),
  Country('RU', 'Russie', 'Russia', 'روسيا'),
  Country('UA', 'Ukraine', 'Ukraine', 'أوكرانيا'),
  Country('BR', 'Brésil', 'Brazil', 'البرازيل'),
  Country('AU', 'Australie', 'Australia', 'أستراليا'),
];

String countryName(String code, AppLang lang) =>
    countries.where((c) => c.code == code).firstOrNull?.name(lang) ?? code;
