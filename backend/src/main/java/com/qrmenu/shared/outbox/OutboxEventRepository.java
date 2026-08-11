package com.qrmenu.shared.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Lives directly in shared.outbox (no .repository subpackage) because shared is
 * explicitly "not a module" (Section 3: "shared-kernel (modül değil, ortak çekirdek)")
 * - unlike tenant/customersession/menu/ordering, ModuleBoundaryTest does not - and
 * should not - restrict access to it; every module writes outbox events directly, the
 * same way every module already uses shared.Money directly.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findAllByPublishedAtIsNullOrderByCreatedAtAsc();
}
