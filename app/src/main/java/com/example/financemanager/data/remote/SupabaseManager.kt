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
 * Uses the client-safe publishable key. That key is readable in any copy of the APK, so it is
 * Row-Level Security on the Supabase project — not the key — that keeps one user out of
 * another's rows. The README carries the policy SQL; without it every row is world-readable.
 *
 * Auth is anonymous + device-based: on first launch we create an anonymous user, and the Auth
 * plugin persists the session itself (autoLoadFromStorage/autoSaveToStorage are on by default),
 * so the same identity survives app restarts.
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
     *
     * There used to be a second copy of the access and refresh tokens here, written to a
     * SharedPreferences file in plaintext and re-imported on the next launch. It bought nothing:
     * awaitInitialization already restores the session the Auth plugin saved for itself. All the
     * copy did was put a second set of live credentials on disk, so it is gone.
     */
    suspend fun ensureSignedIn(): String? {
        if (!isConfigured) return null
        return try {
            val auth = client.auth
            // Restores whatever session the plugin persisted, if any.
            auth.awaitInitialization()
            if (auth.currentSessionOrNull() == null) {
                auth.signInAnonymously()
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
