package org.connecttag.lib.filestorage

import org.connecttag.lib.coroutine.DefaultDispatcherProvider
import org.connecttag.lib.coroutine.DispatcherProvider
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import okio.use

data class FilePayloadReadRequest(
    val openSource: () -> BufferedSource,
    val contentType: String?,
    val fileName: String? = null,
    val declaredSizeBytes: Long? = null,
    val maxBytes: Long,
    val allowedContentTypes: Set<String> = emptySet(),
    val allowedContentTypePrefixes: Set<String> = emptySet(),
    val defaultContentType: String? = null,
) {
    init {
        require(maxBytes > 0L) { "maxBytes must be positive." }
    }
}

data class FileReadPayload(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileReadPayload) return false
        return bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            fileName == other.fileName
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        return result
    }
}

class FilePayloadReader(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) {
    suspend fun read(request: FilePayloadReadRequest): FileReadPayload = withContext(dispatchers.io) {
        val contentType = request.resolvedContentType()
        val declaredSize = request.declaredSizeBytes?.takeIf { it >= 0L }
        if (declaredSize != null && declaredSize > request.maxBytes) {
            throw FilePayloadTooLargeException(sizeBytes = declaredSize, maxBytes = request.maxBytes)
        }

        val bytes = runCatching {
            request.openSource().use { source -> source.readBoundedByteArray(request.maxBytes) }
        }.getOrElse { error ->
            if (error is FileStorageException) throw error
            throw FilePayloadReadFailedException()
        }

        FileReadPayload(
            bytes = bytes,
            contentType = contentType,
            fileName = request.fileName?.takeIf { it.isNotBlank() },
        )
    }

    private fun FilePayloadReadRequest.resolvedContentType(): String {
        val resolved = contentType.normalizedContentType()
            ?: defaultContentType.normalizedContentType()
            ?: throw FileContentTypeUnsupportedException(contentType)

        val allowedTypes = allowedContentTypes.mapTo(mutableSetOf()) { it.normalizedContentType().orEmpty() }
        val allowedPrefixes = allowedContentTypePrefixes.mapTo(mutableSetOf()) {
            it.normalizedContentType().orEmpty()
        }
        val allowsAll = allowedTypes.isEmpty() && allowedPrefixes.isEmpty()
        val isAllowed = allowsAll ||
            resolved in allowedTypes ||
            allowedPrefixes.any { prefix -> prefix.isNotBlank() && resolved.startsWith(prefix) }

        if (!isAllowed) throw FileContentTypeUnsupportedException(resolved)
        return resolved
    }

    private fun BufferedSource.readBoundedByteArray(maxBytes: Long): ByteArray {
        val output = Buffer()
        var totalBytes = 0L
        while (true) {
            val read = read(output, minOf(DEFAULT_CHUNK_BYTES, maxBytes - totalBytes + 1L))
            if (read == -1L) break
            totalBytes += read
            if (totalBytes > maxBytes) {
                throw FilePayloadTooLargeException(sizeBytes = totalBytes, maxBytes = maxBytes)
            }
        }
        return output.readByteArray()
    }

    private fun String?.normalizedContentType(): String? {
        return this
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val DEFAULT_CHUNK_BYTES = 8L * 1024L
    }
}
