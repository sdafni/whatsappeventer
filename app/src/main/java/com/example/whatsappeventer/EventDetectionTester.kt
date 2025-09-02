package com.example.whatsappeventer

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * Utility class to run event detection tests from within the app
 * Can be triggered from MainActivity or OverlayService for easy testing
 */
class EventDetectionTester(private val context: Context) {
    
    companion object {
        private const val TAG = "EventDetectionTester"
    }
    
    fun runQuickTest() {
        Log.i(TAG, "Starting quick event detection test...")
        
        val detector = NlpEventDetector()
        val testRunner = EventDetectionTestRunner(detector)
        
        // Run just the EASY tests for quick feedback
        val results = testRunner.runTestsByDifficulty(TestDifficulty.EASY)
        testRunner.printDetailedResults(results)
        
        val successRate = ((results.passedTests.toFloat() / results.totalTests) * 100).toInt()
        val message = "Quick Test: $successRate% (${results.passedTests}/${results.totalTests}) - See logs for details"
        
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    fun runFullTestSuite() {
        Log.i(TAG, "Starting full event detection test suite...")
        
        Thread {
            try {
                val detector = NlpEventDetector()
                val testRunner = EventDetectionTestRunner(detector)
                
                // Run all tests
                val results = testRunner.runAllTests()
                testRunner.printDetailedResults(results)
                
                val successRate = ((results.passedTests.toFloat() / results.totalTests) * 100).toInt()
                val message = "Full Test: $successRate% (${results.passedTests}/${results.totalTests})"
                
                // Show result on main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                
                // Print detailed breakdown
                logDetailedBreakdown(results)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error running full test suite: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }
    
    fun runSpecificTest(testName: String) {
        Log.i(TAG, "Running specific test: $testName")
        
        val detector = NlpEventDetector()
        val testRunner = EventDetectionTestRunner(detector)
        
        val result = testRunner.runSingleTestByName(testName)
        
        if (result != null) {
            val status = if (result.passed) "PASSED" else "FAILED"
            val message = "Test '$testName': $status (score: ${"%.2f".format(result.score)})"
            
            Log.i(TAG, "Single test result: $message")
            Log.i(TAG, "Details: ${result.details}")
            
            result.actualEvents.forEachIndexed { index, event ->
                Log.i(TAG, "Event $index: ${event.title} - ${event.dateTime} - confidence: ${event.confidence}")
            }
            
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Test '$testName' not found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun logDetailedBreakdown(results: TestSummary) {
        Log.i(TAG, "")
        Log.i(TAG, "DETAILED BREAKDOWN:")
        
        // Run individual difficulty tests for more detail
        val detector = NlpEventDetector()
        val testRunner = EventDetectionTestRunner(detector)
        
        TestDifficulty.values().forEach { difficulty ->
            Log.i(TAG, "")
            Log.i(TAG, "--- ${difficulty.name} TESTS ---")
            
            val difficultyResults = testRunner.runTestsByDifficulty(difficulty)
            val testCases = EventDetectionTestSuite.getTestCasesByDifficulty(difficulty)
            
            testCases.forEach { testCase ->
                val result = testRunner.runSingleTestByName(testCase.name)
                if (result != null) {
                    val status = if (result.passed) "✅" else "❌"
                    Log.i(TAG, "  $status ${testCase.name}: ${result.details}")
                }
            }
        }
    }
    
    fun runCalendarIntegrationTest() {
        Log.i(TAG, "Running calendar integration tests...")
        
        Thread {
            try {
                val detector = NlpEventDetector()
                val calendarTestRunner = CalendarIntegrationTestRunner(detector)
                
                // Run calendar integration tests
                val results = calendarTestRunner.runCalendarIntegrationTests()
                calendarTestRunner.printCalendarIntegrationResults(results)
                
                val readinessRate = ((results.calendarReadyCount.toFloat() / results.totalTests) * 100).toInt()
                val message = "Calendar Integration: $readinessRate% ready (${results.calendarReadyCount}/${results.totalTests})"
                
                // Show result on main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error running calendar integration tests: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }
    
    fun showTestCaseList() {
        Log.i(TAG, "=== AVAILABLE TEST CASES ===")
        
        TestDifficulty.values().forEach { difficulty ->
            Log.i(TAG, "")
            Log.i(TAG, "${difficulty.name} TESTS:")
            
            EventDetectionTestSuite.getTestCasesByDifficulty(difficulty).forEach { testCase ->
                Log.i(TAG, "  • ${testCase.name}: ${testCase.description}")
                Log.i(TAG, "    Input: \"${testCase.conversation.replace("\n", " | ")}\"")
                Log.i(TAG, "    Expected: ${testCase.expectedEvents.size} events")
            }
        }
        Log.i(TAG, "=============================")
        
        Toast.makeText(context, "Test case list logged - check logcat", Toast.LENGTH_SHORT).show()
    }
}