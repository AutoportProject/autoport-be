package autoport.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LocalSignupRequest {

    @Email(message = "올바른 이메일 형식이어야 합니다.")
    @NotBlank(message = "email은 필수입니다.")
    private String email;

    @NotBlank(message = "password는 필수입니다.")
    private String password;

    @NotBlank(message = "name은 필수입니다.")
    private String name;

    private String bio;

    @NotBlank(message = "code는 필수입니다.")
    private String code;
}