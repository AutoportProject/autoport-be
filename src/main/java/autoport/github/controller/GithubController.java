package autoport.github.controller;

import autoport.common.response.ApiResponse;
import autoport.github.dto.GithubAnalyzeRequest;
import autoport.github.dto.GithubAnalyzeResponse;
import autoport.github.dto.GithubRepoListResponse;
import autoport.github.service.GithubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubService githubService;

    @GetMapping("/repos")
    public ResponseEntity<ApiResponse<GithubRepoListResponse>> getRepos(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage) {
        GithubRepoListResponse response = githubService.getRepos(sort, direction, page, perPage);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<GithubAnalyzeResponse>> analyzeRepo(
            @Valid @RequestBody GithubAnalyzeRequest request) {
        GithubAnalyzeResponse response = githubService.analyzeRepo(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}