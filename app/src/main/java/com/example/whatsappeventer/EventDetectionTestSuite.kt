package com.example.whatsappeventer

import java.util.*

data class EventTestCase(
    val name: String,
    val conversation: String,
    val expectedEvents: List<ExpectedEvent>,
    val difficulty: TestDifficulty,
    val description: String
)

data class ExpectedEvent(
    val title: String,
    val hasDateTime: Boolean,
    val hasLocation: Boolean = false,
    val minConfidence: Float = 0.5f
)

enum class TestDifficulty {
    EASY,       // Explicit patterns: "meeting at 3pm", "dentist Friday 12:00"
    MEDIUM,     // Implicit patterns: "see you tomorrow", "lunch?"  
    HARD,       // Complex conversational: negotiations, back-and-forth
    EXTREME     // Ambiguous/contextual: "the usual time", "maybe later"
}

class EventDetectionTestSuite {
    
    companion object {
        fun getAllTestCases(): List<EventTestCase> {
            return listOf(
                // ===== EASY TESTS =====
                EventTestCase(
                    name = "explicit_time_activity",
                    conversation = "dentist Friday 12:00PM",
                    expectedEvents = listOf(
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.EASY,
                    description = "Explicit activity with specific day and time"
                ),
                
                EventTestCase(
                    name = "meeting_at_time",
                    conversation = "meeting at 3pm tomorrow",
                    expectedEvents = listOf(
                        ExpectedEvent("Meeting", hasDateTime = true, minConfidence = 0.8f)
                    ),
                    difficulty = TestDifficulty.EASY,
                    description = "Meeting with explicit 'at' time marker"
                ),
                
                EventTestCase(
                    name = "dinner_with_location",
                    conversation = "dinner at 7pm at Mario's restaurant",
                    expectedEvents = listOf(
                        ExpectedEvent("Meal", hasDateTime = true, hasLocation = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.EASY,
                    description = "Dinner with time and location"
                ),
                
                EventTestCase(
                    name = "appointment_next_week",
                    conversation = "doctor appointment next week Tuesday 10am",
                    expectedEvents = listOf(
                        ExpectedEvent("Appointment", hasDateTime = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.EASY,
                    description = "Medical appointment with specific day and time"
                ),
                
                EventTestCase(
                    name = "movie_tonight",
                    conversation = "movie tonight at 8pm",
                    expectedEvents = listOf(
                        ExpectedEvent("Movie", hasDateTime = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.EASY,
                    description = "Movie with today reference"
                ),
                
                // ===== MEDIUM TESTS =====
                EventTestCase(
                    name = "lunch_question",
                    conversation = "lunch tomorrow?",
                    expectedEvents = listOf(
                        ExpectedEvent("Meal", hasDateTime = true, minConfidence = 0.6f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Question format for meal planning"
                ),
                
                EventTestCase(
                    name = "see_you_tomorrow",
                    conversation = "see you tomorrow morning",
                    expectedEvents = listOf(
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.5f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Implicit meeting reference"
                ),
                
                EventTestCase(
                    name = "call_later",
                    conversation = "I'll call you later today around 4",
                    expectedEvents = listOf(
                        ExpectedEvent("Call", hasDateTime = true, minConfidence = 0.6f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Casual time reference with activity"
                ),
                
                EventTestCase(
                    name = "party_this_weekend",
                    conversation = "party this weekend, probably Saturday night",
                    expectedEvents = listOf(
                        ExpectedEvent("Party", hasDateTime = true, minConfidence = 0.5f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Weekend party with uncertainty"
                ),
                
                EventTestCase(
                    name = "gym_session",
                    conversation = "gym session Monday morning?",
                    expectedEvents = listOf(
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.5f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Activity with day reference"
                ),
                
                // ===== HARD TESTS =====
                EventTestCase(
                    name = "complex_negotiation",
                    conversation = """
                        me: lets meet?
                        Dani: maybe tomorrow?
                        me: actually I can't, maybe the day after, sometime in the morning?
                        Dani: ok
                    """.trimIndent(),
                    expectedEvents = listOf(
                        ExpectedEvent("Meeting", hasDateTime = true, minConfidence = 0.4f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Back-and-forth scheduling negotiation"
                ),
                
                EventTestCase(
                    name = "multi_person_planning",
                    conversation = """
                        John: conference call next Tuesday?
                        Sarah: I'm free in the morning
                        Mike: afternoon works better for me
                        John: let's do 2pm then
                        Sarah: sounds good
                    """.trimIndent(),
                    expectedEvents = listOf(
                        ExpectedEvent("Conference", hasDateTime = true, minConfidence = 0.5f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Multi-person scheduling with time negotiation"
                ),
                
                EventTestCase(
                    name = "conditional_planning",
                    conversation = """
                        if the weather is good tomorrow, we should go hiking
                        meet at the trail at 9am if it's not raining
                    """.trimIndent(),
                    expectedEvents = listOf(
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.3f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Conditional event planning"
                ),
                
                EventTestCase(
                    name = "rescheduling_conversation",
                    conversation = """
                        can we move the meeting from 3pm to 4pm?
                        the client wants to push it back an hour
                        ok, 4pm it is
                    """.trimIndent(),
                    expectedEvents = listOf(
                        ExpectedEvent("Meeting", hasDateTime = true, minConfidence = 0.4f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Rescheduling an existing meeting"
                ),
                
                EventTestCase(
                    name = "vacation_planning",
                    conversation = """
                        thinking about vacation next month
                        maybe the second week would work
                        let's book the flights for the 15th
                    """.trimIndent(),
                    expectedEvents = listOf(
                        ExpectedEvent("Travel", hasDateTime = true, minConfidence = 0.3f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Vacation planning over multiple messages"
                ),
                
                // ===== EXTREME TESTS =====
                EventTestCase(
                    name = "contextual_reference",
                    conversation = "same time as usual next week",
                    expectedEvents = listOf(), // Expected to fail - no context
                    difficulty = TestDifficulty.EXTREME,
                    description = "Reference to unknown 'usual time'"
                ),
                
                EventTestCase(
                    name = "very_ambiguous",
                    conversation = """
                        maybe later?
                        sure
                        the usual place?
                        yep
                    """.trimIndent(),
                    expectedEvents = listOf(), // Expected to fail - too ambiguous
                    difficulty = TestDifficulty.EXTREME,
                    description = "Extremely ambiguous conversation"
                ),
                
                EventTestCase(
                    name = "implied_recurring",
                    conversation = "same as every Monday",
                    expectedEvents = listOf(), // Expected to fail - no specific event
                    difficulty = TestDifficulty.EXTREME,
                    description = "Reference to recurring event without specifics"
                ),
                
                EventTestCase(
                    name = "cultural_reference",
                    conversation = "after the game tonight",
                    expectedEvents = listOf(), // Expected to fail - unclear what game
                    difficulty = TestDifficulty.EXTREME,
                    description = "Cultural reference without context"
                ),
                
                EventTestCase(
                    name = "multiple_overlapping",
                    conversation = """
                        busy day tomorrow: dentist at 9, lunch at 1, gym after work
                        also need to pick up the kids at 5
                    """.trimIndent(),
                    expectedEvents = listOf(
                        ExpectedEvent("Appointment", hasDateTime = true, minConfidence = 0.7f),
                        ExpectedEvent("Meal", hasDateTime = true, minConfidence = 0.7f),
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.4f),
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.4f)
                    ),
                    difficulty = TestDifficulty.EXTREME,
                    description = "Multiple events in single conversation"
                ),
                
                // ===== EDGE CASES =====
                EventTestCase(
                    name = "false_positive_test",
                    conversation = "I was at the meeting yesterday, it was at 3pm",
                    expectedEvents = listOf(), // Past tense - should not detect
                    difficulty = TestDifficulty.HARD,
                    description = "Past tense should not create future events"
                ),
                
                EventTestCase(
                    name = "question_without_commitment",
                    conversation = "what time is the meeting?",
                    expectedEvents = listOf(), // Just a question - no event planning
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Information request, not event planning"
                ),
                
                EventTestCase(
                    name = "cancellation",
                    conversation = "let's cancel the dinner tomorrow",
                    expectedEvents = listOf(), // Cancellation - should not create event
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Event cancellation should not create new event"
                ),
                
                EventTestCase(
                    name = "time_without_activity",
                    conversation = "see you at 3pm",
                    expectedEvents = listOf(
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.5f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Time reference without specific activity"
                ),
                
                // ===== CALENDAR INTEGRATION EDGE CASES =====
                EventTestCase(
                    name = "invalid_time_format",
                    conversation = "meeting at 25:99", // Invalid time
                    expectedEvents = listOf(), // Should not detect invalid times
                    difficulty = TestDifficulty.HARD,
                    description = "Invalid time format should not create events"
                ),
                
                EventTestCase(
                    name = "ambiguous_date_reference",
                    conversation = "let's meet on the 32nd", // Invalid date
                    expectedEvents = listOf(), // Should not detect invalid dates
                    difficulty = TestDifficulty.HARD,
                    description = "Invalid date should not create events"
                ),
                
                EventTestCase(
                    name = "very_long_title_test",
                    conversation = "let's have a meeting about the extremely important project that involves multiple stakeholders and requires careful coordination across various departments and teams at 3pm tomorrow",
                    expectedEvents = listOf(
                        ExpectedEvent("Meeting", hasDateTime = true, minConfidence = 0.6f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Very long conversation should produce manageable event title"
                ),
                
                EventTestCase(
                    name = "unicode_and_special_chars",
                    conversation = "café meeting at 2pm with André & José 📅",
                    expectedEvents = listOf(
                        ExpectedEvent("Meeting", hasDateTime = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Unicode characters and emojis should be handled properly"
                ),
                
                EventTestCase(
                    name = "timezone_edge_case",
                    conversation = "call at 3pm PST tomorrow",
                    expectedEvents = listOf(
                        ExpectedEvent("Call", hasDateTime = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Explicit timezone mentions"
                ),
                
                EventTestCase(
                    name = "midnight_edge_case",
                    conversation = "meeting at midnight tonight",
                    expectedEvents = listOf(
                        ExpectedEvent("Meeting", hasDateTime = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Midnight time reference"
                ),
                
                EventTestCase(
                    name = "noon_edge_case",
                    conversation = "lunch at noon tomorrow",
                    expectedEvents = listOf(
                        ExpectedEvent("Meal", hasDateTime = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Noon time reference"
                ),
                
                EventTestCase(
                    name = "duration_mentions",
                    conversation = "2-hour meeting at 10am tomorrow",
                    expectedEvents = listOf(
                        ExpectedEvent("Meeting", hasDateTime = true, minConfidence = 0.7f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Explicit duration mentions should be preserved"
                ),
                
                EventTestCase(
                    name = "recurring_hint",
                    conversation = "weekly team meeting every Tuesday at 10am",
                    expectedEvents = listOf(
                        ExpectedEvent("Meeting", hasDateTime = true, minConfidence = 0.6f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Recurring event hints"
                ),
                
                EventTestCase(
                    name = "location_with_address",
                    conversation = "meet at Starbucks, 123 Main St at 3pm",
                    expectedEvents = listOf(
                        ExpectedEvent("Event", hasDateTime = true, hasLocation = true, minConfidence = 0.6f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Specific location with address"
                ),
                
                EventTestCase(
                    name = "multiple_times_same_message",
                    conversation = "available between 2pm and 4pm, prefer 3pm",
                    expectedEvents = listOf(
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.5f)
                    ),
                    difficulty = TestDifficulty.HARD,
                    description = "Multiple time references in same message"
                ),
                
                EventTestCase(
                    name = "empty_string_test",
                    conversation = "",
                    expectedEvents = listOf(),
                    difficulty = TestDifficulty.EASY,
                    description = "Empty conversation should produce no events"
                ),
                
                EventTestCase(
                    name = "whitespace_only_test",
                    conversation = "   \n   \t   ",
                    expectedEvents = listOf(),
                    difficulty = TestDifficulty.EASY,
                    description = "Whitespace-only conversation should produce no events"
                ),
                
                EventTestCase(
                    name = "very_short_message",
                    conversation = "3pm",
                    expectedEvents = listOf(
                        ExpectedEvent("Event", hasDateTime = true, minConfidence = 0.4f)
                    ),
                    difficulty = TestDifficulty.MEDIUM,
                    description = "Very short time-only message"
                )
            )
        }
        
        fun getTestCasesByDifficulty(difficulty: TestDifficulty): List<EventTestCase> {
            return getAllTestCases().filter { it.difficulty == difficulty }
        }
        
        fun getTestCaseByName(name: String): EventTestCase? {
            return getAllTestCases().find { it.name == name }
        }
    }
}