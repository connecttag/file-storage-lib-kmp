package org.connecttag.lib.filestorage

data class FileFetchRequest(
    val id: FileResourceId,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(url.isNotBlank()) { "File fetch URL must not be blank." }
    }
}

data class FileFetchPayload(
    val bytes: ByteArray,
    val contentType: String? = null,
    val etag: String? = null,
    val lastModifiedAtMillis: Long? = null,
    val attributes: Map<String, String> = emptyMap(),
)

data class FileWriteRequest(
    val id: FileResourceId,
    val bytes: ByteArray,
    val contentType: String? = null,
    val etag: String? = null,
    val lastModifiedAtMillis: Long? = null,
    val storedAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val sourceUrl: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

data class FileResourceMetadata(
    val id: FileResourceId,
    val sizeBytes: Long,
    val contentType: String? = null,
    val etag: String? = null,
    val lastModifiedAtMillis: Long? = null,
    val storedAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val sourceUrl: String? = null,
    val attributes: Map<String, String> = emptyMap(),
) {
    fun isExpired(nowMillis: Long): Boolean {
        return expiresAtMillis != null && expiresAtMillis <= nowMillis
    }
}

data class StoredFile(
    val metadata: FileResourceMetadata,
    val bytes: ByteArray,
) {
    fun copyForRead(): StoredFile {
        return copy(bytes = bytes.copyOf())
    }
}

data class FileStoragePolicy(
    val ttlMillis: Long? = null,
    val maxEntryBytes: Long? = null,
    val allowStaleOnFetchFailure: Boolean = true,
) {
    init {
        require(ttlMillis == null || ttlMillis > 0L) { "ttlMillis must be positive when set." }
        require(maxEntryBytes == null || maxEntryBytes > 0L) {
            "maxEntryBytes must be positive when set."
        }
    }
}

enum class FileFetchStrategy {
    CacheOnly,
    NetworkOnly,
    CacheFirst,
    NetworkFirst,
}

data class FileFetchOptions(
    val strategy: FileFetchStrategy = FileFetchStrategy.CacheFirst,
    val forceRefresh: Boolean = false,
    val allowExpiredCache: Boolean = false,
    val storagePolicy: FileStoragePolicy = FileStoragePolicy(),
)

sealed class FileStorageException(message: String) : IllegalStateException(message)

class StoredFileNotFoundException(
    val id: FileResourceId,
) : FileStorageException("Stored file not found: ${id.storagePath}")

class FileFetcherUnavailableException :
    FileStorageException("File fetcher is required for network file loading.")

class FileEntryTooLargeException(
    val id: FileResourceId,
    val sizeBytes: Long,
    val maxEntryBytes: Long,
) : FileStorageException(
    "Stored file is too large: ${id.storagePath} size=$sizeBytes max=$maxEntryBytes",
)

class FilePayloadTooLargeException(
    val sizeBytes: Long,
    val maxBytes: Long,
) : FileStorageException("File payload is too large: size=$sizeBytes max=$maxBytes")

class FileContentTypeUnsupportedException(
    val contentType: String?,
) : FileStorageException("File content type is unsupported: $contentType")

class FilePayloadReadFailedException :
    FileStorageException("File payload could not be read.")
