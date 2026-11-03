package fr.bosketball.lobby;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The interactive lobby Reward Crate.
 *
 * <p>A sculpted 3D crate (chest body + lid + rotating loot ring + floating banner) stands at
 * the configured station — by default {@code 120 / 62 / 61} facing <b>West</b>. Clicking it
 * opens the crate for Coins: the lid hinges open with a particle burst, a reward is rolled
 * (coins, a cosmetic or a rare bonus), and the result is announced in chat plus Discord.</p>
 *
 * <p>Rules live in {@code crates:} (cost, daily limit, cooldown, cosmetic chance).</p>
 */
public final class RewardCrateStation {

    /** Scoreboard tag marking every entity owned by this station. */
    public static final String ENTITY_TAG = "bosketball_crate";

    private static final String[] RARITIES = {"ᴄᴏᴍᴍᴏɴ", "ʀᴀʀᴇ", "ᴇᴘɪᴄ", "ʟᴇɢᴇɴᴅᴀʀʏ"};

    private final BosketBallPlugin plugin;
    private final Random rng = new Random();
    private final Map<UUID, Long> lastOpenMillis = new ConcurrentHashMap<>();

    private Interaction clickBox;
    private ItemDisplay crateBody;
    private ItemDisplay crateLid;
    private ItemDisplay lootRing;
    private TextDisplay banner;
    private TextDisplay costLine;
    private float ringAngle = 0f;
    private int openAnimationTicks = 0;
    private int tickCounter = 0;

    public RewardCrateStation(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  SPAWN
    // =========================================================================

    /**
     * Resolves the configured crate pose.
     *
     * <p>Resolution order (first match wins):</p>
     * <ol>
     *   <li>{@code lobby.stations-extra.crate.*} in {@code config.yml} (the documented default),</li>
     *   <li>{@code stations.yml} — only written when an admin moved the crate with the panel,</li>
     *   <li>the hard-coded fallback {@code 120 / 62+lift / 61} facing <b>West</b>.</li>
     * </ol>
     */
    public Location resolveLocation() {
        var spec = plugin.settings().lobby().stationsExtra().crate();
        YamlConfiguration yml = loadPoseOverrides();
        String worldName = yml.getString("crate.world", spec.world());
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }
        double x = yml.getDouble("crate.x", spec.x());
        double y = yml.getDouble("crate.y", spec.y());
        double z = yml.getDouble("crate.z", spec.z());
        String facing = yml.getString("crate.facing", spec.facing());
        // Default: x 120, y 62 (floor) + 2 blocks above, z 61, facing WEST.
        return new Location(world, x, y + spec.lift(), z, yawFor(facing), 0f);
    }

    /** True when {@code lobby.stations-extra.crate.enabled} is on (config.yml). */
    public boolean enabled() {
        return plugin.settings().lobby().stationsExtra().crate().usable();
    }

    /** Compass string → yaw (Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east). */
    public static float yawFor(String facing) {
        if (facing == null) {
            return 90f;
        }
        return switch (facing.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "north", "n" -> 180f;
            case "south", "s" -> 0f;
            case "east", "e" -> 270f;
            case "west", "w" -> 90f;
            default -> 90f;
        };
    }

    private YamlConfiguration loadPoseOverrides() {
        File f = new File(plugin.getDataFolder(), "stations.yml");
        if (!f.exists()) {
            return new YamlConfiguration();
        }
        return fr.bosketball.config.AtomicYamlWriter.loadSafe(f, plugin.getLogger());
    }

    /** Saves a custom crate pose (admin places it with {@code /bb crate set}). */
    public void savePose(Location loc) {
        File f = new File(plugin.getDataFolder(), "stations.yml");
        YamlConfiguration yml = fr.bosketball.config.AtomicYamlWriter.loadSafe(f, plugin.getLogger());
        yml.set("crate.world", loc.getWorld().getName());
        yml.set("crate.x", loc.getX());
        yml.set("crate.y", loc.getY() - 2.0);
        yml.set("crate.z", loc.getZ());
        String facing = switch (Math.round(((loc.getYaw() % 360) + 360) % 360 / 90f) % 4) {
            case 0 -> "south";
            case 1 -> "west";
            case 2 -> "north";
            default -> "east";
        };
        yml.set("crate.facing", facing);
        fr.bosketball.config.AtomicYamlWriter.writeYamlAtomically(f,
            "# BosketBall — custom lobby station poses\n", yml, plugin.getLogger());
    }

