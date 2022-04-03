package love.toad;

import java.util.logging.Logger;
import java.util.HashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;

class TimeOfDay {
    float time;
    float remains;
    boolean night;
    String event;
    BarColor barColor;
    TimeOfDay(float t, float r, boolean n, String e, BarColor c)
    {
        time = t;
        remains = r;
        night = n;
        event = e;
        barColor = c;
    }
}

enum DisplayState {
    OFF,
    ON,
    ON_WITH_MOON,
}

public class Lodestar extends JavaPlugin implements Listener, CommandExecutor {
    Logger log = Logger.getLogger("Minecraft");
    public final HashMap<UUID, BukkitTask> tasks = new HashMap<>();
    private final NamespacedKey keys = new NamespacedKey(this, "state");
    private final DisplayState[] reversed = new DisplayState[]{DisplayState.OFF, DisplayState.ON, DisplayState.ON_WITH_MOON};
    private static final int TICK_UPDATE_PERIOD = 1;
    private static final String[] moonPhases = {"full", "waning gibbous", "last", "waning crescent", "new", "waxing crescent", "first", "waxing gibbous"};
    final boolean DAY_COUNT_DOWN = false;
    final float DAY_LENGTH = 12000;
    final float SUNSET_LENGTH = 1000;
    final float NIGHT_LENGTH = 10000;
    final float SUNRISE_LENGTH = 1000;
    final float FULL_DAY_LENGTH = SUNRISE_LENGTH + DAY_LENGTH + SUNSET_LENGTH;
    final float FULL_NIGHT_LENGTH = NIGHT_LENGTH;
    final float TOTAL_LENGTH = FULL_DAY_LENGTH + FULL_NIGHT_LENGTH;
    final float DAY_START = 0;
    final float SUNSET_START = 12000;
    final float NIGHT_START = 13000;
    final float SUNRISE_START = 23000;
    public enum Compass {
        EIGHT_POINT,
        SIXTEEN_POINT
    }
    final static double YAW_OFFSET = 180.0;
    final static double CIRCLE = 360.0;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        log.info("[Lodestar] Enabled");
    }

    @Override
    public void onDisable() {
        tasks.forEach((uuid, task) -> {
            task.cancel();
            NamespacedKey barKey = new NamespacedKey(this, uuid.toString());
            KeyedBossBar bar = this.getServer().getBossBar(barKey);
            if (bar != null) {
                bar.removeAll();
                this.getServer().removeBossBar(barKey);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DisplayState restoredState = getPlayerDisplayState(player);
        if (restoredState == DisplayState.ON || restoredState == DisplayState.ON_WITH_MOON) {
            createBossBarLocationDisplay(player, restoredState == DisplayState.ON_WITH_MOON);
            player.sendMessage(ChatColor.GREEN + "Lodestar enabled, type /lodestar to disable");
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        if (tasks.containsKey(event.getPlayer().getUniqueId())) {
            destroyBossBarLocationDisplay(event.getPlayer());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("lodestar")) {
            if (sender instanceof Player) {
                final Player player = (Player) sender;
                DisplayState currentState = getPlayerDisplayState(player);

                if (args.length == 1) {
                    if (args[0].equalsIgnoreCase("debug")) {
                        // https://minecraft.fandom.com/el/wiki/Day-night_cycle#Minecraft_time_to_real_time
                        int hour = (int) ((player.getLocation().getWorld().getTime() / 1000) + 6) % 24;
                        TimeOfDay timeOfDay = getTimeOfDay(player);
                        player.sendMessage(ChatColor.GRAY + String.format("event=%s hour=%d time=%f remains=%.02f%% night=%b",
                            timeOfDay.event, hour, timeOfDay.time, timeOfDay.remains * 100, timeOfDay.night));
                        return true;
                    } else if (args[0].equalsIgnoreCase("moon")) {
                        player.sendMessage(ChatColor.GRAY + "Usage: /lodestar moon [ on | off ]");
                        return true;
                    }
                } else if (args.length == 2) {
                    if (args[0].equalsIgnoreCase("moon")) {
                        if (args[1].equalsIgnoreCase("on")) {
                            destroyBossBarLocationDisplay(player);
                            createBossBarLocationDisplay(player, true);
                            setPlayerDisplayState(player, DisplayState.ON_WITH_MOON);
                            player.sendMessage(ChatColor.GREEN + "Moon enabled, type '/lodestar moon off' to disable");
                            return true;
                        } else if (args[1].equalsIgnoreCase("off")) {
                            destroyBossBarLocationDisplay(player);
                            createBossBarLocationDisplay(player, false);
                            setPlayerDisplayState(player, DisplayState.ON);
                            player.sendMessage(ChatColor.GREEN + "Moon disabled, type '/lodestar moon on' to enable again");
                            return true;
                        }
                    }
                }

                if (currentState == DisplayState.ON || currentState == DisplayState.ON_WITH_MOON) {
                    destroyBossBarLocationDisplay(player);
                    setPlayerDisplayState(player, DisplayState.OFF);
                    player.sendMessage(ChatColor.GRAY + "Lodestar disabled");
                    log.info("[Lodestar] Disabled by " + player.getName());
                } else {
                    createBossBarLocationDisplay(player, currentState == DisplayState.ON_WITH_MOON);
                    setPlayerDisplayState(player, DisplayState.ON);
                    player.sendMessage(ChatColor.GREEN + "Lodestar enabled");
                    log.info("[Lodestar] Enabled by " + player.getName());
                }
            } else {
                sender.sendMessage("Not a console command");
                return false;
            }
        }
        return true;
    }

    public void createBossBarLocationDisplay(Player player, boolean moon) {
        this.destroyBossBarLocationDisplay(player);
        BossBar bar = createBossBar(player);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                TimeOfDay timeOfDay = getTimeOfDay(player);
                bar.setTitle(getLocationString(player, timeOfDay, moon));
                bar.setProgress(timeOfDay.remains);
                bar.setColor(timeOfDay.barColor);
            }
        }.runTaskTimer(this, 0, TICK_UPDATE_PERIOD);
        tasks.put(player.getUniqueId(), task);
    }

    /*
     * minecraft time of day
     *
     * day = 0 to 12000
     * sunset = 12000 to 13000
     * night = 13000 to 23000
     * sunrise = 23000 to 24000
     *
     * https://minecraft.fandom.com/wiki/Daylight_cycle
     */
    public TimeOfDay getTimeOfDay(Player player) {
        float time = (float) player.getLocation().getWorld().getTime();

        float remains = 0;
        boolean night = false;
        String event = "never";
        BarColor color = BarColor.WHITE;

        if (time >= SUNRISE_START) {
            // Wrap sunrise around so it's negative and before day
            time = -(TOTAL_LENGTH - time);
        }

        if (time >= NIGHT_START) {
            remains = (SUNRISE_START - time)/FULL_NIGHT_LENGTH;
            event = "night";
            color = BarColor.BLUE;
            night = true;
        } else {
            // Include the sunrise length so it captures the full length
            float adjustedTime = time + SUNRISE_LENGTH;
            remains = adjustedTime/FULL_DAY_LENGTH;
            if (DAY_COUNT_DOWN) {
                remains = FULL_DAY_LENGTH - remains;
            }

            if (time < DAY_START) {
                event = "sunrise";
                color = BarColor.PURPLE;
            } else if (time >= DAY_START && time < SUNSET_START) {
                event = "day";
                color = BarColor.YELLOW;
            } else if (time >= SUNSET_START) {
                event = "sunset";
                color = BarColor.RED;
            }
        }

        return new TimeOfDay(time, remains, night, event, color);
    }

    private BossBar createBossBar(Player player) {
        KeyedBossBar bar = this.getServer().createBossBar(new NamespacedKey(this, player.getUniqueId().toString()),
                "Initializing...", BarColor.WHITE, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setProgress(.5);

        return bar;
    }

    public void destroyBossBarLocationDisplay(Player player) {
        UUID uuid = player.getUniqueId();

        NamespacedKey barKey = new NamespacedKey(this, uuid.toString());
        KeyedBossBar bar = this.getServer().getBossBar(barKey);
        if (bar != null) {
            bar.removeAll();
            this.getServer().removeBossBar(barKey);
        }

        if (tasks.containsKey(uuid)) {
            tasks.get(uuid).cancel();
            tasks.remove(uuid);
        }
    }

   public DisplayState getPlayerDisplayState(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (!container.has(keys, PersistentDataType.BYTE)) {
            return DisplayState.OFF;
        }
        Byte encodedState = player.getPersistentDataContainer().get(keys, PersistentDataType.BYTE);
        if (encodedState != 0b0 && encodedState != 0b01 && encodedState != 0b10) {
            encodedState = 0b0;
            setPlayerDisplayState(player, DisplayState.OFF);
            log.info(String.format("oops: %s display state OOB, resetting to OFF", player.getName()));
        }
        return reversed[encodedState];
    }

    public void setPlayerDisplayState(Player player, DisplayState newState) {
        int data = 0b0;
        if (newState == DisplayState.ON) {
            data = 0b01;
        } else if (newState == DisplayState.ON_WITH_MOON) {
            data = 0b10;
        }
        player.getPersistentDataContainer().set(keys, PersistentDataType.BYTE, (byte) data);
    }

    private String getLocationString(Player player, TimeOfDay timeOfDay, boolean moon) {
        Location loc = player.getLocation();
        String location = String.format("%6s %6s %6s",
            "&7x &f" + loc.getBlockX(),
            "&7y &f" + loc.getBlockY(),
            "&7z &f" + loc.getBlockZ());
        String direction = getCardinalDirection(loc.getYaw(), Compass.SIXTEEN_POINT);

        if (moon) {
            int moonPhase = (int) (player.getWorld().getFullTime() / 24000L) % 8;
            return ChatColor.translateAlternateColorCodes('&', String.format("%10s  %2s  %s %.0f%%  %s",
                location, direction, timeOfDay.event, timeOfDay.remains*100, moonPhases[moonPhase]));
        } else {
            return ChatColor.translateAlternateColorCodes('&', String.format("%10s  %2s  %s %.0f%%",
                location, direction, timeOfDay.event, timeOfDay.remains*100));
        }
    }

    public static String getCardinalDirection(double yaw, Compass compass) {
        double rotation = (yaw + YAW_OFFSET) % CIRCLE;
        if (rotation < 0.0) {
            rotation += CIRCLE;
        }

        switch (compass) {
        case EIGHT_POINT:
            if (0.0 <= rotation && rotation < 22.5) return "N";
            if (22.5 <= rotation && rotation < 67.5) return "NE";
            if (67.5 <= rotation && rotation < 112.5) return "E";
            if (112.5 <= rotation && rotation < 157.5) return "SE";
            if (157.5 <= rotation && rotation < 202.5) return "S";
            if (202.5 <= rotation && rotation < 247.5) return "SW";
            if (247.5 <= rotation && rotation < 292.5) return "W";
            if (292.5 <= rotation && rotation < 337.5) return "NW";
            if (337.5 <= rotation && rotation < 360.0) return "N";
            break;

        case SIXTEEN_POINT:
            if (0.00 <= rotation && rotation < 11.25) return "N";
            if (11.25 <= rotation && rotation < 33.75) return "NNE";
            if (33.75 <= rotation && rotation < 56.25) return "NE";
            if (56.25 <= rotation && rotation < 78.75) return "ENE";
            if (78.75 <= rotation && rotation < 101.25) return "E";
            if (101.25 <= rotation && rotation < 123.75) return "ESE";
            if (123.75 <= rotation && rotation < 146.25) return "SE";
            if (146.25 <= rotation && rotation < 168.75) return "SSE";
            if (168.75 <= rotation && rotation < 191.25) return "S";
            if (191.25 <= rotation && rotation < 213.75) return "SSW";
            if (213.75 <= rotation && rotation < 236.25) return "SW";
            if (236.25 <= rotation && rotation < 258.75) return "WSW";
            if (258.75 <= rotation && rotation < 281.25) return "W";
            if (281.25 <= rotation && rotation < 303.75) return "WNW";
            if (303.75 <= rotation && rotation < 326.25) return "NW";
            if (326.25 <= rotation && rotation < 348.75) return "NNW";
            if (348.75 <= rotation && rotation < 360.00) return "N";
            break;
        }

        return null;
    }

    public static String getCardinalDirection(Player player, Compass compass) {
        return getCardinalDirection(player.getLocation().getYaw(), compass);
    }
}
