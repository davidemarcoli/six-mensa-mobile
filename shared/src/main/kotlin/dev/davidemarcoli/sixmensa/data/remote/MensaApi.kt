package dev.davidemarcoli.sixmensa.data.remote

import dev.davidemarcoli.sixmensa.data.remote.dto.DayMenuDto
import dev.davidemarcoli.sixmensa.data.remote.dto.HistoryEntryDto
import dev.davidemarcoli.sixmensa.data.remote.dto.StatusDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MensaApi {

    /**
     * `day = -1` returns the whole week. Single-day responses (`0..4`) are a bare object
     * rather than an array, and v1 never asks for them, so that shape isn't modelled.
     */
    @GET("{restaurant}/{day}")
    suspend fun week(
        @Path("restaurant") restaurant: String,
        @Path("day") day: Int = -1,
        @Query("language") language: String,
    ): List<DayMenuDto>

    /** `from`/`to` are strictly `YYYY-MM`; other formats silently return an empty list. */
    @GET("history")
    suspend fun history(
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): List<HistoryEntryDto>

    @GET("history/{restaurant}")
    suspend fun history(
        @Path("restaurant") restaurant: String,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): List<HistoryEntryDto>

    /** `{"HTP": url, "HT201": url}`. The Migros media hash rotates — never cache these forever. */
    @GET("pdf-links")
    suspend fun pdfLinks(): Map<String, String>

    @GET("status")
    suspend fun status(): StatusDto
}
