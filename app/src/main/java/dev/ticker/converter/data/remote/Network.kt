package dev.ticker.converter.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object Network {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    private val json by lazy { Json { ignoreUnknownKeys = true; isLenient = true } }

    fun okHttp(): OkHttpClient = client
    fun json(): Json = json

    private fun retrofit(base: String) = Retrofit.Builder()
        .baseUrl(base)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun frankfurter(base: String = "https://api.frankfurter.dev/"): FrankfurterApi =
        retrofit(base).create(FrankfurterApi::class.java)

}
