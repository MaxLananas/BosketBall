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

public final class RewardCrateStation {

    public static final String ENTITY_TAG = "bosketball_crate";

    private static final int OPEN_ANIMATION_TICKS = 28;

    private static final int RING_STRIDE = 2;

    private static final int RING_INTERPOLATION = 2;

    private static final int ORBIT_COUNT = 12;

    private static final int OPEN_DELAY_TICKS = 10;

    private static final String[] RARITIES = {"ᴄᴏᴍᴍᴏɴ", "ʀᴀʀᴇ", "ᴇᴘɪᴄ", "ʟᴇɢᴇɴᴅᴀʀʏ"};

    private final BosketBallPlugin plugin;
    private final Random rng = new Random();
    private final Map<UUID, Long> lastOpenMillis = new ConcurrentHashMap<>();

    private Interaction clickBox;

    private final List<ItemDisplay> orbitParts = new ArrayList<>();

    private final List<ItemDisplay> burstParts = new ArrayList<>();
    private TextDisplay banner;
    private TextDisplay costLine;
    private Location baseLocation;
    private float ringAngle = 0f;
    private int openAnimationTicks = 0;
    private int tickCounter = 0;
    private int hoverTicks = 0;

    public RewardCrateStation(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

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

        double lift = Double.isFinite(spec.lift()) && spec.lift() > 0 ? spec.lift() : 1.0;
        return new Location(world, x, y + lift, z, yawFor(facing), 0f);
    }

    public boolean enabled() {
        return plugin.settings().lobby().stationsExtra().crate().usable();
    }

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
        baseLocation = base.clone();

