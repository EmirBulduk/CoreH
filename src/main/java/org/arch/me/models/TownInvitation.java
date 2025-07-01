package org.arch.me.models;

import java.util.UUID;

/**
 * Represents a town invitation
 */
public class TownInvitation {
    private UUID invitationId;
    private UUID townUuid;
    private UUID inviterUuid;
    private UUID inviteeUuid;
    private long createdAt;
    private long expiresAt;
    private boolean accepted;
    private boolean declined;

    public TownInvitation(UUID townUuid, UUID inviterUuid, UUID inviteeUuid) {
        this.invitationId = UUID.randomUUID();
        this.townUuid = townUuid;
        this.inviterUuid = inviterUuid;
        this.inviteeUuid = inviteeUuid;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + (5 * 60 * 1000); // 5 minutes
        this.accepted = false;
        this.declined = false;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isPending() {
        return !accepted && !declined && !isExpired();
    }

    public long getTimeRemaining() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public String getFormattedTimeRemaining() {
        long remaining = getTimeRemaining() / 1000;
        long minutes = remaining / 60;
        long seconds = remaining % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // Getters and Setters
    public UUID getInvitationId() { return invitationId; }
    public void setInvitationId(UUID invitationId) { this.invitationId = invitationId; }

    public UUID getTownUuid() { return townUuid; }
    public void setTownUuid(UUID townUuid) { this.townUuid = townUuid; }

    public UUID getInviterUuid() { return inviterUuid; }
    public void setInviterUuid(UUID inviterUuid) { this.inviterUuid = inviterUuid; }

    public UUID getInviteeUuid() { return inviteeUuid; }
    public void setInviteeUuid(UUID inviteeUuid) { this.inviteeUuid = inviteeUuid; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }

    public boolean isDeclined() { return declined; }
    public void setDeclined(boolean declined) { this.declined = declined; }
}
