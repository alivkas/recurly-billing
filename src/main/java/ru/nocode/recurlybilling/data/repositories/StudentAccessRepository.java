package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.StudentAccess;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentAccessRepository extends JpaRepository<StudentAccess, UUID> {
    List<StudentAccess> findByStudentIdAndPlanCodeAndStatus(UUID studentId, String planCode, StudentAccess.AccessStatus status);
    List<StudentAccess> findByAccessExpiresAtBeforeAndStatus(LocalDate date, StudentAccess.AccessStatus status);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
            "FROM StudentAccess a " +
            "WHERE a.studentId = :studentId " +
            "AND a.planCode = :planCode " +
            "AND a.accessExpiresAt >= :today " +
            "AND a.status = 'ACTIVE'")
    boolean existsActiveAccess(@Param("studentId") String studentId,
                               @Param("planCode") String planCode,
                               @Param("today") LocalDate today);

    boolean existsByStudentIdAndTenantId(UUID studentId, String tenantId);
}
