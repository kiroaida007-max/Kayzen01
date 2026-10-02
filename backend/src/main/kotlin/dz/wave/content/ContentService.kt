package dz.wave.content

import dz.wave.catalog.CatalogData
import dz.wave.domain.LocalizedText
import dz.wave.domain.Promotion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate

@Serializable
data class GuideSection(val title: LocalizedText, val body: LocalizedText, val source: String? = null)

/** Traveller guides (documents, minors, vehicles, pets...) shown in the app's "Guides" tab. */
@Serializable
data class Guide(
    val id: String,
    val icon: String,
    val title: LocalizedText,
    val summary: LocalizedText,
    val sections: List<GuideSection>,
)

@Serializable
data class PopularRouteDef(val from: String, val to: String, val image: String)

class ContentService(private val data: CatalogData, private val clock: Clock) {
    private val json = Json { ignoreUnknownKeys = false }
    val guides: List<Guide> = read("content/guides.json", Guide.serializer())
    val popular: List<PopularRouteDef> = read("content/popular.json", PopularRouteDef.serializer())

    /** Offers still bookable today ("Bons plans"). */
    fun deals(): List<Promotion> {
        val today = LocalDate.now(clock)
        return data.promotions.filter { p ->
            (p.bookTo == null || !today.isAfter(p.bookTo)) && (p.travelTo == null || !today.isAfter(p.travelTo))
        }
    }

    private fun <T> read(path: String, item: KSerializer<T>): List<T> {
        val text = ContentService::class.java.classLoader.getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Missing resource $path")
        return json.decodeFromString(ListSerializer(item), text)
    }
}
