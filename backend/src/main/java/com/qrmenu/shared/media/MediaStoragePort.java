package com.qrmenu.shared.media;

import java.util.Optional;

/**
 * Section 3.2: "domain doğrudan dosya binary'si tutmaz; bir media/storage adapter'ın
 * döndürdüğü URL/key saklanır" - provider-independent, same shape as
 * PaymentProviderPort/OwnerNotificationPort. LocalFileMediaStorageAdapter is the only
 * implementation today (Section 27, ❓ open decision: production storage provider not
 * chosen yet); a future S3/GCS-backed adapter can implement this same port without
 * touching any caller.
 */
public interface MediaStoragePort {

    /**
     * Validates (content-type sniffing + size) and persists the given bytes under the
     * category's own rules. Throws MediaValidationException (-> 400) if the content is
     * empty, too large, or not a recognized/allowed type for the category.
     */
    StoredMedia store(MediaCategory category, byte[] content);

    /** Reads back the bytes stored under {@code key} (as returned in {@link StoredMedia#key()}). */
    Optional<LoadedMedia> load(String key);

    /**
     * Recovers the storage key from a public URL previously returned by {@link #store}, so a
     * caller that only persisted the URL (e.g. Expense.receiptImageUrl) can still look the file
     * up through an authenticated path instead of the public one. Empty if the URL doesn't look
     * like one this adapter produced.
     */
    Optional<String> resolveKeyFromUrl(String url);
}
