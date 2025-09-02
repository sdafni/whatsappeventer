package com.example.whatsappeventer

import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.*

data class CalendarValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val score: Float // 0.0 to 1.0
)

data class EventValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val hasValidDateTime: Boolean,
    val hasValidTimeZone: Boolean,
    val hasValidTitle: Boolean,
    val hasValidDuration: Boolean
)

class CalendarJsonValidator {
    
    companion object {
        private const val TAG = "CalendarJsonValidator"
        
        // Google Calendar API requirements
        private val VALID_TIMEZONE_PATTERN = Regex("^[A-Za-z_]+/[A-Za-z_/]+$")
        private val ISO8601_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}$")
        private val DATE_ONLY_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        
        private const val MIN_TITLE_LENGTH = 1
        private const val MAX_TITLE_LENGTH = 1024
        private const val MAX_DESCRIPTION_LENGTH = 8192
        private const val MIN_EVENT_DURATION_MINUTES = 1
        private const val MAX_EVENT_DURATION_HOURS = 24 * 30 // 30 days
    }
    
    /**
     * Validates a complete calendar JSON structure
     */
    fun validateCalendarJson(calendarJson: String): CalendarValidationResult {
        Log.d(TAG, "Validating calendar JSON structure...")
        
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            val jsonObject = JSONObject(calendarJson)
            
            // Check for required wrapper structure
            if (!jsonObject.has("events")) {
                if (jsonObject.has("message")) {
                    // Empty events case - valid
                    return CalendarValidationResult(
                        isValid = true,
                        errors = emptyList(),
                        warnings = listOf("No events to validate"),
                        score = 1.0f
                    )
                } else {
                    errors.add("Missing 'events' array in JSON")
                }
            }
            
            // Validate events array
            var totalScore = 0.0f
            var eventCount = 0
            
            if (jsonObject.has("events")) {
                val eventsArray = jsonObject.getJSONArray("events")
                eventCount = eventsArray.length()
                
                if (eventCount == 0) {
                    warnings.add("Events array is empty")
                    return CalendarValidationResult(
                        isValid = true,
                        errors = emptyList(),
                        warnings = warnings,
                        score = 1.0f
                    )
                }
                
                // Validate each event
                for (i in 0 until eventCount) {
                    val event = eventsArray.getJSONObject(i)
                    val eventResult = validateSingleEvent(event, i)
                    
                    if (!eventResult.isValid) {
                        errors.addAll(eventResult.errors.map { "Event $i: $it" })
                    }
                    warnings.addAll(eventResult.warnings.map { "Event $i: $it" })
                    
                    // Calculate event score
                    var eventScore = 0.0f
                    if (eventResult.hasValidTitle) eventScore += 0.3f
                    if (eventResult.hasValidDateTime) eventScore += 0.3f
                    if (eventResult.hasValidTimeZone) eventScore += 0.2f
                    if (eventResult.hasValidDuration) eventScore += 0.2f
                    
                    totalScore += eventScore
                }
            }
            
            // Validate metadata
            if (!jsonObject.has("total_events")) {
                warnings.add("Missing 'total_events' field")
            } else {
                val declaredCount = jsonObject.getInt("total_events")
                if (declaredCount != eventCount) {
                    errors.add("total_events ($declaredCount) doesn't match actual events count ($eventCount)")
                }
            }
            
            if (!jsonObject.has("generated_at")) {
                warnings.add("Missing 'generated_at' timestamp")
            }
            
            val finalScore = if (eventCount > 0) totalScore / eventCount else 1.0f
            val isValid = errors.isEmpty()
            
            Log.d(TAG, "Calendar JSON validation complete: valid=$isValid, score=$finalScore")
            
            return CalendarValidationResult(
                isValid = isValid,
                errors = errors,
                warnings = warnings,
                score = finalScore
            )
            
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error: ${e.message}")
            return CalendarValidationResult(
                isValid = false,
                errors = listOf("Invalid JSON structure: ${e.message}"),
                warnings = warnings,
                score = 0.0f
            )
        }
    }
    
    /**
     * Validates a single calendar event object
     */
    fun validateSingleEvent(eventJson: JSONObject, index: Int = 0): EventValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate required fields
        var hasValidTitle = false
        var hasValidDateTime = false
        var hasValidTimeZone = false
        var hasValidDuration = false
        
        // 1. Validate summary (title)
        if (!eventJson.has("summary")) {
            errors.add("Missing required 'summary' field")
        } else {
            val summary = eventJson.getString("summary")
            when {
                summary.isBlank() -> errors.add("Summary cannot be blank")
                summary.length < MIN_TITLE_LENGTH -> errors.add("Summary too short (min $MIN_TITLE_LENGTH chars)")
                summary.length > MAX_TITLE_LENGTH -> errors.add("Summary too long (max $MAX_TITLE_LENGTH chars)")
                else -> hasValidTitle = true
            }
        }
        
        // 2. Validate start time
        if (!eventJson.has("start")) {
            errors.add("Missing required 'start' field")
        } else {
            val startResult = validateDateTimeField(eventJson.getJSONObject("start"), "start")
            errors.addAll(startResult.first)
            warnings.addAll(startResult.second)
            if (startResult.first.isEmpty()) {
                hasValidDateTime = true
                hasValidTimeZone = true
            }
        }
        
        // 3. Validate end time
        if (!eventJson.has("end")) {
            errors.add("Missing required 'end' field")
        } else {
            val endResult = validateDateTimeField(eventJson.getJSONObject("end"), "end")
            errors.addAll(endResult.first)
            warnings.addAll(endResult.second)
        }
        
        // 4. Validate duration (start vs end)
        if (eventJson.has("start") && eventJson.has("end")) {
            val durationResult = validateEventDuration(
                eventJson.getJSONObject("start"),
                eventJson.getJSONObject("end")
            )
            errors.addAll(durationResult.first)
            warnings.addAll(durationResult.second)
            hasValidDuration = durationResult.first.isEmpty()
        }
        
        // 5. Validate optional fields
        if (eventJson.has("description")) {
            val description = eventJson.getString("description")
            if (description.length > MAX_DESCRIPTION_LENGTH) {
                warnings.add("Description very long (${description.length} chars, max recommended $MAX_DESCRIPTION_LENGTH)")
            }
        }
        
        if (eventJson.has("location")) {
            val location = eventJson.getString("location")
            if (location.isBlank()) {
                warnings.add("Location field is empty")
            }
        }
        
        // 6. Validate confidence if present
        if (eventJson.has("confidence")) {
            try {
                val confidence = eventJson.getDouble("confidence")
                if (confidence < 0.0 || confidence > 1.0) {
                    warnings.add("Confidence should be between 0.0 and 1.0, got $confidence")
                }
            } catch (e: Exception) {
                warnings.add("Invalid confidence value: ${e.message}")
            }
        }
        
        val isValid = errors.isEmpty()
        
        return EventValidationResult(
            isValid = isValid,
            errors = errors,
            warnings = warnings,
            hasValidDateTime = hasValidDateTime,
            hasValidTimeZone = hasValidTimeZone,
            hasValidTitle = hasValidTitle,
            hasValidDuration = hasValidDuration
        )
    }
    
    /**
     * Validates a datetime field (start or end)
     */
    private fun validateDateTimeField(
        dateTimeObj: JSONObject, 
        fieldName: String
    ): Pair<List<String>, List<String>> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Must have either dateTime or date
        val hasDateTime = dateTimeObj.has("dateTime")
        val hasDate = dateTimeObj.has("date")
        
        if (!hasDateTime && !hasDate) {
            errors.add("$fieldName must have either 'dateTime' or 'date' field")
            return Pair(errors, warnings)
        }
        
        if (hasDateTime && hasDate) {
            warnings.add("$fieldName has both 'dateTime' and 'date', 'dateTime' takes precedence")
        }
        
        // Validate datetime format
        if (hasDateTime) {
            val dateTimeStr = dateTimeObj.getString("dateTime")
            Log.d(TAG, "Validating datetime: '$dateTimeStr' for field $fieldName")
            
            if (!ISO8601_PATTERN.matches(dateTimeStr)) {
                Log.d(TAG, "DateTime '$dateTimeStr' failed regex pattern match")
                errors.add("$fieldName.dateTime format invalid. Expected ISO 8601 format (yyyy-MM-ddTHH:mm:ss±HH:mm), got: '$dateTimeStr'")
            } else {
                // Try to parse the datetime
                try {
                    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                    format.parse(dateTimeStr)
                    Log.d(TAG, "DateTime '$dateTimeStr' parsed successfully")
                } catch (e: Exception) {
                    Log.d(TAG, "DateTime '$dateTimeStr' parsing failed: ${e.message}")
                    errors.add("$fieldName.dateTime cannot be parsed: ${e.message}")
                }
            }
        }
        
        // Validate date format (all-day events)
        if (hasDate && !hasDateTime) {
            val dateStr = dateTimeObj.getString("date")
            if (!DATE_ONLY_PATTERN.matches(dateStr)) {
                errors.add("$fieldName.date format invalid. Expected yyyy-MM-dd format")
            }
        }
        
        // Validate timezone
        if (hasDateTime && dateTimeObj.has("timeZone")) {
            val timeZone = dateTimeObj.getString("timeZone")
            if (timeZone.isBlank()) {
                warnings.add("$fieldName.timeZone is empty")
            } else if (!VALID_TIMEZONE_PATTERN.matches(timeZone)) {
                warnings.add("$fieldName.timeZone format unusual: $timeZone")
            } else {
                // Verify timezone is recognized
                try {
                    TimeZone.getTimeZone(timeZone)
                } catch (e: Exception) {
                    warnings.add("$fieldName.timeZone not recognized: $timeZone")
                }
            }
        }
        
        return Pair(errors, warnings)
    }
    
    /**
     * Validates event duration (end must be after start)
     */
    private fun validateEventDuration(
        startObj: JSONObject, 
        endObj: JSONObject
    ): Pair<List<String>, List<String>> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            
            // Parse start time
            val startTimeStr = if (startObj.has("dateTime")) {
                startObj.getString("dateTime")
            } else {
                return Pair(errors, warnings) // Skip duration validation for date-only events
            }
            
            val endTimeStr = if (endObj.has("dateTime")) {
                endObj.getString("dateTime")
            } else {
                return Pair(errors, warnings)
            }
            
            val startTime = format.parse(startTimeStr)
            val endTime = format.parse(endTimeStr)
            
            if (startTime != null && endTime != null) {
                val durationMs = endTime.time - startTime.time
                val durationMinutes = durationMs / (1000 * 60)
                
                when {
                    durationMs <= 0 -> errors.add("End time must be after start time")
                    durationMinutes < MIN_EVENT_DURATION_MINUTES -> 
                        warnings.add("Very short event duration: $durationMinutes minutes")
                    durationMinutes > MAX_EVENT_DURATION_HOURS * 60 ->
                        warnings.add("Very long event duration: ${durationMinutes/60} hours")
                }
            }
            
        } catch (e: Exception) {
            warnings.add("Could not validate event duration: ${e.message}")
        }
        
        return Pair(errors, warnings)
    }
}