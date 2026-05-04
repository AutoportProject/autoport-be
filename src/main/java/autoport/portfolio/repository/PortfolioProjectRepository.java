package autoport.portfolio.repository;

import autoport.portfolio.entity.PortfolioProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioProjectRepository extends JpaRepository<PortfolioProject, Long> {
    List<PortfolioProject> findByPortfolioIdOrderByDisplayOrderAsc(Long portfolioId);

    void deleteByPortfolioId(Long portfolioId);
}