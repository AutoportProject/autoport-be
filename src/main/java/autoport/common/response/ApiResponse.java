package autoport.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
}