package com.example.financemanager.data.remote

import com.example.financemanager.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

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
            // A NULL column must not take a whole table's sync down with it.
            //
            // The Supabase tables are created by hand from the README, and a column added later
            // leaves NULL in every row that predates it. A Kotlin default on the DTO property does
            // NOT cover that: a default fills in a *missing* key, while an explicit `null` for a
            // non-nullable property is a decoding error. One such row threw, `syncTable` logged it
            // and moved on, and the entire table stopped syncing — silently, forever. That is what
            // kept 349 captured bank alerts in the cloud and out of the app.
            //
            // `coerceInputValues` makes an explicit null fall back to the property's declared
            // default, which is the reading every one of these columns already wanted.
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    explicitNulls = false
                }
            )
            install(Auth)
            install(Postgrest)
        }
    }

    /** True only if a URL + key were provided in local.properties. */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()

    /** Serialises [ensureSignedIn]: the app and the sync worker both call it at startup. */
    private val signInLock = Mutex()

    /**
     * Ensures there is a signed-in (anonymous) session, creating one on first launch only.
     *
     * There used to be a second copy of the access and refresh tokens here, written to a
     * SharedPreferences file in plaintext and re-imported on the next launch. It bought nothing:
     * awaitInitialization already restores the session the Auth plugin saved for itself. All the
     * copy did was put a second set of live credentials on disk, so it is gone.
     *
     * A new anonymous user is minted only when the plugin reports that nobody is signed in. It
     * used to be minted whenever `currentSessionOrNull()` was null — and that is also null while a
     * stored session is merely failing to refresh. The access token lasts an hour, so the first
     * start after that does a refresh, and one dropped request (offline, a captive portal, DNS not
     * up yet) left the plugin in RefreshFailure while it retried. This then signed in as somebody
     * new and the plugin saved that session over the real one. An anonymous identity can't be
     * signed back into, so every row already in the cloud was stranded under the old id, and RLS
     * rejected each later upload of those same ids — the "Last sync failed" that never cleared.
     * A refresh failure now just returns null; the plugin keeps retrying and the next sync gets
     * the same user back.
     *
     * Two callers at once with no session would also each mint a user, hence [signInLock].
     */
    suspend fun ensureSignedIn(): String? {
        if (!isConfigured) return null
        return try {
            signInLock.withLock {
                val auth = client.auth
                // Restores whatever session the plugin persisted, if any.
                auth.awaitInitialization()
                when (val status = auth.sessionStatus.value) {
                    is SessionStatus.Authenticated -> status.session.user?.id
                    is SessionStatus.NotAuthenticated -> {
                        auth.signInAnonymously()
                        auth.currentUserOrNull()?.id
                    }
                    is SessionStatus.RefreshFailure -> {
                        android.util.Log.w("SupabaseManager", "Stored session failed to refresh; keeping it and retrying later")
                        null
                    }
                    SessionStatus.Initializing -> null
                }
            }
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
