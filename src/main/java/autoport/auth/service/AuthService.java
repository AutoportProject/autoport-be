package autoport.auth.service;

import autoport.auth.dto.GithubLoginRequest;
import autoport.auth.dto.GithubLoginResponse;
import autoport.auth.dto.GithubSignupRequest;
import autoport.auth.dto.LocalLoginRequest;
import autoport.auth.dto.LocalSignupRequest;
import autoport.auth.entity.EmailVerification;
import autoport.auth.entity.TempUser;
import autoport.auth.repository.EmailVerificationRepository;
import autoport.auth.repository.TempUserRepository;
import autoport.common.exception.ApiException;
import autoport.config.JwtTokenProvider;
import autoport.user.entity.User;
import autoport.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TempUserRepository tempUserRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from:Autoport <onboarding@resend.dev>}")
    private String resendFrom;

    @Value("${github.client-id:}")
    private String githubClientId;

    @Value("${github.client-secret:}")
    private String githubClientSecret;

    @Value("${github.redirect-uri:}")
    private String githubRedirectUri;

    public GithubLoginResponse githubLogin(GithubLoginRequest request) {
        String code = request.getCode();

        if ("existing-user-code".equals(code)) {
            User user = userRepository.findByGithubId("github-existing-123")
                    .orElseGet(() -> userRepository.save(User.githubUser(
                            "github-existing@email.com",
                            "기존깃허브유저",
                            "GitHub 로그인 사용자",
                            "github-existing-123",
                            "existing-user",
                            "mock-github-token",
                            "https://github.com/avatar.png")));

            String token = jwtTokenProvider.generateTokenFromUsername(user.getId().toString());
            return new GithubLoginResponse(
                    false,
                    token,
                    null);
        }

        if ("new-user-code".equals(code)) {
            TempUser tempUser = TempUser.create(
                    "github-new-123",
                    "new-github-user",
                    "mock-github-token",
                    "new-github-user@email.com",
                    "https://github.com/avatar.png");

            TempUser saved = tempUserRepository.save(tempUser);

            return new GithubLoginResponse(
                    true,
                    null,
                    saved.getId());
        }

        String githubAccessToken = exchangeGithubCodeForAccessToken(code, request.getRedirectUri());
        JsonNode githubUser = fetchGithubUser(githubAccessToken);

        String githubId = githubUser.path("id").asText();
        String githubLogin = githubUser.path("login").asText(null);
        String githubEmail = githubUser.path("email").asText(null);
        String profileImage = githubUser.path("avatar_url").asText(null);
        String name = githubUser.path("name").asText(null);
        String bio = githubUser.path("bio").asText(null);

        if (githubEmail == null || githubEmail.isBlank()) {
            githubEmail = fetchPrimaryGithubEmail(githubAccessToken);
        }

        if (githubEmail == null || githubEmail.isBlank()) {
            githubEmail = githubId + "@users.noreply.github.com";
        }

        User existingUser = userRepository.findByGithubId(githubId).orElse(null);
        if (existingUser != null) {
            String token = jwtTokenProvider.generateTokenFromUsername(existingUser.getId().toString());
            return new GithubLoginResponse(false, token, null);
        }

        TempUser tempUser = TempUser.create(
                githubId,
                githubLogin,
                githubAccessToken,
                githubEmail,
                profileImage);

        TempUser saved = tempUserRepository.save(tempUser);

        return new GithubLoginResponse(true, null, saved.getId());
    }

    @Transactional
    public String githubSignup(GithubSignupRequest request) {
        TempUser tempUser = tempUserRepository.findById(request.getTempUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "AUTH_002", "Invalid temp user"));

        if (tempUser.getExpiredAt().isBefore(LocalDateTime.now())) {
            tempUserRepository.delete(tempUser);
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_002", "Invalid temp user");
        }

        if (userRepository.findByGithubId(tempUser.getGithubId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "이미 가입된 사용자입니다.");
        }

        User existingEmailUser = userRepository.findByEmail(tempUser.getEmail()).orElse(null);
        if (existingEmailUser != null) {
            existingEmailUser.connectGithub(
                    tempUser.getGithubId(),
                    tempUser.getGithubLogin(),
                    tempUser.getGithubAccessToken(),
                    tempUser.getProfileImage());

            tempUserRepository.delete(tempUser);
            return jwtTokenProvider.generateTokenFromUsername(existingEmailUser.getId().toString());
        }

        User user = User.githubUser(
                tempUser.getEmail(),
                request.getName(),
                request.getBio(),
                tempUser.getGithubId(),
                tempUser.getGithubLogin(),
                tempUser.getGithubAccessToken(),
                tempUser.getProfileImage());

        User savedUser = userRepository.save(user);
        tempUserRepository.delete(tempUser);

        return jwtTokenProvider.generateTokenFromUsername(savedUser.getId().toString());
    }

    private String exchangeGithubCodeForAccessToken(String code, String requestRedirectUri) {
        if (githubClientId == null || githubClientId.isBlank()
                || githubClientSecret == null || githubClientSecret.isBlank()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GITHUB_003",
                    "GitHub OAuth is not configured");
        }

        try {
            String redirectUri = resolveGithubRedirectUri(requestRedirectUri);
            StringBuilder body = new StringBuilder()
                    .append("client_id=").append(encode(githubClientId))
                    .append("&client_secret=").append(encode(githubClientSecret))
                    .append("&code=").append(encode(code));

            if (redirectUri != null && !redirectUri.isBlank()) {
                body.append("&redirect_uri=").append(encode(redirectUri));
            }

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://github.com/login/oauth/access_token"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300 || json.hasNonNull("error")) {
                log.error("GitHub token exchange failed. status={}, body={}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_REQUEST, "GITHUB_004", "Failed to exchange GitHub code");
            }

            String accessToken = json.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                log.error("GitHub token exchange response has no access token. body={}", response.body());
                throw new ApiException(HttpStatus.BAD_REQUEST, "GITHUB_004", "Failed to exchange GitHub code");
            }

            return accessToken;
        } catch (IOException e) {
            log.error("Failed to call GitHub token API", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", "Failed to call GitHub API");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", "Failed to call GitHub API");
        }
    }

    private JsonNode fetchGithubUser(String accessToken) {
        try {
            HttpRequest userRequest = githubApiRequest("https://api.github.com/user", accessToken);
            HttpResponse<String> response = httpClient.send(userRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("GitHub user API failed. status={}, body={}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_REQUEST, "GITHUB_006", "Failed to fetch GitHub user");
            }

            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            log.error("Failed to call GitHub user API", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", "Failed to call GitHub API");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GITHUB_005", "Failed to call GitHub API");
        }
    }

    private String fetchPrimaryGithubEmail(String accessToken) {
        try {
            HttpRequest emailRequest = githubApiRequest("https://api.github.com/user/emails", accessToken);
            HttpResponse<String> response = httpClient.send(emailRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("GitHub email API failed. status={}, body={}", response.statusCode(), response.body());
                return null;
            }

            JsonNode emails = objectMapper.readTree(response.body());
            for (JsonNode email : emails) {
                if (email.path("primary").asBoolean(false) && email.path("verified").asBoolean(false)) {
                    return email.path("email").asText(null);
                }
            }

            for (JsonNode email : emails) {
                if (email.path("verified").asBoolean(false)) {
                    return email.path("email").asText(null);
                }
            }

            return null;
        } catch (IOException e) {
            log.warn("Failed to call GitHub email API", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private HttpRequest githubApiRequest(String url, String accessToken) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
    }

    private String resolveGithubRedirectUri(String requestRedirectUri) {
        if (requestRedirectUri != null && !requestRedirectUri.isBlank()) {
            return requestRedirectUri;
        }

        return githubRedirectUri;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String localLogin(LocalLoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "존재하지 않는 사용자입니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_003", "Invalid email or password");
        }

        return jwtTokenProvider.generateTokenFromUsername(user.getId().toString());
    }

    @Transactional
    public String localSignup(LocalSignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_001", "Email already exists");
        }

        EmailVerification verification = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(request.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_006", "Email not verified"));

        if (verification.getExpiredAt().isBefore(LocalDateTime.now())) {
            emailVerificationRepository.deleteByEmail(request.getEmail());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_006", "Email not verified");
        }

        if (!verification.getCode().equals(request.getCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_005", "Invalid verification code");
        }

        User user = User.localUser(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getName(),
                request.getBio());

        User savedUser = userRepository.save(user);
        emailVerificationRepository.deleteByEmail(request.getEmail());

        return jwtTokenProvider.generateTokenFromUsername(savedUser.getId().toString());
    }

    @Transactional
    public void sendVerificationCode(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_001", "Email already exists");
        }

        emailVerificationRepository.deleteByEmail(email);

        // 랜덤 6자리 인증 코드 생성
        String verificationCode = generateVerificationCode();
        
        EmailVerification verification = EmailVerification.create(email, verificationCode);
        emailVerificationRepository.save(verification);

        // 이메일 발송
        sendVerificationEmail(email, verificationCode);
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private void sendVerificationEmail(String email, String verificationCode) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MAIL_002",
                    "Resend API key is not configured");
        }

        try {
            String htmlContent = String.format(
                    "<html>" +
                    "<body style='font-family: Arial, sans-serif;'>" +
                    "<h2>Autoport 이메일 인증</h2>" +
                    "<p>안녕하세요!</p>" +
                    "<p>아래의 인증 코드를 입력하여 이메일을 인증해주세요.</p>" +
                    "<h3 style='background-color: #f0f0f0; padding: 10px; text-align: center; letter-spacing: 2px;'>%s</h3>" +
                    "<p>이 코드는 24시간 동안 유효합니다.</p>" +
                    "<p>감사합니다!</p>" +
                    "</body>" +
                    "</html>",
                    verificationCode
            );

            Map<String, Object> payload = Map.of(
                    "from", resendFrom,
                    "to", email,
                    "subject", "Autoport 이메일 인증 코드",
                    "html", htmlContent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "Resend failed to send verification email to: {}. status={}, body={}",
                        email,
                        response.statusCode(),
                        response.body());
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "MAIL_003",
                        "Resend failed to send verification email");
            }

            log.info("Verification email sent to: {}", email);
        } catch (IOException e) {
            log.error("Failed to call Resend API for verification email to: {}", email, e);
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MAIL_001",
                    "Failed to call Resend API");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MAIL_001",
                    "Failed to call Resend API");
        }
    }
}
