package autoport.portfolio.entity;

import autoport.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    @JoinColumn(name = "template_id", nullable = false)
    private PortfolioTemplate template;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bio;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

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
            Boolean isPublic,
            Long featuredProjectId) {
        Portfolio portfolio = new Portfolio();
        portfolio.user = user;
        portfolio.template = template;
        portfolio.title = title;
        portfolio.bio = bio;
        portfolio.isPublic = isPublic != null && isPublic;
        portfolio.featuredProjectId = featuredProjectId;
        portfolio.createdAt = LocalDateTime.now();
        portfolio.updatedAt = LocalDateTime.now();
        return portfolio;
    }

    public void update(
            PortfolioTemplate template,
            String title,
            String bio,
            Boolean isPublic,
            Long featuredProjectId) {
        this.template = template;
        this.title = title;
        this.bio = bio;
        this.isPublic = isPublic != null && isPublic;
        this.featuredProjectId = featuredProjectId;
        this.updatedAt = LocalDateTime.now();
    }
}