package org.arch.me.models;

import java.sql.Timestamp;
import java.util.*;

/**
 * Represents a war between nations and their allies
 */
public class War {
    private long id;
    private UUID declaringNationUuid;
    private UUID defendingNationUuid;
    private Set<UUID> declaringAllies;
    private Set<UUID> defendingAllies;
    private WarStatus status;
    private Timestamp declaredDate;
    private Timestamp startDate;
    private Timestamp endDate;
    private String warName;
    private Map<UUID, Object> warData;

    // Capitulation tracking
    private boolean capitulationInProgress;
    private UUID capitulationChunkId;
    private Set<UUID> playersInCapitalChunk;
    private Timestamp capitulationStartTime;
    private static final long CAPITULATION_DURATION = 2 * 60 * 1000; // 2 minutes in milliseconds

    public enum WarStatus {
        DECLARED,    // War declared but not started yet
        ACTIVE,      // War is active
        CAPITULATION, // Capitulation in progress
        ENDED_VICTORY, // War ended with victory
        ENDED_SURRENDER, // War ended with surrender
        ENDED_PEACE,  // War ended with peace treaty
        CANCELLED     // War was cancelled
    }

    public War(UUID declaringNationUuid, UUID defendingNationUuid, String warName) {
        this.declaringNationUuid = declaringNationUuid;
        this.defendingNationUuid = defendingNationUuid;
        this.warName = warName;
        this.declaringAllies = new HashSet<>();
        this.defendingAllies = new HashSet<>();
        this.status = WarStatus.DECLARED;
        this.declaredDate = new Timestamp(System.currentTimeMillis());
        this.warData = new HashMap<>();
        this.playersInCapitalChunk = new HashSet<>();
        this.capitulationInProgress = false;
    }

