package com.gharfix.service;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Dedicated service for interacting with Google Gemini AI models
 * via the official Google GenAI Java SDK (com.google.genai.Client).
 */
@Service
public class GeminiService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    public static final String DEFAULT_MODEL = "gemini-3.6-flash";

    @Value("${gemini.api-key:${GEMINI_API_KEY:}}")
    private String configuredApiKey;

    @Value("${gemini.model:${GEMINI_MODEL:gemini-3.6-flash}}")
    private String modelName = DEFAULT_MODEL;

    private Client client;
    private boolean ignoreSystemEnv = false;

    public GeminiService() {
    }

    /**
     * Testing / programmatic constructor with pre-configured Client.
     */
    public GeminiService(Client client, String modelName) {
        this.client = client;
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_MODEL;
        this.ignoreSystemEnv = true;
    }

    /**
     * Testing / programmatic constructor with explicit API key and model name.
     * When using this constructor, system environment resolution is skipped to isolate unit tests.
     */
    public GeminiService(String apiKey, String modelName) {
        this.configuredApiKey = apiKey;
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_MODEL;
        this.ignoreSystemEnv = true;
    }

    @PostConstruct
    public void init() {
        String apiKey = resolveApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                this.client = Client.builder().apiKey(apiKey).build();
                log.info("GeminiService initialized successfully with model '{}'", getModelName());
            } catch (Exception e) {
                log.error("Failed to initialize Google GenAI Client: {}", e.getMessage());
            }
        } else {
            log.warn("GEMINI_API_KEY environment variable is not configured. GeminiService will report an error if invoked.");
        }
    }

    /**
     * Resolves the API key with priority:
     * 1. Direct environment variable (System.getenv("GEMINI_API_KEY"))
     * 2. Spring injected property (${gemini.api-key} or ${GEMINI_API_KEY})
     * 3. Java system property (System.getProperty("GEMINI_API_KEY"), e.g. from .env file)
     *
     * Note: The key value is never logged or exposed.
     */
    private String resolveApiKey() {
        if (this.configuredApiKey != null && !this.configuredApiKey.isBlank()) {
            return this.configuredApiKey.trim();
        }
        if (this.ignoreSystemEnv) {
            return null;
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        String propKey = System.getProperty("GEMINI_API_KEY");
        if (propKey != null && !propKey.isBlank()) {
            return propKey.trim();
        }
        return null;
    }

    /**
     * Checks if Gemini AI is properly configured with an API key.
     *
     * @return true if an API key is available or client is already initialized, false otherwise
     */
    public boolean isConfigured() {
        if (this.client != null) {
            return true;
        }
        String key = resolveApiKey();
        return key != null && !key.isBlank();
    }

    /**
     * Retrieves the Google GenAI Client instance.
     * Lazily initializes the client if not yet created.
     *
     * @return active Client instance
     * @throws IllegalStateException if GEMINI_API_KEY is not configured
     */
    public synchronized Client getClient() {
        if (this.client == null) {
            String apiKey = resolveApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                    "GEMINI_API_KEY environment variable is not configured. " +
                    "Please set the GEMINI_API_KEY environment variable or specify it in your .env file."
                );
            }
            this.client = Client.builder().apiKey(apiKey).build();
            log.info("GeminiService initialized Google GenAI Client successfully with model '{}'", getModelName());
        }
        return this.client;
    }

    /**
     * Gets the configured Gemini model name.
     */
    public String getModelName() {
        return (modelName != null && !modelName.isBlank()) ? modelName.trim() : DEFAULT_MODEL;
    }

    /**
     * Generates a text response for the given prompt using the configured Gemini model.
     *
     * @param prompt The input prompt text
     * @return The generated text content from the Gemini model
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws IllegalStateException if GEMINI_API_KEY is not configured
     */
    public String generateContent(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty.");
        }

        try {
            GenerateContentResponse response = getClient().models.generateContent(
                getModelName(),
                prompt,
                null
            );

            if (response == null || response.text() == null) {
                return "";
            }
            return response.text();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate content with Gemini model '{}': {}", getModelName(), e.getMessage());
            throw new RuntimeException("Gemini AI generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a text response with a system instruction to set the persona or context.
     *
     * @param prompt The input prompt text
     * @param systemInstruction System instruction defining the AI's role/behavior
     * @return The generated text content from the Gemini model
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws IllegalStateException if GEMINI_API_KEY is not configured
     */
    public String generateContent(String prompt, String systemInstruction) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty.");
        }

        GenerateContentConfig config = null;
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                .build();
        }

        try {
            GenerateContentResponse response = getClient().models.generateContent(
                getModelName(),
                prompt,
                config
            );

            if (response == null || response.text() == null) {
                return "";
            }
            return response.text();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate content with Gemini model '{}': {}", getModelName(), e.getMessage());
            throw new RuntimeException("Gemini AI generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates content using a custom GenerateContentConfig for advanced use-cases
     * (e.g., custom temperature, safety settings, structured schema output).
     *
     * @param prompt The input prompt text
     * @param config The custom GenerateContentConfig
     * @return The full GenerateContentResponse
     * @throws IllegalArgumentException if prompt is null or empty
     * @throws IllegalStateException if GEMINI_API_KEY is not configured
     */
    public GenerateContentResponse generateContent(String prompt, GenerateContentConfig config) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty.");
        }

        try {
            return getClient().models.generateContent(getModelName(), prompt, config);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate content with Gemini model '{}': {}", getModelName(), e.getMessage());
            throw new RuntimeException("Gemini AI generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Multimodal generation using text prompt and raw image bytes.
     *
     * @param imageBytes Binary content of the image
     * @param mimeType MIME type of the image (e.g. image/jpeg, image/png, image/webp)
     * @param prompt User prompt or query
     * @param systemInstruction System instruction defining persona/constraints
     * @return Generated text response from Gemini
     */
    public String generateContentWithImage(byte[] imageBytes, String mimeType, String prompt, String systemInstruction) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Image bytes cannot be null or empty.");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("MIME type cannot be null or empty.");
        }

        String promptText = (prompt != null && !prompt.isBlank()) ? prompt : "Analyze this household maintenance issue.";

        Part imagePart = Part.fromBytes(imageBytes, mimeType);
        Part textPart = Part.fromText(promptText);
        Content userContent = Content.fromParts(imagePart, textPart);

        GenerateContentConfig config = null;
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                .build();
        }

        try {
            GenerateContentResponse response = getClient().models.generateContent(
                getModelName(),
                userContent,
                config
            );

            if (response == null || response.text() == null) {
                return "";
            }
            return response.text();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed multimodal generation with Gemini model '{}': {}", getModelName(), e.getMessage());
            throw new RuntimeException("Gemini multimodal generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (this.client != null) {
            try {
                this.client.close();
                log.info("GeminiService closed Google GenAI Client successfully.");
            } catch (Exception e) {
                log.warn("Error closing Google GenAI Client: {}", e.getMessage());
            }
        }
    }
}
