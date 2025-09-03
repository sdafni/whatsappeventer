package com.example.whatsappeventer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException

class SignInActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SignInActivity"
    }
    
    private lateinit var googleSignInManager: GoogleSignInManager
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // This activity is transparent and handles sign-in only
        Log.d(TAG, "SignInActivity started for overlay sign-in")
        
        initGoogleSignIn()
        startSignIn()
    }
    
    private fun initGoogleSignIn() {
        googleSignInManager = GoogleSignInManager.getInstance(this)
        
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                Log.d(TAG, "Sign in successful: ${account.email}")
                googleSignInManager.handleSignInResult(account)
                Toast.makeText(this, "✅ Signed in successfully!", Toast.LENGTH_SHORT).show()
                
                // Notify that sign-in was successful via broadcast
                val intent = Intent("com.example.whatsappeventer.SIGN_IN_SUCCESS")
                sendBroadcast(intent)
                
                finish()
            } catch (e: ApiException) {
                Log.w(TAG, "Sign in failed: ${e.statusCode}")
                Toast.makeText(this, "❌ Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    private fun startSignIn() {
        if (googleSignInManager.isSignedIn()) {
            Log.d(TAG, "User already signed in")
            Toast.makeText(this, "Already signed in!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        try {
            val signInIntent = googleSignInManager.getSignInIntent()
            Log.d(TAG, "Starting sign-in process")
            signInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting sign-in: ${e.message}")
            Toast.makeText(this, "Error starting sign-in: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}