package autoport.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PortfolioProjectResponse {
    private String name;
    private String description;
    private List<String> techStacks;
    private List<String> highlights;
}