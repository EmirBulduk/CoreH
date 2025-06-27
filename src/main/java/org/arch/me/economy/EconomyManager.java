package org.arch.me.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.arch.me.EnhancedCoreH;
import org.arch.me.database.DatabaseManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyManager {

    private final EnhancedCoreH plugin;
    private Economy vaultEconomy;
    private boolean useVault;

    public EconomyManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.useVault = false;
    }

    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        vaultEconomy = rsp.getProvider();
        useVault = vaultEconomy != null;

        plugin.getLogger().info("Economy system " + (useVault ? "enabled" : "disabled") + "!");
        return useVault;
    }

    // Player economy methods
    public BigDecimal getPlayerBalance(UUID playerId) {
        if (useVault) {
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
            return BigDecimal.valueOf(vaultEconomy.getBalance(player));
        }

        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT balance FROM %splayers WHERE uuid = ?".formatted(db.getTablePrefix());
            Double balance = db.queryObject(sql, rs -> rs.getDouble("balance"), playerId.toString());
            return balance != null ? BigDecimal.valueOf(balance) : BigDecimal.ZERO;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player balance: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public boolean hasPlayerBalance(UUID playerId, BigDecimal amount) {
        if (useVault) {
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
            return vaultEconomy.has(player, amount.doubleValue());
        }
        return getPlayerBalance(playerId).compareTo(amount) >= 0;
    }

    public CustomEconomyResponse withdrawPlayer(UUID playerId, BigDecimal amount) {
        if (useVault) {
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
            EconomyResponse response = vaultEconomy.withdrawPlayer(player, amount.doubleValue());
            return new CustomEconomyResponse(
                    BigDecimal.valueOf(response.amount),
                    BigDecimal.valueOf(response.balance),
                    response.transactionSuccess() ? CustomEconomyResponse.ResponseType.SUCCESS : CustomEconomyResponse.ResponseType.FAILURE,
                    response.errorMessage
            );
        }

        try {
            DatabaseManager db = plugin.getDatabaseManager();
            BigDecimal currentBalance = getPlayerBalance(playerId);

            if (currentBalance.compareTo(amount) < 0) {
                return new CustomEconomyResponse(amount, currentBalance, CustomEconomyResponse.ResponseType.FAILURE, "Insufficient funds");
            }

            BigDecimal newBalance = currentBalance.subtract(amount);
            String sql = "UPDATE %splayers SET balance = ? WHERE uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, newBalance.doubleValue(), playerId.toString());

            // Log transaction
            logTransaction(playerId, null, amount, "WITHDRAW", "Player withdrawal");

            return new CustomEconomyResponse(amount, newBalance, CustomEconomyResponse.ResponseType.SUCCESS, "");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to withdraw from player: " + e.getMessage());
            return new CustomEconomyResponse(amount, BigDecimal.ZERO, CustomEconomyResponse.ResponseType.FAILURE, "Database error");
        }
    }

    public CustomEconomyResponse depositPlayer(UUID playerId, BigDecimal amount) {
        if (useVault) {
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
            EconomyResponse response = vaultEconomy.depositPlayer(player, amount.doubleValue());
            return new CustomEconomyResponse(
                    BigDecimal.valueOf(response.amount),
                    BigDecimal.valueOf(response.balance),
                    response.transactionSuccess() ? CustomEconomyResponse.ResponseType.SUCCESS : CustomEconomyResponse.ResponseType.FAILURE,
                    response.errorMessage
            );
        }

        try {
            DatabaseManager db = plugin.getDatabaseManager();
            BigDecimal currentBalance = getPlayerBalance(playerId);
            BigDecimal newBalance = currentBalance.add(amount);

            String sql = "UPDATE %splayers SET balance = ? WHERE uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, newBalance.doubleValue(), playerId.toString());

            // Log transaction
            logTransaction(null, playerId, amount, "DEPOSIT", "Player deposit");

            return new CustomEconomyResponse(amount, newBalance, CustomEconomyResponse.ResponseType.SUCCESS, "");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to deposit to player: " + e.getMessage());
            return new CustomEconomyResponse(amount, BigDecimal.ZERO, CustomEconomyResponse.ResponseType.FAILURE, "Database error");
        }
    }

    // Town economy methods
    public BigDecimal getTownBalance(UUID townId) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT balance FROM %stowns WHERE uuid = ?".formatted(db.getTablePrefix());
            Double balance = db.queryObject(sql, rs -> rs.getDouble("balance"), townId.toString());
            return balance != null ? BigDecimal.valueOf(balance) : BigDecimal.ZERO;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get town balance: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public boolean hasTownBalance(UUID townId, BigDecimal amount) {
        return getTownBalance(townId).compareTo(amount) >= 0;
    }

    public boolean withdrawTown(UUID townId, BigDecimal amount) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            BigDecimal currentBalance = getTownBalance(townId);

            if (currentBalance.compareTo(amount) < 0) {
                return false;
            }

            BigDecimal newBalance = currentBalance.subtract(amount);
            String sql = "UPDATE %stowns SET balance = ? WHERE uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, newBalance.doubleValue(), townId.toString());

            // Log transaction
            logTransaction(townId, null, amount, "TOWN_WITHDRAW", "Town withdrawal");

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to withdraw from town: " + e.getMessage());
            return false;
        }
    }

    public boolean depositTown(UUID townId, BigDecimal amount) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            BigDecimal currentBalance = getTownBalance(townId);
            BigDecimal newBalance = currentBalance.add(amount);

            String sql = "UPDATE %stowns SET balance = ? WHERE uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, newBalance.doubleValue(), townId.toString());

            // Log transaction
            logTransaction(null, townId, amount, "TOWN_DEPOSIT", "Town deposit");

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to deposit to town: " + e.getMessage());
            return false;
        }
    }

    // Nation economy methods
    public BigDecimal getNationBalance(UUID nationId) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT balance FROM %snations WHERE uuid = ?".formatted(db.getTablePrefix());
            Double balance = db.queryObject(sql, rs -> rs.getDouble("balance"), nationId.toString());
            return balance != null ? BigDecimal.valueOf(balance) : BigDecimal.ZERO;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation balance: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public boolean hasNationBalance(UUID nationId, BigDecimal amount) {
        return getNationBalance(nationId).compareTo(amount) >= 0;
    }

    public boolean withdrawNation(UUID nationId, BigDecimal amount) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            BigDecimal currentBalance = getNationBalance(nationId);

            if (currentBalance.compareTo(amount) < 0) {
                return false;
            }

            BigDecimal newBalance = currentBalance.subtract(amount);
            String sql = "UPDATE %snations SET balance = ? WHERE uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, newBalance.doubleValue(), nationId.toString());

            // Log transaction
            logTransaction(nationId, null, amount, "NATION_WITHDRAW", "Nation withdrawal");

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to withdraw from nation: " + e.getMessage());
            return false;
        }
    }

    public boolean depositNation(UUID nationId, BigDecimal amount) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            BigDecimal currentBalance = getNationBalance(nationId);
            BigDecimal newBalance = currentBalance.add(amount);

            String sql = "UPDATE %snations SET balance = ? WHERE uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, newBalance.doubleValue(), nationId.toString());

            // Log transaction
            logTransaction(null, nationId, amount, "NATION_DEPOSIT", "Nation deposit");

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to deposit to nation: " + e.getMessage());
            return false;
        }
    }

    // Transfer methods
    public boolean transferPlayerToPlayer(UUID fromPlayer, UUID toPlayer, BigDecimal amount) {
        if (!hasPlayerBalance(fromPlayer, amount)) {
            return false;
        }

        CustomEconomyResponse withdraw = withdrawPlayer(fromPlayer, amount);
        if (withdraw.transactionSuccess()) {
            CustomEconomyResponse deposit = depositPlayer(toPlayer, amount);
            if (deposit.transactionSuccess()) {
                logTransaction(fromPlayer, toPlayer, amount, "PLAYER_TRANSFER", "Player to player transfer");
                return true;
            } else {
                // Rollback
                depositPlayer(fromPlayer, amount);
                return false;
            }
        }
        return false;
    }

    public boolean transferPlayerToTown(UUID playerId, UUID townId, BigDecimal amount) {
        if (!hasPlayerBalance(playerId, amount)) {
            return false;
        }

        CustomEconomyResponse withdraw = withdrawPlayer(playerId, amount);
        if (withdraw.transactionSuccess()) {
            if (depositTown(townId, amount)) {
                logTransaction(playerId, townId, amount, "PLAYER_TO_TOWN", "Player to town transfer");
                return true;
            } else {
                // Rollback
                depositPlayer(playerId, amount);
                return false;
            }
        }
        return false;
    }

    public boolean transferTownToPlayer(UUID townId, UUID playerId, BigDecimal amount) {
        if (!hasTownBalance(townId, amount)) {
            return false;
        }

        if (withdrawTown(townId, amount)) {
            CustomEconomyResponse deposit = depositPlayer(playerId, amount);
            if (deposit.transactionSuccess()) {
                logTransaction(townId, playerId, amount, "TOWN_TO_PLAYER", "Town to player transfer");
                return true;
            } else {
                // Rollback
                depositTown(townId, amount);
                return false;
            }
        }
        return false;
    }

    public boolean transferTownToNation(UUID townUuid, UUID nationUuid, BigDecimal amount) {
        if (!hasTownBalance(townUuid, amount)) {
            return false;
        }

        if (withdrawTown(townUuid, amount)) {
            if (depositNation(nationUuid, amount)) {
                logTransaction(townUuid, nationUuid, amount, "TOWN_TO_NATION", "Town to nation transfer");
                return true;
            } else {
                // Rollback - deposit money back to town
                depositTown(townUuid, amount);
                return false;
            }
        }

        return false;
    }

    // Utility methods
    public String format(BigDecimal amount) {
        if (useVault) {
            return vaultEconomy.format(amount.doubleValue());
        }
        return "$" + amount.toString();
    }

    private void logTransaction(UUID fromId, UUID toId, BigDecimal amount, String type, String description) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "INSERT INTO %stransactions (from_uuid, to_uuid, amount, type, description) VALUES (?, ?, ?, ?, ?)".formatted(db.getTablePrefix());
            db.executeUpdateAsync(sql,
                    fromId != null ? fromId.toString() : null,
                    toId != null ? toId.toString() : null,
                    amount.doubleValue(),
                    type,
                    description
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to log transaction: " + e.getMessage());
        }
    }

    // Async methods
    public CompletableFuture<BigDecimal> getPlayerBalanceAsync(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> getPlayerBalance(playerId));
    }

    public CompletableFuture<CustomEconomyResponse> withdrawPlayerAsync(UUID playerId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> withdrawPlayer(playerId, amount));
    }

    public CompletableFuture<CustomEconomyResponse> depositPlayerAsync(UUID playerId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> depositPlayer(playerId, amount));
    }

    // Getters
    public Economy getVaultEconomy() {
        return vaultEconomy;
    }

    public boolean isUsingVault() {
        return useVault;
    }

    // Custom EconomyResponse class since we need to handle both Vault and internal responses
    public static class CustomEconomyResponse {
        public final BigDecimal amount;
        public final BigDecimal balance;
        public final ResponseType type;
        public final String errorMessage;

        public CustomEconomyResponse(BigDecimal amount, BigDecimal balance, ResponseType type, String errorMessage) {
            this.amount = amount;
            this.balance = balance;
            this.type = type;
            this.errorMessage = errorMessage;
        }

        public boolean transactionSuccess() {
            return type == ResponseType.SUCCESS;
        }

        public enum ResponseType {
            SUCCESS,
            FAILURE,
            NOT_IMPLEMENTED
        }
    }
}