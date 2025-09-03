package com.example.whatsappeventer

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes

class GoogleSignInManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "GoogleSignInManager"
        private const val SERVER_CLIENT_ID = "238877844764-59tr5rqvvcgoersb7h4albcunalgtk7c.apps.googleusercontent.com" // TODO: Replace with actual client ID
        
        @Volatile
        private var INSTANCE: GoogleSignInManager? = null
        
        fun getInstance(context: Context): GoogleSignInManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoogleSignInManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var googleSignInClient: GoogleSignInClient
    private var credential: GoogleAccountCredential? = null
    private var calendarService: Calendar? = null
    
    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }
    
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && !GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR)).not()
    }
    
    fun getCurrentAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            Log.d(TAG, "Sign in successful for: ${account.email}")
            setupCalendarService(account)
        } else {
            Log.w(TAG, "Sign in failed - account is null")
        }
    }
    
    private fun setupCalendarService(account: GoogleSignInAccount) {
        try {
            credential = GoogleAccountCredential.usingOAuth2(
                context, 
                listOf(CalendarScopes.CALENDAR)
            )
            credential?.selectedAccount = account.account
            
            calendarService = Calendar.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName(context.getString(R.string.app_name))
                .build()
                
            Log.d(TAG, "Calendar service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up calendar service: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun getCalendarService(): Calendar? {
        if (calendarService == null) {
            getCurrentAccount()?.let { account ->
                setupCalendarService(account)
            }
        }
        return calendarService
    }
    
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            googleSignInClient.signOut()
            credential = null
            calendarService = null
            Log.d(TAG, "Sign out successful")
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out: ${e.message}")
            throw e
        }
    }
    
    fun hasCalendarPermissions(): Boolean {
        val account = getCurrentAccount()
        return account != null && GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR))
    }
    
    suspend fun requestCalendarPermissions(): Boolean = withContext(Dispatchers.IO) {
        try {
            val account = getCurrentAccount()
            if (account == null) {
                Log.w(TAG, "No account found for permissions request")
                return@withContext false
            }
            
            // Check if we already have permissions
            if (GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR))) {
                Log.d(TAG, "Calendar permissions already granted")
                return@withContext true
            }
            
            // If we don't have permissions, they need to sign in again with calendar scope
            Log.d(TAG, "Calendar permissions not granted - user needs to sign in again")
            return@withContext false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting calendar permissions: ${e.message}")
            return@withContext false
        }
    }
}