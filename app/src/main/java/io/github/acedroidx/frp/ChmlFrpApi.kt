package io.github.acedroidx.frp

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ChmlFrpApi {
    @GET("tunnel")
    suspend fun getTunnels(@Query("token") token: String): Response<ApiResponse>
    
    @GET("cfg.php")
    suspend fun getConfig(
        @Query("id") id: Int,
        @Query("token") token: String,
        @Query("action") action: String = "getcfg"
    ): Response<ConfigResponse>
    
    companion object {
        private const val BASE_URL = "http://cf-v2.uapis.cn/"
        private const val CONFIG_BASE_URL = "https://cf-v1.uapis.cn/api/"
        
        fun create(): ChmlFrpApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()
                
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChmlFrpApi::class.java)
        }
        
        fun createConfigApi(): ChmlFrpApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()
                
            return Retrofit.Builder()
                .baseUrl(CONFIG_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChmlFrpApi::class.java)
        }
    }
}
