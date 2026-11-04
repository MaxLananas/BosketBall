package fr.bosketball.lobby;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.util.Msg;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HallOfFamePodium {

    public static final String ENTITY_TAG = "bosketball_hof";
    private static final String FILE_NAME = "hall-of-fame.yml";

    private record Step(int place, int offsetX, int offsetZ, int height, Material material, String label) {
    }

    private static final Step[] STEPS = {
        new Step(2, -2, 0, 1, Material.POLISHED_DEEPSLATE, "2ND"),
        new Step(1, 0, 0, 2, Material.GOLD_BLOCK, "1ST"),
        new Step(3, 2, 0, 1, Material.POLISHED_DEEPSLATE, "3RD")
    };

    private final BosketBallPlugin plugin;
    private final List<UUID> spawnedDisplays = new ArrayList<>();

    private final Map<Integer, Mannequin> championNpcs = new ConcurrentHashMap<>();

    private String rosterSignature = "";

    private long lastAutoRefreshMillis = 0L;
    private Location origin;
    private BlockFace facing = BlockFace.SOUTH;

    public HallOfFamePodium(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    private File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    public void load() {
        YamlConfiguration yaml = AtomicYamlWriter.loadSafe(file(), plugin.getLogger());
        String world = yaml.getString("origin.world");
        if (world == null) {
            this.origin = null;
            return;
        }
        World w = Bukkit.getWorld(world);
        if (w == null) {
            this.origin = null;
            return;
        }
        this.origin = new Location(w,
            yaml.getDouble("origin.x"),
            yaml.getDouble("origin.y"),
            yaml.getDouble("origin.z"));
        try {
            this.facing = BlockFace.valueOf(yaml.getString("facing", "SOUTH"));
        } catch (IllegalArgumentException ex) {
            this.facing = BlockFace.SOUTH;
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (origin != null) {
            yaml.set("origin.world", origin.getWorld().getName());
            yaml.set("origin.x", AtomicYamlWriter.round2(origin.getX()));
            yaml.set("origin.y", AtomicYamlWriter.round2(origin.getY()));
            yaml.set("origin.z", AtomicYamlWriter.round2(origin.getZ()));
            yaml.set("facing", facing.name());
        }
        AtomicYamlWriter.writeYamlAtomically(file(),
            "BosketBall physical Hall of Fame — set with /bb holo hall-of-fame (OP).", yaml, plugin.getLogger());
    }

    public boolean hasPodium() {
        return origin != null && origin.getWorld() != null;
    }

    public Location origin() {
        return origin == null ? null : origin.clone();
    }

    public void placeHere(org.bukkit.entity.Player operator) {
        Location loc = operator.getLocation().getBlock().getLocation();

        float yaw = operator.getLocation().getYaw();
        BlockFace face = yawToFace(yaw);
        this.origin = loc.clone().add(0.5, 0.0, 0.5);
        this.facing = face.getOppositeFace();
        remove();
        build();
        save();
        Msg.send(operator, Msg.success("Hall of Fame podium built here and saved."));
        Msg.playPersonalPackSound(operator, "game.whistle", 0.9f, 1.1f);
    }

    private static BlockFace yawToFace(float yaw) {
        float norm = ((yaw % 360) + 360) % 360;
        if (norm >= 315 || norm < 45) {
            return BlockFace.SOUTH;
        }
        if (norm < 135) {
            return BlockFace.WEST;
        }
        if (norm < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private void build() {
        if (!hasPodium()) {
            return;
        }
        List<PlayerProfile> top = plugin.statsManager().topBy(StatsManager.LeaderboardCategory.ELO, 3);
        World w = origin.getWorld();

        for (Step step : STEPS) {
            int bx = origin.getBlockX() + step.offsetX();
            int bz = origin.getBlockZ() + step.offsetZ();
            int by = origin.getBlockY();
            for (int h = 0; h <= step.height(); h++) {
                Block b = w.getBlockAt(bx, by + h, bz);
                b.setType(step.material() == Material.POLISHED_DEEPSLATE && h == step.height()
                    ? Material.DEEPSLATE_TILES : step.material(), false);
            }

            Block crownPlate = w.getBlockAt(bx, by + step.height() + 1, bz);
            crownPlate.setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, false);

            PlayerProfile profile = step.place() - 1 < top.size() ? top.get(step.place() - 1) : null;
            Location headLoc = new Location(w, bx + 0.5, by + step.height() + 1.0, bz + 0.5, 0f, 0f);

            if (profile != null) {
                spawnChampionNpc(headLoc, profile, step.place());
                spawnNameplate(headLoc.clone().add(0, 2.45, 0), profile, step.place());
                spawnTrophy(headLoc.clone().add(0.42, 0.55, 0.36), step.place());
            } else {
                spawnEmptyPlate(headLoc, step);
            }
        }
    }

    private Mannequin spawnChampionNpc(Location loc, PlayerProfile profile, int place) {
        Mannequin npc = loc.getWorld().spawn(loc, Mannequin.class, man -> {
            man.addScoreboardTag(ENTITY_TAG);
            man.setPersistent(false);
            man.setImmovable(true);
            man.setGravity(false);
            man.setInvulnerable(true);
            man.setSilent(true);
            man.setCollidable(false);
            man.setCustomNameVisible(false);
            man.setDescription(null);
            try {
                man.setSkinParts(com.destroystokyo.paper.SkinParts.allParts());
            } catch (Throwable ignored) {

            }
            ResolvableProfile skin = championSkin(profile);
            if (skin != null) {
                man.setProfile(skin);
            }
        });
        if (npc != null) {
            float yaw = facing == null ? 0f : switch (facing) {
                case NORTH -> 180f;
                case SOUTH -> 0f;
                case EAST -> 270f;
                case WEST -> 90f;
                default -> 0f;
            };
            npc.setRotation(yaw, 0f);
            championNpcs.put(place, npc);
        }
        return npc;
    }

    private ResolvableProfile championSkin(PlayerProfile profile) {
        Player online = Bukkit.getPlayer(profile.uuid());
        if (online != null && online.isOnline()) {
            var live = online.getPlayerProfile();
            if (!live.getProperties().isEmpty()) {
                return ResolvableProfile.resolvableProfile(live);
            }
        }
        var cached = plugin.statsManager().cachedSkinTexture(profile.uuid());
        if (cached != null && cached.getValue() != null && !cached.getValue().isBlank()) {
            var builder = ResolvableProfile.resolvableProfile()
                .uuid(profile.uuid())
                .name(profile.lastKnownName())
                .addProperty(new com.destroystokyo.paper.profile.ProfileProperty(
                    "textures", cached.getValue(),
                    cached.getSignature() == null ? "" : cached.getSignature()));
            return builder.build();
        }
        try {
            var exact = Bukkit.createProfileExact(profile.uuid(), profile.lastKnownName());
            if (exact != null && exact.completeFromCache(true) && !exact.getProperties().isEmpty()) {
                return ResolvableProfile.resolvableProfile(exact);
            }
        } catch (Throwable ignored) {

        }
        return ResolvableProfile.resolvableProfile().name(profile.lastKnownName()).build();
    }

    private void spawnNameplate(Location loc, PlayerProfile profile, int place) {
        String color = place == 1 ? "<#FFD43B>" : place == 2 ? "<#E2E8F0>" : "<#D08B5B>";
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(120, 8, 10, 18));
            d.text(Msg.parse(
                color + "<bold>#" + place + "  " + profile.lastKnownName() + "</bold>" + color + "\n"
                    + "<gray><white>" + profile.elo() + "</white> ELO</gray> <dark_gray>•</dark_gray> "
                    + "<gray><#4ADE80>" + profile.wins() + "W</#4ADE80>/<#F87171>" + profile.losses()
                    + "L</#F87171></gray>\n"
                    + "<dark_gray>" + profile.effectiveRank().plainDisplayName() + "</dark_gray>"));
        });
        spawnedDisplays.add(display.getUniqueId());
    }

    private void spawnTrophy(Location loc, int place) {
        String model = place == 1 ? "trophy_mvp" : place == 2 ? "trophy_gold" : "trophy_bronze";
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setItemStack(modelItem(model));
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0.2f, 0f),
                new Quaternionf(),
                new Vector3f(1.1f, 1.1f, 1.1f),
                new Quaternionf()));
        });
        spawnedDisplays.add(display.getUniqueId());
    }

    private void spawnEmptyPlate(Location loc, Step step) {
        TextDisplay display = loc.getWorld().spawn(loc.clone().add(0, 1.6, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ENTITY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(Msg.parse("<#64748B><bold>" + step.label() + " PLACE</bold>\n<gray>Waiting for a champion…</gray>"));
        });
        spawnedDisplays.add(display.getUniqueId());
    }

    private static ItemStack modelItem(String modelId) {
        ItemStack stack = new ItemStack(Material.PAPER);
        var meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", modelId));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void remove() {
        for (Mannequin npc : championNpcs.values()) {
            if (npc != null && npc.isValid()) {
                npc.remove();
            }
        }
        championNpcs.clear();
        rosterSignature = "";
        for (UUID id : spawnedDisplays) {
            var entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        spawnedDisplays.clear();
        if (!hasPodium()) {
            return;
        }
        World w = origin.getWorld();
        for (Step step : STEPS) {
            int bx = origin.getBlockX() + step.offsetX();
            int bz = origin.getBlockZ() + step.offsetZ();
            for (int h = 0; h <= step.height() + 1; h++) {
                Block b = w.getBlockAt(bx, origin.getBlockY() + h, bz);
                if (b.getType() == Material.AIR) {
                    continue;
                }
                b.setType(Material.AIR, false);
            }
        }
    }

    private List<PlayerProfile> currentTop() {
        return plugin.statsManager().topBy(StatsManager.LeaderboardCategory.ELO, 3);
    }

    private String currentRosterSignature() {
        StringBuilder sb = new StringBuilder();
        int place = 1;
        for (PlayerProfile p : currentTop()) {
            sb.append(place++).append(':').append(p.uuid()).append(':')
                .append(p.elo()).append(':').append(p.lastKnownName()).append('|');
        }
        return sb.toString();
    }

    private void refreshSilently() {
        remove();
        build();
    }

    public void refresh() {
        if (!hasPodium()) {
            return;
        }
        remove();
        build();
    }

    public void tick() {
        if (!hasPodium()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        int every = Math.max(5, plugin.settings().leaderboards().holoRefreshSeconds());
        if (nowMs - lastAutoRefreshMillis >= every * 1000L) {
            lastAutoRefreshMillis = nowMs;
            String signature = currentRosterSignature();
            boolean npcMissing = championNpcs.size() < Math.min(3, currentTop().size())
                || championNpcs.values().stream().anyMatch(npc -> npc == null || !npc.isValid());
            if (!signature.equals(rosterSignature) || npcMissing) {
                rosterSignature = signature;
                refreshSilently();
            }
        }
        World w = origin.getWorld();
        long tick = w.getFullTime();
        if (tick % 20 != 0) {
            return;
        }
        int bx = origin.getBlockX();
        int bz = origin.getBlockZ();
        Location centre = new Location(w, bx + 0.5, origin.getBlockY() + 3.2, bz + 0.5);
        w.spawnParticle(Particle.END_ROD, centre, 3, 0.6, 0.3, 0.6, 0.01);
        if (tick % 100 == 0) {
            w.spawnParticle(Particle.HAPPY_VILLAGER, centre.clone().add(0, -1.2, 0), 10, 1.6, 0.4, 0.6, 0.0);
        }
    }

    public String summary() {
        if (!hasPodium()) {
            return "Not placed";
        }
        return origin.getBlockX() + " " + origin.getBlockY() + " " + origin.getBlockZ()
            + " (" + facing.name().toLowerCase(java.util.Locale.ROOT) + ")";
    }

    public static String podiumIcon() {
        return "icon_podium";
    }
}
