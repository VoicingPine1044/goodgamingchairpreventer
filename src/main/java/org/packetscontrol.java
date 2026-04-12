package org;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class packetscontrol extends JavaPlugin implements Listener {

    private static final String BYPASS_PERMISSION = "packetscontrol.creator";

    private static final String freecam_key        = "text.autoconfig.freecam.title";
    private static final String flashback       = "flashback.select_replay";
    private static final String healthindicator_key = "yacl3.config.healthindicators:config.category.messages.group.commands";
    private static final String hitirange_key    = "hitrange.name";

    private final Map<UUID, BlockData> originalBlockData = new HashMap<>();
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(this, ListenerPriority.HIGHEST, PacketType.Play.Client.UPDATE_SIGN) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        Player player = event.getPlayer();
                        if (!pendingPlayers.remove(player.getUniqueId())) return;

                        event.setCancelled(true);

                        String[] lines = event.getPacket().getStringArrays().read(0);
                        if (lines == null) return;

                        String line0 = lines.length > 0 ? lines[0] : "";
                        String line1 = lines.length > 1 ? lines[1] : "";
                        String line2 = lines.length > 2 ? lines[2] : "";
                        String line3 = lines.length > 3 ? lines[3] : "";

                        getLogger().info("[PacketsControl] " + player.getName() + " raw lines: ["
                                + line0 + "] [" + line1 + "] [" + line2 + "] [" + line3 + "]");

                        getServer().getScheduler().runTask(packetscontrol.this, () -> {
                            if (!player.isOnline()) return;

                            if (line0.contains("Mod Commands")) {
                                kick(player, "Health Indicator Detected. Please disable the mod and try again!");
                            } else if (line1.contains("HitRange")) {
                                kick(player, "HitRange Detected. Please disable the mod and try again!");
                            } else if (line2.contains("Freecam Options")) {
                                kick(player, "FreeCam Detected. Please disable the mod and try again!");
                            } else if (line3.contains("Select Replay")) {
                                kick(player, "Flashback Detected. Please disable the mod and try again!");
                            }
                        });
                    }
                }
        );

        getLogger().info("PacketsControl enabled.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) return;

        getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            openSignEditor(player);
        }, 1L);
    }

    private void openSignEditor(Player player) {
        try {
            Block block = player.getLocation().clone().add(0, -5, 0).getBlock();
            originalBlockData.put(player.getUniqueId(), block.getBlockData());

            block.setType(Material.OAK_SIGN);
            Sign sign = (Sign) block.getState();
            sign.getSide(Side.BACK).line(0, Component.translatable(healthindicator_key));
            sign.getSide(Side.BACK).line(1, Component.translatable(hitirange_key));
            sign.getSide(Side.BACK).line(2, Component.translatable(freecam_key));
            sign.getSide(Side.BACK).line(3, Component.translatable(flashback));
            sign.setWaxed(false);
            sign.update(true, false);

            getServer().getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) {
                    restoreBlock(player.getUniqueId(), block);
                    return;
                }
                if (!(block.getState() instanceof Sign freshSign)) {
                    restoreBlock(player.getUniqueId(), block);
                    return;
                }
                pendingPlayers.add(player.getUniqueId());
                player.openSign(freshSign, Side.BACK);
                player.closeInventory();
            }, 7L);

        } catch (Exception e) {
            getLogger().warning("Failed to open sign editor for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        pendingPlayers.remove(uuid);

        Block block = player.getLocation().clone().add(0, -5, 0).getBlock();
        restoreBlock(uuid, block);
    }

    private void kick(Player player, String modName) {
        if (player.isOnline()) {
            getLogger().info("[PacketsControl] " + player.getName() + " detected using: " + modName);
            getServer().dispatchCommand(
                    getServer().getConsoleSender(),
                    "kick " + player.getName() + " Mod detected: " + modName
            );
        }
    }

    private void restoreBlock(UUID uuid, Block block) {
        BlockData original = originalBlockData.remove(uuid);
        if (original != null) {
            getServer().getScheduler().runTask(this, () -> block.setBlockData(original));
        }
    }
}