        world.setChunkForceLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4, true);

        clickBox = world.spawn(base.clone().add(0, 0.15, 0), Interaction.class, box -> {
            box.setPersistent(false);
            box.addScoreboardTag(ENTITY_TAG);
            box.setInteractionWidth(1.4f);
            box.setInteractionHeight(1.8f);
            box.setResponsive(true);
        });

        for (int i = 0; i < ORBIT_COUNT; i++) {
            double ang = (Math.PI * 2.0 / ORBIT_COUNT) * i;
            double radius = i < ORBIT_COUNT / 2 ? 0.78 : 0.52;
            double height = i < ORBIT_COUNT / 2 ? 0.30 : 0.92;
            Location slot = base.clone().add(Math.cos(ang) * radius, height, Math.sin(ang) * radius);
            Material token = switch (i % 5) {
                case 0 -> Material.GOLD_INGOT;
                case 1 -> Material.EMERALD;
                case 2 -> Material.AMETHYST_SHARD;
                case 3 -> Material.DIAMOND;
                default -> Material.GOLD_NUGGET;
            };
            ItemDisplay orbit = world.spawn(slot, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(ENTITY_TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setInterpolationDuration(RING_INTERPOLATION);
                d.setTeleportDuration(RING_INTERPOLATION);
                d.setViewRange(1.6f);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setItemStack(new ItemStack(token));
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0.42f, 0.42f, 0.42f), new Quaternionf()
                ));
            });
            orbitParts.add(orbit);
        }

        for (int i = 0; i < 12; i++) {
            ItemDisplay shard = world.spawn(base.clone().add(0, -2.5, 0), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(ENTITY_TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setInterpolationDuration(RING_INTERPOLATION);
                d.setTeleportDuration(RING_INTERPOLATION);
                d.setViewRange(1.8f);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setItemStack(new ItemStack(Material.GOLD_NUGGET));
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()
                ));
            });
            burstParts.add(shard);
        }

        banner = world.spawn(base.clone().add(0, 1.72, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.4f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setSeeThrough(false);
            d.text(Msg.parse("<#FFD43B><bold>🎁 ʀᴇᴡᴀʀᴅ ᴄʀᴀᴛᴇ</bold></#FFD43B>"));
        });

        costLine = world.spawn(base.clone().add(0, 1.40, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.1f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setSeeThrough(false);
            d.text(costText());
        });

        tick();
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
        orbitParts.clear();
        burstParts.clear();
        clickBox = null;
        banner = null;
        costLine = null;
        baseLocation = null;
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

    public boolean isCrateEntity(org.bukkit.entity.Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(ENTITY_TAG);
    }

    public void tick() {
        tickCounter++;

        if (openAnimationTicks > 0) {
            openAnimationTicks--;
        }

        if (tickCounter % 100 == 0 && (orbitParts.isEmpty() || orbitParts.get(0) == null || !orbitParts.get(0).isValid())) {
            spawn();
            return;
        }
        if (baseLocation == null || orbitParts.isEmpty()) {
            return;
        }
        // The ring is a lobby prop: animating 12 display entities for an empty street is pure waste.
        if (!fr.bosketball.util.Viewers.anyWithin(baseLocation, 48.0)) {
            return;
        }
        if ((tickCounter & (RING_STRIDE - 1)) != 0) {
            return;
        }
        hoverTicks++;

        boolean opening = openAnimationTicks > 0;
        float spin = opening ? 0.32f : 0.07f;
        ringAngle += spin;
        for (int i = 0; i < orbitParts.size(); i++) {
            ItemDisplay orbit = orbitParts.get(i);
            if (orbit == null || !orbit.isValid()) {
                continue;
            }
            boolean inner = (i % 2) == 1;
            double radius = (inner ? 0.52 : 0.82) + (opening ? 0.10 : 0.0);
            double direction = inner ? -1.0 : 1.0;
            double ang = ringAngle * direction + (Math.PI * 2.0 / orbitParts.size()) * i;
            double bob = Math.sin(hoverTicks * 0.09 + i * 1.7) * 0.09;
            double height = (inner ? 0.92 : 0.45) + bob + (opening ? 0.25 : 0.0);
            orbit.teleport(baseLocation.clone().add(
                Math.cos(ang) * radius,
                height,
                Math.sin(ang) * radius
            ));
            float pulse = (float) (0.42 + Math.sin(hoverTicks * 0.13 + i) * 0.03);
            orbit.setInterpolationDelay(0);
            orbit.setInterpolationDuration(RING_INTERPOLATION);
            orbit.setTeleportDuration(RING_INTERPOLATION);
            orbit.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf().rotateY(ringAngle * 2f + i),
                new Vector3f(pulse, pulse, pulse),
                new Quaternionf()
            ));
        }

        if (openAnimationTicks > 0) {
            for (int i = 0; i < burstParts.size(); i++) {
                ItemDisplay shard = burstParts.get(i);
                if (shard == null || !shard.isValid()) {
                    continue;
                }
                double age = (OPEN_ANIMATION_TICKS - openAnimationTicks) * 0.5;
                double ang = (Math.PI * 2.0 / burstParts.size()) * i + ringAngle;
                double radius = 0.18 + age * 0.22;
                double up = 0.55 + Math.sin(Math.min(Math.PI * 0.5, age * 0.32)) * 1.05;
                shard.teleport(baseLocation.clone().add(
                    Math.cos(ang) * radius, up, Math.sin(ang) * radius));
                float fade = (float) Math.max(0.001, 0.22 * (openAnimationTicks / (double) OPEN_ANIMATION_TICKS));
                shard.setInterpolationDelay(0);
                shard.setInterpolationDuration(RING_INTERPOLATION);
                shard.setTeleportDuration(RING_INTERPOLATION);
                shard.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf().rotateY((float) (ang * 3.0)),
                    new Vector3f(fade, fade, fade),
                    new Quaternionf()
                ));
            }
        }

        float labelBob = (float) (Math.sin(hoverTicks * 0.06) * 0.035);
        if (banner != null && banner.isValid()) {
            banner.teleport(baseLocation.clone().add(0, 1.72 + labelBob, 0));
        }
        if (costLine != null && costLine.isValid()) {
            costLine.teleport(baseLocation.clone().add(0, 1.40 + labelBob, 0));
        }

        if (tickCounter % 4 == 0 && baseLocation != null) {
            World w = baseLocation.getWorld();
            if (w != null) {
                w.spawnParticle(Particle.END_ROD, baseLocation.clone().add(0, 0.55, 0), 1, 0.45, 0.35, 0.45, 0.0);
                if (tickCounter % 40 == 0) {
                    w.spawnParticle(Particle.WAX_ON, baseLocation.clone().add(0, 0.25, 0), 6, 0.55, 0.2, 0.55, 0.0);
                }
            }
        }
    }

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
        Msg.playPersonalPackSound(player, "ui.click", 1.0f, 1.1f);

        RollOutcome outcome = roll(profile);

        plugin.statsManager().markDirty(player.getUniqueId());
        plugin.statsManager().saveProfileAsync(profile);
        if (baseLocation != null) {
            World w = baseLocation.getWorld();
            if (w != null) {
                w.spawnParticle(Particle.WAX_ON, baseLocation.clone().add(0, 0.4, 0), 24, 0.5, 0.4, 0.5, 0.02);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            openAnimationTicks = OPEN_ANIMATION_TICKS;
            if (baseLocation != null) {
                World w = baseLocation.getWorld();
                if (w != null) {
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, baseLocation.clone().add(0, 0.7, 0), 70, 0.55, 0.65, 0.55, 0.22);
                    w.spawnParticle(Particle.FIREWORK, baseLocation.clone().add(0, 0.8, 0), 30, 0.45, 0.5, 0.45, 0.12);
                    w.playSound(baseLocation, org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.4f, 1.0f);
                }
            }
            if (player.isOnline()) {
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.4f, 1.0f);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 1.25f);
            }
        }, OPEN_DELAY_TICKS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> reveal(player, profile, outcome, crates.costCoins()), OPEN_DELAY_TICKS + 6L);
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

        int bonusCoins = 0;
        if (outcome.bonus()) {
            bonusCoins = plugin.settings().crates().bonusCoins();
            profile.addEarnedCoins(bonusCoins);
        }
        if (player == null || !player.isOnline()) {
            plugin.statsManager().markDirty(profile.uuid());
            plugin.statsManager().saveProfileAsync(profile);
            return;
        }
        plugin.statsManager().markDirty(player.getUniqueId());
        plugin.statsManager().saveProfileAsync(profile);
        plugin.progressService().evaluateAchievements(profile, true);

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
            plugin.integration().onCrateOpened(player, outcome.label(), outcome.rarity(), cost, outcome.coins());
        }

        plugin.progressService().record(profile, fr.bosketball.model.ProgressStat.QUEUE_JOINS, 1);
    }

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
