package com.imesh.streamlite

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** One shared OkHttp client: connection reuse and HTTP/2 save time and battery across seeks and segments. */
object HttpClients {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
