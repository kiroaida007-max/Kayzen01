package dz.wave.pricing

import dz.wave.domain.AccommodationPref
import dz.wave.domain.AccommodationType
import dz.wave.domain.Lang
import dz.wave.domain.LocalizedText
import dz.wave.domain.PenaltyTier
import dz.wave.domain.TariffDef

object Labels {
    private val accommodation = mapOf(
        AccommodationType.SEAT to LocalizedText("Fauteuil", "Seat", "مقعد"),
        AccommodationType.CABIN_INT_2 to LocalizedText("Cabine intérieure 2 lits", "Inside cabin, 2 berths", "مقصورة داخلية بسريرين"),
        AccommodationType.CABIN_INT_4 to LocalizedText("Cabine intérieure 4 lits", "Inside cabin, 4 berths", "مقصورة داخلية بأربعة أسرّة"),
        AccommodationType.CABIN_EXT_2 to LocalizedText("Cabine extérieure 2 lits", "Outside cabin, 2 berths", "مقصورة خارجية بسريرين"),
        AccommodationType.CABIN_EXT_4 to LocalizedText("Cabine extérieure 4 lits", "Outside cabin, 4 berths", "مقصورة خارجية بأربعة أسرّة"),
        AccommodationType.SUITE to LocalizedText("Suite", "Suite", "جناح"),
        AccommodationType.CABIN_PET to LocalizedText("Cabine animaux admis", "Pet-friendly cabin", "مقصورة يُسمح فيها بالحيوانات"),
        AccommodationType.CABIN_PMR to LocalizedText("Cabine adaptée PMR", "Accessible cabin", "مقصورة مهيأة لذوي الحركة المحدودة"),
    )

    private val preferences = mapOf(
        AccommodationPref.SEAT to LocalizedText("Fauteuil", "Seat", "مقعد"),
        AccommodationPref.CABIN_ANY to LocalizedText("Cabine (au choix)", "Cabin (any)", "مقصورة (حسب التوفر)"),
        AccommodationPref.CABIN_INTERIOR to LocalizedText("Cabine intérieure", "Inside cabin", "مقصورة داخلية"),
        AccommodationPref.CABIN_EXTERIOR to LocalizedText("Cabine extérieure", "Outside cabin", "مقصورة خارجية"),
        AccommodationPref.SUITE to LocalizedText("Suite", "Suite", "جناح"),
    )

    fun accommodation(type: AccommodationType, lang: Lang): String = accommodation.getValue(type).get(lang)

    fun preference(pref: AccommodationPref, lang: Lang): String = preferences.getValue(pref).get(lang)

    fun passage(categoryLabel: String, lang: Lang): String = when (lang) {
        Lang.FR -> "Passage $categoryLabel"
        Lang.EN -> "Fare $categoryLabel"
        Lang.AR -> "تذكرة $categoryLabel"
    }

    fun pmrReduction(lang: Lang) = when (lang) {
        Lang.FR -> "Réduction personne à mobilité réduite"
        Lang.EN -> "Reduced-mobility discount"
        Lang.AR -> "تخفيض ذوي الحركة المحدودة"
    }

    fun pet(inCabin: Boolean, lang: Lang) = when (lang) {
        Lang.FR -> if (inCabin) "Animal (cabine)" else "Animal (chenil)"
        Lang.EN -> if (inCabin) "Pet (cabin)" else "Pet (kennel)"
        Lang.AR -> if (inCabin) "حيوان (مقصورة)" else "حيوان (بيت الحيوانات)"
    }

    fun passengerTaxes(lang: Lang) = when (lang) {
        Lang.FR -> "Taxes portuaires et de sécurité (passagers)"
        Lang.EN -> "Port and security taxes (passengers)"
        Lang.AR -> "رسوم الميناء والأمن (المسافرون)"
    }

    fun vehicleTaxes(lang: Lang) = when (lang) {
        Lang.FR -> "Taxes portuaires (véhicule)"
        Lang.EN -> "Port taxes (vehicle)"
        Lang.AR -> "رسوم الميناء (المركبة)"
    }

    fun promotion(title: String, lang: Lang) = when (lang) {
        Lang.FR -> "Offre : $title"
        Lang.EN -> "Offer: $title"
        Lang.AR -> "عرض: $title"
    }

    fun roundTrip(lang: Lang) = when (lang) {
        Lang.FR -> "Réduction aller-retour"
        Lang.EN -> "Round-trip discount"
        Lang.AR -> "تخفيض الذهاب والإياب"
    }

    fun serviceFee(lang: Lang) = when (lang) {
        Lang.FR -> "Frais de service"
        Lang.EN -> "Service fee"
        Lang.AR -> "رسوم الخدمة"
    }

    /** Human summary of a tariff's cancellation policy, generated from the same tiers the engine applies. */
    fun policy(tariff: TariffDef, lang: Lang): String {
        if (!tariff.refundable) {
            return when (lang) {
                Lang.FR -> "Non remboursable" + if (!tariff.modifiable) ", non modifiable" else ", modifiable avec frais"
                Lang.EN -> "Non-refundable" + if (!tariff.modifiable) ", no changes" else ", changes with a fee"
                Lang.AR -> "غير قابلة للاسترداد" + if (!tariff.modifiable) "، غير قابلة للتعديل" else "، قابلة للتعديل برسوم"
            }
        }
        val parts = mutableListOf<String>()
        tariff.freeCancellationHoursAfterPurchase?.let { h ->
            parts += when (lang) {
                Lang.FR -> "annulation gratuite pendant ${h} h après l'achat"
                Lang.EN -> "free cancellation for ${h} h after purchase"
                Lang.AR -> "إلغاء مجاني خلال ${h} ساعة بعد الشراء"
            }
        }
        val tiers = tariff.cancellation.sortedByDescending { it.minHoursBefore }
        tiers.forEach { tier -> parts += tierText(tier, lang) }
        val head = when (lang) {
            Lang.FR -> "Frais d'annulation : "
            Lang.EN -> "Cancellation fees: "
            Lang.AR -> "رسوم الإلغاء: "
        }
        return head + parts.joinToString("; ")
    }

    private fun tierText(tier: PenaltyTier, lang: Lang): String {
        val pct = "${(tier.feePercent * 100).toInt()} %"
        val days = tier.minHoursBefore / 24
        val window = when {
            tier.minHoursBefore == 0 -> when (lang) {
                Lang.FR -> "ensuite"
                Lang.EN -> "afterwards"
                Lang.AR -> "بعد ذلك"
            }
            tier.minHoursBefore % 24 == 0 && days >= 3 -> when (lang) {
                Lang.FR -> "jusqu'à J-$days"
                Lang.EN -> "until $days days before"
                Lang.AR -> "حتى $days أيام قبل المغادرة"
            }
            else -> when (lang) {
                Lang.FR -> "jusqu'à ${tier.minHoursBefore} h avant"
                Lang.EN -> "until ${tier.minHoursBefore} h before"
                Lang.AR -> "حتى ${tier.minHoursBefore} ساعة قبل المغادرة"
            }
        }
        val fee = if (tier.feePercent == 0.0) {
            when (lang) {
                Lang.FR -> "gratuit"
                Lang.EN -> "free"
                Lang.AR -> "مجانًا"
            }
        } else {
            pct
        }
        return "$fee $window"
    }
}
