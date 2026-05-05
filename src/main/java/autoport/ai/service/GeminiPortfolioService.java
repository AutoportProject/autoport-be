package autoport.ai.service;

import autoport.common.exception.ApiException;
import autoport.portfolio.dto.AnalysisResultRequest;
import autoport.portfolio.dto.PortfolioGenerateRequest;
import autoport.portfolio.dto.PortfolioGenerateResponse;
import autoport.portfolio.dto.PortfolioProjectResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiPortfolioService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiPortfolioService(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String model,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = new ObjectMapper();
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

            String responseBody = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            String generatedText = extractText(response);
            PortfolioGenerateResponse generated = parseGeneratedPortfolio(generatedText);

            return new PortfolioGenerateResponse(
                    generated.getPortfolioTitle(),
                    generated.getIntroduction(),
                    generated.getProjects(),
                    generated.getTechnicalContributions(),
                    generated.getCollaborationStyle(),
                    generated.getCodeHighlights(),
                    generated.getProjectLinks(),
                    Instant.now().toString());
        } catch (ApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("Gemini API request failed. status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI_001",
                    "Gemini API request failed: " + e.getStatusCode() + " " + abbreviate(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Failed to generate portfolio with Gemini", e);
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI_001",
                    "Failed to generate portfolio: " + e.getClass().getSimpleName() + " " + abbreviate(e.getMessage()));
        }
    }

    private String buildPrompt(PortfolioGenerateRequest request) {
        AnalysisResultRequest analysis = request.getAnalysisResult();

        return """
                You are an expert AI portfolio generator for developer portfolios.
                Write in Korean, with a professional and credible tone.
                Use only the provided repository analysis data. If commit, PR, issue, code, deployment, or period data is missing, write a careful estimate and clearly phrase it as "추정".
                Do not invent exact numbers such as percentages, dates, review counts, response times, or performance improvements unless they are provided.
                Return valid JSON only. Do not wrap it in markdown.

                JSON schema:
                {
                  "portfolioTitle": "string",
                  "introduction": "string",
                  "projects": [
                    {
                      "name": "string",
                      "oneLineDescription": "string",
                      "description": "string",
                      "estimatedPeriod": "string",
                      "role": "string",
                      "techStacks": ["string"],
                      "mainFeatures": ["string"],
                      "highlights": ["string"]
                    }
                  ],
                  "technicalContributions": ["string"],
                  "collaborationStyle": "string",
                  "codeHighlights": ["string"],
                  "projectLinks": ["string"]
                }

                Portfolio template requirements:
                1. One-line title
                - Include the user's name.
                - Describe the developer identity inferred from the repository analysis.

                2. Project detail
                - Include project name, one-line summary, estimated development period, and the user's role.
                - If commit data is not provided, describe the period and contribution as estimated from available repository analysis.

                3. Tech stack
                - Use the provided stack list as the primary source.
                - Do not add unrelated technologies.

                4. Main features
                - Summarize likely user-facing or technical features from the summary and highlights.

                5. Technical contribution and problem solving
                - Turn meaningful changes into a story.
                - Focus on architecture, authentication, API design, deployment, data modeling, reliability, maintainability, or automation when relevant.
                - Avoid fake metrics.

                6. Collaboration style
                - Explain the collaboration style carefully.
                - If PR/review/issue data is missing, say it should be verified with GitHub activity data instead of inventing counts.

                7. Representative code / highlight
                - Explain the core logic or most portfolio-worthy implementation based on the given analysis.

                8. Project links
                - Include known GitHub/deployment links only if provided in the input.
                - If links are missing, return helpful placeholders like "GitHub 링크 입력 필요".

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
                List<String> technicalContributions = objectMapper.convertValue(
                        value.getOrDefault("technicalContributions", List.of()),
                        new TypeReference<>() {
                        });
                String collaborationStyle = String.valueOf(value.getOrDefault("collaborationStyle", ""));
                List<String> codeHighlights = objectMapper.convertValue(
                        value.getOrDefault("codeHighlights", List.of()),
                        new TypeReference<>() {
                        });
                List<String> projectLinks = objectMapper.convertValue(
                        value.getOrDefault("projectLinks", List.of()),
                        new TypeReference<>() {
                        });

                return new PortfolioGenerateResponse(
                        title,
                        introduction,
                        projects,
                        technicalContributions,
                        collaborationStyle,
                        codeHighlights,
                        projectLinks,
                        Instant.now().toString());
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

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300) + "...";
    }
}
