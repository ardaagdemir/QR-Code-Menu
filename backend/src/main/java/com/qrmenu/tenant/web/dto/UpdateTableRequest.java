package com.qrmenu.tenant.web.dto;

import com.qrmenu.tenant.TableLocation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Masa Düzenle: isim, konum ve kapasite (opsiyonel) bir arada güncellenir. */
public record UpdateTableRequest(@NotBlank String label, @NotNull TableLocation location, Integer capacity) {
}
