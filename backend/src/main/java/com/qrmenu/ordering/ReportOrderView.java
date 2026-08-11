package com.qrmenu.ordering;

import java.util.List;

/** Gap-analysis #8 reporting: one paid order plus its items, as the reporting module needs to aggregate sales. */
public record ReportOrderView(CustomerOrder order, List<OrderItem> items) {
}
