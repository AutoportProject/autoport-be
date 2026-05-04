package autoport.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GithubSignupRequest {

    @NotNull(message = "tempUserId는 필수입니다.")
    private Long tempUserId;

    @NotBlank(message = "name은 필수입니다.")
    private String name;

    private String bio;
}