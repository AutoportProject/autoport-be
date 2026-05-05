package autoport.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioProjectResponse {
    private String name;
    private String oneLineDescription;
    private String description;
    private String estimatedPeriod;
    private String role;
    private List<String> techStacks;
    private List<String> mainFeatures;
    private List<String> highlights;
}
