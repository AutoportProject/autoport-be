package autoport.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyUserResponse {
    private Long userId;
    private String email;
    private String name;
    private String bio;
    private String provider;
    private String profileImage;
    private String createdAt;
}