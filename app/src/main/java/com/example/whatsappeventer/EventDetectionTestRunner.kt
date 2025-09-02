package com.example.whatsappeventer

import android.util.Log

data class TestResult(
    val testCase: EventTestCase,
    val actualEvents: List<DetectedEvent>,
    val passed: Boolean,
    val score: Float,
    val details: String,
    val calendarValidation: CalendarValidationResult? = null,
    val calendarReadiness: Float = 0.0f
)

data class TestSummary(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val averageScore: Float,
    val scoresByDifficulty: Map<TestDifficulty, DifficultyScore>,
    val calendarReadyTests: Int = 0,
    val averageCalendarReadiness: Float = 0.0f
)

data class DifficultyScore(
    val difficulty: TestDifficulty,
    val totalTests: Int,
    val passedTests: Int,
    val averageScore: Float
)

class EventDetectionTestRunner(
    private val detector: EventDetectionInterface
) {
    companion object {
        private const val TAG = "EventDetectionTest"
        private const val CALENDAR_READINESS_THRESHOLD = 0.8f
    }
    
    private val calendarValidator = CalendarJsonValidator()
    
    fun runAllTests(): TestSummary {
        Log.i(TAG, "Starting comprehensive event detection test suite with ${detector.getDetectorName()}")
        
        val testCases = EventDetectionTestSuite.getAllTestCases()
        val results = testCases.map { runSingleTest(it) }
        
        return generateSummary(results)
    }
    
    fun runTestsByDifficulty(difficulty: TestDifficulty): TestSummary {
        Log.i(TAG, "Running ${difficulty.name} tests with ${detector.getDetectorName()}")
        
        val testCases = EventDetectionTestSuite.getTestCasesByDifficulty(difficulty)
        val results = testCases.map { runSingleTest(it) }
        
        return generateSummary(results)
    }
    
    fun runSingleTestByName(testName: String): TestResult? {
        val testCase = EventDetectionTestSuite.getTestCaseByName(testName) ?: return null
        return runSingleTest(testCase)
    }
    
    private fun runSingleTest(testCase: EventTestCase): TestResult {
        Log.d(TAG, "Running test: ${testCase.name} (${testCase.difficulty.name})")
        Log.d(TAG, "Test description: ${testCase.description}")
        Log.d(TAG, "Input conversation: ${testCase.conversation.replace("\n", "\\n")}")
        
        try {
            // Run the detector
            val actualEvents = detector.detectEvents(testCase.conversation)
            Log.d(TAG, "Detected ${actualEvents.size} events")
            
            // Evaluate results
            val (passed, score, details) = evaluateResults(testCase, actualEvents)
            
            // Evaluate calendar readiness
            val (calendarValidation, calendarReadiness) = evaluateCalendarReadiness(actualEvents)
            
            Log.d(TAG, "Test ${testCase.name}: ${if (passed) "PASSED" else "FAILED"} (score: $score)")
            Log.d(TAG, "Calendar readiness: ${"%.2f".format(calendarReadiness)}")
            Log.d(TAG, "Details: $details")
            
            return TestResult(
                testCase = testCase,
                actualEvents = actualEvents,
                passed = passed,
                score = score,
                details = details,
                calendarValidation = calendarValidation,
                calendarReadiness = calendarReadiness
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Test ${testCase.name} threw exception: ${e.message}")
            return TestResult(
                testCase = testCase,
                actualEvents = emptyList(),
                passed = false,
                score = 0.0f,
                details = "Exception during detection: ${e.message}",
                calendarValidation = null,
                calendarReadiness = 0.0f
            )
        }
    }
    
    private fun evaluateResults(
        testCase: EventTestCase, 
        actualEvents: List<DetectedEvent>
    ): Triple<Boolean, Float, String> {
        
        val expectedCount = testCase.expectedEvents.size
        val actualCount = actualEvents.size
        
        when {
            expectedCount == 0 && actualCount == 0 -> {
                return Triple(true, 1.0f, "Correctly detected no events")
            }
            
            expectedCount == 0 && actualCount > 0 -> {
                val falsePositives = actualEvents.joinToString(", ") { it.title }
                return Triple(false, 0.0f, "False positives detected: $falsePositives")
            }
            
            expectedCount > 0 && actualCount == 0 -> {
                val missedEvents = testCase.expectedEvents.joinToString(", ") { it.title }
                return Triple(false, 0.0f, "No events detected, expected: $missedEvents")
            }
            
            else -> {
                return evaluateEventMatches(testCase.expectedEvents, actualEvents)
            }
        }
    }
    
    private fun evaluateEventMatches(
        expectedEvents: List<ExpectedEvent>,
        actualEvents: List<DetectedEvent>
    ): Triple<Boolean, Float, String> {
        
        val matchScores = mutableListOf<Float>()
        val matchDetails = mutableListOf<String>()
        
        for (expected in expectedEvents) {
            val bestMatch = findBestMatch(expected, actualEvents)
            if (bestMatch != null) {
                val score = calculateMatchScore(expected, bestMatch)
                matchScores.add(score)
                matchDetails.add("Expected '${expected.title}' matched '${bestMatch.title}' (score: $score)")
            } else {
                matchScores.add(0.0f)
                matchDetails.add("Expected '${expected.title}' - no match found")
            }
        }
        
        // Check for extra events (false positives)
        val extraEvents = actualEvents.size - expectedEvents.size
        if (extraEvents > 0) {
            matchDetails.add("$extraEvents extra events detected (false positives)")
        }
        
        val averageScore = if (matchScores.isEmpty()) 0.0f else matchScores.average().toFloat()
        val passed = averageScore >= 0.7f && extraEvents <= 0
        
        return Triple(passed, averageScore, matchDetails.joinToString("; "))
    }
    
    private fun findBestMatch(expected: ExpectedEvent, actualEvents: List<DetectedEvent>): DetectedEvent? {
        return actualEvents.maxByOrNull { actual ->
            calculateMatchScore(expected, actual)
        }
    }
    
    private fun calculateMatchScore(expected: ExpectedEvent, actual: DetectedEvent): Float {
        var score = 0.0f
        
        // Title similarity (basic contains check)
        if (actual.title.contains(expected.title, ignoreCase = true) || 
            expected.title.contains(actual.title, ignoreCase = true)) {
            score += 0.4f
        }
        
        // DateTime presence
        if (expected.hasDateTime && actual.dateTime != null) {
            score += 0.3f
        } else if (!expected.hasDateTime && actual.dateTime == null) {
            score += 0.3f
        }
        
        // Location presence
        if (expected.hasLocation && !actual.location.isNullOrEmpty()) {
            score += 0.1f
        } else if (!expected.hasLocation) {
            score += 0.1f
        }
        
        // Confidence check
        if (actual.confidence >= expected.minConfidence) {
            score += 0.2f
        }
        
        return score
    }
    
    /**
     * Evaluates calendar readiness by converting detected events to JSON and validating
     */
    private fun evaluateCalendarReadiness(events: List<DetectedEvent>): Pair<CalendarValidationResult?, Float> {
        if (events.isEmpty()) {
            return Pair(null, 1.0f) // Empty is calendar-ready (no events to create)
        }
        
        return try {
            // Convert to calendar JSON
            val calendarJson = EventToCalendarMapper.convertEventsToCalendarJson(events)
            
            // Validate the JSON
            val validation = calendarValidator.validateCalendarJson(calendarJson)
            
            // Calculate readiness score
            val readinessScore = if (validation.isValid) {
                validation.score
            } else {
                // Penalize invalid JSON more heavily
                validation.score * 0.5f
            }
            
            Log.d(TAG, "Calendar validation: valid=${validation.isValid}, score=${validation.score}")
            if (validation.errors.isNotEmpty()) {
                Log.d(TAG, "Calendar validation errors: ${validation.errors}")
            }
            
            Pair(validation, readinessScore)
            
        } catch (e: Exception) {
            Log.e(TAG, "Calendar readiness evaluation failed: ${e.message}")
            Pair(null, 0.0f)
        }
    }
    
    private fun generateSummary(results: List<TestResult>): TestSummary {
        val totalTests = results.size
        val passedTests = results.count { it.passed }
        val failedTests = totalTests - passedTests
        val averageScore = if (results.isEmpty()) 0.0f else results.map { it.score }.average().toFloat()
        
        // Calculate calendar readiness metrics
        val calendarReadyTests = results.count { it.calendarReadiness >= CALENDAR_READINESS_THRESHOLD }
        val averageCalendarReadiness = if (results.isEmpty()) 0.0f else 
            results.map { it.calendarReadiness }.average().toFloat()
        
        val scoresByDifficulty = results.groupBy { it.testCase.difficulty }
            .mapValues { (difficulty, testResults) ->
                DifficultyScore(
                    difficulty = difficulty,
                    totalTests = testResults.size,
                    passedTests = testResults.count { it.passed },
                    averageScore = testResults.map { it.score }.average().toFloat()
                )
            }
        
        return TestSummary(
            totalTests = totalTests,
            passedTests = passedTests,
            failedTests = failedTests,
            averageScore = averageScore,
            scoresByDifficulty = scoresByDifficulty,
            calendarReadyTests = calendarReadyTests,
            averageCalendarReadiness = averageCalendarReadiness
        )
    }
    
    fun printDetailedResults(results: TestSummary) {
        Log.i(TAG, "=== EVENT DETECTION TEST RESULTS ===")
        Log.i(TAG, "Detector: ${detector.getDetectorName()}")
        Log.i(TAG, "")
        Log.i(TAG, "OVERALL PERFORMANCE:")
        Log.i(TAG, "  Total Tests: ${results.totalTests}")
        Log.i(TAG, "  Passed: ${results.passedTests}")
        Log.i(TAG, "  Failed: ${results.failedTests}")
        Log.i(TAG, "  Success Rate: ${((results.passedTests.toFloat() / results.totalTests) * 100).toInt()}%")
        Log.i(TAG, "  Average Score: ${"%.2f".format(results.averageScore)}")
        Log.i(TAG, "")
        
        Log.i(TAG, "CALENDAR READINESS:")
        Log.i(TAG, "  Calendar-Ready Tests: ${results.calendarReadyTests}/${results.totalTests}")
        Log.i(TAG, "  Calendar Readiness Rate: ${((results.calendarReadyTests.toFloat() / results.totalTests) * 100).toInt()}%")
        Log.i(TAG, "  Average Calendar Score: ${"%.2f".format(results.averageCalendarReadiness)}")
        Log.i(TAG, "")
        
        Log.i(TAG, "PERFORMANCE BY DIFFICULTY:")
        TestDifficulty.values().forEach { difficulty ->
            results.scoresByDifficulty[difficulty]?.let { score ->
                val successRate = ((score.passedTests.toFloat() / score.totalTests) * 100).toInt()
                Log.i(TAG, "  ${difficulty.name}: $successRate% (${score.passedTests}/${score.totalTests}) - Avg: ${"%.2f".format(score.averageScore)}")
            }
        }
        Log.i(TAG, "===================================")
    }
}