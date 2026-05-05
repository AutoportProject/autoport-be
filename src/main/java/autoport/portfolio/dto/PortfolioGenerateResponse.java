package autoport.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioGenerateResponse {
    private String portfolioTitle;
    private String introduction;
    private List<PortfolioProjectResponse> projects;
    private List<String> technicalContributions;
    private String collaborationStyle;
    private List<String> codeHighlights;
    private List<String> projectLinks;
    private String generatedAt;
}
