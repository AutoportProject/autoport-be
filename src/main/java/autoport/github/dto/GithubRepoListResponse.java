package autoport.github.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GithubRepoListResponse {
    private List<GithubRepoResponse> content;
    private int page;
    private int perPage;
    private int totalElements;
    private int totalPages;
}