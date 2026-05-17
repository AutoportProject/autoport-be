package autoport.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PortfolioGenerateRequest {

    @Valid
    @NotNull(message = "analysisResult는 필수입니다.")
    private AnalysisResultRequest analysisResult;

    private Long templateId;

    @NotBlank(message = "userName은 필수입니다.")
    private String userName;

    private String bio;

    private String tone;

    private String emphasis;
}
