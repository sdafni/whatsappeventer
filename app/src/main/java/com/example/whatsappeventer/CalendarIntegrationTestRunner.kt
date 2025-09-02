package com.example.whatsappeventer

import android.util.Log

data class CalendarIntegrationResult(
    val testCase: EventTestCase,
    val detectedEvents: List<DetectedEvent>,
    val generatedJson: String,
    val validationResult: CalendarValidationResult,
    val passed: Boolean,
    val score: Float,
    val details: String
)

data class CalendarIntegrationSummary(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val averageScore: Float,
    val validJsonCount: Int,
    val invalidJsonCount: Int,
    val calendarReadyCount: Int,
    val errorBreakdown: Map<String, Int>
)

/**
 * Dedicated test runner focused specifically on calendar integration readiness
 * This validates the complete pipeline from conversation to Google Calendar JSON
 */
class CalendarIntegrationTestRunner(
    private val detector: EventDetectionInterface
) {
    companion object {
        private const val TAG = "CalendarIntegrationTest"
        private const val CALENDAR_READY_THRESHOLD = 0.8f
    }
    
    private val calendarValidator = CalendarJsonValidator()
    
    /**
     * Runs calendar integration tests focused on JSON generation and validation
     */
    fun runCalendarIntegrationTests(): CalendarIntegrationSummary {
        Log.i(TAG, "Starting calendar integration tests with ${detector.getDetectorName()}")
        
        val testCases = EventDetectionTestSuite.getAllTestCases()
        val results = testCases.map { runCalendarIntegrationTest(it) }
        
        return generateSummary(results)
    }
    
    /**
     * Runs calendar integration tests for specific difficulty
     */
    fun runCalendarTestsByDifficulty(difficulty: TestDifficulty): CalendarIntegrationSummary {
        Log.i(TAG, "Running calendar integration tests for ${difficulty.name} difficulty")
        
        val testCases = EventDetectionTestSuite.getTestCasesByDifficulty(difficulty)
        val results = testCases.map { runCalendarIntegrationTest(it) }
        
        return generateSummary(results)
    }
    
    /**
     * Tests calendar integration for a single test case
     */
    private fun runCalendarIntegrationTest(testCase: EventTestCase): CalendarIntegrationResult {
        Log.d(TAG, "Running calendar integration test: ${testCase.name}")
        
        try {
            // 1. Detect events
            val detectedEvents = detector.detectEvents(testCase.conversation)
            Log.d(TAG, "Detected ${detectedEvents.size} events for ${testCase.name}")
            
            // 2. Generate calendar JSON
            val calendarJson = EventToCalendarMapper.convertEventsToCalendarJson(detectedEvents)
            Log.d(TAG, "Generated JSON (${calendarJson.length} chars)")
            
            // 3. Validate JSON structure
            val validation = calendarValidator.validateCalendarJson(calendarJson)
            Log.d(TAG, "JSON validation: valid=${validation.isValid}, score=${validation.score}")
            
            // 4. Evaluate overall result
            val (passed, score, details) = evaluateCalendarIntegration(
                testCase, detectedEvents, validation
            )
            
            Log.d(TAG, "Calendar integration test ${testCase.name}: ${if (passed) "PASSED" else "FAILED"} (score: $score)")
            
            return CalendarIntegrationResult(
                testCase = testCase,
                detectedEvents = detectedEvents,
                generatedJson = calendarJson,
                validationResult = validation,
                passed = passed,
                score = score,
                details = details
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Calendar integration test ${testCase.name} failed: ${e.message}")
            
            return CalendarIntegrationResult(
                testCase = testCase,
                detectedEvents = emptyList(),
                generatedJson = "",
                validationResult = CalendarValidationResult(
                    isValid = false,
                    errors = listOf("Exception: ${e.message}"),
                    warnings = emptyList(),
                    score = 0.0f
                ),
                passed = false,
                score = 0.0f,
                details = "Exception during test: ${e.message}"
            )
        }
    }
    
    /**
     * Evaluates calendar integration readiness
     */
    private fun evaluateCalendarIntegration(
        testCase: EventTestCase,
        detectedEvents: List<DetectedEvent>,
        validation: CalendarValidationResult
    ): Triple<Boolean, Float, String> {
        
        val details = mutableListOf<String>()
        var score = 0.0f
        
        // 1. Event count matching (30% of score)
        val expectedCount = testCase.expectedEvents.size
        val actualCount = detectedEvents.size
        
        val eventCountScore = when {
            expectedCount == 0 && actualCount == 0 -> 1.0f
            expectedCount == 0 && actualCount > 0 -> 0.0f // False positives
            expectedCount > 0 && actualCount == 0 -> 0.0f // No detection
            else -> {
                val ratio = actualCount.toFloat() / expectedCount
                when {
                    ratio > 2.0f -> 0.2f // Too many events
                    ratio > 1.5f -> 0.5f // Some extra events
                    ratio >= 0.8f -> 1.0f // Good match
                    else -> ratio // Proportional to detection rate
                }
            }
        }
        score += eventCountScore * 0.3f
        details.add("Event count: expected=$expectedCount, actual=$actualCount (score: ${"%.2f".format(eventCountScore)})")
        
        // 2. JSON validity (40% of score)
        val jsonValidityScore = if (validation.isValid) 1.0f else 0.0f
        score += jsonValidityScore * 0.4f
        
        if (validation.isValid) {
            details.add("JSON structure: VALID")
        } else {
            details.add("JSON structure: INVALID - ${validation.errors.joinToString("; ")}")
        }
        
        // 3. JSON quality score (30% of score)
        score += validation.score * 0.3f
        details.add("JSON quality score: ${"%.2f".format(validation.score)}")
        
        // Add validation warnings if any
        if (validation.warnings.isNotEmpty()) {
            details.add("Warnings: ${validation.warnings.joinToString("; ")}")
        }
        
        // Overall pass/fail determination
        val passed = validation.isValid && score >= CALENDAR_READY_THRESHOLD
        
        return Triple(passed, score, details.joinToString("; "))
    }
    
    /**
     * Generates summary of calendar integration test results
     */
    private fun generateSummary(results: List<CalendarIntegrationResult>): CalendarIntegrationSummary {
        val totalTests = results.size
        val passedTests = results.count { it.passed }
        val failedTests = totalTests - passedTests
        val averageScore = if (results.isEmpty()) 0.0f else results.map { it.score }.average().toFloat()
        
        val validJsonCount = results.count { it.validationResult.isValid }
        val invalidJsonCount = totalTests - validJsonCount
        val calendarReadyCount = results.count { it.score >= CALENDAR_READY_THRESHOLD }
        
        // Collect error types for analysis
        val errorBreakdown = mutableMapOf<String, Int>()
        results.forEach { result ->
            if (!result.validationResult.isValid) {
                result.validationResult.errors.forEach { error ->
                    val errorType = categorizeError(error)
                    errorBreakdown[errorType] = errorBreakdown.getOrDefault(errorType, 0) + 1
                }
            }
        }
        
        return CalendarIntegrationSummary(
            totalTests = totalTests,
            passedTests = passedTests,
            failedTests = failedTests,
            averageScore = averageScore,
            validJsonCount = validJsonCount,
            invalidJsonCount = invalidJsonCount,
            calendarReadyCount = calendarReadyCount,
            errorBreakdown = errorBreakdown
        )
    }
    
    /**
     * Categorizes errors for analysis
     */
    private fun categorizeError(error: String): String {
        return when {
            error.contains("dateTime", ignoreCase = true) -> "DateTime Format"
            error.contains("timeZone", ignoreCase = true) -> "TimeZone"
            error.contains("summary", ignoreCase = true) -> "Title/Summary"
            error.contains("duration", ignoreCase = true) -> "Duration"
            error.contains("JSON", ignoreCase = true) -> "JSON Structure"
            error.contains("missing", ignoreCase = true) -> "Missing Fields"
            else -> "Other"
        }
    }
    
    /**
     * Prints detailed calendar integration results
     */
    fun printCalendarIntegrationResults(summary: CalendarIntegrationSummary) {
        Log.i(TAG, "=== CALENDAR INTEGRATION TEST RESULTS ===")
        Log.i(TAG, "Detector: ${detector.getDetectorName()}")
        Log.i(TAG, "")
        
        Log.i(TAG, "OVERALL RESULTS:")
        Log.i(TAG, "  Total Tests: ${summary.totalTests}")
        Log.i(TAG, "  Passed: ${summary.passedTests}")
        Log.i(TAG, "  Failed: ${summary.failedTests}")
        Log.i(TAG, "  Success Rate: ${((summary.passedTests.toFloat() / summary.totalTests) * 100).toInt()}%")
        Log.i(TAG, "  Average Score: ${"%.2f".format(summary.averageScore)}")
        Log.i(TAG, "")
        
        Log.i(TAG, "JSON GENERATION:")
        Log.i(TAG, "  Valid JSON: ${summary.validJsonCount}/${summary.totalTests}")
        Log.i(TAG, "  Invalid JSON: ${summary.invalidJsonCount}/${summary.totalTests}")
        Log.i(TAG, "  JSON Success Rate: ${((summary.validJsonCount.toFloat() / summary.totalTests) * 100).toInt()}%")
        Log.i(TAG, "")
        
        Log.i(TAG, "CALENDAR READINESS:")
        Log.i(TAG, "  Calendar-Ready Tests: ${summary.calendarReadyCount}/${summary.totalTests}")
        Log.i(TAG, "  Calendar Readiness Rate: ${((summary.calendarReadyCount.toFloat() / summary.totalTests) * 100).toInt()}%")
        Log.i(TAG, "  Threshold: ${CALENDAR_READY_THRESHOLD}")
        Log.i(TAG, "")
        
        if (summary.errorBreakdown.isNotEmpty()) {
            Log.i(TAG, "ERROR BREAKDOWN:")
            summary.errorBreakdown.entries.sortedByDescending { it.value }.forEach { (errorType, count) ->
                Log.i(TAG, "  $errorType: $count occurrences")
            }
        }
        
        Log.i(TAG, "============================================")
    }
    
    /**
     * Detailed analysis of a specific test case
     */
    fun analyzeSpecificTest(testName: String): CalendarIntegrationResult? {
        val testCase = EventDetectionTestSuite.getTestCaseByName(testName) ?: return null
        
        val result = runCalendarIntegrationTest(testCase)
        
        Log.i(TAG, "=== DETAILED ANALYSIS: $testName ===")
        Log.i(TAG, "Input: \"${testCase.conversation}\"")
        Log.i(TAG, "Expected Events: ${testCase.expectedEvents.size}")
        Log.i(TAG, "Detected Events: ${result.detectedEvents.size}")
        Log.i(TAG, "")
        
        result.detectedEvents.forEachIndexed { index, event ->
            Log.i(TAG, "Event $index:")
            Log.i(TAG, "  Title: ${event.title}")
            Log.i(TAG, "  DateTime: ${event.dateTime}")
            Log.i(TAG, "  Location: ${event.location}")
            Log.i(TAG, "  Confidence: ${event.confidence}")
        }
        
        Log.i(TAG, "")
        Log.i(TAG, "JSON Validation:")
        Log.i(TAG, "  Valid: ${result.validationResult.isValid}")
        Log.i(TAG, "  Score: ${result.validationResult.score}")
        Log.i(TAG, "  Errors: ${result.validationResult.errors}")
        Log.i(TAG, "  Warnings: ${result.validationResult.warnings}")
        Log.i(TAG, "")
        
        Log.i(TAG, "Overall Result: ${if (result.passed) "PASSED" else "FAILED"}")
        Log.i(TAG, "Score: ${"%.2f".format(result.score)}")
        Log.i(TAG, "Details: ${result.details}")
        Log.i(TAG, "=========================================")
        
        return result
    }
}