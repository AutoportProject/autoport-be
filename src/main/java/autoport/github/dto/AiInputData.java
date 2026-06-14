package autoport.github.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiInputData {
    private String projectName;
    private String summary;
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
