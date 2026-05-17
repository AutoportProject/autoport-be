package autoport.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PortfolioSaveRequest {

    @NotBlank(message = "title은 필수입니다.")
    private String title;

    @NotBlank(message = "bio는 필수입니다.")
    private String bio;

    private Long templateId;

    @Valid
    @NotEmpty(message = "projects는 최소 1개 이상이어야 합니다.")
    private List<PortfolioProjectRequest> projects;

    @JsonProperty("isPublic")
    private Boolean isPublic;

    private Long featuredProjectId;
}
