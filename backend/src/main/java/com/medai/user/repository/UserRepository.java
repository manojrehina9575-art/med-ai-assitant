package com.medai.user.repository;

import com.medai.user.entity.User;
import com.medai.user.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    Page<User> findByTenantIdAndRole(UUID tenantId, UserRole role, Pageable pageable);

    /** Active holders of a role, for critical-result escalation. Deactivated accounts are skipped. */
    List<User> findByTenantIdAndRoleAndIsActiveTrue(UUID tenantId, UserRole role);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    long countByTenantId(UUID tenantId);
}
