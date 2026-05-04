package autoport.auth.repository;

import autoport.auth.entity.TempUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TempUserRepository extends JpaRepository<TempUser, Long> {
}