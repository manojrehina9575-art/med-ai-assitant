package com.medai.billing.repository;

import com.medai.billing.entity.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, UUID> {

    List<InvoiceLineItem> findByInvoiceIdOrderBySortOrderAsc(UUID invoiceId);

    void deleteByInvoiceId(UUID invoiceId);
}
