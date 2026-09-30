package ir.example.slmchat.data

import androidx.annotation.StringRes
import ir.example.slmchat.R

data class CuratedModel(
    val title: String,
    @StringRes val descriptionRes: Int,
    val sizeLabel: String,
    val url: String,
    val fileName: String,
    val gated: Boolean,
)

/** لینک‌ها از مخزن رسمی litert-community روی Hugging Face هستند. */
object CuratedModels {
    val list = listOf(
        CuratedModel(
            title = "Gemma 3 1B IT (int4)",
            descriptionRes = R.string.desc_gemma_1b,
            sizeLabel = "≈ 555 MB",
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            fileName = "gemma3-1b-it-int4.task",
            gated = true,
        ),
        CuratedModel(
            title = "Gemma 3 1B IT (int4, ekv4096)",
            descriptionRes = R.string.desc_gemma_1b_long,
            sizeLabel = "≈ 676 MB",
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_seq128_q4_block128_ekv4096.task",
            fileName = "Gemma3-1B-IT_seq128_q4_block128_ekv4096.task",
            gated = true,
        ),
        CuratedModel(
            title = "Qwen2.5 1.5B Instruct (q8)",
            descriptionRes = R.string.desc_qwen_15b,
            sizeLabel = "≈ 1.6 GB",
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            gated = false,
        ),
    )
}
