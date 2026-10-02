package dz.wave.rules

import dz.wave.domain.AgeBands
import dz.wave.domain.CategoryDiscounts
import dz.wave.domain.Lang
import dz.wave.domain.VehicleCategory
import dz.wave.domain.VehicleInput
import dz.wave.domain.VehicleType
import kotlin.math.ceil

enum class FareCategory { INFANT, CHILD, YOUTH, ADULT, SENIOR }

/** Maps an age to an operator's fare category; every operator draws the age bands differently. */
object FareClassifier {
    fun classify(age: Int, bands: AgeBands): FareCategory = when {
        age <= bands.infantMaxAge -> FareCategory.INFANT
        age <= bands.childMaxAge -> FareCategory.CHILD
        bands.youthMaxAge != null && age <= bands.youthMaxAge -> FareCategory.YOUTH
        bands.seniorMinAge != null && age >= bands.seniorMinAge -> FareCategory.SENIOR
        else -> FareCategory.ADULT
    }

    fun discount(category: FareCategory, discounts: CategoryDiscounts): Double = when (category) {
        FareCategory.INFANT -> discounts.infant
        FareCategory.CHILD -> discounts.child
        FareCategory.YOUTH -> discounts.youth
        FareCategory.SENIOR -> discounts.senior
        FareCategory.ADULT -> 0.0
    }

    /** Infants share a berth/seat with their adult. */
    fun occupiesBerth(category: FareCategory): Boolean = category != FareCategory.INFANT

    fun label(category: FareCategory, lang: Lang): String = when (lang) {
        Lang.FR -> when (category) {
            FareCategory.INFANT -> "Bébé"
            FareCategory.CHILD -> "Enfant"
            FareCategory.YOUTH -> "Jeune"
            FareCategory.ADULT -> "Adulte"
            FareCategory.SENIOR -> "Senior"
        }
        Lang.EN -> when (category) {
            FareCategory.INFANT -> "Infant"
            FareCategory.CHILD -> "Child"
            FareCategory.YOUTH -> "Youth"
            FareCategory.ADULT -> "Adult"
            FareCategory.SENIOR -> "Senior"
        }
        Lang.AR -> when (category) {
            FareCategory.INFANT -> "رضيع"
            FareCategory.CHILD -> "طفل"
            FareCategory.YOUTH -> "شاب"
            FareCategory.ADULT -> "بالغ"
            FareCategory.SENIOR -> "مسنّ"
        }
    }
}

/** Turns what the traveller declares into the operator's pricing class and the deck space used. */
object VehicleClassifier {
    const val STANDARD_MAX_HEIGHT_M = 1.90
    const val STANDARD_MAX_LENGTH_M = 5.0
    private const val ROOF_BOX_EXTRA_M = 0.40
    private const val DEFAULT_TRAILER_LENGTH_M = 4.0
    private const val SPACING_M = 1.0

    private fun defaultLength(type: VehicleType) = when (type) {
        VehicleType.CAR -> 4.5
        VehicleType.SUV -> 4.8
        VehicleType.MOTORCYCLE -> 2.2
        VehicleType.CAMPER -> 7.0
        VehicleType.VAN -> 5.5
    }

    private fun defaultHeight(type: VehicleType) = when (type) {
        VehicleType.CAR -> 1.55
        VehicleType.SUV -> 1.80
        VehicleType.MOTORCYCLE -> 1.30
        VehicleType.CAMPER -> 3.00
        VehicleType.VAN -> 2.40
    }

    /** A declared height is the total height; without one we add a roof box allowance. */
    fun effectiveHeight(v: VehicleInput): Double =
        v.heightM ?: (defaultHeight(v.type) + if (v.roofBox) ROOF_BOX_EXTRA_M else 0.0)

    fun totalLength(v: VehicleInput): Double =
        (v.lengthM ?: defaultLength(v.type)) +
            if (v.withTrailer) (v.trailerLengthM ?: DEFAULT_TRAILER_LENGTH_M) else 0.0

    fun laneMeters(v: VehicleInput): Int = ceil(totalLength(v) + SPACING_M).toInt()

    fun category(v: VehicleInput): VehicleCategory = when {
        v.type == VehicleType.MOTORCYCLE -> VehicleCategory.MOTORCYCLE
        v.type == VehicleType.CAMPER -> VehicleCategory.CAMPER
        v.type == VehicleType.VAN -> VehicleCategory.VAN
        v.withTrailer -> VehicleCategory.CAR_TRAILER
        v.type == VehicleType.SUV -> VehicleCategory.CAR_HIGH
        effectiveHeight(v) > STANDARD_MAX_HEIGHT_M -> VehicleCategory.CAR_HIGH
        (v.lengthM ?: defaultLength(v.type)) > STANDARD_MAX_LENGTH_M -> VehicleCategory.CAR_HIGH
        else -> VehicleCategory.CAR
    }

    fun label(category: VehicleCategory, lang: Lang): String = when (lang) {
        Lang.FR -> when (category) {
            VehicleCategory.CAR -> "Voiture (≤ 5 m, ≤ 1,90 m)"
            VehicleCategory.CAR_HIGH -> "Véhicule haut ou long (SUV, monospace)"
            VehicleCategory.MOTORCYCLE -> "Moto / scooter"
            VehicleCategory.CAMPER -> "Camping-car"
            VehicleCategory.CAR_TRAILER -> "Voiture + remorque / caravane"
            VehicleCategory.VAN -> "Fourgon / utilitaire"
        }
        Lang.EN -> when (category) {
            VehicleCategory.CAR -> "Car (≤ 5 m, ≤ 1.90 m)"
            VehicleCategory.CAR_HIGH -> "High or long vehicle (SUV, MPV)"
            VehicleCategory.MOTORCYCLE -> "Motorcycle / scooter"
            VehicleCategory.CAMPER -> "Motorhome"
            VehicleCategory.CAR_TRAILER -> "Car + trailer / caravan"
            VehicleCategory.VAN -> "Van / utility vehicle"
        }
        Lang.AR -> when (category) {
            VehicleCategory.CAR -> "سيارة (≤ 5 م، ≤ 1.90 م)"
            VehicleCategory.CAR_HIGH -> "مركبة عالية أو طويلة (رباعية الدفع، عائلية)"
            VehicleCategory.MOTORCYCLE -> "دراجة نارية / سكوتر"
            VehicleCategory.CAMPER -> "سيارة تخييم"
            VehicleCategory.CAR_TRAILER -> "سيارة + مقطورة / كرفان"
            VehicleCategory.VAN -> "شاحنة صغيرة / مركبة نفعية"
        }
    }
}
