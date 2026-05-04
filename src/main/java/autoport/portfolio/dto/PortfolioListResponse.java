package autoport.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PortfolioListResponse {
    private List<PortfolioListItemResponse> content;
    private int page;
    private int perPage;
    private int totalElements;
    private int totalPages;
}