package dev.ticker.converter.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class FrankfurterLatestDto(val base: String, val date: String, val rates: Map<String, String>)

@Serializable
data class FrankfurterSeriesDto(val base: String, val rates: Map<String, Map<String, String>>)
