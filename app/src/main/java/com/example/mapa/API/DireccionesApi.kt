package com.example.mapa.API

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val BASE_URL = "https://api.openrouteservice.org"

/*private const val INICIO = "8.681495,49.41461"
private const val FINAL = "20.140153689100682,-101.15067778465794"
private const val API = "5b3ce3597851110001cf6248195446ce6bac45e7851606b557eab502"
*/
private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

private val retrofit =
    Retrofit.Builder().addConverterFactory(MoshiConverterFactory.create(moshi)).baseUrl(BASE_URL)
        .build()


interface DireccionesApi {
    @GET("v2/directionsfoot-walking")
    suspend fun getDirections(
        @Query("api_key") apiKey: String,
        @Query("start") inicio: String,
        @Query("end") final: String
    ): Coordenadas
}


object Direcciones {
    val retrofitService: DireccionesApi by lazy {
        retrofit.create(DireccionesApi::class.java)
    }
}