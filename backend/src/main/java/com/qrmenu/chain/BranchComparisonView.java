package com.qrmenu.chain;

import java.util.UUID;

/** One row of the gap-analysis #7 chain comparison dashboard (Section 13.2, non-financial scope only). */
public record BranchComparisonView(UUID branchId, String branchName, long orderCount, long tableVisitCount) {
}
