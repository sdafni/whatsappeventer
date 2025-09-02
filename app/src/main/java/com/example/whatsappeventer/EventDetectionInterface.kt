package com.example.whatsappeventer

/**
 * Interface for event detection implementations
 * Allows for easy switching between NLP-based detection and LLM-based detection
 */
interface EventDetectionInterface {
    /**
     * Detect events from conversation text
     * @param conversationText The text to analyze for events
     * @return List of detected events
     */
    fun detectEvents(conversationText: String): List<DetectedEvent>
    
    /**
     * Get the name/type of this detection implementation
     */
    fun getDetectorName(): String
    
    /**
     * Get confidence threshold for this detector
     */
    fun getConfidenceThreshold(): Float = 0.5f
}

/**
 * NLP-based implementation wrapper
 */
class NlpEventDetector : EventDetectionInterface {
    private val detector = EventDetector()
    
    override fun detectEvents(conversationText: String): List<DetectedEvent> {
        return detector.detectEvents(conversationText)
    }
    
    override fun getDetectorName(): String = "NLP_REGEX_BASED"
    
    override fun getConfidenceThreshold(): Float = 0.6f
}

/**
 * Placeholder for future LLM implementation
 */
class LlmEventDetector : EventDetectionInterface {
    override fun detectEvents(conversationText: String): List<DetectedEvent> {
        // TODO: Implement LLM-based detection
        throw NotImplementedError("LLM detection not yet implemented")
    }
    
    override fun getDetectorName(): String = "LLM_BASED"
    
    override fun getConfidenceThreshold(): Float = 0.8f
}