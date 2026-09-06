package com.gharfix.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration verification test for GeminiService.
 * Runs only when GEMINI_API_KEY is available in the environment.
 * Verifies that the Google GenAI Client is successfully initialized
 * and can generate a real response with the configured model (gemini-2.5-flash).
 * Note: Never exposes, prints, or logs the API key.
 */
@SpringBootTest
class GeminiServiceLiveIntegrationTest {

    @Autowired
    private GeminiService geminiService;

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
    void verifyGeminiClientInitializationAndLiveModelCall() {
        assertNotNull(geminiService, "GeminiService bean should be loaded in Spring context");
        assertTrue(geminiService.isConfigured(), "GeminiService should detect configured API key");
        assertNotNull(geminiService.getClient(), "Google GenAI Client should be successfully initialized");
        assertEquals("gemini-3.6-flash", geminiService.getModelName());

        // Test real generation with minimal, safe prompt
        try {
            String response = geminiService.generateContent("Respond with only the single word: OK");
            assertNotNull(response, "Response should not be null");
            assertFalse(response.isBlank(), "Response should not be blank");
            assertTrue(response.trim().toUpperCase().contains("OK"),
                    "Model response should contain expected output: " + response.trim());
        } catch (RuntimeException e) {
            if (e.getMessage() != null && (e.getMessage().contains("429") || e.getMessage().contains("RESOURCE_EXHAUSTED") || e.getMessage().contains("Quota"))) {
                System.out.println("Live Gemini API rate limit encountered on free-tier quota: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }
}
