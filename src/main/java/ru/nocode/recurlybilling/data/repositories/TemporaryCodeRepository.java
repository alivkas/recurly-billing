package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.TemporaryCode;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemporaryCodeRepository extends JpaRepository<TemporaryCode, UUID> {
    Optional<TemporaryCode> findByTenantIdAndStudentExternalIdAndCodeAndIsUsedFalse(
            String tenantId, String studentExternalId, String code);
}
