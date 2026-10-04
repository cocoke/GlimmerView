package app.glimmerview.format

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToLong

/** Auteur de l'œuvre : l'attribution est obligatoire pour les licences CC BY. */
data class DwpAuthor(val name: String, val url: String? = null)

/** Outils qui ont produit le paquet. */
data class DwpGenerator(val app: String? = null, val depthModel: String? = null, val inpaintModel: String? = null)

/**
 * Un calque : une image et sa profondeur, plus un masque s'il ne couvre pas toute l'image.
 * Les calques vont du fond au premier plan ; le dernier est l'image principale.
 */
data class DwpLayer(val color: String, val depth: String, val mask: String? = null)

/** Réglages conseillés de l'effet ; chaque lecteur les adapte à son moteur. */
data class DwpDefaults(
    /** Décalage maximal du premier plan, en fraction de la largeur visible (0,035 : effet moyen). */
    val strength: Float? = null,
    /** Disparité (0 = fond, 1 = premier plan) du plan qui reste immobile. */
    val focus: Float? = null,
    /** Agrandissement minimal (≥ 1). */
    val zoom: Float? = null,
    /** Mouvement conseillé : « gyro », « touch » ou « auto ». */
    val motion: String? = null,
)

/** Le fichier manifest.json d'un paquet. Lecture tolérante : les champs inconnus sont ignorés. */
data class DwpManifest(
    val formatVersion: Int = DwpFormat.VERSION,
    val id: String? = null,
    val title: String? = null,
    val author: DwpAuthor? = null,
    /** Identifiant SPDX (« CC0-1.0 », « CC-BY-4.0 »…) ; absent : tous droits réservés. */
    val license: String? = null,
    val sourceUrl: String? = null,
    /** Image produite par une IA générative ; absent : inconnu. */
    val aiGenerated: Boolean? = null,
    /** Technique, comme sur un cartel (« Photographie »), dans la langue [language]. */
    val medium: String? = null,
    /** Langue du titre et de la technique (étiquette BCP 47, « fr-FR »). */
    val language: String? = null,
    /** Date de création, ISO 8601. */
    val created: String? = null,
    val generator: DwpGenerator? = null,
    /** Dimensions de l'image principale, en pixels. */
    val width: Int = 0,
    val height: Int = 0,
    val layers: List<DwpLayer> = listOf(DwpLayer(DwpFormat.COLOR, DwpFormat.DEPTH)),
    val defaults: DwpDefaults? = null,
    val thumbnail: String? = null,
    val tags: List<String> = emptyList(),
) {

    init {
        require(layers.isNotEmpty()) { "Un paquet contient au moins un calque" }
    }

    /** Image principale : le dernier calque (le premier plan, ou l'image entière s'il est seul). */
    val mainLayer: DwpLayer get() = layers.last()

    fun toJson(): String {
        val o = JSONObject()
        o.put("format", DwpFormat.NAME)
        o.put("formatVersion", formatVersion)
        id?.let { o.put("id", it) }
        title?.let { o.put("title", it) }
        author?.let { a -> o.put("author", JSONObject().put("name", a.name).putIfNotNull("url", a.url)) }
        license?.let { o.put("license", it) }
        sourceUrl?.let { o.put("sourceUrl", it) }
        aiGenerated?.let { o.put("aiGenerated", it) }
        medium?.let { o.put("medium", it) }
        language?.let { o.put("language", it) }
        created?.let { o.put("created", it) }
        generator?.let { g ->
            val json = JSONObject()
                .putIfNotNull("app", g.app)
                .putIfNotNull("depthModel", g.depthModel)
                .putIfNotNull("inpaintModel", g.inpaintModel)
            if (json.length() > 0) o.put("generator", json)
        }
        if (width > 0) o.put("width", width)
        if (height > 0) o.put("height", height)
        val layerArray = JSONArray()
        for (layer in layers) {
            layerArray.put(JSONObject().put("color", layer.color).put("depth", layer.depth).putIfNotNull("mask", layer.mask))
        }
        o.put("layers", layerArray)
        defaults?.let { d ->
            val json = JSONObject()
            d.strength?.let { json.put("strength", rounded(it)) }
            d.focus?.let { json.put("focus", rounded(it)) }
            d.zoom?.let { json.put("zoom", rounded(it)) }
            json.putIfNotNull("motion", d.motion)
            if (json.length() > 0) o.put("defaults", json)
        }
        thumbnail?.let { o.put("thumbnail", it) }
        if (tags.isNotEmpty()) o.put("tags", JSONArray(tags))
        return o.toString(2)
    }

    companion object {

        /** Lit un manifest ; refuse ce qui n'est pas un paquet dwp ou vient d'une version plus récente. */
        fun parse(json: String): DwpManifest {
            val o = try {
                JSONObject(json.removePrefix(BYTE_ORDER_MARK))
            } catch (e: JSONException) {
                throw DwpException(DwpException.Reason.NOT_DWP, "manifest.json n'est pas du JSON valide", e)
            }
            if (o.text("format") != DwpFormat.NAME) {
                throw DwpException(DwpException.Reason.NOT_DWP, "Le champ « format » ne vaut pas « ${DwpFormat.NAME} »")
            }
            val version = o.optInt("formatVersion", DwpFormat.VERSION)
            if (version > DwpFormat.VERSION) {
                throw DwpException(
                    DwpException.Reason.UNSUPPORTED_VERSION,
                    "Paquet en version $version ; ce module lit jusqu'à la version ${DwpFormat.VERSION}",
                )
            }
            val layers = o.optJSONArray("layers")?.objects()?.mapNotNull { layer ->
                val color = layer.text("color") ?: return@mapNotNull null
                val depth = layer.text("depth") ?: return@mapNotNull null
                DwpLayer(color, depth, layer.text("mask"))
            }.orEmpty()
            return DwpManifest(
                formatVersion = version,
                id = o.text("id"),
                title = o.text("title"),
                author = when (val author = o.opt("author")) {
                    is JSONObject -> author.text("name")?.let { DwpAuthor(it, author.text("url")) }
                    is String -> author.trim().takeIf { it.isNotEmpty() }?.let { DwpAuthor(it) }
                    else -> null
                },
                license = o.text("license"),
                sourceUrl = o.text("sourceUrl"),
                aiGenerated = o.opt("aiGenerated") as? Boolean,
                medium = o.text("medium"),
                language = o.text("language"),
                created = o.text("created"),
                generator = o.optJSONObject("generator")?.let {
                    DwpGenerator(app = it.text("app"), depthModel = it.text("depthModel"), inpaintModel = it.text("inpaintModel"))
                },
                width = o.optInt("width", 0).coerceAtLeast(0),
                height = o.optInt("height", 0).coerceAtLeast(0),
                layers = layers.ifEmpty { listOf(DwpLayer(DwpFormat.COLOR, DwpFormat.DEPTH)) },
                defaults = o.optJSONObject("defaults")?.let {
                    DwpDefaults(
                        strength = it.number("strength"),
                        focus = it.number("focus"),
                        zoom = it.number("zoom"),
                        motion = it.text("motion"),
                    )
                },
                thumbnail = o.text("thumbnail"),
                tags = o.optJSONArray("tags")?.let { array ->
                    (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.trim()?.takeIf(String::isNotEmpty) }
                }.orEmpty(),
            )
        }

        private fun JSONObject.text(key: String): String? =
            if (isNull(key)) null else (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

        private fun JSONObject.number(key: String): Float? = (opt(key) as? Number)?.toFloat()?.takeIf { it.isFinite() }

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

        private fun JSONObject.putIfNotNull(key: String, value: String?): JSONObject = if (value != null) put(key, value) else this

        /** Marque d'ordre des octets que certains éditeurs placent en tête des fichiers UTF-8. */
        private val BYTE_ORDER_MARK = Char(0xFEFF).toString()

        /** Quatre décimales suffisent, et évitent les « 0.03500000014901161 » des flottants. */
        private fun rounded(value: Float): Double = (value * 10_000.0).roundToLong() / 10_000.0
    }
}
