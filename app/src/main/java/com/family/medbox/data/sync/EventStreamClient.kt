package com.family.medbox.data.sync

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 家庭动态的 SSE 长连接。
 *
 * 服务端有新事件时会立刻推过来，收到后回调一次；连接空闲时靠服务端心跳保活。
 * [open] 是阻塞调用，必须在 IO 线程执行；[close] 可以从任意线程打断它。
 */
class EventStreamClient {

    private var connection: HttpURLConnection? = null

    /** 打断正在读取的长连接（退出家庭组、切到后台、退出登录时调用）。 */
    @Synchronized
    fun close() {
        runCatching { connection?.disconnect() }
        connection = null
    }

    /**
     * 连上 SSE 并持续读取，直到连接断开或被 [close] 打断。
     * 抛出的异常交给调用方决定是否重连。
     */
    fun open(
        serverUrl: String,
        token: String,
        sinceId: Long,
        onOpen: () -> Unit,
        onEvent: (FamilyEvent) -> Unit
    ) {
        val url = serverUrl.trim().trimEnd('/') + "/events/stream?since_id=$sinceId"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            // 服务端 15 秒一次心跳，45 秒没数据说明连接已经死了，让调用方重连。
            readTimeout = 45_000
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Authorization", "Bearer $token")
        }
        synchronized(this) { connection = conn }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw SyncClient.ApiException("实时通道连接失败（HTTP $code）")
            }
            onOpen()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                readEvents(reader, onEvent)
            }
        } finally {
            synchronized(this) {
                if (connection === conn) connection = null
            }
            runCatching { conn.disconnect() }
        }
    }

    /** 解析 SSE 报文：按空行切分，只关心 data 行，注释行（心跳）直接忽略。 */
    private fun readEvents(reader: BufferedReader, onEvent: (FamilyEvent) -> Unit) {
        val data = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: return
            when {
                line.isEmpty() -> {
                    if (data.isNotEmpty()) {
                        runCatching { FamilyEvent.fromJson(JSONObject(data.toString())) }
                            .onSuccess(onEvent)
                        data.clear()
                    }
                }
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trim())
                }
                // event: / id: / retry: / ": ping" 目前都不需要
            }
        }
    }
}
