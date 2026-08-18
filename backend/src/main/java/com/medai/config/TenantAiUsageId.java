package com.medai.config;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link TenantAiUsage}: one row per tenant per day. */
public class TenantAiUsageId implements Serializable {

    private UUID tenantId;
    private LocalDate usageDate;

    public TenantAiUsageId() {
    }

    public TenantAiUsageId(UUID tenantId, LocalDate usageDate) {
        this.tenantId = tenantId;
        this.usageDate = usageDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantAiUsageId other)) return false;
        return Objects.equals(tenantId, other.tenantId) && Objects.equals(usageDate, other.usageDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, usageDate);
    }
}
