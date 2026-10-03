package app.glimmerview.ml

enum class InputLayout { NHWC, NCHW }

enum class InputNormalization {
    /** RVB dans [0, 1] (normalisation incluse dans le modèle). */
    ZERO_ONE,

    /** (RVB − moyenne ImageNet) / écart-type ImageNet. */
    IMAGENET,
}

data class InputSize(val width: Int, val height: Int) {
    override fun toString() = "${width}×$height"
}

/** Fournisseurs d'exécution d'ONNX Runtime (runtime retenu par l'ADR 0001). */
enum class Backend(val label: String) {
    ORT_CPU("ONNX Runtime · CPU"),
    ORT_XNNPACK("ONNX Runtime · XNNPACK"),
}

data class ModelSpec(
    val id: String,
    val label: String,
    val url: String,
    val fileName: String,
    val approxDownloadBytes: Long,
    /** Empreinte SHA-256 du fichier final (après extraction éventuelle), en hexadécimal. */
    val sha256: String,
    val layout: InputLayout,
    val normalization: InputNormalization,
    /** Taille d'entrée imposée par le modèle, ou null si elle est libre (multiples de 14). */
    val fixedInputSize: InputSize?,
    val license: String,
    /** Fichier à extraire quand le téléchargement est une archive ZIP. */
    val zipEntry: String? = null,
)

/** Modèles disponibles (tous issus de Depth Anything V2 Small, licence Apache-2.0). */
object ModelCatalog {

    val DA2_SMALL_ONNX = ModelSpec(
        id = "da2s-onnx-fp32",
        label = "Depth Anything V2 Small · ONNX FP32",
        url = "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model.onnx",
        fileName = "da2s_fp32.onnx",
        approxDownloadBytes = 99_060_839,
        sha256 = "afb6a5c28f3b6bf1618c6e43f02073ef9dfdc70e937502d51603e57b0a1df10c",
        layout = InputLayout.NCHW,
        normalization = InputNormalization.IMAGENET,
        fixedInputSize = null,
        license = "Poids Apache-2.0 · export onnx-community",
    )

    val DA2_SMALL_ONNX_INT8 = ModelSpec(
        id = "da2s-onnx-int8",
        label = "Depth Anything V2 Small · ONNX INT8",
        url = "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model_quantized.onnx",
        fileName = "da2s_int8.onnx",
        approxDownloadBytes = 27_258_801,
        sha256 = "fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d",
        layout = InputLayout.NCHW,
        normalization = InputNormalization.IMAGENET,
        fixedInputSize = null,
        license = "Poids Apache-2.0 · export onnx-community (quantification dynamique)",
    )

    /** Modèle de production (ADR 0001) : le plus léger, 0,3 s sur haut de gamme. */
    val DEFAULT = DA2_SMALL_ONNX_INT8

    val all = listOf(DA2_SMALL_ONNX_INT8, DA2_SMALL_ONNX)
}
