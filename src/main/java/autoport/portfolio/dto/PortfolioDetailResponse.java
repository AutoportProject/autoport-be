package autoport.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PortfolioDetailResponse {
    private Long portfolioId;
    private String title;
    private String bio;
    private String summary;
    private String description;
    private Long templateId;

    @JsonProperty("isPublic")
    private boolean isPublic;

    private Long featuredProjectId;
    private List<PortfolioProjectRequest> projects;
    private String createdAt;
    private String updatedAt;
}
