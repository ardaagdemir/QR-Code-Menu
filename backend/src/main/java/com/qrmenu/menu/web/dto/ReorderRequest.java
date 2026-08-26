package com.qrmenu.menu.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Generic reorder payload reused for categories/products/option groups/options: the
 * caller sends the full sibling set in its desired order, and the service assigns
 * displayOrder = index. Reused rather than one record per level since the shape is
 * identical everywhere reorder applies.
 */
public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {
}
