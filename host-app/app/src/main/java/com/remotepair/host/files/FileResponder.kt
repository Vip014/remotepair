package com.remotepair.host.files

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Answers file requests from the controller over the "files" DataChannel.
 * Runs silently — nothing is shown on the host screen.
 *
 * Protocol (controller -> host, all JSON text frames):
 *   {t:"ls", path}      -> host: {t:"ls_res", path, root, entries:[{name,dir,size}]}
 *   {t:"get", path}     -> host: {t:"file_begin", name, size}, binary chunks…, {t:"file_end"}
 *   {t:"cancel"}        -> host stops the current transfer
 *
 * Access is restricted to the shared storage root; paths that escape it are refused.
 */
class FileResponder(private val dc: DataChannel) {
    private val root: File = Environment.getExternalStorageDirectory()
    @Volatile private var cancelCurrent = false

    fun attach() {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                handle(String(bytes, StandardCharsets.UTF_8))
            }
        })
    }

    private fun sendJson(o: JSONObject) {
        val b = o.toString().toByteArray(StandardCharsets.UTF_8)
        runCatching { dc.send(DataChannel.Buffer(ByteBuffer.wrap(b), false)) }
    }

    private fun rootPath(): String = runCatching { root.canonicalPath }.getOrDefault(root.path)

    private fun safeResolve(path: String?): File? {
        val target = if (path.isNullOrEmpty()) root else File(path)
        return try {
            val canon = target.canonicalFile
            val rp = rootPath()
            if (canon.path == rp || canon.path.startsWith("$rp/")) canon else null
        } catch (e: Exception) { null }
    }

    private fun handle(msg: String) {
        val o = try { JSONObject(msg) } catch (e: Exception) { return }
        when (o.optString("t")) {
            "ls" -> listDir(o.optString("path", root.path))
            "get" -> thread { sendFile(o.optString("path")) }
            "cancel" -> cancelCurrent = true
        }
    }

    private fun listDir(path: String) {
        val dir = safeResolve(path)
        if (dir == null || !dir.isDirectory) {
            sendJson(JSONObject().put("t", "ls_res").put("path", path).put("error", "not_a_dir"))
            return
        }
        val arr = JSONArray()
        dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { f ->
                arr.put(
                    JSONObject()
                        .put("name", f.name)
                        .put("dir", f.isDirectory)
                        .put("size", if (f.isFile) f.length() else 0)
                )
            }
        sendJson(
            JSONObject()
                .put("t", "ls_res")
                .put("path", dir.path)
                .put("root", rootPath())
                .put("entries", arr)
        )
    }

    private fun sendFile(path: String?) {
        val f = safeResolve(path)
        if (f == null || !f.isFile) {
            sendJson(JSONObject().put("t", "error").put("error", "not_a_file"))
            return
        }
        cancelCurrent = false
        sendJson(JSONObject().put("t", "file_begin").put("name", f.name).put("size", f.length()))
        val chunk = ByteArray(16 * 1024)
        runCatching {
            f.inputStream().use { ins ->
                while (true) {
                    if (cancelCurrent) { sendJson(JSONObject().put("t", "file_cancelled")); return }
                    val n = ins.read(chunk)
                    if (n <= 0) break
                    // Back-pressure: don't let the send buffer grow unbounded.
                    var guard = 0
                    while (dc.bufferedAmount() > 4L * 1024 * 1024 && guard < 2000) {
                        Thread.sleep(8); guard++
                    }
                    dc.send(DataChannel.Buffer(ByteBuffer.wrap(chunk.copyOf(n)), true))
                }
            }
        }.onFailure {
            sendJson(JSONObject().put("t", "error").put("error", "read_failed"))
            return
        }
        sendJson(JSONObject().put("t", "file_end").put("name", f.name))
    }
}
