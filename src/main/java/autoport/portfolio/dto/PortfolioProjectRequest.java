package autoport.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PortfolioProjectRequest {

    private Long repoId;

    @NotBlank(message = "project name은 필수입니다.")
    private String name;

    @NotBlank(message = "project description은 필수입니다.")
    private String description;

    private String oneLineDescription;

    private String estimatedPeriod;

    private String role;

    private List<String> techStacks;

    private List<String> mainFeatures;

    private List<String> highlights;

    private String githubUrl;

    private String deployUrl;

    private Integer order;
}
