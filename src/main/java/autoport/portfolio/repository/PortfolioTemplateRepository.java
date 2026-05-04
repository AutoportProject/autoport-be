package autoport.portfolio.repository;

import autoport.portfolio.entity.PortfolioTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioTemplateRepository extends JpaRepository<PortfolioTemplate, Long> {
}