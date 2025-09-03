package com.example.whatsappeventer

import android.util.Log
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class GoogleCalendarService(
    private val signInManager: GoogleSignInManager
) {
    
    companion object {
        private const val TAG = "GoogleCalendarService"
    }
    
    data class CalendarEventResult(
        val success: Boolean,
        val eventId: String? = null,
        val errorMessage: String? = null
    )
    
    suspend fun createCalendarEvent(
        title: String,
        description: String,
        startDateTime: Date,
        endDateTime: Date,
        location: String? = null,
        timeZone: String = TimeZone.getDefault().id
    ): CalendarEventResult = withContext(Dispatchers.IO) {
        
        try {
            val calendarService = signInManager.getCalendarService()
                ?: return@withContext CalendarEventResult(
                    success = false,
                    errorMessage = "Calendar service not available. Please sign in first."
                )
            
            Log.d(TAG, "Creating calendar event: $title")
            Log.d(TAG, "Start: $startDateTime, End: $endDateTime")
            Log.d(TAG, "Location: $location, TimeZone: $timeZone")
            
            // Create the event
            val event = Event().apply {
                summary = title
                this.description = description
                this.location = location
                
                // Set start time
                start = EventDateTime().apply {
                    dateTime = DateTime(startDateTime)
                    this.timeZone = timeZone
                }
                
                // Set end time
                end = EventDateTime().apply {
                    dateTime = DateTime(endDateTime)
                    this.timeZone = timeZone
                }
                
                // Optional: Set reminders
                reminders = Event.Reminders().apply {
                    useDefault = true
                }
            }
            
            // Insert the event
            val insertedEvent = calendarService.events()
                .insert("primary", event)
                .execute()
            
            val eventId = insertedEvent.id
            Log.d(TAG, "✅ Event created successfully with ID: $eventId")
            
            return@withContext CalendarEventResult(
                success = true,
                eventId = eventId
            )
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ Network error creating calendar event: ${e.message}")
            e.printStackTrace()
            return@withContext CalendarEventResult(
                success = false,
                errorMessage = "Network error. Please check your internet connection and try again."
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permission error creating calendar event: ${e.message}")
            return@withContext CalendarEventResult(
                success = false,
                errorMessage = "Calendar permission denied. Please sign in again."
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error creating calendar event: ${e.message}")
            e.printStackTrace()
            return@withContext CalendarEventResult(
                success = false,
                errorMessage = "Failed to create event: ${e.message}"
            )
        }
    }
    
    suspend fun createCalendarEventFromDetectedEvent(
        detectedEvent: DetectedEvent
    ): CalendarEventResult = withContext(Dispatchers.IO) {
        
        try {
            // Parse the detected event data
            val title = detectedEvent.title ?: "Event from WhatsApp"
            val description = buildString {
                detectedEvent.description?.let { desc ->
                    appendLine("Description: $desc")
                }
                appendLine("Source: WhatsApp conversation")
                appendLine("Detected on: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
            }
            
            // Use detected times or create default times
            val startDateTime = detectedEvent.dateTime?.time ?: Date(System.currentTimeMillis() + (60 * 60 * 1000)) // 1 hour from now
            val endDateTime = Date(startDateTime.time + (60 * 60 * 1000)) // 1 hour duration
            
            val location = detectedEvent.location
            
            Log.d(TAG, "Converting detected event to calendar event:")
            Log.d(TAG, "Title: $title")
            Log.d(TAG, "Start: $startDateTime")
            Log.d(TAG, "End: $endDateTime")
            Log.d(TAG, "Location: $location")
            
            return@withContext createCalendarEvent(
                title = title,
                description = description,
                startDateTime = startDateTime,
                endDateTime = endDateTime,
                location = location
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting detected event: ${e.message}")
            e.printStackTrace()
            return@withContext CalendarEventResult(
                success = false,
                errorMessage = "Failed to process event data: ${e.message}"
            )
        }
    }
    
    fun isServiceAvailable(): Boolean {
        return signInManager.isSignedIn() && signInManager.hasCalendarPermissions()
    }
}