# Event Detection Testing Framework

## Overview

This testing framework evaluates the performance of event detection implementations, from simple NLP regex-based detection to future LLM-based implementations. It provides comprehensive test cases ranging from simple explicit events to complex conversational negotiations.

## Architecture

### Core Components

1. **`EventDetectionInterface`** - Abstraction layer for different detection implementations
2. **`NlpEventDetector`** - Current regex-based implementation wrapper
3. **`LlmEventDetector`** - Placeholder for future LLM implementation
4. **`EventDetectionTestSuite`** - Comprehensive test cases with expected results
5. **`EventDetectionTestRunner`** - Test execution and evaluation engine
6. **`EventDetectionTester`** - Utility for running tests from within the app

## Test Cases

### Difficulty Levels

#### EASY (Explicit Patterns)
- `"dentist Friday 12:00PM"`
- `"meeting at 3pm tomorrow"`
- `"dinner at 7pm at Mario's restaurant"`

#### MEDIUM (Implicit Patterns)
- `"lunch tomorrow?"`
- `"see you tomorrow morning"`
- `"I'll call you later today around 4"`

#### HARD (Complex Conversations)
- Multi-person scheduling negotiations
- Conditional event planning
- Rescheduling conversations
- Back-and-forth time negotiations

#### EXTREME (Contextual/Ambiguous)
- `"same time as usual next week"`
- `"maybe later?" / "sure"`
- Multiple overlapping events in single message
- Cultural references without context

## How to Run Tests

### Method 1: Overlay Button (Recommended)

1. **Build and deploy** the app:
   ```bash
   export ANDROID_HOME="/Users/yuvaldafni/Library/Android/sdk"
   ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Start logcat** to see detailed results:
   ```bash
   adb logcat -s EventDetectionTester EventDetectionTest EventDetector
   ```

3. **Use the overlay button**:
   - **Normal tap**: Shows events from current WhatsApp conversation
   - **Long press (2+ seconds)**: Runs quick test suite (EASY difficulty tests)

### Method 2: Programmatic Testing

Add this code to any activity or service:

```kotlin
// Quick test (EASY difficulty only)
val tester = EventDetectionTester(context)
tester.runQuickTest()

// Full test suite (all difficulties)
tester.runFullTestSuite()

// Specific test by name
tester.runSpecificTest("explicit_time_activity")

// Show all available test cases
tester.showTestCaseList()
```

### Method 3: Direct Test Runner

```kotlin
val detector = NlpEventDetector()
val testRunner = EventDetectionTestRunner(detector)

// Run all tests
val results = testRunner.runAllTests()
testRunner.printDetailedResults(results)

// Run by difficulty
val easyResults = testRunner.runTestsByDifficulty(TestDifficulty.EASY)
```

## Understanding Test Results

### Test Output Format

```
=== EVENT DETECTION TEST RESULTS ===
Detector: NLP_REGEX_BASED

OVERALL PERFORMANCE:
  Total Tests: 25
  Passed: 12
  Failed: 13
  Success Rate: 48%
  Average Score: 0.52

PERFORMANCE BY DIFFICULTY:
  EASY: 80% (4/5) - Avg: 0.85
  MEDIUM: 60% (3/5) - Avg: 0.65
  HARD: 40% (2/5) - Avg: 0.45
  EXTREME: 20% (1/5) - Avg: 0.25
```

### Scoring System

Each test receives a score from 0.0 to 1.0 based on:
- **Title Match** (0.4 points): Does detected event title match expected?
- **DateTime Presence** (0.3 points): Does detected event have datetime when expected?
- **Location Presence** (0.1 points): Does detected event have location when expected?
- **Confidence Threshold** (0.2 points): Does confidence meet minimum requirements?

### Test Status

- **PASSED**: Score ≥ 0.7 and no false positives
- **FAILED**: Score < 0.7 or false positives detected

## Expected Performance Baselines

### Current NLP Implementation (Estimated)

- **EASY**: 70-85% success rate
- **MEDIUM**: 40-60% success rate  
- **HARD**: 20-40% success rate
- **EXTREME**: 0-20% success rate

### Target LLM Implementation (Goal)

- **EASY**: 90-95% success rate
- **MEDIUM**: 80-90% success rate
- **HARD**: 60-80% success rate
- **EXTREME**: 40-60% success rate

## Adding New Test Cases

To add a new test case, edit `EventDetectionTestSuite.kt`:

```kotlin
EventTestCase(
    name = "your_test_name",
    conversation = "Your test conversation here",
    expectedEvents = listOf(
        ExpectedEvent("Event Type", hasDateTime = true, hasLocation = false, minConfidence = 0.7f)
    ),
    difficulty = TestDifficulty.MEDIUM,
    description = "Description of what this test evaluates"
)
```

## Integration with Future LLM

When implementing LLM detection:

1. Create a new class implementing `EventDetectionInterface`
2. Replace `NlpEventDetector()` with your LLM implementation in test code
3. Run the same test suite to compare performance
4. The interface ensures drop-in compatibility

Example:
```kotlin
class LlmEventDetector : EventDetectionInterface {
    override fun detectEvents(conversationText: String): List<DetectedEvent> {
        // Your LLM implementation here
    }
    
    override fun getDetectorName(): String = "GPT_4_BASED"
}
```

## Debugging Failed Tests

Check logcat for detailed failure information:

```
Test explicit_time_activity: FAILED (score: 0.60)
Details: Expected 'Event' matched 'Meeting' (score: 0.60); DateTime missing
```

This indicates the test found a "Meeting" event but expected just "Event", and the datetime wasn't detected properly.

## Next Steps

1. **Baseline Current Performance**: Run full test suite to understand current NLP limitations
2. **Identify Patterns**: Look for systematic failures in specific test categories
3. **Prepare for LLM**: Use this framework to evaluate LLM implementations
4. **Continuous Improvement**: Add new test cases as you encounter edge cases in real usage