package com.qrmenu.shared.media;

/** Bytes read back from storage by key, plus the content type sniffed at store time. */
public record LoadedMedia(byte[] content, String contentType) {
}
