package autoport.user.controller;

import autoport.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DbTestController {

    private final UserRepository userRepository;

    @GetMapping("/api/db-test")
    public Map<String, Object> dbTest() {
        long userCount = userRepository.count();

        return Map.of(
                "dbConnected", true,
                "userCount", userCount);
    }
}