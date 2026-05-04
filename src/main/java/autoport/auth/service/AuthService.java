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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TempUserRepository tempUserRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

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

        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 GitHub code입니다.");
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

        EmailVerification verification = EmailVerification.create(email, "123456");
        emailVerificationRepository.save(verification);
    }
}