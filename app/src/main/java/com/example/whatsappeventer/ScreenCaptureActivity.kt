package com.example.whatsappeventer

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

class ScreenCaptureActivity : Activity() {
    
    companion object {
        const val SCREEN_CAPTURE_REQUEST_CODE = 2001
        const val ACTION_REQUEST_SCREEN_CAPTURE = "com.example.whatsappeventer.REQUEST_SCREEN_CAPTURE"
    }
    
    private lateinit var mediaProjectionManager: MediaProjectionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("ScreenCaptureActivity", "Activity created")
        
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Request screen capture permission
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST_CODE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d("ScreenCaptureActivity", "Activity result: requestCode=$requestCode, resultCode=$resultCode")
        
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d("ScreenCaptureActivity", "Screen capture permission granted")
                
                // Send the result back to the service
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    action = "SCREEN_CAPTURE_PERMISSION_GRANTED"
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }
                startService(serviceIntent)
                
            } else {
                Log.d("ScreenCaptureActivity", "Screen capture permission denied")
                
                // Notify service that permission was denied
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    action = "SCREEN_CAPTURE_PERMISSION_DENIED"
                }
                startService(serviceIntent)
            }
        }
        
        // Close the activity
        finish()
    }
}