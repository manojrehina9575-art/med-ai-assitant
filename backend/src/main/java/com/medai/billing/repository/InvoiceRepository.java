package com.medai.billing.repository;

import com.medai.billing.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Invoice> findByTenantIdOrderByPeriodStartDesc(UUID tenantId);

    Optional<Invoice> findByTenantIdAndPeriodStartAndPeriodEnd(
            UUID tenantId, LocalDate periodStart, LocalDate periodEnd);

    /** A UUID is not something an accounts department can quote down a phone line. */
    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    Long nextInvoiceNumber();
}
