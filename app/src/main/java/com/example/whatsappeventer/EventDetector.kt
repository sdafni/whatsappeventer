package com.example.whatsappeventer

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

data class DetectedEvent(
    val title: String,
    val description: String,
    val dateTime: Calendar?,
    val location: String? = null,
    val confidence: Float = 0.0f
)

class EventDetector {

    companion object {
        private const val TAG = "EventDetector"
    }
    
    private val languageDetector = LanguageDetector()

    fun detectEvents(conversationText: String): List<DetectedEvent> {
        Log.d(TAG, "Analyzing conversation text for events: ${conversationText.length} characters")
        
        val events = mutableListOf<DetectedEvent>()
        val lines = conversationText.split("\n")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            
            Log.d(TAG, "Analyzing line: $trimmedLine")
            
            // Skip system messages and obvious non-event content
            if (isSystemMessage(trimmedLine)) continue
            
            val detectedEvents = analyzeLineForEvents(trimmedLine)
            events.addAll(detectedEvents)
        }
        
        Log.d(TAG, "Found ${events.size} potential events")
        return events
    }

    private fun isSystemMessage(line: String): Boolean {
        val systemPatterns = listOf(
            "joined using this group's invite link",
            "left",
            "was added",
            "was removed",
            "created group",
            "changed the group description",
            "Messages and calls are end-to-end encrypted"
        )
        
        return systemPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) }
    }

    private fun analyzeLineForEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Detect language and route to appropriate detection methods
        val language = languageDetector.detectLanguage(line)
        Log.d(TAG, "Detected language: $language for line: $line")
        
        when (language) {
            DetectedLanguage.HEBREW -> {
                events.addAll(detectHebrewEvents(line))
            }
            DetectedLanguage.ENGLISH -> {
                events.addAll(detectEnglishEvents(line))
            }
            DetectedLanguage.MIXED -> {
                // Try both Hebrew and English patterns
                events.addAll(detectHebrewEvents(line))
                events.addAll(detectEnglishEvents(line))
            }
            DetectedLanguage.UNKNOWN -> {
                // Default to English patterns for unknown language
                events.addAll(detectEnglishEvents(line))
            }
        }
        
        // Remove duplicates based on title and description
        return deduplicateEvents(events)
    }
    
    private fun detectEnglishEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Pattern 1: Explicit time mentions with activities (highest priority)
        val timeBasedEvents = detectTimeBasedEvents(line)
        events.addAll(timeBasedEvents)
        
        // Pattern 2: Day-based events (only if no time-based events found)
        if (timeBasedEvents.isEmpty()) {
            val dayBasedEvents = detectDayBasedEvents(line)
            events.addAll(dayBasedEvents)
            
            // Pattern 3: Activity-based events (only if no time or day-based events found)
            if (dayBasedEvents.isEmpty()) {
                events.addAll(detectActivityBasedEvents(line))
            }
        }
        
        return events
    }
    
    private fun detectHebrewEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Pattern 1: Hebrew time-based events (highest priority)
        val hebrewTimeEvents = detectHebrewTimeBasedEvents(line)
        events.addAll(hebrewTimeEvents)
        
        // Pattern 2: Hebrew day-based events (only if no time-based events found)
        if (hebrewTimeEvents.isEmpty()) {
            val hebrewDayEvents = detectHebrewDayBasedEvents(line)
            events.addAll(hebrewDayEvents)
            
            // Pattern 3: Hebrew activity-based events (only if no time or day-based events found)
            if (hebrewDayEvents.isEmpty()) {
                events.addAll(detectHebrewActivityBasedEvents(line))
            }
        }
        
        return events
    }
    
    private fun deduplicateEvents(events: List<DetectedEvent>): List<DetectedEvent> {
        val seen = mutableSetOf<Pair<String, String>>()
        return events.filter { event ->
            val key = Pair(event.title.lowercase(), event.description.lowercase())
            if (seen.contains(key)) {
                Log.d(TAG, "Removing duplicate event: ${event.title}")
                false
            } else {
                seen.add(key)
                true
            }
        }
    }

    private fun detectTimeBasedEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Pattern: "at 3pm", "at 15:00", "3:30 PM", etc.
        // Only match when preceded by "at" or followed by activity context
        val timePatterns = listOf(
            Pattern.compile("\\bat\\s+(\\d{1,2}):?(\\d{0,2})\\s*([ap]m|AM|PM)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{1,2}):?(\\d{0,2})\\s*([ap]m|AM|PM)(?=\\s*(?:for|to|we|let|i|meeting|dinner|lunch|call|appointment|party|movie))", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in timePatterns) {
            val matcher = pattern.matcher(line)
            while (matcher.find()) {
                val timeStr = matcher.group()
                val dateTime = parseTime(timeStr)
                
                if (dateTime != null) {
                    // Look for activity keywords around the time
                    val activity = extractActivityNearTime(line, matcher.start(), matcher.end())
                    
                    // Only create event if we have activity context or explicit "at" usage
                    if (activity.isNotEmpty() || timeStr.lowercase().startsWith("at")) {
                        val title = if (activity.isNotEmpty()) "$activity" else "Event"
                        
                        events.add(DetectedEvent(
                            title = title,
                            description = line.trim(),
                            dateTime = dateTime,
                            confidence = 0.8f
                        ))
                        
                        Log.d(TAG, "Found time-based event: $title at ${formatDateTime(dateTime)}")
                    }
                }
            }
        }
        
        return events
    }

    private fun detectDayBasedEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Pattern: "tomorrow", "next week", "Friday", "Monday morning", etc.
        val dayPatterns = listOf(
            "\\b(tomorrow)\\b" to 1,
            "\\b(today)\\b" to 0,
            "\\b(next\\s+week)\\b" to 7,
            "\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b" to -1,
            "\\b(this\\s+week)\\b" to 3
        )
        
        for ((pattern, daysOffset) in dayPatterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(line)
            
            while (matcher.find()) {
                val dayStr = matcher.group()
                val dateTime = parseDay(dayStr, daysOffset)
                
                if (dateTime != null) {
                    val activity = extractActivityNearDay(line, matcher.start(), matcher.end())
                    val title = if (activity.isNotEmpty()) "$activity" else "Event on $dayStr"
                    
                    events.add(DetectedEvent(
                        title = title,
                        description = line.trim(),
                        dateTime = dateTime,
                        confidence = 0.7f
                    ))
                    
                    Log.d(TAG, "Found day-based event: $title on ${formatDateTime(dateTime)}")
                }
            }
        }
        
        return events
    }

    private fun detectActivityBasedEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Pattern: Common activity keywords
        val activityPatterns = listOf(
            "\\b(meeting|meet)\\b" to "Meeting",
            "\\b(dinner|lunch|breakfast)\\b" to "Meal",
            "\\b(appointment|appt)\\b" to "Appointment", 
            "\\b(call|phone\\s+call)\\b" to "Call",
            "\\b(party|celebration)\\b" to "Party",
            "\\b(movie|film)\\b" to "Movie",
            "\\b(conference|presentation)\\b" to "Conference",
            "\\b(interview)\\b" to "Interview",
            "\\b(vacation|holiday|trip)\\b" to "Travel"
        )
        
        for ((pattern, activityType) in activityPatterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(line)
            
            while (matcher.find()) {
                // Only create event if we can find some time reference in the line
                if (hasTimeReference(line)) {
                    events.add(DetectedEvent(
                        title = activityType,
                        description = line.trim(),
                        dateTime = null, // Will be inferred later
                        confidence = 0.6f
                    ))
                    
                    Log.d(TAG, "Found activity-based event: $activityType")
                }
            }
        }
        
        return events
    }

    private fun hasTimeReference(line: String): Boolean {
        val timeReferences = listOf(
            "tomorrow", "today", "tonight", "morning", "afternoon", "evening",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "next week", "this week", "later", "soon"
        )
        
        return timeReferences.any { ref -> line.contains(ref, ignoreCase = true) }
    }

    private fun parseTime(timeStr: String): Calendar? {
        try {
            val cleanTime = timeStr.replace("at\\s+".toRegex(), "").trim()
            val calendar = Calendar.getInstance()
            
            // Try different time formats
            val timeFormats = listOf(
                "h:mm a", "H:mm", "h a", "ha", "h:mma", "H:mm"
            )
            
            for (format in timeFormats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.getDefault())
                    val time = sdf.parse(cleanTime)
                    if (time != null) {
                        val timeCalendar = Calendar.getInstance()
                        timeCalendar.time = time
                        
                        calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
                        calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
                        calendar.set(Calendar.SECOND, 0)
                        
                        return calendar
                    }
                } catch (e: Exception) {
                    // Try next format
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse time: $timeStr")
        }
        
        return null
    }

    private fun parseDay(dayStr: String, daysOffset: Int): Calendar? {
        var calendar = Calendar.getInstance()
        
        when {
            daysOffset >= 0 -> {
                calendar.add(Calendar.DAY_OF_YEAR, daysOffset)
            }
            dayStr.contains("monday", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.MONDAY)
            }
            dayStr.contains("tuesday", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.TUESDAY)
            }
            dayStr.contains("wednesday", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.WEDNESDAY)
            }
            dayStr.contains("thursday", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.THURSDAY)
            }
            dayStr.contains("friday", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.FRIDAY)
            }
            dayStr.contains("saturday", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.SATURDAY)
            }
            dayStr.contains("sunday", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.SUNDAY)
            }
        }
        
        return calendar
    }

    private fun getNextDayOfWeek(targetDay: Int): Calendar {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val daysToAdd = if (targetDay > currentDay) {
            targetDay - currentDay
        } else {
            7 + targetDay - currentDay
        }
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
        return calendar
    }

    private fun extractActivityNearTime(line: String, timeStart: Int, timeEnd: Int): String {
        // Look for activity keywords before and after the time mention
        val beforeTime = line.substring(0, timeStart).lowercase()
        val afterTime = line.substring(timeEnd).lowercase()
        
        val activities = listOf("meeting", "dinner", "lunch", "call", "appointment", "party", "movie")
        
        for (activity in activities) {
            if (beforeTime.contains(activity) || afterTime.contains(activity)) {
                return activity.replaceFirstChar { it.uppercase() }
            }
        }
        
        return ""
    }

    private fun extractActivityNearDay(line: String, dayStart: Int, dayEnd: Int): String {
        // Similar logic for day-based events
        return extractActivityNearTime(line, dayStart, dayEnd)
    }

    private fun formatDateTime(calendar: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(calendar.time)
    }
    
    // ===== HEBREW EVENT DETECTION METHODS =====
    
    private fun detectHebrewTimeBasedEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Hebrew time patterns: "בשעה 3", "ב-15:00", "בערב", "בבוקר"
        val hebrewTimePatterns = listOf(
            Pattern.compile("בשעה\\s+(\\d{1,2}):?(\\d{0,2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ב-(\\d{1,2}):(\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{1,2}):?(\\d{0,2})\\s*(?=בערב|בבוקר|בצהריים)", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in hebrewTimePatterns) {
            val matcher = pattern.matcher(line)
            while (matcher.find()) {
                val timeStr = matcher.group()
                val dateTime = parseHebrewTime(timeStr, line)
                
                if (dateTime != null) {
                    val activity = extractHebrewActivityNearTime(line, matcher.start(), matcher.end())
                    val title = if (activity.isNotEmpty()) activity else "אירוע" // "Event" in Hebrew
                    
                    events.add(DetectedEvent(
                        title = title,
                        description = line.trim(),
                        dateTime = dateTime,
                        confidence = 0.8f
                    ))
                    
                    Log.d(TAG, "Found Hebrew time-based event: $title at ${formatDateTime(dateTime)}")
                }
            }
        }
        
        return events
    }
    
    private fun detectHebrewDayBasedEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Hebrew day patterns: "מחר", "היום", Hebrew day names
        val hebrewDayPatterns = listOf(
            "\\b(מחר)\\b" to 1,           // tomorrow
            "\\b(היום)\\b" to 0,          // today
            "\\b(יום ראשון)\\b" to -1,    // Sunday
            "\\b(יום שני)\\b" to -1,      // Monday
            "\\b(יום שלישי)\\b" to -1,    // Tuesday
            "\\b(יום רביעי)\\b" to -1,    // Wednesday
            "\\b(יום חמישי)\\b" to -1,    // Thursday
            "\\b(יום שישי)\\b" to -1,     // Friday
            "\\b(שבת)\\b" to -1           // Saturday
        )
        
        for ((pattern, daysOffset) in hebrewDayPatterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(line)
            
            while (matcher.find()) {
                val dayStr = matcher.group()
                val dateTime = parseHebrewDay(dayStr, daysOffset)
                
                if (dateTime != null) {
                    val activity = extractHebrewActivityNearDay(line, matcher.start(), matcher.end())
                    val title = if (activity.isNotEmpty()) activity else "אירוע ב$dayStr" // "Event on [day]"
                    
                    events.add(DetectedEvent(
                        title = title,
                        description = line.trim(),
                        dateTime = dateTime,
                        confidence = 0.7f
                    ))
                    
                    Log.d(TAG, "Found Hebrew day-based event: $title on ${formatDateTime(dateTime)}")
                }
            }
        }
        
        return events
    }
    
    private fun detectHebrewActivityBasedEvents(line: String): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        
        // Hebrew activity patterns
        val hebrewActivityPatterns = listOf(
            "\\b(פגישה|ישיבה)\\b" to "פגישה",        // meeting
            "\\b(ארוחת ערב)\\b" to "ארוחת ערב",      // dinner
            "\\b(ארוחת צהריים)\\b" to "ארוחת צהריים", // lunch
            "\\b(ארוחת בוקר)\\b" to "ארוחת בוקר",    // breakfast
            "\\b(קפה)\\b" to "קפה",                // coffee
            "\\b(רופא|רופא שיניים)\\b" to "תור אצל רופא", // doctor/dentist appointment
            "\\b(שיחה|טלפון)\\b" to "שיחת טלפון",    // phone call
            "\\b(מסיבה|חגיגה)\\b" to "מסיבה",       // party
            "\\b(סרט|קולנוע)\\b" to "סרט"           // movie
        )
        
        for ((pattern, activityType) in hebrewActivityPatterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(line)
            
            while (matcher.find()) {
                // Only create event if we can find some Hebrew time reference in the line
                if (hasHebrewTimeReference(line)) {
                    events.add(DetectedEvent(
                        title = activityType,
                        description = line.trim(),
                        dateTime = null, // Will be inferred later
                        confidence = 0.6f
                    ))
                    
                    Log.d(TAG, "Found Hebrew activity-based event: $activityType")
                }
            }
        }
        
        return events
    }
    
    private fun hasHebrewTimeReference(line: String): Boolean {
        val hebrewTimeReferences = listOf(
            "מחר", "היום", "בערב", "בבוקר", "בצהריים", "בלילה",
            "יום ראשון", "יום שני", "יום שלישי", "יום רביעי", 
            "יום חמישי", "יום שישי", "שבת", "מאוחר יותר", "בקרוב"
        )
        
        return hebrewTimeReferences.any { ref -> line.contains(ref, ignoreCase = true) }
    }
    
    private fun parseHebrewTime(timeStr: String, fullLine: String): Calendar? {
        try {
            val calendar = Calendar.getInstance()
            
            // Extract numbers from Hebrew time string
            val numberRegex = Regex("\\d{1,2}:?\\d{0,2}")
            val timeMatch = numberRegex.find(timeStr)
            
            if (timeMatch != null) {
                val cleanTime = timeMatch.value
                val timeFormats = listOf(
                    "H:mm", "H", "HH:mm", "HH"
                )
                
                for (format in timeFormats) {
                    try {
                        val sdf = SimpleDateFormat(format, Locale.getDefault())
                        val time = sdf.parse(cleanTime)
                        if (time != null) {
                            val timeCalendar = Calendar.getInstance()
                            timeCalendar.time = time
                            
                            calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
                            calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
                            calendar.set(Calendar.SECOND, 0)
                            
                            // Adjust for Hebrew time context
                            if (fullLine.contains("בערב")) {
                                if (calendar.get(Calendar.HOUR_OF_DAY) < 12) {
                                    calendar.add(Calendar.HOUR_OF_DAY, 12)
                                }
                            } else if (fullLine.contains("בבוקר")) {
                                if (calendar.get(Calendar.HOUR_OF_DAY) > 12) {
                                    calendar.add(Calendar.HOUR_OF_DAY, -12)
                                }
                            }
                            
                            return calendar
                        }
                    } catch (e: Exception) {
                        // Try next format
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Hebrew time: $timeStr")
        }
        
        return null
    }
    
    private fun parseHebrewDay(dayStr: String, daysOffset: Int): Calendar? {
        var calendar = Calendar.getInstance()
        
        when {
            daysOffset >= 0 -> {
                calendar.add(Calendar.DAY_OF_YEAR, daysOffset)
            }
            dayStr.contains("יום ראשון", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.SUNDAY)
            }
            dayStr.contains("יום שני", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.MONDAY)
            }
            dayStr.contains("יום שלישי", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.TUESDAY)
            }
            dayStr.contains("יום רביעי", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.WEDNESDAY)
            }
            dayStr.contains("יום חמישי", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.THURSDAY)
            }
            dayStr.contains("יום שישי", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.FRIDAY)
            }
            dayStr.contains("שבת", ignoreCase = true) -> {
                calendar = getNextDayOfWeek(Calendar.SATURDAY)
            }
        }
        
        return calendar
    }
    
    private fun extractHebrewActivityNearTime(line: String, timeStart: Int, timeEnd: Int): String {
        // Look for Hebrew activity keywords before and after the time mention
        val beforeTime = line.substring(0, timeStart).lowercase()
        val afterTime = line.substring(timeEnd).lowercase()
        
        val hebrewActivities = listOf("פגישה", "ארוחת ערב", "ארוחת צהריים", "קפה", "רופא", "שיחה")
        
        for (activity in hebrewActivities) {
            if (beforeTime.contains(activity) || afterTime.contains(activity)) {
                return activity
            }
        }
        
        return ""
    }
    
    private fun extractHebrewActivityNearDay(line: String, dayStart: Int, dayEnd: Int): String {
        // Similar logic for Hebrew day-based events
        return extractHebrewActivityNearTime(line, dayStart, dayEnd)
    }
}