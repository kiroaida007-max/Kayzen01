import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/config.dart';
import '../../core/l10n.dart';
import '../../core/theme.dart';
import '../../widgets/brand.dart';
import '../../widgets/header.dart';

class _Video {
  const _Video(this.title, this.subtitle, this.image, this.query);
  final List<String> title;
  final List<String> subtitle;

  /// Asset name, or `#RRGGBB` for an illustrated ship in that company colour.
  final String image;
  final String query;
}

/// Curated videos (ship tours, boarding tutorials). Links open YouTube searches so they never go stale.
const _videos = [
  _Video(['À bord du Badji Mokhtar III', 'On board Badji Mokhtar III', 'على متن باجي مختار 3'], ['Algérie Ferries · visite du navire', 'Algérie Ferries · ship tour', 'الجزائرية للعبارات · جولة في السفينة'], '#0B7A4B', 'Badji Mokhtar III ferry visite'),
  _Video(['Embarquer en voiture au port d\'Alger', 'Boarding with a car in Algiers', 'الصعود بالسيارة في ميناء الجزائر'], ['Tutoriel · formalités et files d\'attente', 'Tutorial · formalities and queues', 'شرح · الإجراءات والطوابير'], 'dest_alger', 'embarquement voiture port Alger ferry'),
  _Video(['Baleària : Oran – Valence', 'Baleària: Oran – Valencia', 'باليريا: وهران – فالنسيا'], ['Cabines et ponts du navire', 'Cabins and decks', 'المقصورات والطوابق'], 'company_bal', 'Balearia Oran Valencia ferry'),
  _Video(['Corsica Linea : Marseille – Alger', 'Corsica Linea: Marseille – Algiers', 'كورسيكا لينيا: مرسيليا – الجزائر'], ['Traversée de nuit en cabine', 'Overnight crossing in a cabin', 'رحلة ليلية في مقصورة'], 'company_cl', 'Corsica Linea Marseille Alger traversée'),
  _Video(['GNV : Sète – Béjaïa', 'GNV: Sète – Béjaïa', 'جي إن في: سات – بجاية'], ['Le GNV Fantastic en vidéo', 'GNV Fantastic on video', 'سفينة GNV Fantastic بالفيديو'], 'company_gnv', 'GNV Sete Bejaia traversée'),
  _Video(['Nouris Elbahr Ferries', 'Nouris Elbahr Ferries', 'نورس البحر فيريز'], ['Le Cracovia vers Alger', 'Cracovia to Algiers', 'كراكوفيا نحو الجزائر'], 'company_ne', 'Nouris Elbahr ferries Cracovia'),
  _Video(['Choisir sa cabine', 'Choosing your cabin', 'اختيار المقصورة'], ['Intérieure, extérieure ou suite ?', 'Inside, outside or suite?', 'داخلية أم خارجية أم جناح؟'], 'cabin', 'cabine ferry Algérie intérieure extérieure'),
  _Video(['Marseille, porte de la Méditerranée', 'Marseille, gateway to the Med', 'مرسيليا بوابة المتوسط'], ['Terminal Cap Janet : arriver au port', 'Cap Janet terminal: getting there', 'محطة كاب جانيت: الوصول إلى الميناء'], 'dest_marseille', 'gare maritime Cap Janet Marseille ferry Algérie'),
];

class WaveTvPage extends StatelessWidget {
  const WaveTvPage({super.key});

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    return WavePage(
      title: context.t('tv.title'),
      subtitle: context.t('tv.subtitle'),
      child: LayoutBuilder(builder: (context, c) {
        final columns = c.maxWidth >= 1000 ? 4 : (c.maxWidth >= 640 ? 2 : 1);
        final width = (c.maxWidth - (columns - 1) * 16) / columns;
        return Wrap(spacing: 16, runSpacing: 16, children: [
          for (final v in _videos)
            SizedBox(
              width: width,
              child: Card(
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () => launchUrl(
                    Uri.https('www.youtube.com', '/results', {'search_query': v.query}),
                    mode: LaunchMode.externalApplication,
                  ),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    AspectRatio(
                      aspectRatio: 16 / 9,
                      child: Stack(fit: StackFit.expand, children: [
                        if (v.image.startsWith('#')) ShipArt(color: hexColor(v.image)) else Image.asset('assets/images/${v.image}.jpg', fit: BoxFit.cover),
                        Container(color: WaveColors.navyDeep.withValues(alpha: 0.25)),
                        Center(
                          child: Container(
                            padding: const EdgeInsets.all(10),
                            decoration: const BoxDecoration(gradient: WaveColors.goldGradient, shape: BoxShape.circle),
                            child: const Icon(Icons.play_arrow_rounded, size: 32, color: WaveColors.navyDeep),
                          ),
                        ),
                      ]),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(v.title[lang.index], style: const TextStyle(fontWeight: FontWeight.w700)),
                        const SizedBox(height: 4),
                        Text(v.subtitle[lang.index], style: const TextStyle(fontSize: 12.5, color: WaveColors.muted)),
                      ]),
                    ),
                  ]),
                ),
              ),
            ),
        ]);
      }),
    );
  }
}

