package com.example.whatsappeventer

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CalendarEventDialog(
    private val context: Context,
    private val windowManager: WindowManager
) {
    
    companion object {
        private const val TAG = "CalendarEventDialog"
        private val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        private val TIME_FORMAT = SimpleDateFormat("hh:mm a", Locale.getDefault())
    }
    
    private var dialogView: View? = null
    private var signInManager: GoogleSignInManager? = null
    private var calendarService: GoogleCalendarService? = null
    private var currentDetectedEvent: DetectedEvent? = null
    
    // Date/Time variables
    private var startDateTime = Calendar.getInstance()
    private var endDateTime = Calendar.getInstance()
    
    // UI elements
    private lateinit var etEventTitle: EditText
    private lateinit var etEventDescription: EditText
    private lateinit var etEventLocation: EditText
    private lateinit var btnStartDate: Button
    private lateinit var btnStartTime: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnAddToCalendar: Button
    private lateinit var btnCancel: Button
    private lateinit var btnCloseDialog: ImageButton
    
    interface OnDialogDismissListener {
        fun onDialogDismiss()
        fun onEventCreated(success: Boolean, message: String)
    }
    
    private var onDialogDismissListener: OnDialogDismissListener? = null
    
    fun setOnDialogDismissListener(listener: OnDialogDismissListener) {
        onDialogDismissListener = listener
    }
    
    fun show(detectedEvent: DetectedEvent) {
        try {
            Log.d(TAG, "Showing calendar event dialog for: ${detectedEvent.title}")
            
            currentDetectedEvent = detectedEvent
            signInManager = GoogleSignInManager.getInstance(context)
            calendarService = GoogleCalendarService(signInManager!!)
            
            // Check if already signed in
            if (!signInManager!!.isSignedIn()) {
                Log.d(TAG, "User not signed in - showing sign-in flow first")
                showSignInRequiredDialog()
                return
            }
            
            showEventDialog()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing dialog: ${e.message}")
            e.printStackTrace()
            showError("Failed to open calendar dialog: ${e.message}")
        }
    }
    
    private fun showSignInRequiredDialog() {
        try {
            // Create a simple sign-in prompt dialog
            val signInView = LayoutInflater.from(context).inflate(R.layout.dialog_sign_in_required, null)
            
            val btnSignIn = signInView.findViewById<Button>(R.id.btnSignIn)
            val btnCancelSignIn = signInView.findViewById<Button>(R.id.btnCancelSignIn)
            
            btnSignIn.setOnClickListener {
                try {
                    // Launch the main app activity with sign-in trigger
                    val intent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("trigger_signin", true)
                    }
                    context.startActivity(intent)
                    dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open app: ${e.message}")
                    Toast.makeText(context, "Failed to open app. Please open WhatsApp Eventer manually.", Toast.LENGTH_LONG).show()
                    dismiss()
                }
            }
            
            btnCancelSignIn.setOnClickListener {
                dismiss()
            }
            
            showDialog(signInView)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing sign-in dialog: ${e.message}")
            showError("Failed to show sign-in dialog")
        }
    }
    
    private fun showEventDialog() {
        try {
            dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_to_calendar, null)
            initializeViews()
            setupEventData()
            setupClickListeners()
            showDialog(dialogView!!)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing event dialog: ${e.message}")
            e.printStackTrace()
            showError("Failed to create event dialog")
        }
    }
    
    private fun initializeViews() {
        dialogView?.let { view ->
            etEventTitle = view.findViewById(R.id.etEventTitle)
            etEventDescription = view.findViewById(R.id.etEventDescription)
            etEventLocation = view.findViewById(R.id.etEventLocation)
            btnStartDate = view.findViewById(R.id.btnStartDate)
            btnStartTime = view.findViewById(R.id.btnStartTime)
            btnEndDate = view.findViewById(R.id.btnEndDate)
            btnEndTime = view.findViewById(R.id.btnEndTime)
            btnAddToCalendar = view.findViewById(R.id.btnAddToCalendar)
            btnCancel = view.findViewById(R.id.btnCancel)
            btnCloseDialog = view.findViewById(R.id.btnCloseDialog)
        }
    }
    
    private fun setupEventData() {
        currentDetectedEvent?.let { event ->
            // Set title
            etEventTitle.setText(event.title)
            
            // Set description
            etEventDescription.setText(event.description)
            
            // Set location
            etEventLocation.setText(event.location ?: "")
            
            // Set dates and times
            event.dateTime?.let { eventDateTime ->
                startDateTime.time = eventDateTime.time
            } ?: run {
                // Default to 1 hour from now
                startDateTime.add(Calendar.HOUR_OF_DAY, 1)
            }
            
            // Default end time to 1 hour after start time
            endDateTime.time = startDateTime.time
            endDateTime.add(Calendar.HOUR_OF_DAY, 1)
            
            updateDateTimeButtons()
        }
    }
    
    private fun setupClickListeners() {
        btnStartDate.setOnClickListener { showDatePicker(true) }
        btnStartTime.setOnClickListener { showTimePicker(true) }
        btnEndDate.setOnClickListener { showDatePicker(false) }
        btnEndTime.setOnClickListener { showTimePicker(false) }
        
        btnAddToCalendar.setOnClickListener { createCalendarEvent() }
        btnCancel.setOnClickListener { dismiss() }
        btnCloseDialog.setOnClickListener { dismiss() }
    }
    
    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = if (isStartDate) startDateTime else endDateTime
        
        DatePickerDialog(
            context,
            { _: android.widget.DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateTimeButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = if (isStartTime) startDateTime else endDateTime
        
        TimePickerDialog(
            context,
            { _: android.widget.TimePicker, hourOfDay: Int, minute: Int ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                updateDateTimeButtons()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }
    
    private fun updateDateTimeButtons() {
        btnStartDate.text = DATE_FORMAT.format(startDateTime.time)
        btnStartTime.text = TIME_FORMAT.format(startDateTime.time)
        btnEndDate.text = DATE_FORMAT.format(endDateTime.time)
        btnEndTime.text = TIME_FORMAT.format(endDateTime.time)
    }
    
    private fun createCalendarEvent() {
        try {
            val title = etEventTitle.text.toString().trim()
            if (title.isEmpty()) {
                showError("Please enter an event title")
                return
            }
            
            // Show loading state
            btnAddToCalendar.isEnabled = false
            btnAddToCalendar.text = "Adding..."
            
            val description = etEventDescription.text.toString().trim()
            val location = etEventLocation.text.toString().trim().ifEmpty { null }
            
            Log.d(TAG, "Creating event: $title")
            
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = calendarService!!.createCalendarEvent(
                        title = title,
                        description = description,
                        startDateTime = startDateTime.time,
                        endDateTime = endDateTime.time,
                        location = location
                    )
                    
                    if (result.success) {
                        Log.d(TAG, "Event created successfully")
                        onDialogDismissListener?.onEventCreated(true, "✅ Event added to your Google Calendar")
                        dismiss()
                    } else {
                        Log.w(TAG, "Event creation failed: ${result.errorMessage}")
                        showError(result.errorMessage ?: "Failed to create event")
                        restoreButtonState()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating event: ${e.message}")
                    showError("Failed to create event: ${e.message}")
                    restoreButtonState()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing event data: ${e.message}")
            showError("Failed to prepare event data")
            restoreButtonState()
        }
    }
    
    private fun restoreButtonState() {
        btnAddToCalendar.isEnabled = true
        btnAddToCalendar.text = "Add to Calendar"
    }
    
    private fun showDialog(view: View) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        windowManager.addView(view, params)
        dialogView = view
    }
    
    private fun dismiss() {
        try {
            dialogView?.let { view ->
                windowManager.removeView(view)
                dialogView = null
            }
            onDialogDismissListener?.onDialogDismiss()
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing dialog: ${e.message}")
        }
    }
    
    private fun showError(message: String) {
        Log.e(TAG, "Showing error: $message")
        onDialogDismissListener?.onEventCreated(false, "❌ $message")
    }
}