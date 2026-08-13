package com.qrmenu.shared.media;

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
}
