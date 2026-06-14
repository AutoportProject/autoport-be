package autoport.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AnalysisResultRequest {

    @NotBlank(message = "projectName is required")
    @JsonAlias("repoName")
    private String projectName;

    private String summary;

    @JsonAlias("techStacks")
    private List<String> stacks;

    private List<String> highlights;

    private String repoUrl;
    private String description;
    private String mainLanguage;
    private String readmeSummary;
    private String activitySummary;
    private Integer starCount;
    private Integer forkCount;
    private Integer openIssuesCount;
    private Integer commitCount;
    private Integer importanceScore;
    private String repositoryCreatedAt;
    private String repositoryUpdatedAt;
    private String firstCommitAt;
    private String latestCommitAt;
    private String developmentPeriod;
    private List<String> recentCommitMessages;
    private String contributorLogin;
    private Integer userCommitCount;
    private List<String> userRecentCommitMessages;
    private Integer prReviewCount;
}
