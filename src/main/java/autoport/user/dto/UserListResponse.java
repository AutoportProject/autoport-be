package autoport.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class UserListResponse {
    private List<UserSummaryResponse> content;
    private int page;
    private int size;
    private int totalElements;
    private int totalPages;
}