package com.qrmenu.staffaccess.repository;

import com.qrmenu.staffaccess.StaffUserBranch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserBranchRepository extends JpaRepository<StaffUserBranch, UUID> {

    List<StaffUserBranch> findAllByStaffUserId(UUID staffUserId);

    void deleteAllByStaffUserId(UUID staffUserId);
}
