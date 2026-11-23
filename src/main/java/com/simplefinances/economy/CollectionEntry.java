package com.simplefinances.economy;

/**
 * A collectible coin donated to the shared server collection.
 * The coin item itself is gone forever; only this record remains.
 */
public record CollectionEntry(String tier, String description, String date, String recipient,
                              String addedBy, long addedAt) {
}
