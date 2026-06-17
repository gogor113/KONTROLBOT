package com.example.kontrolbot.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class TokenRequest(val id_token: String)
data class TokenResponse(val ok: Boolean, val email: String? = null, val message: String? = null)

interface ApiService {
    @POST("auth/google")
    suspend fun verifyToken(@Body request: TokenRequest): Response<TokenResponse>
}
