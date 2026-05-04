package autoport.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static EmailVerification create(String email, String code) {
        EmailVerification verification = new EmailVerification();
        verification.email = email;
        verification.code = code;
        verification.createdAt = LocalDateTime.now();
        verification.expiredAt = LocalDateTime.now().plusMinutes(10);
        return verification;
    }
}