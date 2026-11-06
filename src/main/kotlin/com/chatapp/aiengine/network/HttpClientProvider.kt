package com.chatapp.aiengine.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton factory providing tuned OkHttpClient instances for real-time SSE streaming.
 */
object HttpClientProvider {

    /**
     * Default OkHttpClient tailored for long-lived LLM streams:
     * - Fast connect timeout (15s)
     * - Extended read timeout (120s) to comfortably accommodate deep thinking models
     * - Connection pool to keep warm HTTP/2 connections alive
     */
    val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build()
    }
}

