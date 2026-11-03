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

    /** Ticks the lid stays open; the chat reveal fires at half that so the loot lands mid-swing. */
    private static final int LID_ANIMATION_TICKS = 28;
    /** Half a second of anticipation before the lid throws open. */
    private static final int OPEN_DELAY_TICKS = 10;

    private static final String[] RARITIES = {"ᴄᴏᴍᴍᴏɴ", "ʀᴀʀᴇ", "ᴇᴘɪᴄ", "ʟᴇɢᴇɴᴅᴀʀʏ"};

    private final BosketBallPlugin plugin;
    private final Random rng = new Random();
    private final Map<UUID, Long> lastOpenMillis = new ConcurrentHashMap<>();

    private Interaction clickBox;
    /** One display piece with the offset/scale it was built with, so animation never re-derives them. */
    private static final class Part {
        final ItemDisplay display;
        final Vector3f offset;
        final float scale;

        Part(ItemDisplay display, Vector3f offset, float scale) {
            this.display = display;
            this.offset = offset;
            this.scale = scale;
        }
    }

    /** Chest body slab + base band: a real 3D crate instead of a floating chest item. */
    private final List<Part> bodyParts = new ArrayList<>();
    /** Hinged lid: pivots around the back edge, so it swings open like a treasure chest. */
    private final List<Part> lidParts = new ArrayList<>();
    /** Orbiting loot cards shown while idle (8 small items on a ring). */
    private final List<ItemDisplay> orbitParts = new ArrayList<>();
    /** Golden beam fired straight up when the crate opens. */
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
        // Default: x 120, y 62 (floor) + 1 block, z 61, facing WEST. One block above the floor is
        // the correct height for a 1.1-block crate: at the old 2-block lift the idle bob and the
        // hovering cost text left the chest visually detached from its pedestal.
        double lift = Double.isFinite(spec.lift()) && spec.lift() > 0 ? spec.lift() : 1.0;
        return new Location(world, x, y + lift, z, yawFor(facing), 0f);
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
        baseLocation = base.clone();
        // Keep the station chunk resident so the crate never blinks out while the lobby is empty.
        world.setChunkForceLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4, true);

        // ── BODY: a chest body slab (bottom 9/16 of the model) + a dark base band. Two slabs
        // stacked read as a solid, bevelled block from every angle; a single chest item floated.
        spawnPart(world, base.clone().add(0, 0.02, 0), Material.CHEST, 1.12f, new Vector3f(0f, 1.0f, 0f),
            new Quaternionf().rotateY((float) Math.toRadians(90)), bodyParts);
        spawnPart(world, base.clone().add(0, -0.24, 0), Material.BARREL, 0.98f, new Vector3f(1.0f, 0.30f, 1.0f),
            new Quaternionf().rotateY((float) Math.toRadians(90)), bodyParts);
        // Brass corner studs: tiny gold blocks at the four uprights, the detail that makes it look
        // crafted rather than dropped in from a creative inventory.
        for (int i = 0; i < 4; i++) {
            double ang = Math.toRadians(45 + i * 90);
            Location stud = base.clone().add(Math.cos(ang) * 0.46, 0.30, Math.sin(ang) * 0.46);
            spawnPart(world, stud, Material.GOLD_BLOCK, 0.13f, new Vector3f(1f, 1f, 1f), new Quaternionf(), bodyParts);
        }

        // ── LID: hinged around the back edge (north-west of the crate once rotated), so opening
        // it tilts the whole slab back instead of translating it through the body.
        Location lidPivot = base.clone().add(0, 0.52, -0.54);
        spawnPart(world, lidPivot, Material.CHEST, 1.14f, new Vector3f(0f, 0.45f, 0f),
            new Quaternionf().rotateY((float) Math.toRadians(90)), lidParts);
        spawnPart(world, lidPivot.clone().add(0, 0.10, 0), Material.GOLD_BLOCK, 0.10f,
            new Vector3f(1f, 1f, 1f), new Quaternionf(), lidParts);

        // ── CLICK BOX (unchanged contract: generous 1.4 x 1.8 volume)
        clickBox = world.spawn(base.clone().add(0, 0.15, 0), Interaction.class, box -> {
            box.setPersistent(false);
            box.addScoreboardTag(ENTITY_TAG);
            box.setInteractionWidth(1.4f);
            box.setInteractionHeight(1.8f);
            box.setResponsive(true);
        });

        // ── ORBIT: eight small loot tokens circling the crate at ankle height.
        for (int i = 0; i < 8; i++) {
            double ang = (Math.PI * 2.0 / 8.0) * i;
            Location slot = base.clone().add(Math.cos(ang) * 0.78, -0.18, Math.sin(ang) * 0.78);
            Material token = switch (i % 4) {
                case 0 -> Material.GOLD_INGOT;
                case 1 -> Material.EMERALD;
                case 2 -> Material.AMETHYST_SHARD;
                default -> Material.DIAMOND;
            };
            ItemDisplay orbit = world.spawn(slot, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(ENTITY_TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setInterpolationDuration(2);
                d.setViewRange(1.4f);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setItemStack(new ItemStack(token));
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0.34f, 0.34f, 0.34f), new Quaternionf()
                ));
            });
            orbitParts.add(orbit);
        }

        // ── BURST: 12 golden shards hidden below the floor until the crate is opened.
        for (int i = 0; i < 12; i++) {
            ItemDisplay shard = world.spawn(base.clone().add(0, -2.5, 0), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(ENTITY_TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setInterpolationDuration(1);
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

        banner = world.spawn(base.clone().add(0, 1.35, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.4f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setSeeThrough(false);
            d.text(Msg.parse("<#FFD43B><bold>🎁 ʀᴇᴡᴀʀᴅ ᴄʀᴀᴛᴇ</bold></#FFD43B>"));
        });

        costLine = world.spawn(base.clone().add(0, 0.95, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.1f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setSeeThrough(false);
            d.text(costText());
        });
    }

    /** Spawns one ItemDisplay part of the crate with a fixed scale/offset. */
    private void spawnPart(
        World world, Location at, Material material, float scale,
        Vector3f offset, Quaternionf rotation, List<Part> bucket
    ) {
        ItemDisplay part = world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setViewRange(1.6f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setItemStack(new ItemStack(material));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(
                offset, rotation, new Vector3f(scale, scale, scale), new Quaternionf()
            ));
        });
        bucket.add(new Part(part, offset, scale));
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
        bodyParts.clear();
        lidParts.clear();
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
        if (tickCounter % 100 == 0 && (orbitParts.isEmpty() || orbitParts.get(0) == null || !orbitParts.get(0).isValid())) {
            spawn();
            return;
        }
        if (baseLocation == null || bodyParts.isEmpty() || !bodyParts.get(0).display.isValid()) {
            return;
        }
        hoverTicks++;

        // ── Idle: the crate breathes through its transform. This used to teleport every piece
        // every tick, which both spammed packets and drifted the crate off its pedestal.
        float floatY = (float) (Math.sin(hoverTicks * 0.05) * 0.03);
        for (Part part : bodyParts) {
            if (part.display == null || !part.display.isValid()) {
                continue;
            }
            part.display.setInterpolationDelay(0);
            part.display.setInterpolationDuration(2);
            part.display.setTransformation(new Transformation(
                new Vector3f(part.offset.x, part.offset.y + floatY, part.offset.z),
                new Quaternionf().rotateY((float) Math.toRadians(90)),
                new Vector3f(part.scale, part.scale, part.scale),
                new Quaternionf()
            ));
        }

        ringAngle += 0.035f;
        for (int i = 0; i < orbitParts.size(); i++) {
            ItemDisplay orbit = orbitParts.get(i);
            if (orbit == null || !orbit.isValid()) {
                continue;
            }
            double ang = ringAngle + (Math.PI * 2.0 / 8.0) * i;
            Location slot = baseLocation.clone().add(
                Math.cos(ang) * 0.82,
                -0.05 + Math.sin(hoverTicks * 0.09 + i) * 0.09,
                Math.sin(ang) * 0.82
            );
            orbit.teleport(slot);
            orbit.setInterpolationDelay(0);
            orbit.setInterpolationDuration(2);
            orbit.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf().rotateY(ringAngle * 2f + i),
                new Vector3f(0.34f, 0.34f, 0.34f),
                new Quaternionf()
            ));
        }

        // ── Lid: swings back on its hinge, then settles shut, over exactly the reveal delay so the
        // loot is visible the moment the chat card lands.
        if (!lidParts.isEmpty()) {
            float tilt = 0f;
            if (openAnimationTicks > 0) {
                openAnimationTicks--;
                double t = 1.0 - openAnimationTicks / (double) LID_ANIMATION_TICKS;
                // Fast throw-open, slow settle: an ease-out curve instead of a symmetric sine.
                double eased = 1.0 - Math.pow(1.0 - Math.min(1.0, t * 1.6), 3.0);
                tilt = (float) (-eased * Math.toRadians(74));
            }
            for (Part lid : lidParts) {
                if (lid.display == null || !lid.display.isValid()) {
                    continue;
                }
                lid.display.setInterpolationDelay(0);
                lid.display.setInterpolationDuration(2);
                // The whole lid slab rotates about the hinge point, so it never sinks into the body.
                lid.display.setTransformation(new Transformation(
                    new Vector3f(lid.offset.x, lid.offset.y + floatY, lid.offset.z),
                    new Quaternionf().rotateX(tilt).rotateY((float) Math.toRadians(90)),
                    new Vector3f(lid.scale, lid.scale * 0.42f, lid.scale),
                    new Quaternionf()
                ));
            }
        }

        // ── Burst: golden shards erupt from the crate for ~14 ticks after a reveal.
        if (openAnimationTicks > 0 && baseLocation != null) {
            for (int i = 0; i < burstParts.size(); i++) {
                ItemDisplay shard = burstParts.get(i);
                if (shard == null || !shard.isValid()) {
                    continue;
                }
                double age = (LID_ANIMATION_TICKS - openAnimationTicks) * 0.5;
                double ang = (Math.PI * 2.0 / burstParts.size()) * i + ringAngle;
                double radius = 0.18 + age * 0.22;
                double up = 0.55 + Math.sin(Math.min(Math.PI * 0.5, age * 0.32)) * 1.05;
                shard.teleport(baseLocation.clone().add(
                    Math.cos(ang) * radius, up, Math.sin(ang) * radius));
                float fade = (float) Math.max(0.001, 0.22 * (openAnimationTicks / (double) LID_ANIMATION_TICKS));
                shard.setInterpolationDelay(0);
                shard.setInterpolationDuration(1);
                shard.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf().rotateY((float) (ang * 3.0)),
                    new Vector3f(fade, fade, fade),
                    new Quaternionf()
                ));
            }
        }

        // ── Idle shimmer + a soft halo so the crate pops in a busy lobby.
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
        Msg.playPersonalPackSound(player, "ui.click", 1.0f, 1.1f);

        // Anticipation first: the crate shakes, then the lid throws open as the loot is revealed.
        RollOutcome outcome = roll(profile);
        if (baseLocation != null) {
            World w = baseLocation.getWorld();
            if (w != null) {
                w.spawnParticle(Particle.WAX_ON, baseLocation.clone().add(0, 0.4, 0), 24, 0.5, 0.4, 0.5, 0.02);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            openAnimationTicks = LID_ANIMATION_TICKS;
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
            plugin.integration().onCrateOpened(player, outcome.label(), outcome.rarity(), cost, outcome.coins());
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
