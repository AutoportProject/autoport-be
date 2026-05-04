package autoport.github.service;

import autoport.common.exception.ApiException;
import autoport.config.UserPrincipal;
import autoport.github.dto.AiInputData;
import autoport.github.dto.GithubAnalyzeRequest;
import autoport.github.dto.GithubAnalyzeResponse;
import autoport.github.dto.GithubRepoListResponse;
import autoport.github.dto.GithubRepoResponse;
import autoport.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GithubService {

    public GithubRepoListResponse getRepos(String sort, String direction, Integer page, Integer perPage) {
        User user = getCurrentUser();
        validateGithubAuthorization(user);

        int currentPage = (page == null || page < 1) ? 1 : page;
        int currentPerPage = (perPage == null || perPage < 1) ? 30 : perPage;

        List<GithubRepoResponse> repos = new ArrayList<>(getMockRepos());

        String sortValue = (sort == null || sort.isBlank()) ? "updated" : sort;
        String directionValue = (direction == null || direction.isBlank()) ? "desc" : direction;

        Comparator<GithubRepoResponse> comparator;

        switch (sortValue.toLowerCase()) {
            case "created":
                comparator = Comparator.comparing(GithubRepoResponse::getUpdatedAt);
                break;
            case "stars":
                comparator = Comparator.comparing(GithubRepoResponse::getStargazersCount);
                break;
            case "updated":
            default:
                comparator = Comparator.comparing(GithubRepoResponse::getUpdatedAt);
                break;
        }

        if ("desc".equalsIgnoreCase(directionValue)) {
            comparator = comparator.reversed();
        }

        repos = repos.stream()
                .sorted(comparator)
                .collect(Collectors.toList());

        int totalElements = repos.size();
        int totalPages = (int) Math.ceil((double) totalElements / currentPerPage);

        int fromIndex = Math.min((currentPage - 1) * currentPerPage, totalElements);
        int toIndex = Math.min(fromIndex + currentPerPage, totalElements);

        List<GithubRepoResponse> pagedContent = repos.subList(fromIndex, toIndex);

        return new GithubRepoListResponse(
                pagedContent,
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

        if (repoId == 999999L) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GITHUB_002", "Repository not found or access denied");
        }

        if ("fail-analyze".equals(repoName)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ANALYZE_001", "Failed to analyze repository");
        }

        if (repoId.equals(123456L) && "autoport".equals(repoName) && "username".equals(owner)) {
            AiInputData aiInputData = new AiInputData(
                    "autoport",
                    "GitHub repository를 기반으로 포트폴리오를 자동 생성하는 서비스",
                    List.of("Java", "Spring Boot", "MySQL"),
                    List.of(
                            "GitHub OAuth 로그인 구현",
                            "레포 분석 기반 포트폴리오 생성",
                            "백엔드 API 설계 및 구현"));

            return new GithubAnalyzeResponse(
                    123456L,
                    "autoport",
                    "포트폴리오 자동 생성 서비스",
                    "Java",
                    List.of("Java", "Spring Boot", "MySQL"),
                    "GitHub repository를 기반으로 포트폴리오를 자동 생성하는 서비스입니다.",
                    "최근 커밋이 활발하며 백엔드 API 개발 중심의 프로젝트입니다.",
                    12,
                    134,
                    87,
                    aiInputData,
                    "2026-04-02T13:00:00Z");
        }

        AiInputData aiInputData = new AiInputData(
                repoName,
                repoName + " 프로젝트 분석 결과 요약",
                List.of("JavaScript", "React"),
                List.of(
                        "README 기반 프로젝트 요약",
                        "기술 스택 자동 추출",
                        "활동 지표 기반 중요도 계산"));

        return new GithubAnalyzeResponse(
                repoId,
                repoName,
                "선택한 repository에 대한 mock 분석 결과입니다.",
                "JavaScript",
                List.of("JavaScript", "React"),
                "README를 기반으로 핵심 내용을 요약한 결과입니다.",
                "최근 업데이트가 있으며 프론트엔드 중심 프로젝트로 보입니다.",
                5,
                42,
                65,
                aiInputData,
                "2026-04-05T13:00:00Z");
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

    private List<GithubRepoResponse> getMockRepos() {
        List<GithubRepoResponse> repos = new ArrayList<>();

        repos.add(new GithubRepoResponse(
                123456L,
                "autoport",
                "username/autoport",
                "https://github.com/username/autoport",
                "포트폴리오 자동 생성 서비스",
                "Java",
                10,
                2,
                "2026-04-02T12:00:00Z",
                false));

        repos.add(new GithubRepoResponse(
                234567L,
                "portfolio-fe",
                "username/portfolio-fe",
                "https://github.com/username/portfolio-fe",
                "포트폴리오 프론트엔드 프로젝트",
                "TypeScript",
                6,
                1,
                "2026-04-03T09:30:00Z",
                false));

        repos.add(new GithubRepoResponse(
                345678L,
                "private-repo",
                "username/private-repo",
                "https://github.com/username/private-repo",
                "비공개 실험용 프로젝트",
                "Python",
                0,
                0,
                "2026-04-01T17:00:00Z",
                true));

        return repos;
    }
}