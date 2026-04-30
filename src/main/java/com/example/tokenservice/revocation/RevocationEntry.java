package com.example.tokenservice.revocation;

import java.time.LocalDateTime;

public class RevocationEntry {

    private final String identifier;
    private final String reason;
    private final LocalDateTime revokedAt;

    public RevocationEntry(String identifier, String reason, LocalDateTime revokedAt) {
        this.identifier = identifier;
        this.reason = reason;
        this.revokedAt = revokedAt;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
