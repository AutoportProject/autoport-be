package autoport.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GithubLoginRequest {

    @NotBlank(message = "code는 필수입니다.")
    private String code;
}