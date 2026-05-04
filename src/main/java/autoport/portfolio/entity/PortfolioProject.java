package autoport.portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_projects")
@Getter
@NoArgsConstructor
public class PortfolioProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "repo_id")
    private Long repoId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "github_url", columnDefinition = "TEXT")
    private String githubUrl;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "tech_stacks_json", columnDefinition = "TEXT")
    private String techStacksJson;

    @Column(name = "highlights_json", columnDefinition = "TEXT")
    private String highlightsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PortfolioProject create(
            Portfolio portfolio,
            Long repoId,
            String name,
            String description,
            String githubUrl,
            Integer displayOrder,
            String techStacksJson,
            String highlightsJson) {
        PortfolioProject project = new PortfolioProject();
        project.portfolio = portfolio;
        project.repoId = repoId;
        project.name = name;
        project.description = description;
        project.githubUrl = githubUrl;
        project.displayOrder = displayOrder;
        project.techStacksJson = techStacksJson;
        project.highlightsJson = highlightsJson;
        project.createdAt = LocalDateTime.now();
        project.updatedAt = LocalDateTime.now();
        return project;
    }
}