package blinker.go.data.extension

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipInputStream

object ExtensionParser {

    fun parse(context: Context, uri: Uri): ExtensionInfo? {
        val bytes: ByteArray
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            bytes = stream.use { it.readBytes() }
        } catch (e: Exception) {
            return null
        }

        val type = detectType(bytes) ?: return null
        val zipBytes = extractZipBytes(bytes, type) ?: return null

        val id = UUID.randomUUID().toString().take(12)
        val extDir = File(context.filesDir, "extensions/$id")

        try {
            extDir.mkdirs()
            var manifestJson: String? = null

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        val safeFile = File(extDir, name)
                        if (!safeFile.canonicalPath.startsWith(extDir.canonicalPath)) {
                            zis.closeEntry()
                            entry = zis.nextEntry
                            continue
                        }
                        safeFile.parentFile?.mkdirs()
                        safeFile.outputStream().use { out -> zis.copyTo(out) }
                        if (name.equals("manifest.json", ignoreCase = true)) {
                            manifestJson = safeFile.readText()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifest = manifestJson?.let { JSONObject(it) } ?: run {
                extDir.deleteRecursively()
                return null
            }

            return buildInfo(manifest, id, type)
        } catch (e: Exception) {
            extDir.deleteRecursively()
            return null
        }
    }

    private fun detectType(bytes: ByteArray): ExtensionType? {
        if (bytes.size < 4) return null
        if (bytes[0] == 0x43.toByte() && bytes[1] == 0x72.toByte() &&
            bytes[2] == 0x32.toByte() && bytes[3] == 0x34.toByte()) {
            return ExtensionType.CRX
        }
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return ExtensionType.XPI
        }
        return null
    }

    private fun extractZipBytes(bytes: ByteArray, type: ExtensionType): ByteArray? {
        return when (type) {
            ExtensionType.XPI -> bytes
            ExtensionType.CRX -> extractCrxZip(bytes)
        }
    }

    private fun extractCrxZip(bytes: ByteArray): ByteArray? {
        if (bytes.size < 12) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val version = buf.int
        return when (version) {
            2 -> {
                if (bytes.size < 16) return null
                val pubKeyLen = buf.int
                val sigLen = buf.int
                val offset = 16 + pubKeyLen + sigLen
                if (offset >= bytes.size) null
                else bytes.copyOfRange(offset, bytes.size)
            }
            3 -> {
                val headerLen = buf.int
                val offset = 12 + headerLen
                if (offset >= bytes.size) null
                else bytes.copyOfRange(offset, bytes.size)
            }
            else -> null
        }
    }

    private fun buildInfo(
        manifest: JSONObject,
        id: String,
        type: ExtensionType
    ): ExtensionInfo {
        val name = manifest.optString("name", "Unknown Extension")
        val version = manifest.optString("version", "0.0")
        val description = manifest.optString("description", "")

        val contentScripts = mutableListOf<ContentScript>()
        manifest.optJSONArray("content_scripts")?.let { csArray ->
            for (i in 0 until csArray.length()) {
                val cs = csArray.getJSONObject(i)
                contentScripts.add(
                    ContentScript(
                        matches = toList(cs.optJSONArray("matches")),
                        excludeMatches = toList(cs.optJSONArray("exclude_matches")),
                        js = toList(cs.optJSONArray("js")),
                        css = toList(cs.optJSONArray("css")),
                        runAt = cs.optString("run_at", "document_idle")
                    )
                )
            }
        }

        return ExtensionInfo(
            id = id,
            name = name,
            version = version,
            description = description,
            contentScripts = contentScripts,
            type = type,
            permissions = toList(manifest.optJSONArray("permissions"))
        )
    }

    private fun toList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull {
            try { array.getString(it) } catch (_: Exception) { null }
        }
    }
}
