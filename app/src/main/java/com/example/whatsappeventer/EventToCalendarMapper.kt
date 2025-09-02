package com.example.whatsappeventer

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class EventToCalendarMapper {
    
    companion object {
        private const val TAG = "EventToCalendarMapper"
        private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        
        fun convertEventsToCalendarJson(events: List<DetectedEvent>): String {
            android.util.Log.d(TAG, "Converting ${events.size} events to calendar JSON")
            
            if (events.isEmpty()) {
                android.util.Log.d(TAG, "No events detected - returning empty message")
                return "{\n  \"message\": \"No events detected\"\n}"
            }
            
            val eventsArray = mutableListOf<JSONObject>()
            
            events.forEachIndexed { index, event ->
                android.util.Log.d(TAG, "Converting event $index: ${event.title}")
                val calendarEvent = convertEventToCalendarJson(event)
                eventsArray.add(calendarEvent)
            }
            
            val result = JSONObject()
            result.put("events", eventsArray)
            result.put("total_events", events.size)
            result.put("generated_at", getCurrentTimestamp())
            
            val jsonString = result.toString(2) // Pretty print with 2-space indentation
            android.util.Log.d(TAG, "Generated JSON (${jsonString.length} chars): ${jsonString.take(200)}...")
            return jsonString
        }
        
        private fun convertEventToCalendarJson(event: DetectedEvent): JSONObject {
            val calendarEvent = JSONObject()
            
            // Required fields
            calendarEvent.put("summary", event.title.ifEmpty { "Event from WhatsApp" })
            calendarEvent.put("description", event.description)
            
            // Start time
            val startTime = JSONObject()
            if (event.dateTime != null) {
                startTime.put("dateTime", formatDateTime(event.dateTime))
                startTime.put("timeZone", getDeviceTimeZone())
            } else {
                // Default to today if no specific date/time detected
                val defaultTime = Calendar.getInstance()
                startTime.put("dateTime", formatDateTime(defaultTime))
                startTime.put("timeZone", getDeviceTimeZone())
            }
            calendarEvent.put("start", startTime)
            
            // End time (default to 1 hour after start)
            val endTime = JSONObject()
            val endDateTime = if (event.dateTime != null) {
                val endCal = event.dateTime.clone() as Calendar
                endCal.add(Calendar.HOUR, 1) // Default 1 hour duration
                endCal
            } else {
                val defaultEndTime = Calendar.getInstance()
                defaultEndTime.add(Calendar.HOUR, 1)
                defaultEndTime
            }
            endTime.put("dateTime", formatDateTime(endDateTime))
            endTime.put("timeZone", getDeviceTimeZone())
            calendarEvent.put("end", endTime)
            
            // Optional fields
            event.location?.let { location ->
                if (location.isNotEmpty()) {
                    calendarEvent.put("location", location)
                }
            }
            
            // Additional metadata
            calendarEvent.put("source", "WhatsAppEventer")
            calendarEvent.put("confidence", event.confidence)
            
            return calendarEvent
        }
        
        private fun formatDateTime(calendar: Calendar): String {
            return dateTimeFormat.format(calendar.time)
        }
        
        private fun getDeviceTimeZone(): String {
            return TimeZone.getDefault().id
        }
        
        private fun getCurrentTimestamp(): String {
            return formatDateTime(Calendar.getInstance())
        }
    }
}