package com.qrmenu.customersession.repository;

import com.qrmenu.customersession.TableVisit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableVisitRepository extends JpaRepository<TableVisit, UUID> {

    Optional<TableVisit> findFirstByAnonymousCustomerSessionIdAndTableIdOrderByStartedAtDesc(
            UUID anonymousCustomerSessionId, UUID tableId);
}
