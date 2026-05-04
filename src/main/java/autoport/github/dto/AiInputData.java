package autoport.github.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiInputData {
    private String projectName;
    private String summary;
    private List<String> stacks;
    private List<String> highlights;
}