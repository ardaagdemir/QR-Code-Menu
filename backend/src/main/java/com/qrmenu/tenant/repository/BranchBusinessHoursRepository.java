package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.BranchBusinessHours;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchBusinessHoursRepository extends JpaRepository<BranchBusinessHours, UUID> {

    List<BranchBusinessHours> findAllByBranchId(UUID branchId);

    Optional<BranchBusinessHours> findByBranchIdAndDayOfWeek(UUID branchId, DayOfWeek dayOfWeek);

    void deleteAllByBranchId(UUID branchId);
}
