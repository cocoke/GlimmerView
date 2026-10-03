package app.glimmerview.ml

enum class ModelFormat { TFLITE, ONNX }

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

enum class Backend(val label: String, val format: ModelFormat) {
    LITERT_GPU("LiteRT · GPU", ModelFormat.TFLITE),
    LITERT_CPU("LiteRT · CPU", ModelFormat.TFLITE),
    LITERT_NPU("LiteRT · NPU", ModelFormat.TFLITE),
    ORT_CPU("ONNX Runtime · CPU", ModelFormat.ONNX),
    ORT_XNNPACK("ONNX Runtime · XNNPACK", ModelFormat.ONNX),
}

data class ModelSpec(
    val id: String,
    val label: String,
    val url: String,
    val fileName: String,
    val approxDownloadBytes: Long,
    val format: ModelFormat,
    val layout: InputLayout,
    val normalization: InputNormalization,
    /** Taille d'entrée imposée par le modèle, ou null si elle est libre (multiples de 14). */
    val fixedInputSize: InputSize?,
    val license: String,
    /** Fichier à extraire quand le téléchargement est une archive ZIP. */
    val zipEntry: String? = null,
)

/** Modèles testés en Phase 0 (tous issus de Depth Anything V2 Small, licence Apache-2.0). */
object ModelCatalog {

    val DA2_SMALL_TFLITE = ModelSpec(
        id = "da2s-tflite-fp32",
        label = "Depth Anything V2 Small · TFLite FP32",
        url = "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/" +
            "depth_anything_v2/releases/v0.63.0/depth_anything_v2-tflite-float.zip",
        fileName = "da2s_fp32.tflite",
        approxDownloadBytes = 91_864_237,
        format = ModelFormat.TFLITE,
        layout = InputLayout.NHWC,
        normalization = InputNormalization.ZERO_ONE,
        fixedInputSize = InputSize(518, 518),
        license = "Poids Apache-2.0 · export Qualcomm AI Hub",
        zipEntry = "depth_anything_v2-tflite-float/depth_anything_v2.tflite",
    )

    val DA2_SMALL_ONNX = ModelSpec(
        id = "da2s-onnx-fp32",
        label = "Depth Anything V2 Small · ONNX FP32",
        url = "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model.onnx",
        fileName = "da2s_fp32.onnx",
        approxDownloadBytes = 99_060_839,
        format = ModelFormat.ONNX,
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
        format = ModelFormat.ONNX,
        layout = InputLayout.NCHW,
        normalization = InputNormalization.IMAGENET,
        fixedInputSize = null,
        license = "Poids Apache-2.0 · export onnx-community (quantification dynamique)",
    )

    val all = listOf(DA2_SMALL_TFLITE, DA2_SMALL_ONNX, DA2_SMALL_ONNX_INT8)
}
