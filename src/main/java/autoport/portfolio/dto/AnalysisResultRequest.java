package autoport.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AnalysisResultRequest {

    @NotBlank(message = "projectName은 필수입니다.")
    private String projectName;

    @NotBlank(message = "summary는 필수입니다.")
    private String summary;

    @NotEmpty(message = "stacks는 최소 1개 이상이어야 합니다.")
    private List<String> stacks;

    @NotEmpty(message = "highlights는 최소 1개 이상이어야 합니다.")
    private List<String> highlights;
}