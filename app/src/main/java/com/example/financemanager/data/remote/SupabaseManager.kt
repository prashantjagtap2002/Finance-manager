package com.example.financemanager.data.remote

import com.example.financemanager.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single entry point for the Supabase backend.
 *
 * Uses the client-safe publishable key (all access goes through Row-Level Security).
 * Auth is anonymous + device-based: on first launch we create an anonymous user; the
 * session is persisted by the Auth plugin so the same identity survives app restarts.
 */
object SupabaseManager {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    /** True only if a URL + key were provided in local.properties. */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()

    /**
     * Ensures there is a signed-in (anonymous) session, creating one on first launch.
     * Returns the user id, or null if Supabase isn't configured / sign-in failed.
     */
    suspend fun ensureSignedIn(context: android.content.Context): String? {
        if (!isConfigured) return null
        val prefs = context.getSharedPreferences("supabase_session", android.content.Context.MODE_PRIVATE)
        return try {
            val auth = client.auth
            auth.awaitInitialization()
            
            if (auth.currentSessionOrNull() == null) {
                val savedAccessToken = prefs.getString("access_token", null)
                val savedRefreshToken = prefs.getString("refresh_token", null)
                
                if (savedAccessToken != null && savedRefreshToken != null) {
                    try {
                        auth.importAuthToken(savedAccessToken, savedRefreshToken)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            if (auth.currentSessionOrNull() == null) {
                auth.signInAnonymously()
                val session = auth.currentSessionOrNull()
                if (session != null) {
                    prefs.edit()
                        .putString("access_token", session.accessToken)
                        .putString("refresh_token", session.refreshToken)
                        .apply()
                }
            }
            auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            android.util.Log.e("SupabaseManager", "Failed to ensure signed in", e)
            null
        }
    }

    /**
     * Signs in using a Google ID Token retrieved via Android CredentialManager.
     */
    suspend fun signInWithGoogleIdToken(token: String): String? {
        if (!isConfigured) return null
        return try {
            val auth = client.auth
            auth.signInWith(io.github.jan.supabase.auth.providers.builtin.IDToken) {
                idToken = token
                provider = io.github.jan.supabase.auth.providers.Google
            }
            auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
