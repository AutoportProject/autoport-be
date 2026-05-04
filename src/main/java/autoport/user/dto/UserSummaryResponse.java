package autoport.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSummaryResponse {
    private Long userId;
    private String email;
    private String name;
    private String provider;
    private String createdAt;
    private Integer portfolioCount;
}