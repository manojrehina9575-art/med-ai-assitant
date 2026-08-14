package com.medai.common.entity;

import com.medai.tenant.TenantContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Base entity for all tenant-scoped tables.
 * The Hibernate filter automatically adds {@code WHERE tenant_id = :tenantId}
 * to all queries when the filter is enabled in the session.
 * This works alongside PostgreSQL Row-Level Security (V3 migration) for
 * defense-in-depth tenant isolation.
 */
@MappedSuperclass
@Getter
@Setter
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    public void prePersist() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.requireTenantId();
        }
    }
}
