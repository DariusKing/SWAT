package com.division.swat.core;

import com.division.battlegrounds.core.Battleground;
import com.division.battlegrounds.core.BattlegroundPlayer;
import com.division.battlegrounds.core.Gametype;
import com.division.battlegrounds.event.BattlegroundJoinEvent;
import com.division.battlegrounds.event.BattlegroundQuitEvent;
import com.division.battlegrounds.event.RoundEndEvent;
import com.division.battlegrounds.event.RoundStartEvent;
import com.division.battlegrounds.mech.FriendlyFireBypass;
import com.division.battlegrounds.region.Region;
import com.division.swat.config.SWConfig;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 *
 * @author Evan
 */
public class SWATBattleground extends Battleground {

    private Set<Player> teamRed = new HashSet<Player>();
    private Set<Player> teamBlue = new HashSet<Player>();
    private SWATGametype swGametype;

    public SWATBattleground(String name, Gametype gametype, Region region) {
        super(name, gametype, region);
        this.setMinPlayers(SWConfig.getMinPlayers());
        this.setMaxPlayers(SWConfig.getMaxPlayers());
        this.setDynamic(true);
        this.swGametype = (SWATGametype) gametype;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent evt) {
        if (!(evt.getEntity() instanceof Player)) {
            return;
        }

        Player defender = (Player) evt.getEntity();
        if (!isPlayerInBattleground(defender)) {
            return;
        }
        if (evt.getCause() == DamageCause.FALL) {
            evt.setCancelled(true);
            return;
        }
        EntityDamageByEntityEvent edee;
        if (evt instanceof EntityDamageByEntityEvent) {
            edee = (EntityDamageByEntityEvent) evt;
        } else {
            return;
        }
        Entity eAttacker = checkSource(edee.getDamager());
        if (eAttacker instanceof Player) {
            Player attacker = (Player) eAttacker;
            if (isOnSameTeam(attacker, defender)) {
                evt.setCancelled(true);
                return;
            }
            if (evt.isCancelled()) {
                FriendlyFireBypass.damage(defender, true, evt.getDamage());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent evt) {
        Player player = evt.getEntity();
        if (!isPlayerInBattleground(player)) {
            return;
        }
        evt.getDrops().clear();
        if (teamRed.contains(player)) {
            swGametype.incrementRedScore();
        } else {
            swGametype.incrementBlueScore();
        }
    }

    public boolean isOnSameTeam(Player attacker, Player defender) {
        if ((teamRed.contains(attacker) && teamRed.contains(defender)) || (teamBlue.contains(attacker) && teamBlue.contains(defender))) {
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent evt) {
        Player player = evt.getPlayer();
        if (isPlayerInBattleground(player)) {
            System.out.println("[SWATBG] Attempting to respawn player: " + player.getName());
            evt.setRespawnLocation(getSpawnPoint(player));
            Bukkit.getServer().getScheduler().runTaskLater(SWAT.instance, new ResupplyRunnable(player), 5L);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBattlegroundLeave(BattlegroundQuitEvent evt) {
        if (evt.getBattleground() == this) {
            evt.getPlayer().setHealth(20);
            evt.getPlayer().setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBattlegroundJoin(BattlegroundJoinEvent evt) {
        if (evt.getBattleground() == this) {
            distributeToTeam(evt.getPlayer());
            this.getQueue().remove(evt.getPlayer());
            this.sendToSpawnPoint(evt.getPlayer());
            evt.getPlayer().setScoreboard(swGametype.getScoreboard());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRoundPrep(RoundStartEvent evt) {
        if (!evt.getBattleground().getName().equals(this.getName())) {
            System.out.println("[SWATBG] not a SWAT roundStart, ignoring event.");
            return;
        }
        Random rand = new Random();
        Player[] players = evt.getPlayersToBeAdded().toArray(new Player[0]);
        for (Player p : players) {
            boolean rndBool = rand.nextBoolean();
            if (rndBool) {
                if (!(teamBlue.size() >= (this.getMinPlayers() / 2))) {
                    System.out.println("[SWATBG] Player: " + p.getName() + " has been added to blue team.");
                    handleBlueJoin(p);
                } else {
                    System.out.println("[SWATBG] Player: " + p.getName() + " has been added to red team.");
                    handleRedJoin(p);
                }
            } else {
                if (!(teamRed.size() >= (this.getMinPlayers() / 2))) {
                    System.out.println("[SWATBG] Player: " + p.getName() + " has been added to red team.");
                    handleRedJoin(p);
                } else {
                    System.out.println("[SWATBG] Player: " + p.getName() + " has been added to blue team.");
                    handleBlueJoin(p);
                }
            }
            this.inBattleground.put(new BattlegroundPlayer(p.getName()), p.getLocation());
            this.getQueue().remove(p);
            p.setScoreboard(swGametype.getScoreboard());
        }
        Set<BattlegroundPlayer> inGamePlayers = this.inBattleground.keySet();
        System.out.println("Got to spawn code.");
        for (BattlegroundPlayer bgPlayer : inGamePlayers) {
            System.out.println("[SWATBG] Player: " + bgPlayer.getName() + " was sent to their spawn point.");
            this.sendToSpawnPoint(bgPlayer.getPlayer());
        }
        System.out.println("Got past spawn code.");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void handleRoundEnd(RoundEndEvent evt) {
        if (evt.getBattleground() == this) {
            teamRed.clear();
            teamBlue.clear();
            swGametype.resetScoreboard();
        }
    }

    public Entity checkSource(Entity source) {
        if (source instanceof Player) {
            return source;
        }
        if ((source instanceof Projectile) && (((Projectile) source).getShooter() instanceof Player)) {
            return ((Projectile) source).getShooter();
        }
        if ((source instanceof ThrownPotion) && (((ThrownPotion) source).getShooter() instanceof Player)) {
            return ((ThrownPotion) source).getShooter();
        }
        return null;
    }

    public void sendToSpawnPoint(Player player) {
        Location red = SWConfig.getRedSpawn();
        Location blue = SWConfig.getBlueSpawn();
        if (teamRed.contains(player)) {
            player.teleport(red);
        } else {
            player.teleport(blue);
        }
        this.reSupply(player);
    }

    public Location getSpawnPoint(Player player) {
        Location red = SWConfig.getRedSpawn();
        Location blue = SWConfig.getBlueSpawn();
        if (teamRed.contains(player)) {
            return red;
        } else {
            return blue;
        }
    }

    public void reSupply(Player player) {
        player.setHealth(2);
        player.setFoodLevel(15);
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[inv.getArmorContents().length]);
        inv.addItem(new ItemStack(Material.BOW, 1));
        inv.addItem(new ItemStack(Material.ARROW, 64));
    }

    private void handleRedJoin(Player player) {
        player.setScoreboard(swGametype.getScoreboard());
        teamRed.add(player);
    }

    private void handleBlueJoin(Player player) {
        player.setScoreboard(swGametype.getScoreboard());
        teamBlue.add(player);
    }

    public void distributeToTeam(Player player) {
        int redCount = teamRed.size();
        int blueCount = teamBlue.size();
        if (redCount > blueCount) {
            handleBlueJoin(player);
        } else if (blueCount > redCount) {
            handleRedJoin(player);
        } else {
            Random rand = new Random();
            if (rand.nextBoolean()) {
                handleBlueJoin(player);
            } else {
                handleRedJoin(player);
            }
        }
    }

    public class ResupplyRunnable implements Runnable {

        private Player target;

        public ResupplyRunnable(Player player) {
            this.target = player;
        }

        @Override
        public void run() {
            reSupply(target);
        }
    }
}
