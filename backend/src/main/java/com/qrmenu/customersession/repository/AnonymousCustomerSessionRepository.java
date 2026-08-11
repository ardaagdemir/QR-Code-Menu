package com.qrmenu.customersession.repository;

import com.qrmenu.customersession.AnonymousCustomerSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonymousCustomerSessionRepository extends JpaRepository<AnonymousCustomerSession, UUID> {
}
