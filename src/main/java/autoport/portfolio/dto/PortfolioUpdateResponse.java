package autoport.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PortfolioUpdateResponse {
    private Long portfolioId;
    private String title;
    private String updatedAt;
}