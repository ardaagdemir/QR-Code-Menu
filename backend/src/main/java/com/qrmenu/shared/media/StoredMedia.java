package com.qrmenu.shared.media;

/** key is the storage-relative path (e.g. "product-images/{uuid}.jpg"), url is publicly fetchable. */
public record StoredMedia(String key, String url) {
}
