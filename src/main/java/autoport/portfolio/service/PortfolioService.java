package autoport.portfolio.service;

import autoport.ai.service.GeminiPortfolioService;
import autoport.common.exception.ApiException;
import autoport.config.UserPrincipal;
import autoport.portfolio.dto.*;
import autoport.portfolio.entity.Portfolio;
import autoport.portfolio.entity.PortfolioProject;
import autoport.portfolio.entity.PortfolioTemplate;
import autoport.portfolio.repository.PortfolioProjectRepository;
import autoport.portfolio.repository.PortfolioRepository;
import autoport.portfolio.repository.PortfolioTemplateRepository;
import autoport.user.entity.User;
import autoport.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class PortfolioService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioProjectRepository portfolioProjectRepository;
    private final PortfolioTemplateRepository portfolioTemplateRepository;
    private final GeminiPortfolioService geminiPortfolioService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PortfolioService(
            UserRepository userRepository,
            PortfolioRepository portfolioRepository,
            PortfolioProjectRepository portfolioProjectRepository,
            PortfolioTemplateRepository portfolioTemplateRepository,
            GeminiPortfolioService geminiPortfolioService) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioProjectRepository = portfolioProjectRepository;
        this.portfolioTemplateRepository = portfolioTemplateRepository;
        this.geminiPortfolioService = geminiPortfolioService;
    }

    public PortfolioGenerateResponse generatePortfolio(PortfolioGenerateRequest request) {
        // 인증은 SecurityConfig에서 처리되므로 별도 검증 불필요

        return geminiPortfolioService.generate(request);
    }

    @Transactional
    public PortfolioSaveResponse savePortfolio(PortfolioSaveRequest request) {
        Long userId = getCurrentUserId();

        User user = findUser(userId);
        PortfolioTemplate template = findTemplateIfPresent(request.getTemplateId());

        Portfolio portfolio = Portfolio.create(
                user,
                template,
                request.getTitle(),
                request.getBio(),
                resolveSummary(request),
                resolveDescription(request),
                writeJson(request.getTechnicalContributions()),
                writeJson(request.getCodeHighlights()),
                writeJson(request.getProjectLinks()),
                request.getGeneratedAt(),
                request.getIsPublic(),
                request.getFeaturedProjectId());

        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        saveProjects(savedPortfolio, request.getProjects());

        return new PortfolioSaveResponse(
                savedPortfolio.getId(),
                savedPortfolio.getTitle(),
                savedPortfolio.getSummary(),
                savedPortfolio.getDescription(),
                savedPortfolio.getIsPublic(),
                savedPortfolio.getCreatedAt().toString());
    }

    @Transactional
    public PortfolioUpdateResponse updatePortfolio(
            Long portfolioId,
            PortfolioSaveRequest request) {
        Long userId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_005", "Portfolio not found"));

        checkOwner(portfolio, userId);

        PortfolioTemplate template = findTemplateIfPresent(request.getTemplateId());

        portfolio.update(
                template,
                request.getTitle(),
                request.getBio(),
                resolveSummary(request),
                resolveDescription(request),
                writeJson(request.getTechnicalContributions()),
                writeJson(request.getCodeHighlights()),
                writeJson(request.getProjectLinks()),
                request.getGeneratedAt(),
                request.getIsPublic(),
                request.getFeaturedProjectId());

        portfolioProjectRepository.deleteByPortfolioId(portfolioId);
        saveProjects(portfolio, request.getProjects());

        return new PortfolioUpdateResponse(
                portfolio.getId(),
                portfolio.getTitle(),
                portfolio.getSummary(),
                portfolio.getDescription(),
                portfolio.getUpdatedAt().toString());
    }

    @Transactional
    public MessageResponse deletePortfolio(Long portfolioId) {
        Long userId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_005", "Portfolio not found"));

        checkOwner(portfolio, userId);

        portfolioRepository.delete(portfolio);

        return new MessageResponse("Portfolio deleted successfully");
    }

    public PortfolioListResponse getPortfolioList(
            Integer page,
            Integer perPage,
            String sort,
            String direction) {
        Long userId = getCurrentUserId();

        int currentPage = (page == null || page < 1) ? 1 : page;
        int currentPerPage = (perPage == null || perPage < 1) ? 10 : perPage;

        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        Comparator<Portfolio> comparator = Comparator.comparing(Portfolio::getUpdatedAt);
        if ("createdAt".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing(Portfolio::getCreatedAt);
        }

        if (direction == null || "desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }

        List<PortfolioListItemResponse> content = portfolios.stream()
                .sorted(comparator)
                .skip((long) (currentPage - 1) * currentPerPage)
                .limit(currentPerPage)
                .map(this::toListItem)
                .toList();

        int totalElements = portfolios.size();
        int totalPages = (int) Math.ceil((double) totalElements / currentPerPage);

        return new PortfolioListResponse(
                content,
                currentPage,
                currentPerPage,
                totalElements,
                totalPages);
    }

    public PortfolioDetailResponse getPortfolioDetail(Long portfolioId) {
        Long userId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PORTFOLIO_005", "Portfolio not found"));

        checkOwner(portfolio, userId);

        List<PortfolioProjectRequest> projects = portfolioProjectRepository
                .findByPortfolioIdOrderByDisplayOrderAsc(portfolioId)
                .stream()
                .map(this::toProjectRequest)
                .toList();

        return new PortfolioDetailResponse(
                portfolio.getId(),
                portfolio.getTitle(),
                portfolio.getBio(),
                portfolio.getSummary(),
                portfolio.getDescription(),
                readStringList(portfolio.getTechnicalContributionsJson()),
                readStringList(portfolio.getCodeHighlightsJson()),
                readStringList(portfolio.getProjectLinksJson()),
                portfolio.getGeneratedAt(),
                getTemplateId(portfolio),
                portfolio.getIsPublic(),
                portfolio.getFeaturedProjectId(),
                projects,
                portfolio.getCreatedAt().toString(),
                portfolio.getUpdatedAt().toString());
    }

    private void saveProjects(Portfolio portfolio, List<PortfolioProjectRequest> projects) {
        for (PortfolioProjectRequest projectRequest : projects) {
            PortfolioProject project = PortfolioProject.create(
                    portfolio,
                    projectRequest.getRepoId(),
                    projectRequest.getName(),
                    projectRequest.getDescription(),
                    projectRequest.getOneLineDescription(),
                    projectRequest.getEstimatedPeriod(),
                    projectRequest.getRole(),
                    projectRequest.getGithubUrl(),
                    projectRequest.getDeployUrl(),
                    projectRequest.getOrder(),
                    writeJson(projectRequest.getTechStacks()),
                    writeJson(projectRequest.getMainFeatures()),
                    writeJson(projectRequest.getHighlights()));

            portfolioProjectRepository.save(project);
        }
    }

    private String resolveSummary(PortfolioSaveRequest request) {
        if (hasText(request.getSummary())) {
            return request.getSummary().trim();
        }

        PortfolioProjectRequest firstProject = firstProject(request);
        if (firstProject != null && hasText(firstProject.getName())) {
            return abbreviate(firstProject.getName().trim() + " 기반 포트폴리오", 80);
        }

        return abbreviate(request.getTitle(), 80);
    }

    private String resolveDescription(PortfolioSaveRequest request) {
        if (hasText(request.getDescription())) {
            return request.getDescription().trim();
        }

        if (hasText(request.getBio())) {
            return abbreviate(request.getBio(), 180);
        }

        PortfolioProjectRequest firstProject = firstProject(request);
        if (firstProject != null && hasText(firstProject.getDescription())) {
            return abbreviate(firstProject.getDescription(), 180);
        }

        return abbreviate(request.getTitle(), 180);
    }

    private PortfolioProjectRequest firstProject(PortfolioSaveRequest request) {
        if (request.getProjects() == null || request.getProjects().isEmpty()) {
            return null;
        }
        return request.getProjects().stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private PortfolioListItemResponse toListItem(Portfolio portfolio) {
        String featuredProjectName = null;

        if (portfolio.getFeaturedProjectId() != null) {
            featuredProjectName = portfolioProjectRepository.findById(portfolio.getFeaturedProjectId())
                    .map(PortfolioProject::getName)
                    .orElse(null);
        }

        return new PortfolioListItemResponse(
                portfolio.getId(),
                portfolio.getTitle(),
                portfolio.getSummary(),
                portfolio.getDescription(),
                getTemplateId(portfolio),
                portfolio.getIsPublic(),
                featuredProjectName,
                portfolio.getCreatedAt().toString(),
                portfolio.getUpdatedAt().toString());
    }

    private PortfolioProjectRequest toProjectRequest(PortfolioProject project) {
        PortfolioProjectRequest request = new PortfolioProjectRequest();

        setField(request, "repoId", project.getRepoId());
        setField(request, "name", project.getName());
        setField(request, "description", project.getDescription());
        setField(request, "oneLineDescription", project.getOneLineDescription());
        setField(request, "estimatedPeriod", project.getEstimatedPeriod());
        setField(request, "role", project.getRole());
        setField(request, "techStacks", readStringList(project.getTechStacksJson()));
        setField(request, "mainFeatures", readStringList(project.getMainFeaturesJson()));
        setField(request, "highlights", readStringList(project.getHighlightsJson()));
        setField(request, "githubUrl", project.getGithubUrl());
        setField(request, "deployUrl", project.getDeployUrl());
        setField(request, "order", project.getDisplayOrder());

        return request;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_005", "Unauthorized"));
    }

    private PortfolioTemplate findTemplateIfPresent(Long templateId) {
        if (templateId == null) {
            return null;
        }

        return portfolioTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "BAD_REQUEST",
                        "templateId가 올바르지 않습니다."));
    }

    private Long getTemplateId(Portfolio portfolio) {
        return portfolio.getTemplate() != null ? portfolio.getTemplate().getId() : null;
    }

    private void checkOwner(Portfolio portfolio, Long userId) {
        if (!portfolio.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PORTFOLIO_002", "Forbidden");
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_005", "Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return userPrincipal.getUser().getId();
    }

    private String writeJson(List<String> values) {
        try {
            if (values == null) {
                return "[]";
            }
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PORTFOLIO_001", "Failed to save portfolio");
        }
    }

    private List<String> readStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PORTFOLIO_004",
                    "Failed to fetch portfolio detail");
        }
    }

    private String resolveToneText(String tone) {
        if (tone == null || tone.isBlank()) {
            return "균형 잡힌 톤의";
        }

        return switch (tone.toLowerCase()) {
            case "professional" -> "전문적인 톤의";
            case "casual" -> "자연스럽고 친근한 톤의";
            case "simple" -> "간결한 톤의";
            default -> "균형 잡힌 톤의";
        };
    }
}
