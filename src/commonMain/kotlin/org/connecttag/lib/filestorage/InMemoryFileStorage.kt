package org.connecttag.lib.filestorage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryFileStorage : FileStorage {
    private val lock = Mutex()
    private val files = LinkedHashMap<FileResourceId, StoredFile>()

    override suspend fun read(id: FileResourceId): StoredFile? = lock.withLock {
        files[id]?.copyForRead()
    }

    override suspend fun write(request: FileWriteRequest): StoredFile = lock.withLock {
        val stored = request.toStoredFile()
        files[request.id] = stored.copyForRead()
        stored.copyForRead()
    }

    override suspend fun metadata(id: FileResourceId): FileResourceMetadata? = lock.withLock {
        files[id]?.metadata
    }

    override suspend fun list(namespace: String?): List<FileResourceMetadata> = lock.withLock {
        files.values
            .asSequence()
            .map { it.metadata }
            .filter { metadata -> namespace == null || metadata.id.namespace == namespace }
            .toList()
    }

    override suspend fun delete(id: FileResourceId): Boolean = lock.withLock {
        files.remove(id) != null
    }

    override suspend fun clear(namespace: String?): Int = lock.withLock {
        val ids = files.keys
            .filter { id -> namespace == null || id.namespace == namespace }
        ids.forEach(files::remove)
        ids.size
    }

    private fun FileWriteRequest.toStoredFile(): StoredFile {
        val copiedBytes = bytes.copyOf()
        return StoredFile(
            metadata = FileResourceMetadata(
                id = id,
                sizeBytes = copiedBytes.size.toLong(),
                contentType = contentType,
                etag = etag,
                lastModifiedAtMillis = lastModifiedAtMillis,
                storedAtMillis = storedAtMillis,
                expiresAtMillis = expiresAtMillis,
                sourceUrl = sourceUrl,
                attributes = attributes.toMap(),
            ),
            bytes = copiedBytes,
        )
    }
}

