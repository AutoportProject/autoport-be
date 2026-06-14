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
                    clearProjectHighlights(generated.getProjects()),
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

    private List<PortfolioProjectResponse> clearProjectHighlights(List<PortfolioProjectResponse> projects) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }

        return projects.stream()
                .map(project -> new PortfolioProjectResponse(
                        project.getName(),
                        project.getOneLineDescription(),
                        project.getDescription(),
                        project.getEstimatedPeriod(),
                        project.getRole(),
                        project.getTechStacks(),
                        project.getMainFeatures(),
                        List.of()))
                .toList();
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
                Do not use markdown syntax in any string value. Plain text only.
                Vary sentence rhythm intentionally. Mix short and long sentences across array items.
                Do not repeat the same ending pattern such as "구현했습니다" or "설계했습니다" three or more times in a row.
                Do not use vague adjectives such as "효율적", "안정적", "최적화", or "견고한" unless the repository data provides concrete evidence.
                Do not use stiff official-document phrasing such as "본", "해당", "본 프로젝트", or "해당 프로젝트".
                Do not use sentence structures like "이 프로젝트는 ~", "본 포트폴리오는 ~", or "해당 프로젝트는 ~" at the beginning of project descriptions.
                Do not end project descriptions with "를 목표로 합니다", "에 중점을 두었습니다", or "역량을 보여줍니다".
                Do not use the pattern "~을 통해 ~를 제공합니다".
                Do not force every array to have the same number of items. Omit weak or repetitive items.
                If User emphasis request is provided, place that topic first in technicalContributions.
                Do not copy or paraphrase README content directly into description or summary. Rewrite based on what was actually implemented.
                Do not write vague phrases like "핵심 기능을 구현했습니다" or "주요 기능을 개발했습니다". Name the actual features.
                Do not state obvious consequences of using a technology, such as "TypeScript로 타입 안정성을 확보". Focus on what was actually built, not what the tool provides by default.
                When generating portfolio content, emphasize these service values only when supported by repository data:
                - Automated technical narrative: turn README, commits, and activity data into a coherent story of technical work.
                - Evidence-based portfolio writing: use commit count, development period, activity summary, README summary, and user-authored commits as supporting evidence.
                - Curated implementation evidence: place meaningful implementation decisions from README and commit messages in technicalContributions, and reserve codeHighlights for quantitative evidence.
                Do not describe these as product features of Autoport unless the portfolio project itself is Autoport.
                
                Attribute work to the user only when it is supported by User-authored commit data, User recent commit messages, User emphasis request, or explicit user-provided bio.
                Repository-wide README, highlights, and recent commit messages describe the project, but they do not prove the user personally implemented every item.
                If User-authored commit data is empty or sparse, avoid claiming ownership of specific features. Use neutral phrasing such as "프로젝트에서 다룬 구현 범위" or "확인된 기여 정보는 제한적입니다".
                
                Keep field responsibilities separate:
                description explains what the project is.
                mainFeatures explains only what users can do. It must not include implementation methods, technology names, or architecture details.
                technicalContributions explains how it was implemented and what technical decisions were made. It must not repeat mainFeatures or codeHighlights.
                codeHighlights contains only quantitative evidence such as commit counts, PR flow, and activity metrics. It must not repeat technicalContributions.
                projects[].highlights must always be []. Move any meaningful highlight content to technicalContributions.
                
                If Importance score is 5 or lower, keep the output brief and include only strongly supported technicalContributions and codeHighlights.
                Write the introduction without a subject. Avoid third-person expressions like "정민서는", "개발자는", or "정민서 개발자는".
                Avoid user-facing guide phrases such as "확인할 수 있습니다", "입력 필요", "정보가 필요합니다", or "제공합니다".
                
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
                      "highlights": []
                    }
                  ],
                  "technicalContributions": ["string"],
                  "codeHighlights": ["string"],
                  "projectLinks": ["string"]
                }
                
                Portfolio template requirements:

                Portfolio output must be organized around these sections:
                1. Portfolio title and summary
                - portfolioTitle is the main portfolio headline.
                - introduction is the short portfolio introduction.
                - summary is the one-line summary for portfolio cards and lists.

                2. Project overview
                - Each project must include project name, development period, role, GitHub/deployment links when provided, and tech stacks.
                - Use firstCommitAt/latestCommitAt or Development period for the development period.
                - Role must distinguish solo project, collaborative project, and inferred responsibility when data supports it.

                3. Project description
                - Explain what was built.
                - Explain what problem the project addresses when repository data supports it.
                - Include the main user flow or core feature flow.
                - description must not repeat introduction.
                - Compress the representative project or core implementation area into one concise sentence.
                - Do not use unsupported result claims such as "사용자 편의를 향상했습니다" or "전체 흐름을 다루었습니다".

                4. Main features
                - mainFeatures must describe only what users can do.
                - Do not include implementation methods, technology names, libraries, frameworks, or architecture details.
                - Prefer 2-4 meaningful features over a long exhaustive list.

                5. Technical contributions
                - technicalContributions must explain architecture, problem solving, performance, UX, maintainability, or implementation decisions.
                - Use User-authored commits first when describing personal contribution.
                - Each item must cover a different implementation aspect. Do not repeat the same feature using different wording.
                - Omit items that are only as specific as "~기능을 구현했습니다". Include an item only when a concrete technical decision or problem-solving detail is supported.
                - End every item as a concise Korean noun phrase, such as "인증 토큰 갱신 흐름 설계" or "예외 응답 구조 통합".
                - Do not repeat content already present in mainFeatures or codeHighlights.

                6. Code and commit-based evidence
                - codeHighlights must contain only quantitative evidence from commit count, PR flow, and activity metrics.
                - Do not include implementation explanations, technology descriptions, or content already present in technicalContributions.
                - If quantitative evidence is unavailable, return an empty codeHighlights array.
                
                1. One-line title
                - Write one natural Korean sentence, not a label or fragment.
                - Include the user's name.
                - Do not use a colon after the user's name.
                - Do not use exaggerated words such as "전문가", "마스터", "최고", "탁월한".
                - Use the headline form "~한 개발자 {userName}입니다.".
                - In the "~한" part, do not simply list technology names.
                - Express the actual work with verbs such as "다룬", "맡은", "집중한", "개선한", or "설계한".
                - Base the "~한" part only on work that is supported by commit messages, issues, README, user-authored commits, or user-provided emphasis.
                - Prefer role-specific titles such as "프론트엔드 개발자", "백엔드 개발자", "풀스택 개발자", or "AI 개발자" when the repository data supports the role.
                - Do not write awkward fragments like "~한 {userName}." or "{userName}, ~한 개발자".
                - Do not use "개발한 개발자" in the title. The verb before "개발자" must not be "개발한". Prefer verbs such as "다룬", "맡은", "집중한", or "설계한".
                - Do not write technology-only headlines such as "Next.js 기반 인증 시스템을 구현한 개발자 {userName}입니다.".
                - Do not write vague adjective headlines such as "효율적인 프론트엔드를 개발하는 개발자 {userName}입니다.".
                - Keep the title under 30 Korean characters when possible.
                - Bad example: "Next.js 기반 개인 포트폴리오 서비스 프론트엔드를 개발한 개발자 민서입니다."
                - Good example: "인증과 WebView 환경을 다룬 프론트엔드 개발자 김효은입니다."
                - Good example: "Next.js로 사용자 인증 흐름을 맡은 프론트엔드 개발자 김효은입니다."
                
                Introduction
                - Explain the user's project experience in no more than 2 natural Korean sentences.
                - Do not start with "저는".
                - Avoid patterns like "저는 ~를 수행했습니다" or "저는 ~를 담당했습니다".
                - Start without an explicit subject and focus on the actual work performed.
                - Do not start with third-person phrasing such as "정민서는", "개발자는", or "저는".
                - Do not introduce the user as an expert unless the input data strongly supports it.
                - Base the introduction on what was implemented, improved, designed, or analyzed in the repository.
                - Avoid broad claims that are not supported by the repository data.
                - Put a technology name and concrete work in the same sentence when possible.
                - Good example: "Next.js로 인증 흐름을 구현하고, iOS WebView 환경에서의 쿠키 세션 복원까지 처리했습니다."
                
                Summary
                - Create a new top-level summary for portfolio cards and My Page lists.
                - Do not copy introduction exactly.
                - Do not copy or paraphrase README content directly. Rewrite based on what was actually implemented.
                - Keep it to one concise Korean sentence under 80 Korean characters when possible.
                - Summarize the strongest project identity or contribution area.
                
                Description
                - Create a new top-level description for portfolio detail previews.
                - Do not copy introduction exactly.
                - Do not repeat the same content or sentence structure used in introduction.
                - Do not copy or paraphrase README content directly. Rewrite based on what was actually implemented.
                - Write one concise Korean sentence that compresses the representative project or core implementation area.
                - Keep it shorter and more scannable than introduction.
                - Do not write vague phrases like "핵심 기능을 구현했습니다". Name the actual features.
                - Do not use unsupported result claims such as "사용자 편의를 향상했습니다" or "전체 흐름을 다루었습니다".
                
                2. Project detail
                - Include project name, one-line summary, development period, and the user's role.
                - The project description must include the project purpose, target users, and core feature flow when the repository data supports them.
                - The project description should answer what the project is, not how it was implemented.
                - Project description must not start with "이 프로젝트는", "본 포트폴리오는", or "해당 프로젝트는".
                - Project description must not end with "를 목표로 합니다", "에 중점을 두었습니다", or "역량을 보여줍니다".
                - Avoid "본 ~" and "해당 ~" in all project fields.
                - Avoid the pattern "~을 통해 ~를 제공합니다"; write the actual action directly instead.
                - Put a technology name and a concrete action in the same sentence when possible.
                - Use Development period from commit analysis as the project's estimatedPeriod when it is provided.
                - Development period is calculated from the entire repository commit history, not from one user's personal commits.
                - If Development period is empty, write "개발 기간 정보 없음".
                - For role: if the repository appears to be a solo repository, write "풀스택 개발자 (1인 개발)".
                - For role: if collaborative, infer the role primarily from User-authored commit count and User recent commit messages.
                - For role: if the role cannot be inferred, write "역할 정보 없음".
                
                3. Tech stack
                - Use the provided stack list as the primary source.
                - Do not add unrelated technologies.
                
                4. Main features
                - Summarize only user-facing capabilities from the summary and README.
                - mainFeatures should describe what users can do, not how the project was implemented.
                - Do not include technology names, libraries, frameworks, architecture, data models, or implementation details.
                - Order mainFeatures by importance.
                - Omit less important features instead of filling the list evenly.
                
                5. Technical contribution and problem solving
                - Turn meaningful changes into a story.
                - technicalContributions should read like a technical narrative, not a task list.
                - technicalContributions should describe how the project was implemented or improved.
                - Connect the problem, implementation choice, and resulting value when the repository data supports it.
                - Focus on architecture, authentication, API design, deployment, data modeling, reliability, maintainability, or automation when relevant.
                - Analyze User recent commit messages first.
                - Do not turn repository-wide recent commit messages into the user's personal contribution unless the same work appears in User recent commit messages or user-provided emphasis.
                - Each item must cover a different implementation aspect. Do not repeat the same feature in different wording.
                - Omit generic items at the level of "~기능을 구현했습니다" unless a concrete technical decision or problem-solving detail is provided.
                - Do not repeat information already used in mainFeatures or codeHighlights.
                - End every technicalContributions item as a concise Korean noun phrase, not a full sentence ending in "했습니다".
                - Avoid fake metrics.
                
                6. Representative code / highlight
                - codeHighlights must contain only quantitative repository evidence.
                - Use commit count, user-authored commit count, PR flow, and activity metrics only when they are provided.
                - Do not include technical implementation explanations or restate technicalContributions.
                - Do not invent PR counts, review counts, percentages, or activity metrics.
                - Return [] when no meaningful quantitative evidence is available.
                
                Highlights
                - projects[].highlights must always be [].
                - Move meaningful implementation content that would otherwise appear in highlights to technicalContributions.
                
                7. Project links
                - If Repo URL is provided, include the raw URL exactly as one projectLinks item.
                - Do not add labels such as "GitHub:" inside projectLinks values.
                - Do not fabricate deployment links.
                - If no link is provided, return an empty projectLinks array. Do not write "입력 필요".
                
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
                PR review count: %s
                
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
                numberToText(analysis.getPrReviewCount()),
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
                - readmeSummary must not start with "\uC800\uC7A5\uC18C \uBD84\uC11D \uACB0\uACFC" or "\uC774 \uD504\uB85C\uC81D\uD2B8\uB294".
                - Do not copy or directly paraphrase README sentences. Rewrite based on the repository facts and implementation context.
                - Do not copy README lists, API paths, endpoint names, dependency names, environment setup, Swagger routes, or command snippets.
                - Do not include raw URL paths such as "/api/..." or file names unless they are essential.
                - activitySummary must be one natural Korean sentence, 90 Korean characters or fewer.
                - activitySummary must not use subjective evaluation words such as "\uD65C\uBC1C\uD55C" or "\uAFB8\uC900\uD55C".
                - highlights must contain 3-4 short portfolio-worthy points, each 45 Korean characters or fewer.
                - Write all highlights in a consistent summary style as concise noun phrases.
                - Do not write highlights as full sentences ending with "\uD588\uC2B5\uB2C8\uB2E4", "\uD569\uB2C8\uB2E4", or "\uC785\uB2C8\uB2E4".
                - Good highlight examples: "\uC778\uC99D \uD750\uB984 \uAD6C\uD604", "WebView \uC138\uC158 \uBCF5\uC6D0 \uCC98\uB9AC", "API \uC751\uB2F5 \uAD6C\uC870 \uC815\uB9AC".
                - highlights must not be simple tool lists or obvious tool consequences such as "TypeScript\uB85C \uD0C0\uC785 \uC548\uC815\uC131\uC744 \uD655\uBCF4".
                - Keep every field readable in a compact card UI.
                - Prefer plain product/engineering descriptions over exhaustive feature lists.
                - Avoid vague praise such as "\uD6A8\uC728\uC801", "\uC548\uC815\uC801", "\uCD5C\uC801\uD654", or "\uACAC\uACE0\uD55C" unless the data supports it.
                - If README information is sparse, say what is known from repository description, languages, and recent commits.
                - If repository data is insufficient, state it once in readmeSummary only.
                - Do not repeat "\uC815\uBCF4\uAC00 \uBD80\uC871\uD569\uB2C8\uB2E4" or "\uC81C\uD55C\uC801\uC785\uB2C8\uB2E4" across multiple fields.
                - Leave highlights as [] if there is not enough data to infer meaningful points.

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
                PR review count: %s
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
                numberToText(analysis.getPrReviewCount()),
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

