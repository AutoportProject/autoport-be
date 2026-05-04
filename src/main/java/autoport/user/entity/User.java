package autoport.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "github_id", unique = true, length = 100)
    private String githubId;

    @Column(name = "github_login", length = 100)
    private String githubLogin;

    @Column(name = "github_access_token", columnDefinition = "TEXT")
    private String githubAccessToken;

    @Column(name = "profile_image", columnDefinition = "TEXT")
    private String profileImage;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static User localUser(String email, String password, String name, String bio) {
        User user = new User();
        user.email = email;
        user.password = password;
        user.name = name;
        user.bio = bio;
        user.provider = "LOCAL";
        user.role = "USER";
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        return user;
    }

    public static User githubUser(
            String email,
            String name,
            String bio,
            String githubId,
            String githubLogin,
            String githubAccessToken,
            String profileImage) {
        User user = new User();
        user.email = email;
        user.name = name;
        user.bio = bio;
        user.provider = "GITHUB";
        user.githubId = githubId;
        user.githubLogin = githubLogin;
        user.githubAccessToken = githubAccessToken;
        user.profileImage = profileImage;
        user.role = "USER";
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        return user;
    }
}