    public void spawn() {
        despawn();
        if (!enabled() || !plugin.settings().crates().enabled()) {
            return;
        }
        Location base = resolveLocation();
        if (base == null) {
            return;
        }
        World world = base.getWorld();
        // Keep the station chunk resident so the crate never blinks out while the lobby is empty.
        world.setChunkForceLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4, true);

        crateBody = world.spawn(base, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setViewRange(1.6f);
            d.setBrightness(new Display.Brightness(15, 15));
            // ItemModel has no display transform, so an item/generated sprite would render as
            // a flat card. A real block model keeps the crate physical from every angle.
            d.setItemStack(new ItemStack(Material.CHEST));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(
                new Vector3f(0f, -0.5f, 0f), new Quaternionf().rotateY((float) Math.toRadians(90)),
                new Vector3f(1.05f, 1.05f, 1.05f), new Quaternionf()
            ));
        });

        // The lid is the top slice of the chest, scaled flat so it reads as a hinge line and
        // tilts back when the crate opens.
        crateLid = world.spawn(base.clone().add(0, 0.58, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setViewRange(1.6f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setItemStack(new ItemStack(Material.CHEST));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(
                new Vector3f(0f, -0.5f, 0f), new Quaternionf().rotateY((float) Math.toRadians(90)),
                new Vector3f(1.06f, 0.30f, 1.06f), new Quaternionf()
            ));
        });

        // Invisible interaction box: players aim at the crate, but the clickable volume is a
        // full 1.4-block cube centred on it, so partial aim never misses.
        clickBox = world.spawn(base.clone().add(0, 0.15, 0), Interaction.class, box -> {
            box.setPersistent(false);
            box.addScoreboardTag(ENTITY_TAG);
            box.setInteractionWidth(1.4f);
            box.setInteractionHeight(1.8f);
            box.setResponsive(true);
        });

        lootRing = world.spawn(base.clone().add(0, -0.55, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setViewRange(1.6f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setItemStack(modelItem(Material.PAPER, "ring_gold", 1.0f));
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f), new Quaternionf(),
                new Vector3f(1.30f, 1.0f, 1.30f), new Quaternionf()
            ));
        });

        banner = world.spawn(base.clone().add(0, 1.35, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.4f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setSeeThrough(false);
            d.text(Msg.parse("<#FFD43B><bold>🎁 ʀᴇᴡᴀʀᴅ ᴄʀᴀᴛᴇ</bold></#FFD43B>"));
        });

        costLine = world.spawn(base.clone().add(0, -0.10, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.1f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setSeeThrough(false);
            d.text(costText());
        });
    }

    private Component costText() {
        var crates = plugin.settings().crates();
        return Msg.parse("<#FDE047>" + crates.costCoins() + " Coins</#FDE047> <dark_gray>•</dark_gray> "
            + "<gray>Right-Click to open</gray>");
    }

    public void despawn() {
        for (org.bukkit.entity.Entity e : collectEntities()) {
            e.remove();
        }
        crateBody = null;
        crateLid = null;
        clickBox = null;
        lootRing = null;
        banner = null;
        costLine = null;
    }

    private List<org.bukkit.entity.Entity> collectEntities() {
        List<org.bukkit.entity.Entity> out = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(ENTITY_TAG)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    /** True when the clicked entity belongs to the crate station. */
    public boolean isCrateEntity(org.bukkit.entity.Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(ENTITY_TAG);
    }

    // =========================================================================
    //  ANIMATION
    // =========================================================================

    public void tick() {
        tickCounter++;
        // Self-heal: non-persistent displays vanish when their chunk unloads or on a hot /reload.
        if (tickCounter % 100 == 0 && (lootRing == null || !lootRing.isValid())) {
            spawn();
            return;
        }
        if (lootRing == null || !lootRing.isValid()) {
            return;
        }
        ringAngle += 0.035f;
        lootRing.setInterpolationDelay(0);
        lootRing.setInterpolationDuration(2);
        lootRing.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            new Quaternionf().rotateY(ringAngle),
            new Vector3f(1.30f, 1.0f + (float) (Math.sin(tickCounter * 0.08) * 0.05), 1.30f),
            new Quaternionf()
        ));

        // Lid hinges open then closes over ~24 ticks after a reveal
        if (crateLid != null && crateLid.isValid()) {
            if (openAnimationTicks > 0) {
                openAnimationTicks--;
                float tilt = (float) Math.sin((1.0 - openAnimationTicks / 24.0) * Math.PI) * -0.9f;
                crateLid.setInterpolationDelay(0);
                crateLid.setInterpolationDuration(2);
                crateLid.setTransformation(new Transformation(
                    new Vector3f(0f, -0.5f, 0f),
                    new Quaternionf().rotateX(tilt).rotateY((float) Math.toRadians(90)),
                    new Vector3f(1.06f, 0.30f, 1.06f), new Quaternionf()
                ));
            }
        }

        // Idle shimmer
        if (tickCounter % 6 == 0 && crateBody != null && crateBody.isValid()) {
            crateBody.getWorld().spawnParticle(Particle.END_ROD,
                crateBody.getLocation().add(0, 0.2, 0), 1, 0.35, 0.25, 0.35, 0.0);
        }
    }

    // =========================================================================
    //  OPENING
    // =========================================================================

    /** Player interacted with the crate station. */
    public void handleClick(Player player) {
        var crates = plugin.settings().crates();
        if (!crates.enabled()) {
            Msg.send(player, Msg.warn("The Reward Crate is currently disabled."));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastOpenMillis.get(player.getUniqueId());
        if (last != null && now - last < crates.cooldownSeconds() * 1000L) {
            Msg.send(player, Msg.warn("The crate is still cooling down…"));
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

        long today = now / (24L * 60L * 60L * 1000L);
        if (profile.crateDayEpoch() != today) {
            profile.setCrateDayEpoch(today);
            profile.setDailyCratesOpened(0);
        }
        if (profile.dailyCratesOpened() >= crates.dailyLimit()) {
            Msg.send(player, Msg.error("Daily crate limit reached <white>(" + crates.dailyLimit()
                + "/" + crates.dailyLimit() + ")</white>. Come back tomorrow!"));
            return;
        }
        if (!profile.spendCoins(crates.costCoins())) {
            Msg.send(player, Msg.error("You need <white><bold>" + crates.costCoins()
                + " Coins</bold></white> to open the crate (you have " + profile.coins() + ")."));
            Msg.playPersonalPackSound(player, "ui.click", 0.8f, 0.7f);
            return;
        }

        lastOpenMillis.put(player.getUniqueId(), now);
        profile.setDailyCratesOpened(profile.dailyCratesOpened() + 1);
        openAnimationTicks = 24;
        Msg.playPersonalPackSound(player, "ui.click", 1.0f, 1.1f);

        RollOutcome outcome = roll(profile);
        // Delayed reveal for drama
        Bukkit.getScheduler().runTaskLater(plugin, () -> reveal(player, profile, outcome, crates.costCoins()), 14L);
        plugin.statsManager().markDirty(player.getUniqueId());
    }

    private record RollOutcome(String label, String rarity, int coins, String cosmeticKind, String cosmeticName, boolean bonus) {
    }

    private RollOutcome roll(PlayerProfile profile) {
        var crates = plugin.settings().crates();
        boolean cosmetic = rng.nextDouble() < crates.cosmeticChance();
        boolean bonus = rng.nextDouble() < crates.bonusChance();

        if (cosmetic) {
            int pick = rng.nextInt(3);
            if (pick == 0) {
                List<BallSkin> locked = new ArrayList<>();
                for (BallSkin skin : BallSkin.values()) {
                    if (!profile.hasUnlockedBall(skin)) {
                        locked.add(skin);
                    }
                }
                if (!locked.isEmpty()) {
                    BallSkin skin = locked.get(rng.nextInt(locked.size()));
                    profile.unlockBall(skin);
                    profile.setSelectedBallSkin(skin);
                    return new RollOutcome(fr.bosketball.util.SmallCaps.toSmallCaps(stripMini(skin.displayNameMm())) + " Basketball", "ʀᴀʀᴇ", 0, "BALL", skin.name(), bonus);
                }
            } else if (pick == 1) {
                List<DunkStyle> locked = new ArrayList<>();
                for (DunkStyle style : DunkStyle.values()) {
                    if (!profile.hasUnlockedDunk(style)) {
                        locked.add(style);
                    }
                }
                if (!locked.isEmpty()) {
                    DunkStyle style = locked.get(rng.nextInt(locked.size()));
                    profile.unlockDunk(style);
                    profile.setSelectedDunkStyle(style);
                    return new RollOutcome(fr.bosketball.util.SmallCaps.toSmallCaps(stripMini(style.displayNameMm())) + " Dunk", "ᴇᴘɪᴄ", 0, "DUNK", style.name(), bonus);
                }
            } else {
                List<ChatTag> locked = new ArrayList<>();
                for (ChatTag tag : ChatTag.values()) {
                    if (tag.canBeUsedBy(profile, profile.lastKnownName(), false) && !profile.hasUnlockedTag(tag)) {
                        locked.add(tag);
                    }
                }
                if (!locked.isEmpty()) {
                    ChatTag tag = locked.get(rng.nextInt(locked.size()));
                    profile.unlockTag(tag);
                    return new RollOutcome(fr.bosketball.util.SmallCaps.toSmallCaps(stripMini(tag.displayName())) + " Chat Tag", "ʟᴇɢᴇɴᴅᴀʀʏ", 0, "TAG", tag.name(), bonus);
                }
            }
        }

        int coins = Math.max(crates.coinMin(), crates.coinMax() > crates.coinMin()
            ? crates.coinMin() + rng.nextInt(Math.max(1, crates.coinMax() - crates.coinMin() + 1))
            : crates.coinMin());
        profile.addEarnedCoins(coins);
        String rarity = coins > (crates.coinMin() + crates.coinMax()) / 2 ? "ʀᴀʀᴇ" : "ᴄᴏᴍᴍᴏɴ";
        return new RollOutcome(coins + " Coins", rarity, coins, null, null, bonus);
    }

    private void reveal(Player player, PlayerProfile profile, RollOutcome outcome, int cost) {
        if (!player.isOnline()) {
            return;
        }
        int bonusCoins = 0;
        if (outcome.bonus()) {
            bonusCoins = plugin.settings().crates().bonusCoins();
            profile.addEarnedCoins(bonusCoins);
        }
        plugin.statsManager().markDirty(player.getUniqueId());
        plugin.statsManager().saveProfileAsync(profile);
        plugin.progressService().evaluateAchievements(profile);

        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 60, 0.6, 0.7, 0.6, 0.25);
        player.getWorld().spawnParticle(Particle.FIREWORK, loc, 30, 0.5, 0.5, 0.5, 0.1);
        Msg.playPersonalPackSound(player, "crowd.cheer", 1.1f, 1.15f);

        Msg.title(player,
            "<#FDE047><bold>🎁 " + outcome.rarity() + "</bold></#FDE047>",
            "<white><bold>" + outcome.label() + "</bold></white>",
            120, 1800, 350);

        List<String> body = new ArrayList<>();
        body.add("<gray>You opened a Reward Crate for</gray> " + Msg.coinsBadge(cost));
        body.add("<#FDE047>Reward:</#FDE047> <white><bold>" + outcome.label() + "</bold></white> <gray>(" + outcome.rarity() + ")</gray>");
        if (bonusCoins > 0) {
            body.add("<#4ADE80>✦ RARE BONUS:</#4ADE80> " + Msg.coinsBadge(bonusCoins));
        }
        body.add("<gray>Openings today:</gray> <white>" + profile.dailyCratesOpened() + "/"
            + plugin.settings().crates().dailyLimit() + "</white>");
        Msg.sendFramedCard(player, "REWARD CRATE", body);

        if (plugin.integration() != null) {
            plugin.integration().onCrateOpened(player, outcome.label(), outcome.rarity(), cost);
        }
        // Achievement/quest hook for the "open 10 crates" style objectives
        plugin.progressService().record(profile, fr.bosketball.model.ProgressStat.QUEUE_JOINS, 1);
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    /** Removes MiniMessage tags so a label can be re-styled or sent as plain text. */
    private static String stripMini(String mini) {
        return mini == null ? "" : mini.replaceAll("<[^>]+>", "");
    }

    private static ItemStack modelItem(Material material, String modelId, float unusedScale) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", modelId));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
