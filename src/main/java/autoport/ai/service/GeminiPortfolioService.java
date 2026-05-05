package autoport.ai.service;

import autoport.common.exception.ApiException;
import autoport.portfolio.dto.AnalysisResultRequest;
import autoport.portfolio.dto.PortfolioGenerateRequest;
import autoport.portfolio.dto.PortfolioGenerateResponse;
import autoport.portfolio.dto.PortfolioProjectResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class GeminiPortfolioService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiPortfolioService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String model,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public PortfolioGenerateResponse generate(PortfolioGenerateRequest request) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "Gemini API key is not configured");
        }

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", buildPrompt(request))))),
                    "generationConfig", Map.of(
                            "temperature", 0.6,
                            "responseMimeType", "application/json"));

            JsonNode response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String generatedText = extractText(response);
            PortfolioGenerateResponse generated = parseGeneratedPortfolio(generatedText);

            return new PortfolioGenerateResponse(
                    generated.getPortfolioTitle(),
                    generated.getIntroduction(),
                    generated.getProjects(),
                    Instant.now().toString());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "Failed to generate portfolio");
        }
    }

    private String buildPrompt(PortfolioGenerateRequest request) {
        AnalysisResultRequest analysis = request.getAnalysisResult();

        return """
                You are an expert technical portfolio writer for developer portfolios.
                Write in Korean, with a professional and concise tone.
                Return valid JSON only. Do not wrap it in markdown.

                JSON schema:
                {
                  "portfolioTitle": "string",
                  "introduction": "string",
                  "projects": [
                    {
                      "name": "string",
                      "description": "string",
                      "techStacks": ["string"],
                      "highlights": ["string"]
                    }
                  ]
                }

                User name: %s
                User bio: %s
                Requested tone: %s
                Template id: %s

                Project name: %s
                Project summary: %s
                Tech stacks: %s
                Highlights: %s
                """.formatted(
                request.getUserName(),
                blankToEmpty(request.getBio()),
                blankToDefault(request.getTone(), "professional"),
                request.getTemplateId(),
                analysis.getProjectName(),
                analysis.getSummary(),
                analysis.getStacks(),
                analysis.getHighlights());
    }

    private String extractText(JsonNode response) {
        JsonNode textNode = response
                .path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text");

        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "Gemini returned an empty response");
        }

        return stripMarkdownFence(textNode.asText());
    }

    private PortfolioGenerateResponse parseGeneratedPortfolio(String generatedText) {
        try {
            return objectMapper.readValue(generatedText, PortfolioGenerateResponse.class);
        } catch (Exception e) {
            try {
                Map<String, Object> value = objectMapper.readValue(generatedText, new TypeReference<>() {
                });

                String title = String.valueOf(value.getOrDefault("portfolioTitle", "Developer Portfolio"));
                String introduction = String.valueOf(value.getOrDefault("introduction", ""));
                List<PortfolioProjectResponse> projects = objectMapper.convertValue(
                        value.getOrDefault("projects", List.of()),
                        new TypeReference<>() {
                        });

                return new PortfolioGenerateResponse(title, introduction, projects, Instant.now().toString());
            } catch (Exception ignored) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "Failed to parse Gemini response");
            }
        }
    }

    private String stripMarkdownFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            return trimmed.substring(7, trimmed.length() - 3).trim();
        }
        if (trimmed.startsWith("```")) {
            return trimmed.substring(3, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
