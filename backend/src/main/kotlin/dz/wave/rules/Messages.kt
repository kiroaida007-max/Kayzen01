package dz.wave.rules

import dz.wave.domain.Lang
import dz.wave.domain.LocalizedText
import dz.wave.domain.RuleSeverity
import dz.wave.domain.Violation

/** Server-side wording of every rule so all clients show the same, reviewed text. */
object Messages {
    private val templates: Map<String, LocalizedText> = mapOf(
        // Search request
        "PORT_UNKNOWN" to t(
            "Port inconnu : {port}.",
            "Unknown port: {port}.",
            "ميناء غير معروف: {port}.",
        ),
        "ROUTE_SAME_PORT" to t(
            "Le port de départ et le port d'arrivée doivent être différents.",
            "Departure and arrival ports must be different.",
            "يجب أن يكون ميناء المغادرة مختلفًا عن ميناء الوصول.",
        ),
        "NO_ROUTE" to t(
            "Aucune traversée directe entre {from} et {to}.",
            "No direct crossing between {from} and {to}.",
            "لا توجد رحلة بحرية مباشرة بين {from} و{to}.",
        ),
        "OPERATOR_UNKNOWN" to t(
            "Compagnie inconnue : {operator}.",
            "Unknown operator: {operator}.",
            "شركة غير معروفة: {operator}.",
        ),
        "DATE_IN_PAST" to t(
            "La date de départ est déjà passée.",
            "The departure date is in the past.",
            "تاريخ المغادرة قد مضى.",
        ),
        "DATE_TOO_FAR" to t(
            "Les réservations ouvrent au maximum {days} jours à l'avance.",
            "Bookings open at most {days} days ahead.",
            "تُفتح الحجوزات قبل {days} يومًا كحد أقصى.",
        ),
        "RETURN_REQUIRED" to t(
            "Choisissez une date de retour pour un aller-retour.",
            "Choose a return date for a round trip.",
            "اختر تاريخ العودة لرحلة الذهاب والإياب.",
        ),
        "RETURN_BEFORE_DEPARTURE" to t(
            "Le retour doit avoir lieu le jour de l'aller ou après.",
            "The return must be on or after the outbound date.",
            "يجب أن تكون العودة في يوم الذهاب أو بعده.",
        ),
        "PAX_ADULT_REQUIRED" to t(
            "Au moins un adulte (18 ans ou plus) doit voyager : les mineurs ne peuvent pas réserver seuls.",
            "At least one adult (18+) must travel: minors cannot book alone.",
            "يجب أن يسافر شخص بالغ واحد على الأقل (18 سنة فما فوق): لا يمكن للقاصرين الحجز بمفردهم.",
        ),
        "PAX_MAX" to t(
            "{max} passagers maximum par réservation.",
            "Maximum {max} passengers per booking.",
            "الحد الأقصى {max} مسافرين لكل حجز.",
        ),
        "PAX_INVALID" to t(
            "Nombre de passagers invalide.",
            "Invalid number of passengers.",
            "عدد المسافرين غير صالح.",
        ),
        "CHILD_AGE_INVALID" to t(
            "L'âge de chaque enfant doit être compris entre 0 et 17 ans.",
            "Each child's age must be between 0 and 17.",
            "يجب أن يكون عمر كل طفل بين 0 و17 سنة.",
        ),
        "INFANTS_EXCEED_ADULTS" to t(
            "Chaque bébé de moins de 2 ans doit être accompagné de son propre adulte.",
            "Each infant under 2 must be accompanied by their own adult.",
            "يجب أن يرافق كل رضيع دون السنتين شخصٌ بالغ خاص به.",
        ),
        "VEHICLE_DIMENSIONS_INVALID" to t(
            "Dimensions du véhicule invalides.",
            "Invalid vehicle dimensions.",
            "أبعاد المركبة غير صالحة.",
        ),
        "VEHICLE_TOO_HIGH" to t(
            "Les véhicules de plus de {max} m de haut ne sont pas acceptés.",
            "Vehicles higher than {max} m are not accepted.",
            "لا تُقبل المركبات التي يتجاوز ارتفاعها {max} م.",
        ),
        "VEHICLE_TOO_LONG" to t(
            "Les véhicules de plus de {max} m de long ne sont pas acceptés.",
            "Vehicles longer than {max} m are not accepted.",
            "لا تُقبل المركبات التي يتجاوز طولها {max} م.",
        ),
        "PETS_MAX" to t(
            "{max} animaux maximum par réservation.",
            "Maximum {max} animals per booking.",
            "الحد الأقصى {max} حيوانات لكل حجز.",
        ),
        "PET_COUNT_INVALID" to t(
            "Nombre d'animaux invalide.",
            "Invalid number of animals.",
            "عدد الحيوانات غير صالح.",
        ),
        "FLEX_DAYS_INVALID" to t(
            "La flexibilité doit être comprise entre 0 et 3 jours.",
            "Date flexibility must be between 0 and 3 days.",
            "يجب أن تكون مرونة التاريخ بين 0 و3 أيام.",
        ),
        // Notices
        "DOC_PASSPORT" to t(
            "Passeport valide obligatoire (la carte d'identité n'est pas acceptée sur les lignes d'Algérie). Visa Schengen ou titre de séjour requis pour les ressortissants algériens vers l'Europe.",
            "A valid passport is mandatory (ID cards are not accepted on Algeria lines). Algerian nationals travelling to Europe need a Schengen visa or a residence permit.",
            "جواز سفر صالح إلزامي (لا تُقبل بطاقة التعريف على خطوط الجزائر). يحتاج المواطنون الجزائريون المسافرون إلى أوروبا إلى تأشيرة شنغن أو بطاقة إقامة.",
        ),
        "MINOR_AUTHORIZATION" to t(
            "Mineurs : prévoyez l'autorisation de sortie du territoire (AST) si l'enfant voyage sans l'un de ses parents, et l'autorisation paternelle pour quitter l'Algérie sans le père.",
            "Minors: bring the exit authorisation (AST) if the child travels without one of their parents, and the paternal authorisation to leave Algeria without the father.",
            "القاصرون: أحضروا ترخيص الخروج من التراب (AST) إذا سافر الطفل دون أحد والديه، والترخيص الأبوي لمغادرة الجزائر دون الأب.",
        ),
        "VEHICLE_DOCS" to t(
            "Véhicule : carte grise au nom du conducteur (ou procuration), permis de conduire, assurance carte verte valable en Algérie et titre de passage en douane (TPD, demande en ligne sur ALCES).",
            "Vehicle: registration certificate in the driver's name (or power of attorney), driving licence, green-card insurance valid in Algeria and the customs pass (TPD, apply online on ALCES).",
            "المركبة: البطاقة الرمادية باسم السائق (أو وكالة)، رخصة السياقة، تأمين البطاقة الخضراء صالح في الجزائر، وسند المرور الجمركي (TPD، يُطلب عبر منصة ALCES).",
        ),
        "PET_DOCS" to t(
            "Animaux : puce électronique, vaccin antirabique valide, certificat sanitaire visé dans les 48 h avant l'embarquement. Retour vers l'UE : titrage antirabique réalisé au moins 3 mois avant.",
            "Pets: microchip, valid rabies vaccination, health certificate endorsed within 48 h before boarding. Return to the EU: rabies antibody titration done at least 3 months before.",
            "الحيوانات: شريحة إلكترونية، تلقيح ساري ضد داء الكلب، وشهادة صحية مصادق عليها خلال 48 ساعة قبل الصعود. للعودة إلى الاتحاد الأوروبي: معايرة الأجسام المضادة لداء الكلب قبل 3 أشهر على الأقل.",
        ),
        "PMR_NOTICE" to t(
            "Assistance PMR : signalez vos besoins au moins 48 h avant le départ (règlement UE 1177/2010).",
            "Reduced-mobility assistance: notify your needs at least 48 h before departure (EU Regulation 1177/2010).",
            "مساعدة ذوي الحركة المحدودة: أبلغوا عن احتياجاتكم قبل 48 ساعة على الأقل من المغادرة (اللائحة الأوروبية 1177/2010).",
        ),
        "CHECKIN" to t(
            "Enregistrement : présentez-vous au port 2 h avant le départ à pied, 3 h avant avec un véhicule. Retard = embarquement refusé.",
            "Check-in: be at the port 2 h before departure on foot, 3 h before with a vehicle. Late arrivals may be refused.",
            "التسجيل: احضروا إلى الميناء قبل ساعتين من المغادرة للمشاة، و3 ساعات مع مركبة. قد يُرفض صعود المتأخرين.",
        ),
        "SCHEDULE_NOT_PUBLISHED" to t(
            "{operator} n'a pas encore publié ses horaires pour cette date (publiés jusqu'au {date}).",
            "{operator} has not published sailings for this date yet (published until {date}).",
            "لم تنشر {operator} بعد مواعيد هذا التاريخ (منشورة حتى {date}).",
        ),
        "NO_SAILING_ON_DATE" to t(
            "Aucun départ ce jour-là. Prochain départ : {date}.",
            "No departure on that day. Next departure: {date}.",
            "لا توجد رحلة في ذلك اليوم. الرحلة القادمة: {date}.",
        ),
        "PRICE_REFERENCE" to t(
            "Prix indicatif calculé sur les tarifs publics de la compagnie, confirmé au moment de la réservation.",
            "Indicative price based on the operator's public fares, confirmed at booking.",
            "سعر تقديري مبني على الأسعار المعلنة للشركة، يتم تأكيده عند الحجز.",
        ),
        // Sailing eligibility
        "SAILING_CANCELLED" to t(
            "Traversée annulée par la compagnie.",
            "Sailing cancelled by the operator.",
            "ألغت الشركة هذه الرحلة.",
        ),
        "SAILING_CLOSED" to t(
            "Vente fermée : départ dans moins de {hours} h.",
            "Sales closed: departure in less than {hours} h.",
            "البيع مغلق: المغادرة بعد أقل من {hours} ساعات.",
        ),
        "PETS_NOT_ACCEPTED" to t(
            "Ce navire n'accepte pas les animaux.",
            "This ship does not accept animals.",
            "هذه السفينة لا تقبل الحيوانات.",
        ),
        "PET_CABIN_UNAVAILABLE" to t(
            "Pas de cabine animaux disponible : choisissez le chenil.",
            "No pet-friendly cabin available: choose the kennel.",
            "لا توجد مقصورة متاحة للحيوانات: اختر بيت الحيوانات.",
        ),
        "PET_KENNEL_UNAVAILABLE" to t(
            "Plus de place au chenil sur cette traversée.",
            "The kennel is full on this sailing.",
            "بيت الحيوانات ممتلئ في هذه الرحلة.",
        ),
        "PETS_IN_KENNEL" to t(
            "En fauteuil, votre animal voyage au chenil (muselière et laisse sur les ponts extérieurs).",
            "With a seat, your pet travels in the kennel (muzzle and leash on outside decks).",
            "مع المقعد، يسافر حيوانك في بيت الحيوانات (كمامة ورسن على الأسطح الخارجية).",
        ),
        "VEHICLE_TOO_HIGH_VESSEL" to t(
            "Véhicule trop haut pour ce navire (maximum {max} m).",
            "Vehicle too high for this ship (maximum {max} m).",
            "المركبة أعلى من المسموح به في هذه السفينة (الحد الأقصى {max} م).",
        ),
        "VEHICLE_TOO_LONG_VESSEL" to t(
            "Véhicule trop long pour ce navire (maximum {max} m).",
            "Vehicle too long for this ship (maximum {max} m).",
            "المركبة أطول من المسموح به في هذه السفينة (الحد الأقصى {max} م).",
        ),
        "VEHICLE_NOT_ACCEPTED" to t(
            "Ce type de véhicule n'est pas accepté sur cette traversée.",
            "This type of vehicle is not accepted on this sailing.",
            "هذا النوع من المركبات غير مقبول في هذه الرحلة.",
        ),
        "VEHICLE_SPACE_FULL" to t(
            "Garage complet sur cette traversée.",
            "The car deck is full on this sailing.",
            "مرآب السفينة ممتلئ في هذه الرحلة.",
        ),
        "PMR_CABIN_UNAVAILABLE" to t(
            "Aucune cabine adaptée aux personnes à mobilité réduite n'est disponible.",
            "No cabin adapted for reduced mobility is available.",
            "لا توجد مقصورة مهيأة لذوي الحركة المحدودة متاحة.",
        ),
        "CABINS_NEED_ADULTS" to t(
            "Chaque cabine doit être occupée par au moins un adulte : pas assez d'adultes pour répartir le groupe.",
            "Each cabin needs at least one adult: not enough adults to split the group.",
            "يجب أن يكون في كل مقصورة شخص بالغ على الأقل: عدد البالغين غير كافٍ لتوزيع المجموعة.",
        ),
        "CABINS_FULL" to t(
            "Plus de cabine disponible correspondant à votre choix.",
            "No cabin left matching your choice.",
            "لم تعد هناك مقصورة متاحة تطابق اختيارك.",
        ),
        "SEATS_FULL" to t(
            "Plus de place disponible sur cette traversée.",
            "No seats left on this sailing.",
            "لم تعد هناك مقاعد متاحة في هذه الرحلة.",
        ),
        "ACCOMMODATION_NOT_OFFERED" to t(
            "Ce type d'hébergement n'existe pas sur ce navire.",
            "This accommodation type is not available on this ship.",
            "هذا النوع من الإقامة غير متوفر على هذه السفينة.",
        ),
        "VESSEL_TBA" to t(
            "Navire communiqué ultérieurement par la compagnie.",
            "Ship to be announced by the operator.",
            "ستعلن الشركة عن السفينة لاحقًا.",
        ),
        // Booking
        "SAILING_NOT_FOUND" to t(
            "Cette traversée n'est plus disponible. Relancez la recherche.",
            "This sailing is no longer available. Please search again.",
            "هذه الرحلة لم تعد متاحة. يرجى إعادة البحث.",
        ),
        "LEG_ORDER_INVALID" to t(
            "Le retour doit partir après l'arrivée de l'aller.",
            "The return must depart after the outbound arrival.",
            "يجب أن تنطلق رحلة العودة بعد وصول رحلة الذهاب.",
        ),
        "LEG_ROUTE_MISMATCH" to t(
            "Le retour doit relier les mêmes villes en sens inverse.",
            "The return must connect the same cities in the opposite direction.",
            "يجب أن تربط رحلة العودة نفس المدن في الاتجاه المعاكس.",
        ),
        "TARIFF_UNAVAILABLE" to t(
            "Ce tarif n'est plus disponible pour cette traversée.",
            "This fare is no longer available for this sailing.",
            "هذه التعريفة لم تعد متاحة لهذه الرحلة.",
        ),
        "TRAVELLER_COUNT_MISMATCH" to t(
            "Renseignez exactement un voyageur par passager ({count}).",
            "Provide exactly one traveller per passenger ({count}).",
            "أدخل مسافرًا واحدًا بالضبط لكل راكب ({count}).",
        ),
        "TRAVELLER_AGE_MISMATCH" to t(
            "Les dates de naissance ne correspondent pas aux âges indiqués lors de la recherche.",
            "Dates of birth do not match the ages entered in the search.",
            "تواريخ الميلاد لا تتطابق مع الأعمار المدخلة في البحث.",
        ),
        "DOB_INVALID" to t(
            "Date de naissance invalide pour {name}.",
            "Invalid date of birth for {name}.",
            "تاريخ ميلاد غير صالح لـ {name}.",
        ),
        "NAME_INVALID" to t(
            "Nom ou prénom invalide : utilisez les lettres latines du passeport.",
            "Invalid first or last name: use the Latin letters printed in the passport.",
            "الاسم أو اللقب غير صالح: استخدم الأحرف اللاتينية المطبوعة في جواز السفر.",
        ),
        "NATIONALITY_INVALID" to t(
            "Nationalité invalide (code pays ISO à 2 lettres).",
            "Invalid nationality (2-letter ISO country code).",
            "جنسية غير صالحة (رمز البلد ISO من حرفين).",
        ),
        "DOC_NUMBER_INVALID" to t(
            "Numéro de document invalide pour {name}.",
            "Invalid document number for {name}.",
            "رقم الوثيقة غير صالح لـ {name}.",
        ),
        "DOC_ID_CARD_NOT_ACCEPTED" to t(
            "La carte d'identité n'est pas acceptée : un passeport est obligatoire pour {name}.",
            "ID cards are not accepted: {name} needs a passport.",
            "بطاقة التعريف غير مقبولة: جواز السفر إلزامي لـ {name}.",
        ),
        "DOC_EXPIRED" to t(
            "Le document de {name} expire avant la fin du voyage.",
            "{name}'s document expires before the end of the trip.",
            "تنتهي صلاحية وثيقة {name} قبل نهاية الرحلة.",
        ),
        "DOC_EXPIRES_SOON" to t(
            "Le passeport de {name} expire moins de 6 mois après le voyage : vérifiez les exigences de votre visa.",
            "{name}'s passport expires less than 6 months after the trip: check your visa requirements.",
            "ينتهي جواز سفر {name} بعد أقل من 6 أشهر من الرحلة: تحقق من متطلبات التأشيرة.",
        ),
        "EMAIL_INVALID" to t(
            "Adresse e-mail invalide.",
            "Invalid e-mail address.",
            "عنوان البريد الإلكتروني غير صالح.",
        ),
        "PHONE_INVALID" to t(
            "Numéro de téléphone invalide.",
            "Invalid phone number.",
            "رقم الهاتف غير صالح.",
        ),
        "TERMS_NOT_ACCEPTED" to t(
            "Vous devez accepter les conditions de transport et de vente.",
            "You must accept the conditions of carriage and sale.",
            "يجب قبول شروط النقل والبيع.",
        ),
        "VEHICLE_DETAILS_REQUIRED" to t(
            "Renseignez l'immatriculation, la marque et le modèle du véhicule.",
            "Provide the vehicle's plate, make and model.",
            "أدخل رقم التسجيل وعلامة وطراز المركبة.",
        ),
        "PLATE_INVALID" to t(
            "Immatriculation invalide.",
            "Invalid registration plate.",
            "رقم التسجيل غير صالح.",
        ),
        "PRICE_CHANGED" to t(
            "Le prix a changé depuis votre recherche : nouveau total {total}.",
            "The price changed since your search: new total {total}.",
            "تغيّر السعر منذ بحثك: المجموع الجديد {total}.",
        ),
        "SOLD_OUT_DURING_BOOKING" to t(
            "Les dernières places viennent d'être réservées. Choisissez une autre traversée.",
            "The last places were just booked. Please choose another sailing.",
            "تم حجز آخر الأماكن للتو. يرجى اختيار رحلة أخرى.",
        ),
        "BOOKING_NOT_FOUND" to t(
            "Réservation introuvable. Vérifiez la référence et le nom.",
            "Booking not found. Check the reference and the last name.",
            "الحجز غير موجود. تحقق من المرجع واللقب.",
        ),
        "BOOKING_NOT_PAYABLE" to t(
            "Cette réservation ne peut plus être payée (statut {status}).",
            "This booking can no longer be paid (status {status}).",
            "لم يعد بالإمكان دفع هذا الحجز (الحالة {status}).",
        ),
        "BOOKING_NOT_CANCELLABLE" to t(
            "Cette réservation ne peut pas être annulée (statut {status}).",
            "This booking cannot be cancelled (status {status}).",
            "لا يمكن إلغاء هذا الحجز (الحالة {status}).",
        ),
        "PAYMENT_METHOD_UNAVAILABLE" to t(
            "Ce moyen de paiement n'est pas disponible.",
            "This payment method is not available.",
            "طريقة الدفع هذه غير متاحة.",
        ),
    )

    private fun t(fr: String, en: String, ar: String) = LocalizedText(fr, en, ar)

    fun text(code: String, lang: Lang, params: Map<String, String> = emptyMap()): String {
        val template = templates[code]?.get(lang) ?: code
        return params.entries.fold(template) { acc, (k, v) -> acc.replace("{$k}", v) }
    }

    fun has(code: String): Boolean = code in templates

    fun violation(
        code: String,
        severity: RuleSeverity,
        lang: Lang,
        field: String? = null,
        params: Map<String, String> = emptyMap(),
    ): Violation = Violation(code, severity, text(code, lang, params), field, params)
}