class _Faq {
  const _Faq(this.q, this.a);
  final List<String> q;
  final List<String> a;
}

/// Answers mirror the rules the booking engine enforces (see docs/research/ferry-booking-algeria.md).
const _faq = [
  _Faq(
    ['Quels documents faut-il pour embarquer ?', 'Which documents do I need to board?', 'ما هي الوثائق اللازمة للصعود؟'],
    [
      'Un passeport valide pour tout le voyage pour chaque passager, y compris les bébés (la carte d\'identité n\'est pas acceptée). Les ressortissants algériens ont besoin d\'un visa Schengen ou d\'un titre de séjour pour l\'Europe ; les étrangers d\'un visa algérien.',
      'A passport valid for the whole trip for every passenger, babies included (ID cards are not accepted). Algerian nationals need a Schengen visa or residence permit for Europe; foreign nationals need an Algerian visa.',
      'جواز سفر صالح طوال الرحلة لكل مسافر بما في ذلك الرضع (بطاقة التعريف غير مقبولة). يحتاج الجزائريون إلى تأشيرة شنغن أو بطاقة إقامة لأوروبا، والأجانب إلى تأشيرة جزائرية.',
    ],
  ),
  _Faq(
    ['Mon enfant peut-il voyager sans moi ?', 'Can my child travel without me?', 'هل يمكن لطفلي السفر بدوني؟'],
    [
      'Chaque réservation doit compter au moins un adulte. Un mineur qui voyage sans ses deux parents doit présenter une autorisation de sortie du territoire (France) ou une autorisation paternelle légalisée (Algérie).',
      'Every booking needs at least one adult. A minor travelling without both parents must carry an exit authorisation (France) or a certified paternal authorisation (Algeria).',
      'يجب أن يضم كل حجز بالغا واحدا على الأقل. القاصر الذي يسافر بدون والديه يحتاج إلى ترخيص الخروج من التراب (فرنسا) أو ترخيص أبوي مصادق عليه (الجزائر).',
    ],
  ),
  _Faq(
    ['À quelle heure dois-je être au port ?', 'When should I be at the port?', 'متى يجب أن أكون في الميناء؟'],
    [
      'À pied : 2 h avant (fermeture 1 h avant). Avec un véhicule : 3 h avant (fermeture 1 h 30 avant). Un retard peut entraîner un refus d\'embarquement.',
      'On foot: 2 h before (closes 1 h before). With a vehicle: 3 h before (closes 1 h 30 before). Late passengers may be refused.',
      'راجلا: قبل ساعتين (يغلق قبل ساعة). مع مركبة: قبل 3 ساعات (يغلق قبل ساعة ونصف). قد يرفض صعود المتأخرين.',
    ],
  ),
  _Faq(
    ['Quels véhicules sont refusés en été ?', 'Which vehicles are refused in summer?', 'ما هي المركبات المرفوضة في الصيف؟'],
    [
      'Du 15 juin au 15 septembre, les fourgons et utilitaires ne peuvent pas embarquer vers les ports algériens, et les véhicules neufs ou de moins de 3 ans importés sont refusés à Alger et Oran. WAVE applique ces règles automatiquement dans la recherche.',
      'From 15 June to 15 September, vans and utility vehicles cannot sail to Algerian ports, and new or under-3-year-old imported vehicles are refused at Algiers and Oran. WAVE applies these rules automatically in search.',
      'من 15 جوان إلى 15 سبتمبر لا يمكن للشاحنات الصغيرة والنفعية الصعود نحو الموانئ الجزائرية، وترفض المركبات الجديدة أو التي يقل عمرها عن 3 سنوات في الجزائر ووهران. يطبق WAVE هذه القواعد تلقائيا.',
    ],
  ),
  _Faq(
    ['Puis-je voyager avec mon chien ou mon chat ?', 'Can I travel with my dog or cat?', 'هل يمكنني السفر مع كلبي أو قطي؟'],
    [
      'Oui, en chenil ou en cabine « animaux admis » selon le navire. Il faut une puce électronique, un vaccin antirabique valide, un certificat vétérinaire et, pour revenir dans l\'UE, un titrage antirabique fait au moins 3 mois avant.',
      'Yes, in a kennel or a pet-friendly cabin depending on the ship. You need a microchip, a valid rabies vaccination, a vet certificate and, to come back to the EU, a rabies antibody test done at least 3 months before.',
      'نعم، في بيت الحيوانات أو في مقصورة مخصصة حسب السفينة. يلزم شريحة إلكترونية وتلقيح ضد الكلب وشهادة بيطرية، وللعودة إلى الاتحاد الأوروبي تحليل الأجسام المضادة قبل 3 أشهر على الأقل.',
    ],
  ),
  _Faq(
    ['Comment payer en dinars ?', 'How do I pay in dinars?', 'كيف أدفع بالدينار؟'],
    [
      'Par carte CIB ou EDAHABIA via la plateforme sécurisée SATIM, ou en agence sous 24 h. Les prix en euros sont convertis au taux officiel de la Banque d\'Algérie affiché avec chaque prix.',
      'With a CIB or EDAHABIA card through the secure SATIM platform, or at the agency within 24 h. Euro prices are converted at the official Bank of Algeria rate shown with every price.',
      'ببطاقة CIB أو الذهبية عبر منصة ساتيم الآمنة، أو في الوكالة خلال 24 ساعة. تحول الأسعار باليورو بالسعر الرسمي لبنك الجزائر المعروض مع كل سعر.',
    ],
  ),
  _Faq(
    ['Puis-je annuler ma réservation ?', 'Can I cancel my booking?', 'هل يمكنني إلغاء حجزي؟'],
    [
      'Oui, depuis « Ma vague ». Les frais dépendent du tarif et de la date : par exemple 20 % jusqu\'à 30 jours avant le départ chez Algérie Ferries, et 100 % sous 48 h. Le montant exact est affiché avant de confirmer.',
      'Yes, from "My wave". Fees depend on the fare and the date: for example 20 % until 30 days before departure with Algérie Ferries, and 100 % within 48 h. The exact amount is shown before you confirm.',
      'نعم، من "موجتي". تعتمد الرسوم على التعريفة والتاريخ: مثلا 20٪ حتى 30 يوما قبل المغادرة لدى الجزائرية للعبارات و100٪ قبل أقل من 48 ساعة. يظهر المبلغ الدقيق قبل التأكيد.',
    ],
  ),
  _Faq(
    ['Combien de bagages puis-je emporter ?', 'How much baggage can I take?', 'كم من الأمتعة يمكنني أخذها؟'],
    [
      'Cela dépend de la compagnie : chez Algérie Ferries, 30 kg par adulte en fauteuil et 60 kg en cabine. Les franchises de chaque compagnie sont détaillées dans l\'onglet Compagnies.',
      'It depends on the company: with Algérie Ferries, 30 kg per adult with a seat and 60 kg in a cabin. Each company\'s allowance is listed in the Companies tab.',
      'يعتمد ذلك على الشركة: لدى الجزائرية للعبارات 30 كغ لكل بالغ مع المقعد و60 كغ في المقصورة. تفاصيل كل شركة في قسم الشركات.',
    ],
  ),
];

