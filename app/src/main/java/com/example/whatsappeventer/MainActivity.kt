package com.example.whatsappeventer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var btnStartStop: Button
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnUsageStatsPermission: Button
    private lateinit var tvStatus: TextView
    
    private var isOverlayRunning = false
    
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
        private const val USAGE_STATS_PERMISSION_REQUEST_CODE = 5678
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
        checkPermissions()
    }
    
    private fun initViews() {
        btnStartStop = findViewById(R.id.btnStartStop)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnUsageStatsPermission = findViewById(R.id.btnUsageStatsPermission)
        tvStatus = findViewById(R.id.tvStatus)
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
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
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
        }
    }
    
    override fun onResume() {
        super.onResume()
        checkPermissions()
        checkServiceStatus()
    }
} 