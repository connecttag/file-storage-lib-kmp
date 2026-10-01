package org.connecttag.lib.filestorage

import org.connecttag.lib.coroutine.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilePayloadReaderTest {
    @Test
    fun readAcceptsAllowedContentTypeAndReturnsPayload() = runTest {
        val reader = createReader()

        val payload = reader.read(
            FilePayloadReadRequest(
                openSource = { "proof".source() },
                contentType = "IMAGE/PNG; charset=utf-8",
                fileName = "proof.png",
                maxBytes = 20L,
                allowedContentTypes = setOf("image/png"),
            ),
        )

        assertEquals("proof", payload.bytes.decodeToString())
        assertEquals("image/png", payload.contentType)
        assertEquals("proof.png", payload.fileName)
    }

    @Test
    fun readRejectsUnsupportedContentType() = runTest {
        val reader = createReader()

        assertFailsWith<FileContentTypeUnsupportedException> {
            reader.read(
                FilePayloadReadRequest(
                    openSource = { "proof".source() },
                    contentType = "text/plain",
                    maxBytes = 20L,
                    allowedContentTypes = setOf("image/png"),
                ),
            )
        }
    }

    @Test
    fun readRejectsPayloadLargerThanLimitWhileStreaming() = runTest {
        val reader = createReader()

        assertFailsWith<FilePayloadTooLargeException> {
            reader.read(
                FilePayloadReadRequest(
                    openSource = { "too-large".source() },
                    contentType = "image/jpeg",
                    maxBytes = 3L,
                    allowedContentTypePrefixes = setOf("image/"),
                ),
            )
        }
    }

    @Test
    fun readUsesDefaultContentTypeWhenSourceDoesNotDeclareOne() = runTest {
        val reader = createReader()

        val payload = reader.read(
            FilePayloadReadRequest(
                openSource = { "avatar".source() },
                contentType = null,
                defaultContentType = "image/jpeg",
                maxBytes = 20L,
                allowedContentTypePrefixes = setOf("image/"),
            ),
        )

        assertEquals("image/jpeg", payload.contentType)
    }

    private fun createReader(): FilePayloadReader {
        val dispatcher = UnconfinedTestDispatcher()
        return FilePayloadReader(TestDispatcherProvider(dispatcher))
    }

    private fun String.source(): Buffer {
        return Buffer().write(encodeToByteArray())
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
