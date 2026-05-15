package autoport.auth.controller;

import autoport.auth.dto.EmailSendRequest;
import autoport.auth.dto.GithubLoginRequest;
import autoport.auth.dto.GithubLoginResponse;
import autoport.auth.dto.GithubSignupRequest;
import autoport.auth.dto.LocalLoginRequest;
import autoport.auth.dto.LocalSignupRequest;
import autoport.auth.dto.SimpleMessageResponse;
import autoport.auth.dto.TokenResponse;
import autoport.auth.service.AuthService;
import autoport.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/github")
    public ResponseEntity<ApiResponse<GithubLoginResponse>> githubLogin(
            @Valid @RequestBody GithubLoginRequest request) {
        GithubLoginResponse response = authService.githubLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/signup/github")
    public ResponseEntity<ApiResponse<TokenResponse>> githubSignup(
            @Valid @RequestBody GithubSignupRequest request) {
        String accessToken = authService.githubSignup(request);
        return ResponseEntity.ok(ApiResponse.success(new TokenResponse(accessToken)));
    }

    @PostMapping("/github/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> githubSignupAlias(
            @Valid @RequestBody GithubSignupRequest request) {
        return githubSignup(request);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> localLogin(
            @Valid @RequestBody LocalLoginRequest request) {
        String accessToken = authService.localLogin(request);
        return ResponseEntity.ok(ApiResponse.success(new TokenResponse(accessToken)));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> localSignup(
            @Valid @RequestBody LocalSignupRequest request) {
        String accessToken = authService.localSignup(request);
        return ResponseEntity.ok(ApiResponse.success(new TokenResponse(accessToken)));
    }

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<SimpleMessageResponse>> sendEmailVerification(
            @Valid @RequestBody EmailSendRequest request) {
        authService.sendVerificationCode(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(new SimpleMessageResponse("Verification code sent")));
    }
}
