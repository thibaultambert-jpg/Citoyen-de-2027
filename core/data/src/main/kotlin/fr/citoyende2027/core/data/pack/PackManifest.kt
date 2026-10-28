package fr.citoyende2027.core.data.pack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** manifest.json written by pipeline/citoyen_pipeline/sign.py. */
@Serializable
data class PackManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("generated_on") val generatedOn: String,
    @SerialName("embedding_model_id") val embeddingModelId: String? = null,
    val packs: List<PackEntry>,
) {
    fun pack(id: String): PackEntry? = packs.firstOrNull { it.id == id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(bytes: ByteArray): PackManifest = json.decodeFromString(serializer(), bytes.decodeToString())
    }
}

@Serializable
data class PackEntry(
    val id: String,
    val version: String,
    val file: String,
    val sha256: String,
    val size: Long,
    val licenses: List<String> = emptyList(),
)
