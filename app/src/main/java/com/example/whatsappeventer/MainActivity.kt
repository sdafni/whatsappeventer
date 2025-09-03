package com.example.whatsappeventer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import android.content.ComponentName
import android.text.TextUtils
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {
    
    private lateinit var btnStartStop: Button
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnUsageStatsPermission: Button
    private lateinit var btnAccessibilityPermission: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var tvStatus: TextView
    
    // Test buttons
    private lateinit var btnRunQuickTest: Button
    private lateinit var btnRunFullTest: Button
    private lateinit var btnRunCalendarTest: Button
    private lateinit var btnShowTestCases: Button
    
    private var isOverlayRunning = false
    private lateinit var googleSignInManager: GoogleSignInManager
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "MainActivity"
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
        private const val USAGE_STATS_PERMISSION_REQUEST_CODE = 5678
        private const val ACCESSIBILITY_PERMISSION_REQUEST_CODE = 3456
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initGoogleSignIn()
        initViews()
        setupClickListeners()
        checkPermissions()
        
        // Check if this activity was launched for sign-in from overlay
        handleOverlaySignInRequest()
    }
    
    private fun initGoogleSignIn() {
        googleSignInManager = GoogleSignInManager.getInstance(this)
        
        // Initialize sign-in launcher
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                Log.d(TAG, "Sign in successful: ${account.email}")
                googleSignInManager.handleSignInResult(account)
                updateGoogleSignInButton()
                Toast.makeText(this, "✅ Signed in successfully", Toast.LENGTH_SHORT).show()
                
                // If sign-in was triggered from overlay, direct user to WhatsApp
                if (intent.getBooleanExtra("trigger_signin", false)) {
                    handler.postDelayed({
                        openWhatsApp()
                    }, 2000) // Give user time to see success message
                }
            } catch (e: ApiException) {
                Log.w(TAG, "Sign in failed: ${e.statusCode}")
                Toast.makeText(this, "❌ Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
                updateGoogleSignInButton()
            }
        }
    }
    
    private fun initViews() {
        btnStartStop = findViewById(R.id.btnStartStop)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnUsageStatsPermission = findViewById(R.id.btnUsageStatsPermission)
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvStatus = findViewById(R.id.tvStatus)
        
        // Test buttons
        btnRunQuickTest = findViewById(R.id.btnRunQuickTest)
        btnRunFullTest = findViewById(R.id.btnRunFullTest)
        btnRunCalendarTest = findViewById(R.id.btnRunCalendarTest)
        btnShowTestCases = findViewById(R.id.btnShowTestCases)
    }
    
    private fun setupClickListeners() {
        btnStartStop.setOnClickListener {
            if (isOverlayRunning) {
                stopOverlay()
            } else {
                startOverlay()
            }
        }
        
        btnOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }
        
        btnUsageStatsPermission.setOnClickListener {
            requestUsageStatsPermission()
        }
        
        btnAccessibilityPermission.setOnClickListener {
            requestAccessibilityPermission()
        }
        
        btnGoogleSignIn.setOnClickListener {
            Log.d(TAG, "Google Sign In button clicked")
            handleGoogleSignInClick()
        }
        
        // Test button listeners
        btnRunQuickTest.setOnClickListener {
            runQuickTest()
        }
        
        btnRunFullTest.setOnClickListener {
            runFullTest()
        }
        
        btnRunCalendarTest.setOnClickListener {
            runCalendarIntegrationTest()
        }
        
        btnShowTestCases.setOnClickListener {
            showTestCases()
        }
    }
    
    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            tvStatus.text = "Status: Overlay permission required"
            return
        }
        
        if (!hasUsageStatsPermission()) {
            tvStatus.text = "Status: Usage stats permission required"
            return
        }
        
        if (!hasAccessibilityPermission()) {
            tvStatus.text = "Status: Accessibility permission required"
            return
        }
        
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        isOverlayRunning = true
        btnStartStop.text = getString(R.string.stop_overlay)
        tvStatus.text = "Status: ${getString(R.string.overlay_service_running)}"
    }
    
    private fun stopOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        
        isOverlayRunning = false
        btnStartStop.text = getString(R.string.start_overlay)
        tvStatus.text = "Status: ${getString(R.string.overlay_service_stopped)}"
    }
    
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivityForResult(intent, USAGE_STATS_PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun requestAccessibilityPermission() {
        if (!hasAccessibilityPermission()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivityForResult(intent, ACCESSIBILITY_PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    
    private fun hasAccessibilityPermission(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        ) == 1
        
        if (accessibilityEnabled) {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            
            while (colonSplitter.hasNext()) {
                val componentName = ComponentName.unflattenFromString(colonSplitter.next())
                if (componentName != null && componentName.packageName == packageName) {
                    return true
                }
            }
        }
        
        return false
    }
    
    
    private fun isOverlayServiceRunning(): Boolean {
        try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (service.service.className == "com.example.whatsappeventer.OverlayService") {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking service status: ${e.message}")
            return false
        }
    }
    
    private fun checkServiceStatus() {
        try {
            val isServiceRunning = isOverlayServiceRunning()
            if (isServiceRunning != isOverlayRunning) {
                isOverlayRunning = isServiceRunning
                if (isOverlayRunning) {
                    btnStartStop.text = getString(R.string.stop_overlay)
                    tvStatus.text = "Status: ${getString(R.string.overlay_service_running)}"
                    android.util.Log.d("MainActivity", "Service detected as running - updating UI to Stop Overlay")
                } else {
                    btnStartStop.text = getString(R.string.start_overlay)
                    tvStatus.text = "Status: ${getString(R.string.overlay_service_stopped)}"
                    android.util.Log.d("MainActivity", "Service detected as stopped - updating UI to Start Overlay")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in checkServiceStatus: ${e.message}")
        }
    }
    
    private fun checkPermissions() {
        if (Settings.canDrawOverlays(this)) {
            btnOverlayPermission.text = "Overlay Permission: Granted"
            btnOverlayPermission.isEnabled = false
        }
        
        if (hasUsageStatsPermission()) {
            btnUsageStatsPermission.text = "Usage Stats Permission: Granted"
            btnUsageStatsPermission.isEnabled = false
        }
        
        if (hasAccessibilityPermission()) {
            btnAccessibilityPermission.text = "Accessibility Permission: Granted"
            btnAccessibilityPermission.isEnabled = false
        }
        
        updateGoogleSignInButton()
    }
    
    private fun handleGoogleSignInClick() {
        Log.d(TAG, "handleGoogleSignInClick called")
        if (googleSignInManager.isSignedIn()) {
            Log.d(TAG, "User already signed in - showing sign out option")
            // User is already signed in - offer to sign out
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    googleSignInManager.signOut()
                    updateGoogleSignInButton()
                    Toast.makeText(this@MainActivity, "Signed out successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Sign out error: ${e.message}")
                    Toast.makeText(this@MainActivity, "Error signing out", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d(TAG, "User not signed in - starting sign-in process")
            try {
                // Start sign-in process
                val signInIntent = googleSignInManager.getSignInIntent()
                Log.d(TAG, "Got sign-in intent, launching...")
                signInLauncher.launch(signInIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting sign-in: ${e.message}")
                Toast.makeText(this, "Error starting sign-in: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateGoogleSignInButton() {
        if (googleSignInManager.isSignedIn()) {
            val account = googleSignInManager.getCurrentAccount()
            btnGoogleSignIn.text = "Signed in as: ${account?.email ?: "Unknown"}"
            btnGoogleSignIn.isEnabled = true
        } else {
            btnGoogleSignIn.text = "Sign in to Google Calendar"
            btnGoogleSignIn.isEnabled = true
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                checkPermissions()
            }
            USAGE_STATS_PERMISSION_REQUEST_CODE -> {
                checkPermissions()
            }
            ACCESSIBILITY_PERMISSION_REQUEST_CODE -> {
                checkPermissions()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        checkPermissions()
        checkServiceStatus()
    }
    
    private fun handleOverlaySignInRequest() {
        val shouldTriggerSignIn = intent.getBooleanExtra("trigger_signin", false)
        if (shouldTriggerSignIn && !googleSignInManager.isSignedIn()) {
            // Remove the flag to prevent triggering again
            intent.removeExtra("trigger_signin")
            
            // Trigger sign-in automatically
            Toast.makeText(this, "Please sign in to add events to Google Calendar", Toast.LENGTH_SHORT).show()
            handler.postDelayed({
                handleGoogleSignInClick()
            }, 500) // Small delay to let the activity fully load
        }
    }
    
    private fun openWhatsApp() {
        try {
            val packageManager = packageManager
            val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (intent != null) {
                Toast.makeText(this, "Opening WhatsApp - look for the add event button!", Toast.LENGTH_LONG).show()
                startActivity(intent)
                finish() // Close the MainActivity
            } else {
                Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WhatsApp: ${e.message}")
            Toast.makeText(this, "Failed to open WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Test methods
    private fun runQuickTest() {
        android.util.Log.i("MainActivity", "Running quick event detection test...")
        
        val tester = EventDetectionTester(this)
        tester.runQuickTest()
    }
    
    private fun runFullTest() {
        android.util.Log.i("MainActivity", "Running full event detection test suite...")
        
        val tester = EventDetectionTester(this)
        tester.runFullTestSuite()
    }
    
    private fun runCalendarIntegrationTest() {
        android.util.Log.i("MainActivity", "Running calendar integration tests...")
        
        val tester = EventDetectionTester(this)
        tester.runCalendarIntegrationTest()
    }
    
    private fun showTestCases() {
        android.util.Log.i("MainActivity", "Showing all test cases...")
        
        val tester = EventDetectionTester(this)
        tester.showTestCaseList()
    }
} 