package com.qrmenu.chain.web.dto;

import java.util.UUID;

public record BranchComparisonResponse(UUID branchId, String branchName, long orderCount, long tableVisitCount) {
}
