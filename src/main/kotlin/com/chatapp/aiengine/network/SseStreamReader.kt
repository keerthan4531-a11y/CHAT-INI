package com.chatapp.aiengine.network

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Low-level SSE parser that streams lines as soon as bytes hit the TCP socket.
 */
object SseStreamReader {

    /**
     * Reads Server-Sent Events stream line-by-line and triggers [onDataPayload] for each data: packet.
     *
     * @param inputStream The raw network stream from the HTTP response.
     * @param onDataPayload Callback invoked with payload string (stripped of the "data:" prefix).
     * @param onComplete Callback invoked when [DONE] or end-of-stream is reached.
     */
    fun readStream(
        inputStream: InputStream,
        onDataPayload: (payload: String) -> Unit,
        onComplete: () -> Unit
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 8192)
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue

                if (currentLine.isEmpty() || currentLine.startsWith(":")) {
                    // Empty heartbeat or SSE comment line (keepalive)
                    continue
                }

                if (currentLine.startsWith("data:")) {
                    val payload = currentLine.substring(5).trim()
                    if (payload == "[DONE]") {
                        onComplete()
                        return
                    }
                    onDataPayload(payload)
                }
            }
            onComplete()
        } finally {
            try {
                reader.close()
            } catch (_: Exception) {}
        }
    }
}

