package autoport.user.service;

import autoport.common.exception.ApiException;
import autoport.config.UserPrincipal;
import autoport.user.dto.MyUserResponse;
import autoport.user.dto.UserListResponse;
import autoport.user.dto.UserSummaryResponse;
import autoport.user.entity.User;
import autoport.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public MyUserResponse getMyInfo() {
        User user = getCurrentUser();

        return new MyUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getBio(),
                user.getProvider(),
                user.getProfileImage(),
                user.getCreatedAt().toString());
    }

    public UserListResponse getUsers(Integer page, Integer size, String keyword, String provider) {
        validateAdminAuthorization();

        int currentPage = (page == null || page < 1) ? 1 : page;
        int currentSize = (size == null || size < 1) ? 10 : size;

        List<UserSummaryResponse> users = getMockUsers();

        if (keyword != null && !keyword.isBlank()) {
            users = users.stream()
                    .filter(user -> (user.getName() != null && user.getName().contains(keyword)) ||
                            (user.getEmail() != null && user.getEmail().contains(keyword)))
                    .collect(Collectors.toList());
        }

        if (provider != null && !provider.isBlank()) {
            users = users.stream()
                    .filter(user -> provider.equalsIgnoreCase(user.getProvider()))
                    .collect(Collectors.toList());
        }

        int totalElements = users.size();
        int totalPages = (int) Math.ceil((double) totalElements / currentSize);

        int fromIndex = Math.min((currentPage - 1) * currentSize, totalElements);
        int toIndex = Math.min(fromIndex + currentSize, totalElements);

        List<UserSummaryResponse> pagedContent = users.subList(fromIndex, toIndex);

        return new UserListResponse(
                pagedContent,
                currentPage,
                currentSize,
                totalElements,
                totalPages);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_005", "Unauthorized");
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return userPrincipal.getUser();
    }

    private void validateAdminAuthorization() {
        User user = getCurrentUser();
        if (!"ADMIN".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_006", "Forbidden");
        }
    }

    private List<UserSummaryResponse> getMockUsers() {
        List<UserSummaryResponse> users = new ArrayList<>();

        users.add(new UserSummaryResponse(
                1L,
                "test@email.com",
                "홍길동",
                "GITHUB",
                "2026-04-01T12:00:00Z",
                3));

        users.add(new UserSummaryResponse(
                2L,
                "user2@email.com",
                "김개발",
                "LOCAL",
                "2026-04-02T09:00:00Z",
                1));

        users.add(new UserSummaryResponse(
                3L,
                "backend@email.com",
                "이백엔드",
                "GITHUB",
                "2026-04-03T10:30:00Z",
                2));

        users.add(new UserSummaryResponse(
                4L,
                "frontend@email.com",
                "박프론트",
                "LOCAL",
                "2026-04-04T14:15:00Z",
                5));

        return users;
    }
}