    // Getters and setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UUID getDeclaringNationUuid() {
        return declaringNationUuid;
    }

    public void setDeclaringNationUuid(UUID declaringNationUuid) {
        this.declaringNationUuid = declaringNationUuid;
    }

    public UUID getDefendingNationUuid() {
        return defendingNationUuid;
    }

    public void setDefendingNationUuid(UUID defendingNationUuid) {
        this.defendingNationUuid = defendingNationUuid;
    }

    public Set<UUID> getDeclaringAllies() {
        return declaringAllies;
    }

    public void setDeclaringAllies(Set<UUID> declaringAllies) {
        this.declaringAllies = declaringAllies;
    }

    public Set<UUID> getDefendingAllies() {
        return defendingAllies;
    }

    public void setDefendingAllies(Set<UUID> defendingAllies) {
        this.defendingAllies = defendingAllies;
    }

    public WarStatus getStatus() {
        return status;
    }

    public void setStatus(WarStatus status) {
        this.status = status;
    }

    public Timestamp getDeclaredDate() {
        return declaredDate;
    }

    public void setDeclaredDate(Timestamp declaredDate) {
        this.declaredDate = declaredDate;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public String getWarName() {
        return warName;
    }

    public void setWarName(String warName) {
        this.warName = warName;
    }

    public Map<UUID, Object> getWarData() {
        return warData;
    }

    public void setWarData(Map<UUID, Object> warData) {
        this.warData = warData;
    }

    public boolean isCapitulationInProgress() {
        return capitulationInProgress;
    }

    public void setCapitulationInProgress(boolean capitulationInProgress) {
        this.capitulationInProgress = capitulationInProgress;
    }

    public UUID getCapitulationChunkId() {
        return capitulationChunkId;
    }

    public void setCapitulationChunkId(UUID capitulationChunkId) {
        this.capitulationChunkId = capitulationChunkId;
    }

    public Set<UUID> getPlayersInCapitalChunk() {
        return playersInCapitalChunk;
    }

    public void setPlayersInCapitalChunk(Set<UUID> playersInCapitalChunk) {
        this.playersInCapitalChunk = playersInCapitalChunk;
    }

    public Timestamp getCapitulationStartTime() {
        return capitulationStartTime;
    }

    public void setCapitulationStartTime(Timestamp capitulationStartTime) {
        this.capitulationStartTime = capitulationStartTime;
    }

    // Utility methods
    public boolean isActive() {
        return status == WarStatus.ACTIVE || status == WarStatus.CAPITULATION;
    }

    public boolean isEnded() {
        return status == WarStatus.ENDED_VICTORY ||
               status == WarStatus.ENDED_SURRENDER ||
               status == WarStatus.ENDED_PEACE ||
               status == WarStatus.CANCELLED;
    }

    // War participation and enemy checks
    public boolean isParticipant(UUID nationUuid) {
        return declaringNationUuid.equals(nationUuid) ||
               defendingNationUuid.equals(nationUuid) ||
               declaringAllies.contains(nationUuid) ||
               defendingAllies.contains(nationUuid);
    }

    public boolean areEnemies(UUID nation1, UUID nation2) {
        boolean nation1Declaring = isDeclaringSide(nation1);
        boolean nation2Declaring = isDeclaringSide(nation2);

        // They are enemies if they are on different sides
        return nation1Declaring != nation2Declaring;
    }

    public boolean isDeclaringSide(UUID nationUuid) {
        return declaringNationUuid.equals(nationUuid) || declaringAllies.contains(nationUuid);
    }

    public boolean isDefendingSide(UUID nationUuid) {
        return defendingNationUuid.equals(nationUuid) || defendingAllies.contains(nationUuid);
    }

    public Set<UUID> getAllDeclaringSide() {
        Set<UUID> all = new HashSet<>();
        all.add(declaringNationUuid);
        all.addAll(declaringAllies);
        return all;
    }

    public Set<UUID> getAllDefendingSide() {
        Set<UUID> all = new HashSet<>();
        all.add(defendingNationUuid);
        all.addAll(defendingAllies);
        return all;
    }

    public Set<UUID> getAllParticipants() {
        Set<UUID> all = new HashSet<>();
        all.addAll(getAllDeclaringSide());
        all.addAll(getAllDefendingSide());
        return all;
    }

    // Ally management
    public void addDeclaringAlly(UUID nationUuid) {
        declaringAllies.add(nationUuid);
    }

    public void addDefendingAlly(UUID nationUuid) {
        defendingAllies.add(nationUuid);
    }

    public void removeDeclaringAlly(UUID nationUuid) {
        declaringAllies.remove(nationUuid);
    }

    public void removeDefendingAlly(UUID nationUuid) {
        defendingAllies.remove(nationUuid);
    }

    public void addPlayerToCapitalChunk(UUID playerUuid) {
        playersInCapitalChunk.add(playerUuid);
        if (!capitulationInProgress && !playersInCapitalChunk.isEmpty()) {
            startCapitulation();
        }
    }

    public void removePlayerFromCapitalChunk(UUID playerUuid) {
        playersInCapitalChunk.remove(playerUuid);
        if (playersInCapitalChunk.isEmpty() && capitulationInProgress) {
            stopCapitulation();
        }
    }

    public void startCapitulation() {
        this.capitulationInProgress = true;
        this.capitulationStartTime = new Timestamp(System.currentTimeMillis());
        this.status = WarStatus.CAPITULATION;
    }

    public void stopCapitulation() {
        this.capitulationInProgress = false;
        this.capitulationStartTime = null;
        this.status = WarStatus.ACTIVE;
        this.playersInCapitalChunk.clear();
    }

    public boolean isCapitulationComplete() {
        if (!capitulationInProgress || capitulationStartTime == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - capitulationStartTime.getTime();
        return elapsed >= CAPITULATION_DURATION;
    }

    public long getCapitulationTimeRemaining() {
        if (!capitulationInProgress || capitulationStartTime == null) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - capitulationStartTime.getTime();
        return Math.max(0, CAPITULATION_DURATION - elapsed);
    }

    public String getFormattedTimeRemaining() {
        long remaining = getCapitulationTimeRemaining();
        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        War war = (War) o;
        return id == war.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "War{" +
                "id=" + id +
                ", warName='" + warName + '\'' +
                ", status=" + status +
                ", declaringNation=" + declaringNationUuid +
                ", defendingNation=" + defendingNationUuid +
                '}';
    }

    public boolean hasPlayersInCapitalChunk() {
        return !playersInCapitalChunk.isEmpty();
    }
}
