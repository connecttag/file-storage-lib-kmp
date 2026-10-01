package org.connecttag.lib.filestorage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.connecttag.lib.coroutine.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import okio.buffer
import okio.source

class AndroidContentUriFileReader(
    context: Context,
    private val payloadReader: FilePayloadReader = FilePayloadReader(),
) {
    constructor(
        context: Context,
        dispatcher: CoroutineDispatcher,
    ) : this(
        context = context,
        payloadReader = FilePayloadReader(SingleDispatcherProvider(dispatcher)),
    )

    private val applicationContext = context.applicationContext

    suspend fun read(request: AndroidContentUriFileReadRequest): FileReadPayload {
        val resolver = applicationContext.contentResolver
        val uri = request.uri

        return payloadReader.read(
            FilePayloadReadRequest(
                openSource = {
                    resolver.openInputStream(uri)
                        ?.source()
                        ?.buffer()
                        ?: throw FilePayloadReadFailedException()
                },
                contentType = resolver.getType(uri),
                fileName = resolver.readDisplayName(uri),
                declaredSizeBytes = resolver.readDeclaredSize(uri),
                maxBytes = request.maxBytes,
                allowedContentTypes = request.allowedContentTypes,
                allowedContentTypePrefixes = request.allowedContentTypePrefixes,
                defaultContentType = request.defaultContentType,
            ),
        )
    }
}

data class AndroidContentUriFileReadRequest(
    val uri: Uri,
    val maxBytes: Long,
    val allowedContentTypes: Set<String> = emptySet(),
    val allowedContentTypePrefixes: Set<String> = emptySet(),
    val defaultContentType: String? = null,
)

private class SingleDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val unconfined: CoroutineDispatcher = dispatcher
}

private fun android.content.ContentResolver.readDisplayName(uri: Uri): String? {
    return query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }?.takeIf { it.isNotBlank() }
}

private fun android.content.ContentResolver.readDeclaredSize(uri: Uri): Long? {
    return query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else null
    }
}
