package org.connecttag.lib.filestorage

import org.connecttag.lib.coroutine.DefaultDispatcherProvider
import org.connecttag.lib.coroutine.DispatcherProvider
import org.connecttag.lib.datetime.ClockProvider
import org.connecttag.lib.datetime.SystemClockProvider
import kotlinx.coroutines.withContext

class FileStorageManager(
    private val storage: FileStorage,
    private val fetcher: FileFetcher? = null,
    private val clockProvider: ClockProvider = SystemClockProvider,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) {
    suspend fun getFile(
        request: FileFetchRequest,
        options: FileFetchOptions = FileFetchOptions(),
    ): Result<StoredFile> = withContext(dispatchers.io) {
        when (options.strategy) {
            FileFetchStrategy.CacheOnly -> readFile(request.id, allowExpired = options.allowExpiredCache)
            FileFetchStrategy.NetworkOnly -> fetchAndStoreResult(request, options)
            FileFetchStrategy.CacheFirst -> getCacheFirst(request, options)
            FileFetchStrategy.NetworkFirst -> getNetworkFirst(request, options)
        }
    }

    suspend fun storeFile(
        id: FileResourceId,
        payload: FileFetchPayload,
        sourceUrl: String? = null,
        policy: FileStoragePolicy = FileStoragePolicy(),
    ): Result<StoredFile> = withContext(dispatchers.io) {
        runCatching { writePayload(id = id, payload = payload, sourceUrl = sourceUrl, policy = policy) }
    }

    suspend fun readFile(
        id: FileResourceId,
        allowExpired: Boolean = false,
    ): Result<StoredFile> = withContext(dispatchers.io) {
        val nowMillis = clockProvider.nowMillis()
        val stored = storage.read(id)
        when {
            stored == null -> Result.failure(StoredFileNotFoundException(id))
            !allowExpired && stored.metadata.isExpired(nowMillis) -> {
                Result.failure(StoredFileNotFoundException(id))
            }
            else -> Result.success(stored)
        }
    }

    suspend fun deleteFile(id: FileResourceId): Boolean = withContext(dispatchers.io) {
        storage.delete(id)
    }

    suspend fun clear(namespace: String? = null): Int = withContext(dispatchers.io) {
        storage.clear(namespace)
    }

    suspend fun pruneExpired(namespace: String? = null): Int = withContext(dispatchers.io) {
        val nowMillis = clockProvider.nowMillis()
        storage.list(namespace)
            .filter { it.isExpired(nowMillis) }
            .countDeleted()
    }

    suspend fun trimToSize(maxBytes: Long, namespace: String? = null): Int = withContext(dispatchers.io) {
        require(maxBytes >= 0L) { "maxBytes must not be negative." }
        val entries = storage.list(namespace).sortedWith(
            compareBy<FileResourceMetadata> { it.storedAtMillis }
                .thenBy { it.id.storagePath },
        )
        var totalBytes = entries.sumOf { it.sizeBytes }
        var removed = 0
        for (entry in entries) {
            if (totalBytes <= maxBytes) break
            if (storage.delete(entry.id)) {
                totalBytes -= entry.sizeBytes
                removed += 1
            }
        }
        removed
    }

    private suspend fun getCacheFirst(
        request: FileFetchRequest,
        options: FileFetchOptions,
    ): Result<StoredFile> {
        val cached = storage.read(request.id)
        val nowMillis = clockProvider.nowMillis()
        if (!options.forceRefresh && cached != null && cached.isUsableCache(options, nowMillis)) {
            return Result.success(cached)
        }
        return fetchAndStoreResult(request, options, fallback = cached)
    }

    private suspend fun getNetworkFirst(
        request: FileFetchRequest,
        options: FileFetchOptions,
    ): Result<StoredFile> {
        val cached = storage.read(request.id)
        return fetchAndStoreResult(request, options, fallback = cached)
    }

    private suspend fun fetchAndStoreResult(
        request: FileFetchRequest,
        options: FileFetchOptions,
        fallback: StoredFile? = null,
    ): Result<StoredFile> {
        val result = runCatching {
            val activeFetcher = fetcher ?: throw FileFetcherUnavailableException()
            writePayload(
                id = request.id,
                payload = activeFetcher.fetch(request),
                sourceUrl = request.url,
                policy = options.storagePolicy,
                requestAttributes = request.attributes,
            )
        }

        if (result.isSuccess) return result

        return if (fallback != null && options.storagePolicy.allowStaleOnFetchFailure) {
            Result.success(fallback)
        } else {
            Result.failure(result.exceptionOrNull() ?: FileFetcherUnavailableException())
        }
    }

    private suspend fun writePayload(
        id: FileResourceId,
        payload: FileFetchPayload,
        sourceUrl: String?,
        policy: FileStoragePolicy,
        requestAttributes: Map<String, String> = emptyMap(),
    ): StoredFile {
        val sizeBytes = payload.bytes.size.toLong()
        val maxBytes = policy.maxEntryBytes
        if (maxBytes != null && sizeBytes > maxBytes) {
            throw FileEntryTooLargeException(id = id, sizeBytes = sizeBytes, maxEntryBytes = maxBytes)
        }

        val storedAtMillis = clockProvider.nowMillis()
        return storage.write(
            FileWriteRequest(
                id = id,
                bytes = payload.bytes,
                contentType = payload.contentType,
                etag = payload.etag,
                lastModifiedAtMillis = payload.lastModifiedAtMillis,
                storedAtMillis = storedAtMillis,
                expiresAtMillis = expiresAtMillis(storedAtMillis, policy.ttlMillis),
                sourceUrl = sourceUrl,
                attributes = requestAttributes + payload.attributes,
            ),
        )
    }

    private suspend fun List<FileResourceMetadata>.countDeleted(): Int {
        var removed = 0
        forEach { metadata ->
            if (storage.delete(metadata.id)) removed += 1
        }
        return removed
    }

    private fun StoredFile?.isUsableCache(options: FileFetchOptions, nowMillis: Long): Boolean {
        return this != null && (options.allowExpiredCache || !metadata.isExpired(nowMillis))
    }

    private fun expiresAtMillis(storedAtMillis: Long, ttlMillis: Long?): Long? {
        if (ttlMillis == null) return null
        return if (Long.MAX_VALUE - storedAtMillis < ttlMillis) {
            Long.MAX_VALUE
        } else {
            storedAtMillis + ttlMillis
        }
    }
}
