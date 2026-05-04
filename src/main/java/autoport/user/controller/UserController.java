package autoport.user.controller;

import autoport.common.response.ApiResponse;
import autoport.user.dto.MyUserResponse;
import autoport.user.dto.UserListResponse;
import autoport.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyUserResponse>> getMyInfo() {
        MyUserResponse response = userService.getMyInfo();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserListResponse>> getUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provider) {
        UserListResponse response = userService.getUsers(page, size, keyword, provider);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}