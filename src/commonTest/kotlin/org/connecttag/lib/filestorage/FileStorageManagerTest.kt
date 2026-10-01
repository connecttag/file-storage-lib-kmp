package org.connecttag.lib.filestorage

import org.connecttag.lib.coroutine.DispatcherProvider
import org.connecttag.lib.datetime.FakeClockProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileStorageManagerTest {
    @Test
    fun cacheFirstUsesFreshCacheWithoutFetching() = runTest {
        val fetcher = RecordingFileFetcher(payload = "network".payload())
        val clock = FakeClockProvider(initialMillis = 1_000L)
        val manager = createManager(fetcher = fetcher, clock = clock)
        val id = FileResourceId(namespace = "avatars", key = "user-1.webp")

        manager.storeFile(
            id = id,
            payload = "cached".payload(),
            policy = FileStoragePolicy(ttlMillis = 10_000L),
        ).getOrThrow()

        val result = manager.getFile(
            request = FileFetchRequest(id = id, url = "https://cdn.test/user-1.webp"),
            options = FileFetchOptions(strategy = FileFetchStrategy.CacheFirst),
        )

        assertEquals("cached", result.getOrThrow().bytes.decodeToString())
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun forceRefreshFetchesAndStoresNetworkPayload() = runTest {
        val fetcher = RecordingFileFetcher(payload = "network".payload(etag = "v2"))
        val clock = FakeClockProvider(initialMillis = 1_000L)
        val storage = InMemoryFileStorage()
        val manager = createManager(storage = storage, fetcher = fetcher, clock = clock)
        val id = FileResourceId(namespace = "avatars", key = "user-1.webp")

        val result = manager.getFile(
            request = FileFetchRequest(id = id, url = "https://cdn.test/user-1.webp"),
            options = FileFetchOptions(
                strategy = FileFetchStrategy.CacheFirst,
                forceRefresh = true,
                storagePolicy = FileStoragePolicy(ttlMillis = 10_000L),
            ),
        )

        val stored = result.getOrThrow()
        assertEquals("network", stored.bytes.decodeToString())
        assertEquals("v2", stored.metadata.etag)
        assertEquals(1, fetcher.calls)
        assertEquals("network", storage.read(id)?.bytes?.decodeToString())
    }

    @Test
    fun networkFirstFallsBackToStaleCacheWhenFetchFails() = runTest {
        val fetcher = RecordingFileFetcher(
            payload = "network".payload(),
            failure = IllegalStateException("offline"),
        )
        val clock = FakeClockProvider(initialMillis = 1_000L)
        val manager = createManager(fetcher = fetcher, clock = clock)
        val id = FileResourceId(namespace = "documents", key = "invoice.pdf")

        manager.storeFile(
            id = id,
            payload = "stale".payload(),
            policy = FileStoragePolicy(ttlMillis = 1L),
        ).getOrThrow()
        clock.setNowMillis(5_000L)

        val result = manager.getFile(
            request = FileFetchRequest(id = id, url = "https://cdn.test/invoice.pdf"),
            options = FileFetchOptions(strategy = FileFetchStrategy.NetworkFirst),
        )

        assertEquals("stale", result.getOrThrow().bytes.decodeToString())
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun cacheOnlyFailsWhenCacheIsEmpty() = runTest {
        val manager = createManager(fetcher = RecordingFileFetcher(payload = "network".payload()))
        val id = FileResourceId(namespace = "documents", key = "missing.pdf")

        val result = manager.getFile(
            request = FileFetchRequest(id = id, url = "https://cdn.test/missing.pdf"),
            options = FileFetchOptions(strategy = FileFetchStrategy.CacheOnly),
        )

        assertTrue(result.isFailure)
        assertIs<StoredFileNotFoundException>(result.exceptionOrNull())
    }

    @Test
    fun trimToSizeRemovesOldestFilesFirst() = runTest {
        val clock = FakeClockProvider(initialMillis = 1_000L)
        val manager = createManager(clock = clock)

        val first = FileResourceId(namespace = "cache", key = "first.bin")
        val second = FileResourceId(namespace = "cache", key = "second.bin")
        manager.storeFile(first, "12345".payload()).getOrThrow()
        clock.advanceBy(1L)
        manager.storeFile(second, "67890".payload()).getOrThrow()

        val removed = manager.trimToSize(maxBytes = 5L, namespace = "cache")

        assertEquals(1, removed)
        assertTrue(manager.readFile(first).isFailure)
        assertEquals("67890", manager.readFile(second).getOrThrow().bytes.decodeToString())
    }

    private fun createManager(
        storage: InMemoryFileStorage = InMemoryFileStorage(),
        fetcher: FileFetcher? = null,
        clock: FakeClockProvider = FakeClockProvider(initialMillis = 1_000L),
    ): FileStorageManager {
        val dispatcher = UnconfinedTestDispatcher()
        return FileStorageManager(
            storage = storage,
            fetcher = fetcher,
            clockProvider = clock,
            dispatchers = TestDispatcherProvider(dispatcher),
        )
    }

    private fun String.payload(etag: String? = null): FileFetchPayload {
        return FileFetchPayload(bytes = encodeToByteArray(), etag = etag)
    }

    private class RecordingFileFetcher(
        private val payload: FileFetchPayload,
        private val failure: Throwable? = null,
    ) : FileFetcher {
        var calls: Int = 0
            private set

        override suspend fun fetch(request: FileFetchRequest): FileFetchPayload {
            calls += 1
            failure?.let { throw it }
            return payload
        }
    }

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val unconfined: CoroutineDispatcher = dispatcher
    }
}

