package dev.personalterminal.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.personalterminal.BuildConfig
import kotlinx.coroutines.tasks.await

/**
 * Handles Google account selection (Credential Manager) and Drive scope authorisation
 * (Play Services Identity `AuthorizationClient`).
 *
 * We request only `drive.file` – the app can only see files it created, which is exactly what a
 * backup folder needs, and it keeps the OAuth consent screen simple.
 */
class GoogleAuth(private val context: Context) {

    sealed class SignInResult {
        data class Success(val email: String, val displayName: String?) : SignInResult()
        data class Error(val message: String) : SignInResult()
        data object NotConfigured : SignInResult()
    }

    /** Result of the Drive authorisation step. */
    sealed class AuthzResult {
        data class Granted(val accessToken: String) : AuthzResult()
        /** User interaction needed – launch this intent with an ActivityResultLauncher and retry. */
        data class NeedsResolution(val intent: Intent) : AuthzResult()
        data class Error(val message: String) : AuthzResult()
    }

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signIn(activity: Activity): SignInResult {
        if (!isConfigured) return SignInResult.NotConfigured
        return try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(context).getCredential(activity, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                SignInResult.Success(g.id, g.displayName)
            } else {
                SignInResult.Error("unexpected credential type ${cred.type}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "sign-in failed", e)
            SignInResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun signOut() {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /**
     * Obtain an OAuth access token for the Drive scope. Works silently once the user granted consent;
     * otherwise returns [AuthzResult.NeedsResolution] with the consent intent.
     */
    suspend fun authorizeDrive(): AuthzResult = try {
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
        val result: AuthorizationResult = Identity.getAuthorizationClient(context).authorize(req).await()
        when {
            result.hasResolution() -> {
                val pi = result.pendingIntent
                if (pi != null) AuthzResult.NeedsResolution(Intent().apply { putExtra(EXTRA_PENDING_INTENT, pi) })
                else AuthzResult.Error("authorization requires user consent but no intent was provided")
            }
            result.accessToken != null -> AuthzResult.Granted(result.accessToken!!)
            else -> AuthzResult.Error("no access token returned")
        }
    } catch (e: Exception) {
        Log.w(TAG, "drive authorization failed", e)
        AuthzResult.Error(e.message ?: e.javaClass.simpleName)
    }

    /** Parse the result of the consent activity. */
    fun tokenFromConsentResult(data: Intent?): String? = runCatching {
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
    }.getOrNull()

    companion object {
        private const val TAG = "GoogleAuth"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val EXTRA_PENDING_INTENT = "pending_intent"
    }
}
