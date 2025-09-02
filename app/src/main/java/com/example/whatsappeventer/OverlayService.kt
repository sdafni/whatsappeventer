package com.example.whatsappeventer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.*

class OverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: Handler
    
    private var isOverlayVisible = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // Screen capture and OCR properties
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var textRecognizer: com.google.mlkit.vision.text.TextRecognizer
    private lateinit var executor: ExecutorService
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var screenDensity = 0
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val CHECK_INTERVAL = 1000L // Check every second
        private const val SCREEN_CAPTURE_REQUEST_CODE = 2001
    }
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        
        // Initialize screen capture components
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenDensity = resources.displayMetrics.densityDpi
        
        // Initialize OCR components
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        executor = Executors.newSingleThreadExecutor()
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // Start monitoring WhatsApp usage
        startWhatsAppMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                "SCREEN_CAPTURE_PERMISSION_GRANTED" -> {
                    val resultCode = it.getIntExtra("resultCode", -1)
                    val data = it.getParcelableExtra<Intent>("data")
                    if (data != null) {
                        android.util.Log.d("OverlayService", "Screen capture permission granted, setting up MediaProjection")
                        setupMediaProjection(resultCode, data)
                    } else {
                        android.util.Log.e("OverlayService", "Permission granted but no data received")
                    }
                }
                "SCREEN_CAPTURE_PERMISSION_DENIED" -> {
                    android.util.Log.d("OverlayService", "Screen capture permission denied")
                }
                else -> {
                    android.util.Log.d("OverlayService", "Unknown intent action: ${it.action}")
                }
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service for WhatsApp overlay button"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhatsApp Eventer")
            .setContentText("Overlay service is running")
            .setSmallIcon(R.drawable.ic_whatsapp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startWhatsAppMonitoring() {
        // Start continuous monitoring loop
        handler.post(object : Runnable {
            override fun run() {
                try {
                    // Check if WhatsApp is in foreground
                    val isWhatsAppActive = isWhatsAppInForeground()
                    
                    if (isWhatsAppActive) {
                        if (!isOverlayVisible) {
                            android.util.Log.d("OverlayService", "WhatsApp detected - showing overlay")
                            showOverlay()
                        } else {
                            android.util.Log.d("OverlayService", "WhatsApp still active - overlay already visible")
                        }
                    } else {
                        if (isOverlayVisible) {
                            android.util.Log.d("OverlayService", "WhatsApp not active - hiding overlay")
                            hideOverlay()
                        } else {
                            android.util.Log.d("OverlayService", "WhatsApp not active - overlay already hidden")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("OverlayService", "Error in monitoring loop: ${e.message}")
                    e.printStackTrace()
                }
                
                // Continue monitoring - this creates an infinite loop
                // that only stops when the service is destroyed
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        })
        
        // Log that monitoring has started
        android.util.Log.d("OverlayService", "WhatsApp monitoring started - checking every ${CHECK_INTERVAL}ms")
    }
    
    private fun isWhatsAppInForeground(): Boolean {
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 30000 // Check last 30 seconds for much better reliability
            
            // Use queryUsageStats with multiple time windows for better detection
            val usageStats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                beginTime,
                endTime
            )
            
            var lastUsedApp = ""
            var lastTimeUsed = 0L
            
            for (usageStat in usageStats) {
                if (usageStat.lastTimeUsed > lastTimeUsed) {
                    lastTimeUsed = usageStat.lastTimeUsed
                    lastUsedApp = usageStat.packageName
                }
            }
            
            val isWhatsApp = lastUsedApp == WHATSAPP_PACKAGE
            
            // Enhanced persistence logic: if overlay is visible and WhatsApp was recently active, keep it much longer
            if (isWhatsApp && isOverlayVisible) {
                val timeSinceLastUse = endTime - lastTimeUsed
                if (timeSinceLastUse < 45000) { // Keep active for 45 seconds after last use
                    android.util.Log.d("OverlayService", "WhatsApp recently active - keeping overlay visible (${timeSinceLastUse}ms ago)")
                    return true
                }
            }
            
            // Additional logic: if WhatsApp was detected recently, give it more time
            if (isWhatsApp) {
                val timeSinceLastUse = endTime - lastTimeUsed
                if (timeSinceLastUse < 20000) { // If WhatsApp was used in last 20 seconds, consider it active
                    android.util.Log.d("OverlayService", "WhatsApp very recently active (${timeSinceLastUse}ms ago) - showing overlay")
                    return true
                }
            }
            
            android.util.Log.d("OverlayService", "Detection result - app: $lastUsedApp, isWhatsApp: $isWhatsApp, timeWindow: ${endTime - beginTime}ms, lastUse: ${endTime - lastTimeUsed}ms ago")
            return isWhatsApp
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error detecting WhatsApp: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    private fun showOverlay() {
        if (isOverlayVisible) return
        
        android.util.Log.d("OverlayService", "Creating overlay button...")
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }
        
        android.util.Log.d("OverlayService", "Setting up touch listener...")
        setupTouchListener(overlayView, params)
        
        try {
            android.util.Log.d("OverlayService", "Adding overlay to window manager...")
            windowManager.addView(overlayView, params)
            isOverlayVisible = true
            android.util.Log.d("OverlayService", "Overlay button created and visible!")
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error creating overlay: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun hideOverlay() {
        if (!isOverlayVisible) return
        
        try {
            windowManager.removeView(overlayView)
            isOverlayVisible = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupTouchListener(view: View, params: WindowManager.LayoutParams) {
        android.util.Log.d("OverlayService", "Setting up touch listener for overlay button")
        
        view.setOnTouchListener { v, event ->
            android.util.Log.d("OverlayService", "Touch event received: action=${event.action}, x=${event.x}, y=${event.y}")
            android.util.Log.d("OverlayService", "Any touch triggers click!")
            onOverlayButtonClick()
            true
        }
        
        android.util.Log.d("OverlayService", "Touch listener setup complete")
    }
    
    private fun onOverlayButtonClick() {
        android.util.Log.d("OverlayService", "Overlay button clicked - starting screen capture and OCR")
        if (mediaProjection == null) {
            android.util.Log.d("OverlayService", "MediaProjection not available, requesting permission")
            requestScreenCapturePermission()
        } else {
            android.util.Log.d("OverlayService", "MediaProjection available, capturing screen")
            captureScreenAndPerformOCR()
        }
    }
    
    private fun requestScreenCapturePermission() {
        android.util.Log.d("OverlayService", "Requesting screen capture permission")
        val intent = Intent(this, ScreenCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
    
    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            android.util.Log.d("OverlayService", "Setting up MediaProjection")
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            // Register callback before starting capture
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    android.util.Log.d("OverlayService", "MediaProjection stopped")
                    cleanupScreenCapture()
                    mediaProjection = null
                }
            }, null)
            
            android.util.Log.d("OverlayService", "MediaProjection setup complete, now capturing screen")
            captureScreenAndPerformOCR()
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error setting up MediaProjection: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun captureScreenAndPerformOCR() {
        try {
            android.util.Log.d("OverlayService", "Starting real screen capture and OCR process")
            
            if (mediaProjection != null) {
                captureScreenWithMediaProjection()
            } else {
                android.util.Log.d("OverlayService", "MediaProjection not available, using fallback")
                performFallbackOCR()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error in screen capture: ${e.message}")
            e.printStackTrace()
            performFallbackOCR()
        }
    }
    
    private fun captureScreenWithMediaProjection() {
        try {
            android.util.Log.d("OverlayService", "Capturing screen with MediaProjection")
            
            // Get the current display metrics
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            android.util.Log.d("OverlayService", "Screen dimensions: ${screenWidth}x${screenHeight}")
            
            // Setup ImageReader
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 1)
            
            // Setup VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            
            // Set up image available listener
            imageReader?.setOnImageAvailableListener({
                try {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        android.util.Log.d("OverlayService", "Image captured, converting to bitmap")
                        val bitmap = imageTobitmap(image)
                        image.close()
                        
                        if (bitmap != null) {
                            performRealOCR(bitmap)
                        } else {
                            android.util.Log.e("OverlayService", "Failed to convert image to bitmap")
                            performFallbackOCR()
                        }
                        
                        // Clean up after capture
                        cleanupScreenCapture()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("OverlayService", "Error processing captured image: ${e.message}")
                    e.printStackTrace()
                    performFallbackOCR()
                }
            }, null)
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error setting up screen capture: ${e.message}")
            e.printStackTrace()
            performFallbackOCR()
        }
    }
    
    private fun imageTobitmap(image: android.media.Image): Bitmap? {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // Crop to actual screen size if there's padding
            return if (rowPadding == 0) {
                bitmap
            } else {
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                bitmap.recycle()
                croppedBitmap
            }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error converting image to bitmap: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    private fun cleanupScreenCapture() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            virtualDisplay = null
            imageReader = null
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error cleaning up screen capture: ${e.message}")
            e.printStackTrace()
        }
    }
    
    
    private fun performRealOCR(bitmap: Bitmap) {
        try {
            android.util.Log.d("OverlayService", "Starting real OCR on bitmap")
            
            // Convert bitmap to InputImage for ML Kit
            val image = InputImage.fromBitmap(bitmap, 0)
            
            // Perform text recognition
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val ocrText = visionText.text
                    android.util.Log.d("OverlayService", "Real OCR completed! Text length: ${ocrText.length}")
                    android.util.Log.d("OverlayService", "OCR Result: $ocrText")
                    
                    if (ocrText.isNotEmpty()) {
                        processOCRResult(ocrText)
                    } else {
                        android.util.Log.d("OverlayService", "No text detected in image")
                        processOCRResult("No text detected")
                    }
                    
                    // Clean up bitmap
                    bitmap.recycle()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("OverlayService", "OCR failed: ${e.message}")
                    e.printStackTrace()
                    bitmap.recycle()
                    performFallbackOCR()
                }
                
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error in real OCR: ${e.message}")
            e.printStackTrace()
            bitmap.recycle()
            performFallbackOCR()
        }
    }
    
    private fun performFallbackOCR() {
        android.util.Log.d("OverlayService", "Using fallback OCR method")
        val fallbackText = "Fallback: Screen capture not available yet - requires MediaProjection permission"
        android.util.Log.d("OverlayService", "Fallback OCR Result: $fallbackText")
        processOCRResult(fallbackText)
    }
    
    private fun processOCRResult(text: String) {
        try {
            android.util.Log.d("OverlayService", "Processing OCR result: $text")
            
            // Add your custom logic here to process the extracted text
            // For example, search for specific keywords, extract phone numbers, etc.
            
            // Log the final processed result
            android.util.Log.d("OverlayService", "Final processed OCR result: $text")
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error processing OCR result: ${e.message}")
            e.printStackTrace()
        }
        
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayVisible) {
            hideOverlay()
        }
        handler.removeCallbacksAndMessages(null)
        
        // Clean up screen capture resources
        cleanupScreenCapture()
        mediaProjection?.stop()
        mediaProjection = null
        
        // Clean up OCR resources
        textRecognizer.close()
        executor.shutdown()
    }
} 