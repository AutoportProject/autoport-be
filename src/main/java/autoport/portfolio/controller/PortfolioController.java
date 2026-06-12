package autoport.portfolio.controller;

import autoport.common.response.ApiResponse;
import autoport.portfolio.dto.MessageResponse;
import autoport.portfolio.dto.PortfolioDetailResponse;
import autoport.portfolio.dto.PortfolioGenerateRequest;
import autoport.portfolio.dto.PortfolioGenerateResponse;
import autoport.portfolio.dto.PortfolioListResponse;
import autoport.portfolio.dto.PortfolioSaveRequest;
import autoport.portfolio.dto.PortfolioSaveResponse;
import autoport.portfolio.dto.PortfolioShareResponse;
import autoport.portfolio.dto.PortfolioUpdateResponse;
import autoport.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<PortfolioGenerateResponse>> generatePortfolio(
            @Valid @RequestBody PortfolioGenerateRequest request) {
        PortfolioGenerateResponse response = portfolioService.generatePortfolio(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioSaveResponse>> savePortfolio(
            @Valid @RequestBody PortfolioSaveRequest request) {
        PortfolioSaveResponse response = portfolioService.savePortfolio(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<PortfolioUpdateResponse>> updatePortfolio(
            @PathVariable Long portfolioId,
            @Valid @RequestBody PortfolioSaveRequest request) {
        PortfolioUpdateResponse response = portfolioService.updatePortfolio(portfolioId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<MessageResponse>> deletePortfolio(
            @PathVariable Long portfolioId) {
        MessageResponse response = portfolioService.deletePortfolio(portfolioId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PortfolioListResponse>> getPortfolioList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer perPage,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        PortfolioListResponse response = portfolioService.getPortfolioList(page, perPage, sort, direction);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<PortfolioDetailResponse>> getPortfolioDetail(
            @PathVariable Long portfolioId) {
        PortfolioDetailResponse response = portfolioService.getPortfolioDetail(portfolioId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{portfolioId}/share")
    public ResponseEntity<ApiResponse<PortfolioShareResponse>> createShareLink(
            @PathVariable Long portfolioId) {
        String shareUrlPrefix = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/portfolio/share")
                .toUriString();
        PortfolioShareResponse response = portfolioService.createShareLink(portfolioId, shareUrlPrefix);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/share/{shareToken}")
    public ResponseEntity<ApiResponse<PortfolioDetailResponse>> getSharedPortfolio(
            @PathVariable String shareToken) {
        PortfolioDetailResponse response = portfolioService.getSharedPortfolio(shareToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
