package autoport.portfolio.service;

import autoport.ai.service.GeminiPortfolioService;
import autoport.common.exception.ApiException;
import autoport.config.UserPrincipal;
import autoport.portfolio.dto.PortfolioDetailResponse;
import autoport.portfolio.dto.PortfolioProjectRequest;
import autoport.portfolio.dto.PortfolioSaveResponse;
import autoport.portfolio.dto.PortfolioSaveRequest;
import autoport.portfolio.dto.PortfolioShareResponse;
import autoport.portfolio.entity.Portfolio;
import autoport.portfolio.entity.PortfolioProject;
import autoport.portfolio.repository.PortfolioProjectRepository;
import autoport.portfolio.repository.PortfolioRepository;
import autoport.portfolio.repository.PortfolioTemplateRepository;
import autoport.user.entity.User;
import autoport.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioProjectRepository portfolioProjectRepository;

    @Mock
    private PortfolioTemplateRepository portfolioTemplateRepository;

    @Mock
    private GeminiPortfolioService geminiPortfolioService;

    private PortfolioService portfolioService;
    private User user;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(
                userRepository,
                portfolioRepository,
                portfolioProjectRepository,
                portfolioTemplateRepository,
                geminiPortfolioService);

        user = User.localUser("user@example.com", "password", "사용자", "bio");
        ReflectionTestUtils.setField(user, "id", 1L);

        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createShareLinkReturnsUrlForPublicPortfolio() {
        Portfolio portfolio = createPortfolio(true);
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));

        PortfolioShareResponse response = portfolioService.createShareLink(
                1L,
                "https://autoport.site/api/portfolio/share");

        assertThat(response.getPortfolioId()).isEqualTo(1L);
        assertThat(response.isPublic()).isTrue();
        assertThat(response.getShareToken()).isNotBlank();
        assertThat(response.getShareUrl()).isEqualTo(
                "https://autoport.site/api/portfolio/share/" + response.getShareToken());
    }

    @Test
    void createShareLinkRejectsPrivatePortfolio() {
        Portfolio portfolio = createPortfolio(false);
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> portfolioService.createShareLink(
                1L,
                "https://autoport.site/api/portfolio/share"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("PORTFOLIO_006");
                });

        assertThat(portfolio.getShareToken()).isNull();
    }

    @Test
    void getSharedPortfolioReturnsPublicPortfolioDetail() {
        Portfolio portfolio = createPortfolio(true);
        String shareToken = portfolio.getShareToken();
        when(portfolioRepository.findByShareToken(shareToken)).thenReturn(Optional.of(portfolio));
        when(portfolioProjectRepository.findByPortfolioIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());

        PortfolioDetailResponse response = portfolioService.getSharedPortfolio(shareToken);

        assertThat(response.getPortfolioId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("title");
        assertThat(response.isPublic()).isTrue();
    }

    @Test
    void getSharedPortfolioHidesPrivatePortfolio() {
        Portfolio portfolio = createPortfolio(false);
        ReflectionTestUtils.setField(portfolio, "shareToken", "old-token");
        when(portfolioRepository.findByShareToken("old-token")).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> portfolioService.getSharedPortfolio("old-token"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("PORTFOLIO_005");
                });
    }

    @Test
    void updatePortfolioClearsShareTokenWhenMadePrivate() {
        Portfolio portfolio = createPortfolio(true);
        assertThat(portfolio.getShareToken()).isNotBlank();

        PortfolioSaveRequest request = new PortfolioSaveRequest();
        ReflectionTestUtils.setField(request, "title", "updated title");
        ReflectionTestUtils.setField(request, "bio", "updated bio");
        ReflectionTestUtils.setField(request, "summary", "updated summary");
        ReflectionTestUtils.setField(request, "description", "updated description");
        ReflectionTestUtils.setField(request, "technicalContributions", List.of());
        ReflectionTestUtils.setField(request, "codeHighlights", List.of());
        ReflectionTestUtils.setField(request, "projectLinks", List.of());
        ReflectionTestUtils.setField(request, "projects", List.of());
        ReflectionTestUtils.setField(request, "isPublic", false);

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));

        portfolioService.updatePortfolio(1L, request);

        assertThat(portfolio.getIsPublic()).isFalse();
        assertThat(portfolio.getShareToken()).isNull();
        assertThat(portfolio.getSharedAt()).isNull();
    }

    @Test
    void savePortfolioMapsFeaturedProjectRepoIdToSavedProjectId() {
        PortfolioSaveRequest request = createSaveRequestWithProject(1231214650L);
        Portfolio[] savedPortfolioHolder = new Portfolio[1];

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> {
            Portfolio portfolio = invocation.getArgument(0);
            ReflectionTestUtils.setField(portfolio, "id", 1L);
            savedPortfolioHolder[0] = portfolio;
            return portfolio;
        });
        when(portfolioProjectRepository.save(any(PortfolioProject.class))).thenAnswer(invocation -> {
            PortfolioProject project = invocation.getArgument(0);
            ReflectionTestUtils.setField(project, "id", 2L);
            return project;
        });

        PortfolioSaveResponse response = portfolioService.savePortfolio(request);

        assertThat(response.getPortfolioId()).isEqualTo(1L);
        assertThat(savedPortfolioHolder[0].getFeaturedProjectId()).isEqualTo(2L);
    }

    private Portfolio createPortfolio(boolean isPublic) {
        Portfolio portfolio = Portfolio.create(
                user,
                null,
                "title",
                "bio",
                "summary",
                "description",
                "[]",
                "[]",
                "[]",
                "2026-06-12T00:00:00",
                isPublic,
                null);
        ReflectionTestUtils.setField(portfolio, "id", 1L);
        return portfolio;
    }

    private PortfolioSaveRequest createSaveRequestWithProject(Long featuredProjectId) {
        PortfolioProjectRequest project = new PortfolioProjectRequest();
        ReflectionTestUtils.setField(project, "repoId", featuredProjectId);
        ReflectionTestUtils.setField(project, "name", "project");
        ReflectionTestUtils.setField(project, "description", "description");

        PortfolioSaveRequest request = new PortfolioSaveRequest();
        ReflectionTestUtils.setField(request, "title", "title");
        ReflectionTestUtils.setField(request, "bio", "bio");
        ReflectionTestUtils.setField(request, "technicalContributions", List.of());
        ReflectionTestUtils.setField(request, "codeHighlights", List.of());
        ReflectionTestUtils.setField(request, "projectLinks", List.of());
        ReflectionTestUtils.setField(request, "projects", List.of(project));
        ReflectionTestUtils.setField(request, "isPublic", false);
        ReflectionTestUtils.setField(request, "featuredProjectId", featuredProjectId);
        return request;
    }
}
