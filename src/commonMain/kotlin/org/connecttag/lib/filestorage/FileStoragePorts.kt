package org.connecttag.lib.filestorage

fun interface FileFetcher {
    suspend fun fetch(request: FileFetchRequest): FileFetchPayload
}

class FunctionFileFetcher(
    private val fetchBlock: suspend (FileFetchRequest) -> FileFetchPayload,
) : FileFetcher {
    override suspend fun fetch(request: FileFetchRequest): FileFetchPayload {
        return fetchBlock(request)
    }
}

interface FileStorage {
    suspend fun read(id: FileResourceId): StoredFile?
    suspend fun write(request: FileWriteRequest): StoredFile
    suspend fun metadata(id: FileResourceId): FileResourceMetadata?
    suspend fun list(namespace: String? = null): List<FileResourceMetadata>
    suspend fun delete(id: FileResourceId): Boolean
    suspend fun clear(namespace: String? = null): Int
}

