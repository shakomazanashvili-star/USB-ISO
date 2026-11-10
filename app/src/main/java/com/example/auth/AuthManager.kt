package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

object AuthManager {
    private const val TAG = "AuthManager"

    private fun getWebClientId(context: Context): String {
        return try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.w(TAG, "default_web_client_id resource not found: ${e.message}")
            ""
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance().also { instance ->
                instance.addAuthStateListener { firebaseAuth ->
                    _currentUser.value = firebaseAuth.currentUser
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth initialization bypassed: ${e.message}")
            null
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        try {
            _currentUser.value = auth?.currentUser
        } catch (e: Exception) {
            // Ignored in test/offline environments
        }
    }

    /**
     * Interactive Sign-In with Google using GetSignInWithGoogleOption exclusively.
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        _isLoading.value = true
        val credentialManager = CredentialManager.create(context)
        val clientId = getWebClientId(context)
        if (clientId.isBlank()) {
            _isLoading.value = false
            return Result.failure(IllegalStateException("Web Client ID is not configured"))
        }

        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(request = request, context = context)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val firebaseAuth = auth ?: throw IllegalStateException("Firebase is not initialized")
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign-in")
                _currentUser.value = user
                _isLoading.value = false
                Result.success(user)
            } else {
                _isLoading.value = false
                val err = "Unexpected credential type: ${credential.type}"
                Log.w(TAG, err)
                Result.failure(Exception(err))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "User cancelled Google Sign-in flow", e)
            _isLoading.value = false
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "CredentialManager error during Google Sign-in: ${e.message}", e)
            _isLoading.value = false
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Google Sign-in: ${e.message}", e)
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Silent sign-in check using GetGoogleIdOption in a separate request.
     */
    suspend fun trySilentSignIn(context: Context): Boolean {
        val firebaseAuth = auth ?: return false
        if (firebaseAuth.currentUser != null) return true
        val credentialManager = CredentialManager.create(context)
        val clientId = getWebClientId(context)
        if (clientId.isBlank()) return false

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(request = request, context = context)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                _currentUser.value = authResult.user
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign-in not available or failed: ${e.message}")
            false
        }
    }

    fun signOut() {
        auth?.signOut()
        _currentUser.value = null
    }
}
