package autoport.github.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GithubAnalyzeRequest {

    @NotNull(message = "repoId는 필수입니다.")
    private Long repoId;

    @NotBlank(message = "repoName은 필수입니다.")
    private String repoName;

    @NotBlank(message = "owner는 필수입니다.")
    private String owner;
}