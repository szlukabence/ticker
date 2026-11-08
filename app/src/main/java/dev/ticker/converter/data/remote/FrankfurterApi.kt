package dev.ticker.converter.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FrankfurterApi {
    @GET("v1/latest")
    suspend fun latest(@Query("base") base: String = "USD"): FrankfurterLatestDto

    /** [range] is "YYYY-MM-DD..YYYY-MM-DD". [symbols] is comma separated. */
    @GET("v1/{range}")
    suspend fun series(
        @Path("range", encoded = true) range: String,
        @Query("base") base: String = "USD",
        @Query("symbols", encoded = true) symbols: String?,
    ): FrankfurterSeriesDto
}
