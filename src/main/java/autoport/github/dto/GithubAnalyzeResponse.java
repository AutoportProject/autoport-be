package autoport.github.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GithubAnalyzeResponse {
    private Long repoId;
    private String repoName;
    private String description;
    private String mainLanguage;
    private List<String> techStacks;
    private String readmeSummary;
    private String activitySummary;
    private Integer starCount;
    private Integer commitCount;
    private Integer importanceScore;
    private AiInputData aiInputData;
    private String analyzedAt;
}