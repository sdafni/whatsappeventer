package com.example.whatsappeventer

import android.util.Log

enum class DetectedLanguage {
    HEBREW,
    ENGLISH,
    MIXED,
    UNKNOWN
}

class LanguageDetector {
    
    companion object {
        private const val TAG = "LanguageDetector"
        
        // Hebrew Unicode range: U+0590 to U+05FF
        private const val HEBREW_START = 0x0590
        private const val HEBREW_END = 0x05FF
        
        // Minimum percentage of characters to determine language
        private const val LANGUAGE_THRESHOLD = 0.15f
    }
    
    /**
     * Detects the primary language of the given text
     * @param text The text to analyze
     * @return DetectedLanguage enum indicating the primary language
     */
    fun detectLanguage(text: String): DetectedLanguage {
        if (text.isBlank()) {
            return DetectedLanguage.UNKNOWN
        }
        
        var hebrewChars = 0
        var englishChars = 0
        var totalLetters = 0
        
        for (char in text) {
            val codePoint = char.code
            
            when {
                // Hebrew characters
                codePoint in HEBREW_START..HEBREW_END -> {
                    hebrewChars++
                    totalLetters++
                }
                // English characters (Latin alphabet)
                char.isLetter() && codePoint in 65..122 -> {
                    englishChars++
                    totalLetters++
                }
            }
        }
        
        if (totalLetters == 0) {
            Log.d(TAG, "No letters found in text: '$text'")
            return DetectedLanguage.UNKNOWN
        }
        
        val hebrewRatio = hebrewChars.toFloat() / totalLetters
        val englishRatio = englishChars.toFloat() / totalLetters
        
        Log.d(TAG, "Language analysis for '$text': Hebrew=$hebrewRatio, English=$englishRatio")
        
        return when {
            hebrewRatio >= LANGUAGE_THRESHOLD && englishRatio >= LANGUAGE_THRESHOLD -> {
                Log.d(TAG, "Detected mixed language")
                DetectedLanguage.MIXED
            }
            hebrewRatio >= LANGUAGE_THRESHOLD -> {
                Log.d(TAG, "Detected Hebrew language")
                DetectedLanguage.HEBREW
            }
            englishRatio >= LANGUAGE_THRESHOLD -> {
                Log.d(TAG, "Detected English language")
                DetectedLanguage.ENGLISH
            }
            else -> {
                Log.d(TAG, "Could not determine language")
                DetectedLanguage.UNKNOWN
            }
        }
    }
    
    /**
     * Checks if the text contains Hebrew characters
     */
    fun containsHebrew(text: String): Boolean {
        return text.any { char ->
            char.code in HEBREW_START..HEBREW_END
        }
    }
    
    /**
     * Checks if the text contains English characters
     */
    fun containsEnglish(text: String): Boolean {
        return text.any { char ->
            char.isLetter() && char.code in 65..122
        }
    }
}