package com.example.tokenservice.revocation;

public interface TokenRevocationStore {

    void revokeByJwtId(String jwtId, String reason);

    void revokeByTokenValue(String tokenValue, String reason);

    boolean isRevoked(String jwtId, String tokenValue);

    void removeFromBlacklist(String jwtId);

    void clearExpiredEntries(long maxAgeSeconds);

    long getBlacklistSize();
}
