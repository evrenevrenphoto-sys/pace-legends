package com.pace.legends.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pace.legends.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val userId: String, val isSetupCompleted: Boolean) : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInWithGoogleUseCase: com.pace.legends.domain.usecase.auth.SignInWithGoogleUseCase,
    private val continueAnonymouslyUseCase: com.pace.legends.domain.usecase.auth.ContinueAnonymouslyUseCase,
    private val authRepository: AuthRepository, // Account linking ve diğer utility'ler için gerekli olabilir
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()
    
    // Firebase Console > Project Settings > General > Web API Key
    // veya google-services.json "client_id" (type 3 - web client)
    private val webClientId = com.pace.legends.BuildConfig.WEB_CLIENT_ID

    private val credentialManager = CredentialManager.create(context)

    /**
     * Google ile giriş yap
     */
    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false) // Seçiciyi zorla aç
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result: GetCredentialResponse = credentialManager.getCredential(
                    request = request,
                    context = activityContext
                )

                handleSignInResult(result)
                
            } catch (e: GetCredentialException) {
                _loginState.value = LoginState.Error("Giriş iptal edildi veya başarısız: ${e.message}")
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Beklenmeyen hata: ${e.message}")
            }
        }
    }

    private suspend fun handleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential
        
        when {
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                
                // Account linking logic'i karmaşık olduğu için şimdilik repoda bırakıldı.
                // İleride LinkAccountUseCase yazılabilir.
                if (authRepository.isAnonymousUser()) {
                    val linkResult = authRepository.linkAnonymousToGoogle(idToken)
                    if (linkResult.isSuccess) {
                        // Linked!
                        _loginState.value = LoginState.Success(authRepository.getCurrentUserId() ?: "", false)
                        return
                    }
                    // Linking başarısız olduysa normal sign-in use case'ini dene
                }
                
                // UseCase ile Sign-In ve User Creation
                val result = signInWithGoogleUseCase(idToken)
                when (result) {
                    is com.pace.legends.domain.model.AuthResult.Success -> {
                         _loginState.value = LoginState.Success(result.user.id, result.user.isSetupCompleted)
                    }
                    is com.pace.legends.domain.model.AuthResult.Error -> {
                        _loginState.value = LoginState.Error(result.message)
                    }
                }
            }
            else -> {
                _loginState.value = LoginState.Error("Desteklenmeyen credential türü")
            }
        }
    }

    /**
     * Anonim olarak devam et
     */
    fun continueAnonymously() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
            // UseCase ile Anonim Giriş
            val result = continueAnonymouslyUseCase()
            when (result) {
                is com.pace.legends.domain.model.AuthResult.Success -> {
                    _loginState.value = LoginState.Success(result.user.id, result.user.isSetupCompleted)
                }
                is com.pace.legends.domain.model.AuthResult.Error -> {
                    _loginState.value = LoginState.Error(result.message)
                }
            }
        }
    }
    
    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}
