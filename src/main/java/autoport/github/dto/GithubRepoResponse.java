package autoport.github.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class GithubRepoResponse {
    private Long repoId;
    private String name;
    private String fullName;
    private String htmlUrl;
    private String description;
    private String language;
    private Integer stargazersCount;
    private Integer forksCount;
    private String updatedAt;

    @JsonProperty("isPrivate")
    private boolean isPrivate;
}