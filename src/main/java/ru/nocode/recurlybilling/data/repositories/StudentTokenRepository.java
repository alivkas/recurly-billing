package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.StudentToken;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentTokenRepository extends JpaRepository<StudentToken, UUID> {
    Optional<StudentToken> findByToken(String token);
    void deleteByExpiresAtBefore(java.time.LocalDateTime now);
}
