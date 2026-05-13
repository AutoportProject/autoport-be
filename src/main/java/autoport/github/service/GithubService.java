package autoport.github.service;

import autoport.common.exception.ApiException;
import autoport.config.UserPrincipal;
import autoport.github.dto.AiInputData;
import autoport.github.dto.GithubAnalyzeRequest;
import autoport.github.dto.GithubAnalyzeResponse;
import autoport.github.dto.GithubRepoListResponse;
import autoport.github.dto.GithubRepoResponse;
import autoport.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class GithubService {

    private static final String GITHUB_API_BASE_URL = "https://api.github.com";

    private final RestClient restClient = RestClient.builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GithubRepoListResponse getRepos(String sort, String direction, Integer page, Integer perPage) {
        User user = getCurrentUser();
        validateGithubAuthorization(user);

        int currentPage = (page == null || page < 1) ? 1 : page;
        int currentPerPage = (perPage == null || perPage < 1) ? 30 : perPage;
        String sortValue = normalizeRepoSort(sort);
        String directionValue = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";

        GithubPage<List<GithubRepoResponse>> repos = fetchRepos(
                user.getGithubAccessToken(),
                sortValue,
                directionValue,
                currentPage,
                currentPerPage);

        int totalElements = repos.totalElements();
        int totalPages = totalElements == 0
                ? (repos.content().isEmpty() ? 0 : currentPage)
                : (int) Math.ceil((double) totalElements / currentPerPage);

        return new GithubRepoListResponse(
                repos.content(),
                currentPage,
                currentPerPage,
                totalElements,
                totalPages);
    }

    public GithubAnalyzeResponse analyzeRepo(GithubAnalyzeRequest request) {
        User user = getCurrentUser();
        validateGithubAuthorization(user);

        Long repoId = request.getRepoId();
        String repoName = request.getRepoName();
        String owner = request.getOwner();

        JsonNode repo = fetchRepo(user.getGithubAccessToken(), owner, repoName);
        if (!repo.path("id").isMissingNode() && repo.path("id").asLong() != repoId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GITHUB_002", "Repository not found or access denied");
        }

        List<String> languages = fetchLanguages(user.getGithubAccessToken(), owner, repoName);
        String readme = fetchReadme(user.getGithubAccessToken(), owner, repoName);
        GithubPage<Integer> commits = fetchCommitCount(user.getGithubAccessToken(), owner, repoName);

        String description = repo.path("description").asText("");
        String mainLanguage = repo.path("language").asText(null);
        int starCount = repo.path("stargazers_count").asInt(0);
        int forkCount = repo.path("forks_count").asInt(0);
        int openIssuesCount = repo.path("open_issues_count").asInt(0);
        int commitCount = commits.totalElements() > 0 ? commits.totalElements() : commits.content();
        int importanceScore = calculateImportanceScore(starCount, forkCount, commitCount, languages.size(), hasText(readme));

        List<String> highlights = buildHighlights(repo, languages, commitCount, readme);
        String readmeSummary = summarizeReadme(readme, description);
        String activitySummary = buildActivitySummary(commitCount, starCount, forkCount, openIssuesCount);

        AiInputData aiInputData = new AiInputData(
                repoName,
                buildAiSummary(repoName, description, readmeSummary, activitySummary),
                languages.isEmpty() ? List.of(defaultString(mainLanguage, "Unknown")) : languages,
                highlights);

        return new GithubAnalyzeResponse(
                repoId,
                repoName,
                description,
                mainLanguage,
                languages,
                readmeSummary,
                activitySummary,
                starCount,
                commitCount,
                importanceScore,
                aiInputData,
                java.time.Instant.now().toString());
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_005", "Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return userPrincipal.getUser();
    }

    private void validateGithubAuthorization(User user) {
        if (user.getGithubAccessToken() == null || user.getGithubAccessToken().isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GITHUB_001", "GitHub access token is invalid");
        }
    }

    private GithubPage<List<GithubRepoResponse>> fetchRepos(
            String accessToken,
            String sort,
            String direction,
            int page,
            int perPage) {
        try {
            org.springframework.http.ResponseEntity<String> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("affiliation", "owner,collaborator,organization_member")
                            .queryParam("visibility", "all")
                            .queryParam("sort", sort)
                            .queryParam("direction", direction)
                            .queryParam("page", page)
                            .queryParam("per_page", perPage)
                            .build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toEntity(String.class);

            JsonNode repos = objectMapper.readTree(response.getBody());
            List<GithubRepoResponse> content = new ArrayList<>();
            for (JsonNode repo : repos) {
                content.add(toRepoResponse(repo));
            }

            return new GithubPage<>(content, parseTotalElements(response.getHeaders().getFirst("Link"), page, perPage, content.size()));
        } catch (RestClientResponseException e) {
            throw githubApiException(e, "Failed to fetch GitHub repositories");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", "Failed to call GitHub API");
        }
    }

    private JsonNode fetchRepo(String accessToken, String owner, String repoName) {
        try {
            String body = restClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repoName)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);

            return objectMapper.readTree(body);
        } catch (RestClientResponseException e) {
            throw githubApiException(e, "Repository not found or access denied");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", "Failed to call GitHub API");
        }
    }

    private List<String> fetchLanguages(String accessToken, String owner, String repoName) {
        try {
            String body = restClient.get()
                    .uri("/repos/{owner}/{repo}/languages", owner, repoName)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);

            JsonNode languages = objectMapper.readTree(body);
            Iterator<Map.Entry<String, JsonNode>> fields = languages.fields();
            Iterable<Map.Entry<String, JsonNode>> iterable = () -> fields;
            return StreamSupport.stream(iterable.spliterator(), false)
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (RestClientResponseException e) {
            throw githubApiException(e, "Failed to fetch repository languages");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", "Failed to call GitHub API");
        }
    }

    private String fetchReadme(String accessToken, String owner, String repoName) {
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}/readme", owner, repoName)
                    .accept(MediaType.valueOf("application/vnd.github.raw"))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            return "";
        } catch (RestClientResponseException e) {
            throw githubApiException(e, "Failed to fetch repository README");
        }
    }

    private GithubPage<Integer> fetchCommitCount(String accessToken, String owner, String repoName) {
        try {
            org.springframework.http.ResponseEntity<String> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/commits")
                            .queryParam("per_page", 1)
                            .queryParam("page", 1)
                            .build(owner, repoName))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toEntity(String.class);

            JsonNode commits = objectMapper.readTree(response.getBody());
            int currentPageCount = commits.isArray() ? commits.size() : 0;
            return new GithubPage<>(currentPageCount, parseTotalElements(response.getHeaders().getFirst("Link"), 1, 1, currentPageCount));
        } catch (HttpClientErrorException.Conflict e) {
            return new GithubPage<>(0, 0);
        } catch (RestClientResponseException e) {
            throw githubApiException(e, "Failed to fetch repository commits");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", "Failed to call GitHub API");
        }
    }

    private GithubRepoResponse toRepoResponse(JsonNode repo) {
        return new GithubRepoResponse(
                repo.path("id").asLong(),
                repo.path("name").asText(null),
                repo.path("full_name").asText(null),
                repo.path("html_url").asText(null),
                repo.path("description").asText(null),
                repo.path("language").asText(null),
                repo.path("stargazers_count").asInt(0),
                repo.path("forks_count").asInt(0),
                repo.path("updated_at").asText(null),
                repo.path("private").asBoolean(false));
    }

    private String normalizeRepoSort(String sort) {
        if ("created".equalsIgnoreCase(sort) || "pushed".equalsIgnoreCase(sort)
                || "updated".equalsIgnoreCase(sort) || "full_name".equalsIgnoreCase(sort)) {
            return sort.toLowerCase();
        }
        return "updated";
    }

    private int parseTotalElements(String linkHeader, int page, int perPage, int currentPageCount) {
        Integer lastPage = parseLastPage(linkHeader);
        if (lastPage != null) {
            return Math.max((lastPage - 1) * perPage + currentPageCount, currentPageCount);
        }
        if (page <= 1) {
            return currentPageCount;
        }
        return (page - 1) * perPage + currentPageCount;
    }

    private Integer parseLastPage(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return null;
        }

        for (String link : linkHeader.split(",")) {
            if (!link.contains("rel=\"last\"")) {
                continue;
            }
            int pageIndex = link.indexOf("page=");
            if (pageIndex < 0) {
                return null;
            }
            int start = pageIndex + 5;
            int end = start;
            while (end < link.length() && Character.isDigit(link.charAt(end))) {
                end++;
            }
            try {
                return Integer.parseInt(link.substring(start, end));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int calculateImportanceScore(int stars, int forks, int commits, int languageCount, boolean hasReadme) {
        int score = 30;
        score += Math.min(stars * 2, 20);
        score += Math.min(forks * 3, 15);
        score += Math.min(commits / 5, 20);
        score += Math.min(languageCount * 3, 10);
        if (hasReadme) {
            score += 5;
        }
        return Math.min(score, 100);
    }

    private List<String> buildHighlights(JsonNode repo, List<String> languages, int commitCount, String readme) {
        List<String> highlights = new ArrayList<>();
        if (!languages.isEmpty()) {
            highlights.add("주요 기술 스택: " + String.join(", ", languages));
        }
        if (hasText(repo.path("description").asText(null))) {
            highlights.add("프로젝트 설명 기반 핵심 주제: " + repo.path("description").asText());
        }
        if (hasText(readme)) {
            highlights.add("README 문서를 기반으로 프로젝트 목적과 사용 흐름 확인 가능");
        }
        highlights.add("최근 커밋 수 기준 활동량: " + commitCount + "개");
        if (repo.path("private").asBoolean(false)) {
            highlights.add("비공개 저장소 분석");
        }
        return highlights;
    }

    private String summarizeReadme(String readme, String description) {
        if (hasText(readme)) {
            String normalized = readme
                    .replaceAll("(?m)^#{1,6}\\s*", "")
                    .replaceAll("(?s)```.*?```", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (normalized.length() > 500) {
                return normalized.substring(0, 500) + "...";
            }
            return normalized;
        }
        if (hasText(description)) {
            return description;
        }
        return "README 또는 repository description이 없어 요약 정보가 부족합니다.";
    }

    private String buildActivitySummary(int commits, int stars, int forks, int openIssues) {
        return "커밋 " + commits + "개, 스타 " + stars + "개, 포크 " + forks
                + "개, 열린 이슈 " + openIssues + "개 기준으로 활동성을 계산했습니다.";
    }

    private String buildAiSummary(String repoName, String description, String readmeSummary, String activitySummary) {
        return repoName + " 저장소 분석 결과. "
                + (hasText(description) ? description + " " : "")
                + readmeSummary + " " + activitySummary;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultString(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private ApiException githubApiException(RestClientResponseException e, String message) {
        if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
            return new ApiException(HttpStatus.FORBIDDEN, "GITHUB_001", "GitHub access token is invalid");
        }
        if (e.getStatusCode().value() == 404) {
            return new ApiException(HttpStatus.NOT_FOUND, "GITHUB_002", "Repository not found or access denied");
        }
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", message);
    }

    private record GithubPage<T>(T content, int totalElements) {
    }
}
