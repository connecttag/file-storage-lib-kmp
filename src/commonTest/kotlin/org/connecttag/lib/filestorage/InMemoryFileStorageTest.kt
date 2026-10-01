package org.connecttag.lib.filestorage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryFileStorageTest {
    @Test
    fun writeAndReadCopyBytesSoCallersCannotMutateStorage() = runTest {
        val storage = InMemoryFileStorage()
        val id = FileResourceId(namespace = "cache", key = "avatar.webp")
        val bytes = byteArrayOf(1, 2, 3)

        storage.write(
            FileWriteRequest(
                id = id,
                bytes = bytes,
                storedAtMillis = 1_000L,
            ),
        )
        bytes[0] = 9

        val firstRead = storage.read(id) ?: error("Expected stored file")
        assertEquals(1, firstRead.bytes[0])

        firstRead.bytes[0] = 8
        val secondRead = storage.read(id) ?: error("Expected stored file")
        assertEquals(1, secondRead.bytes[0])
    }

    @Test
    fun clearCanTargetOneNamespace() = runTest {
        val storage = InMemoryFileStorage()
        val avatar = FileResourceId(namespace = "avatars", key = "user.webp")
        val document = FileResourceId(namespace = "documents", key = "invoice.pdf")

        storage.write(FileWriteRequest(id = avatar, bytes = byteArrayOf(1), storedAtMillis = 1L))
        storage.write(FileWriteRequest(id = document, bytes = byteArrayOf(2), storedAtMillis = 1L))

        val removed = storage.clear(namespace = "avatars")

        assertEquals(1, removed)
        assertFalse(storage.delete(avatar))
        assertTrue(storage.delete(document))
    }

    @Test
    fun safeKeysRejectTraversalButAllowNestedRelativeKeys() {
        FileResourceId(namespace = "images", key = "avatars/user.webp")

        assertFailsFor(namespace = "images", key = "../secret.txt")
        assertFailsFor(namespace = "images", key = "/secret.txt")
        assertFailsFor(namespace = "../images", key = "safe.txt")
    }

    private fun assertFailsFor(namespace: String, key: String) {
        val failed = runCatching { FileResourceId(namespace = namespace, key = key) }.isFailure
        assertTrue(failed, "Expected invalid id for namespace=$namespace key=$key")
    }
}

