package autoport.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "temp_users")
@Getter
@NoArgsConstructor
public class TempUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false, length = 100)
    private String githubId;

    @Column(name = "github_login", length = 100)
    private String githubLogin;

    @Column(name = "github_access_token", columnDefinition = "TEXT")
    private String githubAccessToken;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "profile_image", columnDefinition = "TEXT")
    private String profileImage;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static TempUser create(
            String githubId,
            String githubLogin,
            String githubAccessToken,
            String email,
            String profileImage) {
        TempUser tempUser = new TempUser();
        tempUser.githubId = githubId;
        tempUser.githubLogin = githubLogin;
        tempUser.githubAccessToken = githubAccessToken;
        tempUser.email = email;
        tempUser.profileImage = profileImage;
        tempUser.createdAt = LocalDateTime.now();
        tempUser.expiredAt = LocalDateTime.now().plusMinutes(30);
        return tempUser;
    }
}