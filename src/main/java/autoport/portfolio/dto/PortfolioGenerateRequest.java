package autoport.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PortfolioGenerateRequest {

    @Valid
    @NotNull(message = "analysisResult is required")
    @JsonAlias({"aiInputData", "repositoryAnalysis", "githubAnalysis", "data"})
    private AnalysisResultRequest analysisResult;

    private Long templateId;

    private String userName;

    private String bio;

    private String tone;

    private String emphasis;
}
