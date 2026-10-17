package org.connecttag.lib.filestorage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class OkioFileStorage(
    private val fileSystem: FileSystem,
    private val baseDirectory: Path,
) : FileStorage {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun read(id: FileResourceId): StoredFile? {
        val metadata = metadata(id) ?: return null
        val contentPath = contentPath(id)
        if (!fileSystem.exists(contentPath)) return null
        val bytes = fileSystem.read(contentPath) { readByteArray() }
        return StoredFile(metadata = metadata, bytes = bytes)
    }

    override suspend fun write(request: FileWriteRequest): StoredFile {
        val directory = resourceDirectory(request.id)
        fileSystem.createDirectories(directory)
        fileSystem.write(contentPath(request.id)) {
            write(request.bytes)
        }

        val metadata = request.toMetadata()
        val record = metadata.toRecord()
        fileSystem.write(metadataPath(request.id)) {
            writeUtf8(json.encodeToString(record))
        }

        return StoredFile(metadata = metadata, bytes = request.bytes.copyOf())
    }

    override suspend fun metadata(id: FileResourceId): FileResourceMetadata? {
        if (!fileSystem.exists(contentPath(id))) return null
        return readMetadata(metadataPath(id))
    }

    override suspend fun list(namespace: String?): List<FileResourceMetadata> {
        val namespaceDirectories = if (namespace == null) {
            fileSystem.listOrNull(baseDirectory).orEmpty()
        } else {
            listOf(baseDirectory / namespace.validatedNamespace())
        }

        return namespaceDirectories
            .flatMap { directory -> fileSystem.listOrNull(directory).orEmpty() }
            .mapNotNull { resourceDirectory -> readMetadata(resourceDirectory / METADATA_FILE_NAME) }
            .filter { metadata -> namespace == null || metadata.id.namespace == namespace }
    }

    override suspend fun delete(id: FileResourceId): Boolean {
        val directory = resourceDirectory(id)
        if (!fileSystem.exists(directory)) return false
        fileSystem.deleteRecursively(directory)
        return true
    }

    override suspend fun clear(namespace: String?): Int {
        val entries = list(namespace)
        entries.forEach { metadata -> delete(metadata.id) }
        return entries.size
    }

    private fun FileWriteRequest.toMetadata(): FileResourceMetadata {
        return FileResourceMetadata(
            id = id,
            sizeBytes = bytes.size.toLong(),
            contentType = contentType,
            etag = etag,
            lastModifiedAtMillis = lastModifiedAtMillis,
            storedAtMillis = storedAtMillis,
            expiresAtMillis = expiresAtMillis,
            sourceUrl = sourceUrl,
            attributes = attributes.toMap(),
        )
    }

    private fun readMetadata(path: Path): FileResourceMetadata? {
        if (!fileSystem.exists(path)) return null
        return runCatching {
            json.decodeFromString<StoredFileMetadataRecord>(
                fileSystem.read(path) { readUtf8() },
            ).toMetadata()
        }.getOrNull()
    }

    private fun resourceDirectory(id: FileResourceId): Path {
        return baseDirectory / id.namespace / id.key.encodeKeyPathSegment()
    }

    private fun String.validatedNamespace(): String {
        return FileResourceId(namespace = this, key = VALIDATION_KEY).namespace
    }

    private fun contentPath(id: FileResourceId): Path {
        return resourceDirectory(id) / CONTENT_FILE_NAME
    }

    private fun metadataPath(id: FileResourceId): Path {
        return resourceDirectory(id) / METADATA_FILE_NAME
    }

    private fun String.encodeKeyPathSegment(): String {
        return replace("/", SLASH_TOKEN)
    }

    private fun FileResourceMetadata.toRecord(): StoredFileMetadataRecord {
        return StoredFileMetadataRecord(
            namespace = id.namespace,
            key = id.key,
            sizeBytes = sizeBytes,
            contentType = contentType,
            etag = etag,
            lastModifiedAtMillis = lastModifiedAtMillis,
            storedAtMillis = storedAtMillis,
            expiresAtMillis = expiresAtMillis,
            sourceUrl = sourceUrl,
            attributes = attributes,
        )
    }

    private fun StoredFileMetadataRecord.toMetadata(): FileResourceMetadata {
        return FileResourceMetadata(
            id = FileResourceId(namespace = namespace, key = key),
            sizeBytes = sizeBytes,
            contentType = contentType,
            etag = etag,
            lastModifiedAtMillis = lastModifiedAtMillis,
            storedAtMillis = storedAtMillis,
            expiresAtMillis = expiresAtMillis,
            sourceUrl = sourceUrl,
            attributes = attributes,
        )
    }

    @Serializable
    private data class StoredFileMetadataRecord(
        val namespace: String,
        val key: String,
        val sizeBytes: Long,
        val contentType: String? = null,
        val etag: String? = null,
        val lastModifiedAtMillis: Long? = null,
        val storedAtMillis: Long,
        val expiresAtMillis: Long? = null,
        val sourceUrl: String? = null,
        val attributes: Map<String, String> = emptyMap(),
    )

    private companion object {
        const val CONTENT_FILE_NAME = "content.bin"
        const val METADATA_FILE_NAME = "metadata.json"
        const val SLASH_TOKEN = "%2F"
        const val VALIDATION_KEY = "file"
    }
}
