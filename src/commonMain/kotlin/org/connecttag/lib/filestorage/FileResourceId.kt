package org.connecttag.lib.filestorage

data class FileResourceId(
    val namespace: String,
    val key: String,
) {
    init {
        require(namespace.isSafeStorageSegment()) {
            "File namespace must be non-blank and contain only safe path characters."
        }
        require(key.isSafeStoragePath()) {
            "File key must be a safe relative path."
        }
    }

    val storagePath: String = "$namespace/$key"
}

object FileStorageKeys {
    fun fromUrl(namespace: String, url: String, fallbackKey: String = "file"): FileResourceId {
        val candidate = url
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
            .ifBlank { fallbackKey }
        return FileResourceId(namespace = namespace, key = safeKey(candidate))
    }

    fun safeKey(raw: String, fallback: String = "file"): String {
        val collapsed = raw.toSafeKeyToken()
        val safeFallback = fallback.toSafeKeyToken().ifBlank { "file" }

        return collapsed.ifBlank { safeFallback }
    }

    private fun String.toSafeKeyToken(): String {
        return trim()
            .map { char -> if (char.isSafeFileKeyChar()) char.lowercaseChar() else '-' }
            .joinToString(separator = "")
            .collapseDashes()
            .trim('-', '.', '_')
            .take(MAX_KEY_LENGTH)
    }

    private const val MAX_KEY_LENGTH = 160
}

private fun String.isSafeStorageSegment(): Boolean {
    return isNotBlank() && '/' !in this && '\\' !in this && this != "." && this != ".." && all {
        it.isSafeFileKeyChar()
    }
}

private fun String.isSafeStoragePath(): Boolean {
    if (isBlank() || startsWith("/") || startsWith("\\") || endsWith("/") || endsWith("\\")) return false
    if ('\\' in this || "//" in this) return false
    return split('/').all { it.isSafeStorageSegment() }
}

private fun Char.isSafeFileKeyChar(): Boolean {
    return isLetterOrDigit() || this == '-' || this == '_' || this == '.'
}

private fun String.collapseDashes(): String {
    val output = StringBuilder(length)
    var previousWasDash = false
    for (char in this) {
        if (char == '-') {
            if (!previousWasDash) output.append(char)
            previousWasDash = true
        } else {
            output.append(char)
            previousWasDash = false
        }
    }
    return output.toString()
}
