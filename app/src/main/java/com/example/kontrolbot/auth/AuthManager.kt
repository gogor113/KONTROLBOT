package com.example.kontrolbot.auth

import android.content.Context

object AuthManager {
    private const val PREFS_NAME = "kontrolbot_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_EMAIL = "email"
    private const val KEY_ID_TOKEN = "id_token"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_IS_LOGGED_IN, false)

    fun saveSession(ctx: Context, email: String?, idToken: String?) {
        prefs(ctx).edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_EMAIL, email)
            .putString(KEY_ID_TOKEN, idToken)
            .apply()
    }

    fun clearSession(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun getEmail(ctx: Context): String? = prefs(ctx).getString(KEY_EMAIL, null)
    fun getIdToken(ctx: Context): String? = prefs(ctx).getString(KEY_ID_TOKEN, null)
}
