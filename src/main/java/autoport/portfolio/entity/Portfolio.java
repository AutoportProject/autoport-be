package autoport.portfolio.entity;

import autoport.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolios")
@Getter
@NoArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private PortfolioTemplate template;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "technical_contributions_json", columnDefinition = "TEXT")
    private String technicalContributionsJson;

    @Column(name = "code_highlights_json", columnDefinition = "TEXT")
    private String codeHighlightsJson;

    @Column(name = "project_links_json", columnDefinition = "TEXT")
    private String projectLinksJson;

    @Column(name = "generated_at", columnDefinition = "TEXT")
    private String generatedAt;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @Column(name = "share_token", unique = true, length = 36)
    private String shareToken;

    @Column(name = "shared_at")
    private LocalDateTime sharedAt;

    @Column(name = "featured_project_id")
    private Long featuredProjectId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Portfolio create(
            User user,
            PortfolioTemplate template,
            String title,
            String bio,
            String summary,
            String description,
            String technicalContributionsJson,
            String codeHighlightsJson,
            String projectLinksJson,
            String generatedAt,
            Boolean isPublic,
            Long featuredProjectId) {
        Portfolio portfolio = new Portfolio();
        portfolio.user = user;
        portfolio.template = template;
        portfolio.title = title;
        portfolio.bio = bio;
        portfolio.summary = summary;
        portfolio.description = description;
        portfolio.technicalContributionsJson = technicalContributionsJson;
        portfolio.codeHighlightsJson = codeHighlightsJson;
        portfolio.projectLinksJson = projectLinksJson;
        portfolio.generatedAt = generatedAt;
        portfolio.isPublic = isPublic != null && isPublic;
        if (portfolio.isPublic) {
            portfolio.ensureShareToken();
        }
        portfolio.featuredProjectId = featuredProjectId;
        portfolio.createdAt = LocalDateTime.now();
        portfolio.updatedAt = LocalDateTime.now();
        return portfolio;
    }

    public void update(
            PortfolioTemplate template,
            String title,
            String bio,
            String summary,
            String description,
            String technicalContributionsJson,
            String codeHighlightsJson,
            String projectLinksJson,
            String generatedAt,
            Boolean isPublic,
            Long featuredProjectId) {
        this.template = template;
        this.title = title;
        this.bio = bio;
        this.summary = summary;
        this.description = description;
        this.technicalContributionsJson = technicalContributionsJson;
        this.codeHighlightsJson = codeHighlightsJson;
        this.projectLinksJson = projectLinksJson;
        this.generatedAt = generatedAt;
        this.isPublic = isPublic != null && isPublic;
        if (this.isPublic) {
            ensureShareToken();
        } else {
            clearShareToken();
        }
        this.featuredProjectId = featuredProjectId;
        this.updatedAt = LocalDateTime.now();
    }

    public void ensureShareToken() {
        if (this.shareToken == null || this.shareToken.isBlank()) {
            this.shareToken = UUID.randomUUID().toString();
            this.sharedAt = LocalDateTime.now();
        }
    }

    public void clearShareToken() {
        this.shareToken = null;
        this.sharedAt = null;
    }

    public void updateFeaturedProjectId(Long featuredProjectId) {
        this.featuredProjectId = featuredProjectId;
        this.updatedAt = LocalDateTime.now();
    }
}
