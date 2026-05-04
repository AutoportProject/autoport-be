package autoport.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmailSendRequest {

    @Email(message = "올바른 이메일 형식이어야 합니다.")
    @NotBlank(message = "email은 필수입니다.")
    private String email;
}