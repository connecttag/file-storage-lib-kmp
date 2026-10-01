package org.connecttag.lib.filestorage
 
import kotlin.jvm.JvmInline

enum class PlatformPickerSource {
    File,
    Image,
}

/** Opaque platform selection handle; adapters decode it without leaking Uri/File. */
@JvmInline
value class PlatformFileReference(val value: String) {
    init {
        require(value.isNotBlank()) { "Platform file reference must not be blank." }
    }
}

data class PlatformFilePickerRequest(
    val source: PlatformPickerSource = PlatformPickerSource.File,
    val mimeTypes: Set<String> = emptySet(),
    val allowMultiple: Boolean = false,
)

data class PlatformPickedFile(
    val reference: PlatformFileReference,
    val resourceId: FileResourceId? = null,
    val displayName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

sealed interface PlatformFilePickerResult {
    data class Selected(val files: List<PlatformPickedFile>) : PlatformFilePickerResult
    data object Cancelled : PlatformFilePickerResult
    data object Unsupported : PlatformFilePickerResult
    data class Failed(val cause: Throwable? = null) : PlatformFilePickerResult
}

/**
 * Platform-neutral file/image selection port.
 *
 * Platform launchers, URI handles, system services, and picker UI lifecycle remain in the
 * platform adapter that implements this contract.
 */
fun interface PlatformFilePicker {
    suspend fun pick(request: PlatformFilePickerRequest): PlatformFilePickerResult
}