class CommunityPage extends StatelessWidget {
  const CommunityPage({super.key});

  @override
  Widget build(BuildContext context) {
    final lang = context.lang;
    return WavePage(
      title: context.t('community.title'),
      subtitle: context.t('community.subtitle'),
      maxWidth: 900,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Wrap(spacing: 16, runSpacing: 12, crossAxisAlignment: WrapCrossAlignment.center, children: [
              const Icon(Icons.forum_outlined, size: 34, color: WaveColors.navy),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 460),
                child: Text(context.t('community.subtitle'), style: const TextStyle(fontSize: 14.5)),
              ),
              GoldButton(label: context.t('community.ask'), icon: Icons.chat_rounded, height: 46, fontSize: 14, onPressed: () => openWhatsApp(context)),
              for (final n in AppConfig.whatsappNumbers)
                TextButton.icon(
                  onPressed: () => launchUrl(Uri.parse('tel:$n')),
                  icon: const Icon(Icons.call_outlined, size: 18),
                  label: Text(n, textDirection: TextDirection.ltr),
                ),
            ]),
          ),
        ),
        const SizedBox(height: 22),
        SectionTitle(context.t('community.faq'), size: 22),
        const SizedBox(height: 12),
        for (final f in _faq)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Card(
              clipBehavior: Clip.antiAlias,
              child: Theme(
                data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                child: ExpansionTile(
                  leading: const Icon(Icons.help_outline_rounded, color: WaveColors.goldDeep),
                  title: Text(f.q[lang.index], style: const TextStyle(fontWeight: FontWeight.w600)),
                  childrenPadding: const EdgeInsets.fromLTRB(18, 0, 18, 16),
                  expandedCrossAxisAlignment: CrossAxisAlignment.start,
                  children: [Text(f.a[lang.index], style: const TextStyle(fontSize: 14, height: 1.55))],
                ),
              ),
            ),
          ),
      ]),
    );
  }
}
