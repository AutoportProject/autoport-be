package autoport.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PortfolioGenerateResponse {
    private String portfolioTitle;
    private String introduction;
    private List<PortfolioProjectResponse> projects;
    private String generatedAt;
}