package autoport.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PortfolioProjectRequest {

    @NotNull(message = "repoId는 필수입니다.")
    private Long repoId;

    @NotBlank(message = "project name은 필수입니다.")
    private String name;

    @NotBlank(message = "project description은 필수입니다.")
    private String description;

    private List<String> techStacks;

    private List<String> highlights;

    private String githubUrl;

    private Integer order;
}