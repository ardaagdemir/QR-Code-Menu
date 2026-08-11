package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.QrTokenStatus;
import com.qrmenu.tenant.TableQrToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableQrTokenRepository extends JpaRepository<TableQrToken, UUID> {

    Optional<TableQrToken> findByToken(String token);

    Optional<TableQrToken> findByTableIdAndStatus(UUID tableId, QrTokenStatus status);

    Optional<TableQrToken> findByIdAndBusinessId(UUID id, UUID businessId);
}
