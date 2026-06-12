package autoport.ai.service;

import autoport.common.exception.ApiException;
import autoport.github.dto.AiInputData;
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

    public RepositoryAnalysisSummary summarizeRepository(AiInputData analysis) {
        if (!isConfigured()) {
            log.warn("Gemini repository analysis skipped because GEMINI_API_KEY is blank");
            return null;
        }

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", buildRepositoryAnalysisPrompt(analysis))))),
                    "generationConfig", Map.of(
                            "temperature", 0.4,
                            "responseMimeType", "application/json"));

            String responseBody = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(responseBody);
            String generatedText = extractText(response);
            return parseRepositoryAnalysisSummary(generatedText);
        } catch (RestClientResponseException e) {
            log.warn(
                    "Gemini repository analysis request failed. status={}, body={}",
                    e.getStatusCode(),
                    abbreviate(e.getResponseBodyAsString()),
                    e);
            return null;
        } catch (Exception e) {
            log.warn(
                    "Failed to summarize repository analysis with Gemini. cause={} message={}",
                    e.getClass().getSimpleName(),
                    abbreviate(e.getMessage()),
                    e);
            return null;
        }
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
                    generated.getSummary(),
                    generated.getDescription(),
                    generated.getProjects(),
                    generated.getTechnicalContributions(),
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
                Use only the provided repository analysis data. If code or deployment data is missing, clearly say that the information needs to be added.
                Do not invent exact numbers such as percentages, dates, review counts, response times, or performance improvements unless they are provided.
                Return valid JSON only. Do not wrap it in markdown.
                Never include text outside the JSON object. Never include markdown fences.
                Return empty arrays as [] instead of null.
                Do not use markdown syntax in any string value. Plain text only. Do not use **bold**, *italic*, ## headings, bullet markers, or markdown links inside JSON string values.
                Vary sentence rhythm intentionally. Mix short and long sentences across array items.
                Do not repeat the same ending pattern such as "\uAD6C\uD604\uD588\uC2B5\uB2C8\uB2E4" or "\uC124\uACC4\uD588\uC2B5\uB2C8\uB2E4" three or more times in a row.
                Do not use vague adjectives such as "\uD6A8\uC728\uC801", "\uC548\uC815\uC801", "\uCD5C\uC801\uD654", or "\uACAC\uACE0\uD55C" unless the repository data provides concrete evidence.
                Do not use stiff official-document phrasing such as "\uBCF8", "\uD574\uB2F9", "\uBCF8 \uD504\uB85C\uC81D\uD2B8", or "\uD574\uB2F9 \uD504\uB85C\uC81D\uD2B8".
                Do not use sentence structures like "\uC774 \uD504\uB85C\uC81D\uD2B8\uB294 ~", "\uBCF8 \uD3EC\uD2B8\uD3F4\uB9AC\uC624\uB294 ~", or "\uD574\uB2F9 \uD504\uB85C\uC81D\uD2B8\uB294 ~" at the beginning of project descriptions.
                Do not end project descriptions with "\uB97C \uBAA9\uD45C\uB85C \uD569\uB2C8\uB2E4", "\uC5D0 \uC911\uC810\uC744 \uB450\uC5C8\uC2B5\uB2C8\uB2E4", or "\uC5ED\uB7C9\uC744 \uBCF4\uC5EC\uC90D\uB2C8\uB2E4".
                Do not use the pattern "~\uC744 \uD1B5\uD574 ~\uB97C \uC81C\uACF5\uD569\uB2C8\uB2E4".
                Do not force every array to have the same number of items. Omit weak or repetitive items.
                If User emphasis request is provided, place that topic first in technicalContributions or highlights.
                Attribute work to the user only when it is supported by User-authored commit data, User recent commit messages, User emphasis request, or explicit user-provided bio.
                Repository-wide README, highlights, and recent commit messages describe the project, but they do not prove the user personally implemented every item.
                If User-authored commit data is empty or sparse, avoid claiming ownership of specific features. Use neutral phrasing such as "\uD504\uB85C\uC81D\uD2B8\uC5D0\uC11C \uB2E4\uB8EC \uAD6C\uD604 \uBC94\uC704" or "\uD655\uC778\uB41C \uAE30\uC5EC \uC815\uBCF4\uB294 \uC81C\uD55C\uC801\uC785\uB2C8\uB2E4".
                Keep field responsibilities separate: description explains what the project is, mainFeatures explains what it does for users, technicalContributions explains how it was implemented, and codeHighlights explains why a specific implementation matters.
                If Importance score is 5 or lower, keep the output brief and focus mainly on highlights.
                Write the introduction without a subject or in first person. Avoid third-person expressions like "\uC815\uBBFC\uC11C\uB294", "\uAC1C\uBC1C\uC790\uB294", or "\uC815\uBBFC\uC11C \uAC1C\uBC1C\uC790\uB294".
                Avoid user-facing guide phrases such as "\uD655\uC778\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4", "\uC785\uB825 \uD544\uC694", "\uC815\uBCF4\uAC00 \uD544\uC694\uD569\uB2C8\uB2E4", or "\uC81C\uACF5\uD569\uB2C8\uB2E4".

                JSON schema:
                {
                  "portfolioTitle": "string",
                  "introduction": "string",
                  "summary": "string",
                  "description": "string",
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
                  "codeHighlights": ["string"],
                  "projectLinks": ["string"]
                }

                Portfolio template requirements:
                1. One-line title
                - Write one natural Korean sentence, not a label or fragment.
                - Include the user's name.
                - Do not use a colon after the user's name.
                - Do not use exaggerated words such as "\uC804\uBB38\uAC00", "\uB9C8\uC2A4\uD130", "\uCD5C\uACE0", "\uD0C1\uC6D4\uD55C".
                - Use the headline form "~\uD55C \uAC1C\uBC1C\uC790 {userName}\uC785\uB2C8\uB2E4.".
                - In the "~\uD55C" part, do not simply list technology names. Express the actual work with verbs such as "\uB2E4\uB8EC", "\uB9E1\uC740", "\uC9D1\uC911\uD55C", "\uAC1C\uC120\uD55C", or "\uC124\uACC4\uD55C".
                - Base the "~\uD55C" part only on work that is supported by commit messages, issues, README, user-authored commits, or user-provided emphasis.
                - Prefer role-specific titles such as "\uD504\uB860\uD2B8\uC5D4\uB4DC \uAC1C\uBC1C\uC790", "\uBC31\uC5D4\uB4DC \uAC1C\uBC1C\uC790", "\uD480\uC2A4\uD0DD \uAC1C\uBC1C\uC790", or "AI \uAC1C\uBC1C\uC790" when the repository data supports the role.
                - Do not write awkward fragments like "~\uD55C {userName}." or "{userName}, ~\uD55C \uAC1C\uBC1C\uC790".
                - Do not write technology-only headlines such as "Next.js \uAE30\uBC18 \uC778\uC99D \uC2DC\uC2A4\uD15C\uC744 \uAD6C\uD604\uD55C \uAC1C\uBC1C\uC790 {userName}\uC785\uB2C8\uB2E4.".
                - Do not write vague adjective headlines such as "\uD6A8\uC728\uC801\uC778 \uD504\uB860\uD2B8\uC5D4\uB4DC\uB97C \uAC1C\uBC1C\uD558\uB294 \uAC1C\uBC1C\uC790 {userName}\uC785\uB2C8\uB2E4.".
                - Keep it concise enough for a hero/title area.
                - If repository data is sparse, describe the project experience rather than claiming broad expertise.
                - Good example: "\uC778\uC99D\uACFC WebView \uD658\uACBD\uC744 \uB2E4\uB8EC \uD504\uB860\uD2B8\uC5D4\uB4DC \uAC1C\uBC1C\uC790 \uAE40\uD6A8\uC740\uC785\uB2C8\uB2E4.".
                - Good example: "Next.js\uB85C \uC0AC\uC6A9\uC790 \uC778\uC99D \uD750\uB984\uC744 \uB9E1\uC740 \uD504\uB860\uD2B8\uC5D4\uB4DC \uAC1C\uBC1C\uC790 \uAE40\uD6A8\uC740\uC785\uB2C8\uB2E4.".
                - Bad example: "\uC774\uCC44\uC6D0: \uBA40\uD2F0\uBAA8\uB2EC RAG \uAE30\uBC18 AI \uD29C\uD130\uB9C1 \uC2DC\uC2A4\uD15C \uAC1C\uBC1C \uC804\uBB38\uAC00".
                - Bad example: "\uBD84\uC11D \uBC0F \uC2DC\uAC01\uD654 \uAC1C\uC120\uC744 \uC218\uD589\uD55C \uAE40\uD6A8\uC740.".

                Introduction
                - Explain the user's project experience in no more than 2 natural Korean sentences.
                - Do not start with "\uC800\uB294". Avoid patterns like "\uC800\uB294 ~\uB97C \uC218\uD589\uD588\uC2B5\uB2C8\uB2E4" or "\uC800\uB294 ~\uB97C \uB2F4\uB2F9\uD588\uC2B5\uB2C8\uB2E4".
                - Start without an explicit subject and focus on the actual work performed.
                - Do not start with third-person phrasing such as "\uC815\uBBFC\uC11C\uB294", "\uAC1C\uBC1C\uC790\uB294", or "\uC800\uB294".
                - Do not introduce the user as an expert unless the input data strongly supports it.
                - Base the introduction on what was implemented, improved, designed, or analyzed in the repository.
                - Avoid broad claims that are not supported by the repository data.
                - Put a technology name and concrete work in the same sentence when possible.
                - Good example: "Next.js\uB85C \uC778\uC99D \uD750\uB984\uC744 \uAD6C\uD604\uD558\uACE0, iOS WebView \uD658\uACBD\uC5D0\uC11C\uC758 \uCFE0\uD0A4 \uC138\uC158 \uBCF5\uC6D0\uAE4C\uC9C0 \uCC98\uB9AC\uD588\uC2B5\uB2C8\uB2E4.".
                - Bad example: "\uC800\uB294 Next.js\uB97C \uD65C\uC6A9\uD558\uC5EC \uD504\uB85C\uC81D\uD2B8\uB97C \uC218\uD589\uD588\uC2B5\uB2C8\uB2E4. \uC2E0\uB8B0\uC131\uC744 \uB192\uC600\uC2B5\uB2C8\uB2E4.".

                Summary
                - Create a new top-level summary for portfolio cards and My Page lists.
                - Do not copy introduction exactly.
                - Keep it to one concise Korean sentence under 80 Korean characters when possible.
                - Summarize the strongest project identity or contribution area.

                Description
                - Create a new top-level description for portfolio detail previews.
                - Do not copy introduction exactly.
                - Write 1-2 Korean sentences that explain the overall portfolio theme, representative project, and practical value.
                - Keep it shorter and more scannable than introduction.

                2. Project detail
                - Include project name, one-line summary, development period, and the user's role.
                - The project description must include the project purpose, target users, and core feature flow when the repository data supports them.
                - The project description should answer what the project is, not how it was implemented.
                - Project description must not start with "\uC774 \uD504\uB85C\uC81D\uD2B8\uB294", "\uBCF8 \uD3EC\uD2B8\uD3F4\uB9AC\uC624\uB294", or "\uD574\uB2F9 \uD504\uB85C\uC81D\uD2B8\uB294".
                - Project description must not end with "\uB97C \uBAA9\uD45C\uB85C \uD569\uB2C8\uB2E4", "\uC5D0 \uC911\uC810\uC744 \uB450\uC5C8\uC2B5\uB2C8\uB2E4", or "\uC5ED\uB7C9\uC744 \uBCF4\uC5EC\uC90D\uB2C8\uB2E4".
                - Avoid "\uBCF8 ~" and "\uD574\uB2F9 ~" in all project fields.
                - Avoid the pattern "~\uC744 \uD1B5\uD574 ~\uB97C \uC81C\uACF5\uD569\uB2C8\uB2E4"; write the actual action directly instead.
                - Prefer subjectless or first-person phrasing such as "\uB2F4\uB2F9\uD588\uC2B5\uB2C8\uB2E4" and "\uAD6C\uD604\uD588\uC2B5\uB2C8\uB2E4" when describing the user's work, but do not repeat "\uD588\uC2B5\uB2C8\uB2E4" in every sentence.
                - Put a technology name and a concrete action in the same sentence when possible, such as "Next.js App Router \uAE30\uBC18\uC73C\uB85C \uC778\uC99D \uD750\uB984\uC744 \uAD6C\uD604\uD588\uC2B5\uB2C8\uB2E4".
                - Use Development period from commit analysis as the project's estimatedPeriod when it is provided.
                - Development period is calculated from the entire repository commit history, not from one user's personal commits.
                - If Development period is empty, write "\uAC1C\uBC1C \uAE30\uAC04 \uC815\uBCF4 \uC5C6\uC74C".
                - For role: if the repository appears to be a solo repository, write "\uD480\uC2A4\uD0DD \uAC1C\uBC1C\uC790 (1\uC778 \uAC1C\uBC1C)".
                - For role: if the repository appears collaborative, infer the role primarily from User-authored commit count and User recent commit messages. Use repository-wide commit messages only as project context.
                - For role: if the role cannot be inferred, write "\uC5ED\uD560 \uC815\uBCF4 \uC5C6\uC74C".

                3. Tech stack
                - Use the provided stack list as the primary source.
                - Do not add unrelated technologies.

                4. Main features
                - Summarize likely user-facing or technical features from the summary, README, and highlights.
                - mainFeatures should describe what the project does from a user or service perspective, not implementation details.
                - Order mainFeatures by importance.
                - Omit less important features instead of filling the list evenly.

                5. Technical contribution and problem solving
                - Turn meaningful changes into a story.
                - technicalContributions should describe how the project was implemented or improved.
                - Focus on architecture, authentication, API design, deployment, data modeling, reliability, maintainability, or automation when relevant.
                - Analyze User recent commit messages first and reflect concrete implementation work such as feature additions, bug fixes, refactoring, documentation changes, and rendering fixes.
                - Do not turn repository-wide recent commit messages into the user's personal contribution unless the same work appears in User recent commit messages or user-provided emphasis.
                - Avoid generic contribution items. Prefer details that can be traced to commit messages, README, highlights, or repository facts.
                - Vary item length deliberately. Use one short, direct item and one more detailed item when appropriate.
                - Do not make all technicalContributions the same length.
                - Avoid fake metrics.

                6. Representative code / highlight
                - Explain the core logic or most portfolio-worthy implementation based on the given analysis.
                - Keep explanations concise and focused on why the code matters.
                - codeHighlights should explain why a specific implementation is meaningful, not repeat the project description.
                - Do not leave codeHighlights empty when recent commit messages, README summary, highlights, or project summary contain implementation clues.
                - If actual source code snippets are not provided, infer representative implementation points from recent commit messages and repository facts without pretending that source code was inspected.
                - Good codeHighlights should mention a concrete module, API, data flow, rendering fix, authentication flow, update logic, or analysis pipeline when such evidence exists.

                Highlights
                - Avoid ending every highlight with "\uD588\uC2B5\uB2C8\uB2E4".
                - Mix sentence endings naturally, such as noun phrases, "\uAC1C\uC120", "\uC815\uB9AC", "\uBCF4\uAC15", "\uD574\uACB0", and complete sentences.
                - Keep highlights concise and do not make every item the same length.

                7. Project links
                - If Repo URL is provided, include the raw URL exactly as one projectLinks item.
                - Do not add labels such as "GitHub:" inside projectLinks values.
                - Do not fabricate deployment links.
                - If no link is provided, return an empty projectLinks array. Do not write "\uC785\uB825 \uD544\uC694".

                User name: %s
                User bio: %s
                Requested tone: %s
                User emphasis request: %s
                Template id: %s

                Repository facts from GitHub analysis:
                Repo URL: %s
                Description: %s
                Main language: %s
                README content for AI summary: %s
                Activity summary: %s
                Stars: %s
                Forks: %s
                Open issues: %s
                Commit count: %s
                Importance score: %s
                Repository created at: %s
                Repository updated at: %s
                First commit at: %s
                Latest commit at: %s
                Development period: %s
                Repository-wide recent commit messages: %s
                User GitHub login: %s
                User-authored commit count: %s
                User recent commit messages: %s

                Project name: %s
                Project summary: %s
                Tech stacks: %s
                Highlights: %s
                """.formatted(
                blankToDefault(request.getUserName(), "사용자"),
                blankToEmpty(request.getBio()),
                blankToDefault(request.getTone(), "professional"),
                blankToEmpty(request.getEmphasis()),
                request.getTemplateId(),
                blankToEmpty(analysis.getRepoUrl()),
                blankToEmpty(analysis.getDescription()),
                blankToEmpty(analysis.getMainLanguage()),
                blankToEmpty(analysis.getReadmeSummary()),
                blankToEmpty(analysis.getActivitySummary()),
                numberToText(analysis.getStarCount()),
                numberToText(analysis.getForkCount()),
                numberToText(analysis.getOpenIssuesCount()),
                numberToText(analysis.getCommitCount()),
                numberToText(analysis.getImportanceScore()),
                blankToEmpty(analysis.getRepositoryCreatedAt()),
                blankToEmpty(analysis.getRepositoryUpdatedAt()),
                blankToEmpty(analysis.getFirstCommitAt()),
                blankToEmpty(analysis.getLatestCommitAt()),
                blankToEmpty(analysis.getDevelopmentPeriod()),
                listToText(analysis.getRecentCommitMessages()),
                blankToEmpty(analysis.getContributorLogin()),
                numberToText(analysis.getUserCommitCount()),
                listToText(analysis.getUserRecentCommitMessages()),
                blankToEmpty(analysis.getProjectName()),
                firstNonBlank(analysis.getSummary(), analysis.getReadmeSummary(), analysis.getDescription()),
                listToText(analysis.getStacks()),
                listToText(analysis.getHighlights()));
    }

    private String buildRepositoryAnalysisPrompt(AiInputData analysis) {
        return """
                You are an expert AI assistant that summarizes GitHub repository analysis for a developer portfolio service.
                Write in Korean. Return valid JSON only. Do not wrap it in markdown.
                Never include text outside the JSON object.
                Do not use markdown syntax in string values.
                Do not invent exact metrics, dates, deployment URLs, or performance improvements.
                Use only the provided repository facts.

                JSON schema:
                {
                  "readmeSummary": "string",
                  "activitySummary": "string",
                  "highlights": ["string"]
                }

                Requirements:
                - readmeSummary must be 2 short Korean sentences, 180 Korean characters or fewer in total.
                - readmeSummary must explain what the project is and its main user-facing or API flow.
                - Do not copy README lists, API paths, endpoint names, dependency names, environment setup, Swagger routes, or command snippets.
                - Do not include raw URL paths such as "/api/..." or file names unless they are essential.
                - activitySummary must be one natural Korean sentence, 90 Korean characters or fewer.
                - highlights must contain 3-4 short portfolio-worthy points, each 45 Korean characters or fewer.
                - Keep every field readable in a compact card UI.
                - Prefer plain product/engineering descriptions over exhaustive feature lists.
                - Avoid vague praise such as "\uD6A8\uC728\uC801", "\uC548\uC815\uC801", "\uCD5C\uC801\uD654", or "\uACAC\uACE0\uD55C" unless the data supports it.
                - If README information is sparse, say what is known from repository description, languages, and recent commits.

                Repository facts:
                Project name: %s
                Repo URL: %s
                Description: %s
                Main language: %s
                Tech stacks: %s
                README content: %s
                Activity summary: %s
                Stars: %s
                Forks: %s
                Open issues: %s
                Commit count: %s
                Importance score: %s
                Repository created at: %s
                Repository updated at: %s
                First commit at: %s
                Latest commit at: %s
                Development period: %s
                Recent commit messages: %s
                Current highlights: %s
                """.formatted(
                analysis.getProjectName(),
                blankToEmpty(analysis.getRepoUrl()),
                blankToEmpty(analysis.getDescription()),
                blankToEmpty(analysis.getMainLanguage()),
                analysis.getStacks(),
                blankToEmpty(analysis.getReadmeSummary()),
                blankToEmpty(analysis.getActivitySummary()),
                numberToText(analysis.getStarCount()),
                numberToText(analysis.getForkCount()),
                numberToText(analysis.getOpenIssuesCount()),
                numberToText(analysis.getCommitCount()),
                numberToText(analysis.getImportanceScore()),
                blankToEmpty(analysis.getRepositoryCreatedAt()),
                blankToEmpty(analysis.getRepositoryUpdatedAt()),
                blankToEmpty(analysis.getFirstCommitAt()),
                blankToEmpty(analysis.getLatestCommitAt()),
                blankToEmpty(analysis.getDevelopmentPeriod()),
                listToText(analysis.getRecentCommitMessages()),
                listToText(analysis.getHighlights()));
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
                String summary = String.valueOf(value.getOrDefault("summary", ""));
                String description = String.valueOf(value.getOrDefault("description", ""));
                List<PortfolioProjectResponse> projects = objectMapper.convertValue(
                        value.getOrDefault("projects", List.of()),
                        new TypeReference<>() {
                        });
                List<String> technicalContributions = objectMapper.convertValue(
                        value.getOrDefault("technicalContributions", List.of()),
                        new TypeReference<>() {
                        });
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
                        summary,
                        description,
                        projects,
                        technicalContributions,
                        codeHighlights,
                        projectLinks,
                        Instant.now().toString());
            } catch (Exception ignored) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "Failed to parse Gemini response");
            }
        }
    }

    private RepositoryAnalysisSummary parseRepositoryAnalysisSummary(String generatedText) {
        try {
            return objectMapper.readValue(generatedText, RepositoryAnalysisSummary.class);
        } catch (Exception e) {
            try {
                Map<String, Object> value = objectMapper.readValue(generatedText, new TypeReference<>() {
                });
                List<String> highlights = objectMapper.convertValue(
                        value.getOrDefault("highlights", List.of()),
                        new TypeReference<>() {
                        });

                return new RepositoryAnalysisSummary(
                        String.valueOf(value.getOrDefault("readmeSummary", "")),
                        String.valueOf(value.getOrDefault("activitySummary", "")),
                        highlights);
            } catch (Exception ignored) {
                return null;
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String numberToText(Integer value) {
        return value == null ? "" : value.toString();
    }

    private String listToText(List<String> values) {
        return values == null ? "" : values.toString();
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

    public record RepositoryAnalysisSummary(
            String readmeSummary,
            String activitySummary,
            List<String> highlights) {
    }
}
