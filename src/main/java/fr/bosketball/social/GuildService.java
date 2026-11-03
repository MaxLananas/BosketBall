package fr.bosketball.social;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.social.Guild.Member;
import fr.bosketball.social.Guild.Role;
import fr.bosketball.util.Msg;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Persistent guilds: creation, invitations, roster management, guild ELO and leaderboard.
 *
 * <p>Everything lives in {@code plugins/BosketBall/guilds.yml} written through
 * {@link AtomicYamlWriter}, so a crash mid-save can never corrupt the file. Guild ELO is
 * updated from {@link #recordMatchResult} which is called at the end of every team match.</p>
 */
public final class GuildService {

    private static final String FILE_NAME = "guilds.yml";

    /** Pending invitation keyed by invitee UUID. */
    private record Invite(String guildId, UUID inviterId, long expiresAtMillis) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private final BosketBallPlugin plugin;
    private final Map<String, Guild> guildsById = new LinkedHashMap<>();
    private final Map<UUID, String> guildByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> pendingInvites = new ConcurrentHashMap<>();
    private File file;

    public GuildService(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================

    public void load() {
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        guildsById.clear();
        guildByPlayer.clear();
        YamlConfiguration yaml = AtomicYamlWriter.loadSafe(file, plugin.getLogger());
        ConfigurationSection root = yaml.getConfigurationSection("guilds");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection g = root.getConfigurationSection(id);
            if (g == null) {
                continue;
            }
            Guild guild = new Guild(
                id,
                g.getString("name", id),
                g.getString("tag", id.toUpperCase(Locale.ROOT)),
                g.getLong("created-at", System.currentTimeMillis()),
                plugin.settings().guilds().startingElo());
            guild.setElo(g.getInt("elo", guild.elo()));
            guild.setMatchesPlayed(g.getInt("matches-played", 0));
            guild.setMatchesWon(g.getInt("matches-won", 0));
            guild.setTrophies(g.getInt("trophies", 0));
            guild.setMotd(g.getString("motd", ""));
            guild.setColorRgb(g.getInt("color", 0xFF8C1A));

            ConfigurationSection roster = g.getConfigurationSection("members");
            if (roster != null) {
                for (String rawUuid : roster.getKeys(false)) {
                    ConfigurationSection m = roster.getConfigurationSection(rawUuid);
                    if (m == null) {
                        continue;
                    }
                    try {
                        UUID uuid = UUID.fromString(rawUuid);
                        Role role = Role.valueOf(m.getString("role", "MEMBER"));
                        guild.members().add(new Member(
                            uuid,
                            m.getString("name", "Unknown"),
                            role,
                            m.getLong("joined-at", System.currentTimeMillis()),
                            m.getInt("contributed-elo", 0)));
                        guildByPlayer.put(uuid, guild.id());
                    } catch (IllegalArgumentException ignored) {
                        // Corrupted entry — skip rather than killing the whole roster.
                    }
                }
            }
            guildsById.put(id, guild);
        }
        plugin.getLogger().info("[Guilds] Loaded " + guildsById.size() + " guilds ("
            + guildByPlayer.size() + " members).");
    }

    public void save() {
        if (file == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Guild guild : guildsById.values()) {
            String base = "guilds." + guild.id();
            yaml.set(base + ".name", guild.name());
            yaml.set(base + ".tag", guild.tag());
            yaml.set(base + ".created-at", guild.createdAtMillis());
            yaml.set(base + ".elo", guild.elo());
            yaml.set(base + ".matches-played", guild.matchesPlayed());
            yaml.set(base + ".matches-won", guild.matchesWon());
            yaml.set(base + ".trophies", guild.trophies());
            yaml.set(base + ".motd", guild.motd());
            yaml.set(base + ".color", guild.colorRgb());
            for (Member m : guild.members()) {
                String p = base + ".members." + m.uuid();
                yaml.set(p + ".name", m.name());
                yaml.set(p + ".role", m.role().name());
                yaml.set(p + ".joined-at", m.joinedAtMillis());
                yaml.set(p + ".contributed-elo", m.contributedElo());
            }
        }
        AtomicYamlWriter.writeYamlAtomically(file, "BosketBall persistent guilds — do not edit while the server runs.",
            yaml, plugin.getLogger());
    }

    // =========================================================================
    //  LOOKUPS
    // =========================================================================

    public Guild guildOf(UUID playerId) {
        String id = playerId == null ? null : guildByPlayer.get(playerId);
        return id == null ? null : guildsById.get(id);
    }

    public Guild byName(String name) {
        if (name == null) {
            return null;
        }
        for (Guild g : guildsById.values()) {
            if (g.name().equalsIgnoreCase(name) || g.tag().equalsIgnoreCase(name)) {
                return g;
            }
        }
        return null;
    }

    public List<Guild> all() {
        return new ArrayList<>(guildsById.values());
    }

    /** Top guilds by ELO, exactly like the player leaderboards. */
    public List<Guild> topByElo(int limit) {
        return guildsById.values().stream()
            .sorted(Comparator.comparingInt(Guild::elo).reversed()
                .thenComparing(Guild::name, String.CASE_INSENSITIVE_ORDER))
            .limit(Math.max(1, limit))
            .toList();
    }

    public int placementOf(Guild guild) {
        List<Guild> sorted = topByElo(Integer.MAX_VALUE);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).id().equals(guild.id())) {
                return i + 1;
            }
        }
        return -1;
    }

    // =========================================================================
    //  ACTIONS
    // =========================================================================

    /** Result of a guild action, carrying a ready-to-send message. */
    public record ActionResult(boolean success, String message) {
        static ActionResult ok(String message) {
            return new ActionResult(true, message);
        }

        static ActionResult fail(String message) {
            return new ActionResult(false, message);
        }
    }

    public ActionResult create(Player creator, String rawName, String rawTag) {
        var cfg = plugin.settings().guilds();
        if (!cfg.enabled()) {
            return ActionResult.fail("Guilds are disabled on this server.");
        }
        if (guildOf(creator.getUniqueId()) != null) {
            return ActionResult.fail("You are already in a guild — leave it first.");
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < cfg.nameMinLength() || name.length() > cfg.nameMaxLength()) {
            return ActionResult.fail("Guild name must be " + cfg.nameMinLength() + "–"
                + cfg.nameMaxLength() + " characters.");
        }
        if (!name.matches("[A-Za-z0-9 _'-]+")) {
            return ActionResult.fail("Guild name may only contain letters, digits, spaces, _ ' and -.");
        }
        if (byName(rawName) != null) {
            return ActionResult.fail("A guild with that name already exists.");
        }
        String tag = (rawTag == null || rawTag.isBlank())
            ? name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT)
            : rawTag.trim().toUpperCase(Locale.ROOT);
        if (tag.length() > cfg.tagMaxLength()) {
            tag = tag.substring(0, cfg.tagMaxLength());
        }
        if (tag.length() < 2) {
            return ActionResult.fail("Guild tag must be at least 2 characters.");
        }
        if (byName(tag) != null) {
            return ActionResult.fail("That tag is already taken.");
        }

        PlayerProfile profile = plugin.statsManager().getOrCreate(creator);
        if (profile.coins() < cfg.createCostCoins()) {
            return ActionResult.fail("Creating a guild costs " + cfg.createCostCoins()
                + " Coins — you have " + profile.coins() + ".");
        }
        profile.removeCoins(cfg.createCostCoins());
        plugin.statsManager().saveProfileAsync(profile);

        String id = Long.toHexString(System.currentTimeMillis())
            + Integer.toHexString(name.hashCode());
        Guild guild = new Guild(id, name, tag, System.currentTimeMillis(), cfg.startingElo());
        guild.addMember(creator.getUniqueId(), creator.getName(), Role.LEADER);
        guildsById.put(id, guild);
        guildByPlayer.put(creator.getUniqueId(), id);
        save();

        return ActionResult.ok("Guild " + guild.displayName() + " " + guild.displayTag()
            + " created for <white>" + cfg.createCostCoins() + " Coins</white>.");
    }

    public ActionResult invite(Player inviter, String targetName) {
        Guild guild = guildOf(inviter.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        Role role = guild.roleOf(inviter.getUniqueId());
        if (role == null || !role.canInvite()) {
            return ActionResult.fail("Only leaders and officers can invite players.");
        }
        if (guild.members().size() >= plugin.settings().guilds().maxMembers()) {
            return ActionResult.fail("That guild is full (" + plugin.settings().guilds().maxMembers() + " members).");
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            return ActionResult.fail("Player not online: " + targetName);
        }
        if (guild.hasMember(target.getUniqueId())) {
            return ActionResult.fail(target.getName() + " is already in your guild.");
        }
        if (guildOf(target.getUniqueId()) != null) {
            return ActionResult.fail(target.getName() + " is already in another guild.");
        }
        pendingInvites.put(target.getUniqueId(),
            new Invite(guild.id(), inviter.getUniqueId(), System.currentTimeMillis() + 120_000L));

        Msg.send(target, "<" + guild.hexColor() + "><bold>" + guild.name() + "</bold></" + guild.hexColor()
            + "> <gray>invited you to their guild!</gray> "
            + "<yellow>/guild join " + guild.name() + "</yellow> <dark_gray>(120s)</dark_gray>");
        return ActionResult.ok("Invitation sent to <white>" + target.getName() + "</white>.");
    }

    public ActionResult acceptInvite(Player player, String guildName) {
        Invite invite = pendingInvites.get(player.getUniqueId());
        if (invite == null || invite.expired()) {
            return ActionResult.fail("You have no pending guild invitation.");
        }
        Guild guild = guildsById.get(invite.guildId());
        if (guild == null) {
            return ActionResult.fail("That guild no longer exists.");
        }
        if (guildName != null && !guild.name().equalsIgnoreCase(guildName) && !guild.tag().equalsIgnoreCase(guildName)) {
            return ActionResult.fail("Your pending invitation is for " + guild.name() + ".");
        }
        if (guildOf(player.getUniqueId()) != null) {
            return ActionResult.fail("Leave your current guild first.");
        }
        if (guild.members().size() >= plugin.settings().guilds().maxMembers()) {
            return ActionResult.fail("That guild is now full.");
        }
        guild.addMember(player.getUniqueId(), player.getName(), Role.MEMBER);
        guildByPlayer.put(player.getUniqueId(), guild.id());
        pendingInvites.remove(player.getUniqueId());
        save();

        for (Member m : guild.members()) {
            Player online = Bukkit.getPlayer(m.uuid());
            if (online != null) {
                Msg.send(online, "<#4ADE80>✔</#4ADE80> <white>" + player.getName()
                    + "</white> joined the guild.");
            }
        }
        return ActionResult.ok("You joined " + guild.displayName() + ".");
    }

    public ActionResult leave(Player player) {
        Guild guild = guildOf(player.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (guild.roleOf(player.getUniqueId()) == Role.LEADER && guild.members().size() > 1) {
            return ActionResult.fail("Transfer leadership first with <yellow>/guild promote <player></yellow>,"
                + " or disband with <yellow>/guild disband</yellow>.");
        }
        guild.removeMember(player.getUniqueId());
        guildByPlayer.remove(player.getUniqueId());
        if (guild.members().isEmpty()) {
            guildsById.remove(guild.id());
        }
        save();
        return ActionResult.ok("You left " + guild.displayName() + ".");
    }

    public ActionResult kick(Player actor, String targetName) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        Role actorRole = guild.roleOf(actor.getUniqueId());
        if (actorRole == null || !actorRole.canKick()) {
            return ActionResult.fail("Only leaders and officers can kick members.");
        }
        Member target = null;
        for (Member m : guild.members()) {
            if (m.name().equalsIgnoreCase(targetName)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            return ActionResult.fail("No such member: " + targetName);
        }
        if (target.role() == Role.LEADER) {
            return ActionResult.fail("You cannot kick the guild leader.");
        }
        if (target.role() == Role.OFFICER && actorRole != Role.LEADER) {
            return ActionResult.fail("Only the leader can kick an officer.");
        }
        guild.removeMember(target.uuid());
        guildByPlayer.remove(target.uuid());
        save();
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            Msg.send(online, Msg.warn("You were removed from " + guild.name() + "."));
        }
        return ActionResult.ok("Kicked <white>" + target.name() + "</white> from the guild.");
    }

    public ActionResult promote(Player actor, String targetName) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (guild.roleOf(actor.getUniqueId()) != Role.LEADER) {
            return ActionResult.fail("Only the guild leader can promote members.");
        }
        Member target = null;
        for (Member m : guild.members()) {
            if (m.name().equalsIgnoreCase(targetName)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            return ActionResult.fail("No such member: " + targetName);
        }
        if (target.role() == Role.MEMBER) {
            target.setRole(Role.OFFICER);
            save();
            return ActionResult.ok("<white>" + target.name() + "</white> is now an officer.");
        }
        // Officer -> Leader transfers the guild.
        for (Member m : guild.members()) {
            if (m.role() == Role.LEADER) {
                m.setRole(Role.OFFICER);
            }
        }
        target.setRole(Role.LEADER);
        save();
        return ActionResult.ok("<white>" + target.name() + "</white> is now the guild leader.");
    }

    public ActionResult disband(Player actor) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (guild.roleOf(actor.getUniqueId()) != Role.LEADER) {
            return ActionResult.fail("Only the leader can disband the guild.");
        }
        for (Member m : guild.members()) {
            guildByPlayer.remove(m.uuid());
            Player online = Bukkit.getPlayer(m.uuid());
            if (online != null) {
                Msg.send(online, Msg.warn("Your guild <white>" + guild.name() + "</white> was disbanded."));
            }
        }
        guildsById.remove(guild.id());
        save();
        return ActionResult.ok("Guild disbanded.");
    }

    // =========================================================================
    //  ELO
    // =========================================================================

    /**
     * Updates guild ELO after a team match. Called once per guild taking part, with the
     * guild member's individual ELO delta summed into the guild total.
     */
    public void recordMatchResult(Guild guild, boolean won, int eloDelta, int memberCount) {
        if (guild == null) {
            return;
        }
        guild.setMatchesPlayed(guild.matchesPlayed() + 1);
        if (won) {
            guild.setMatchesWon(guild.matchesWon() + 1);
        }
        guild.adjustElo(eloDelta);
        save();
    }

    /** Called from MatchSession: updates the guild of every participant. */
    public void onMatchFinished(List<UUID> winners, List<UUID> losers, int eloDelta) {
        Map<String, Boolean> guildResults = new LinkedHashMap<>();
        Map<String, Integer> memberCounts = new LinkedHashMap<>();
        for (UUID id : winners) {
            Guild g = guildOf(id);
            if (g != null) {
                guildResults.put(g.id(), true);
                memberCounts.merge(g.id(), 1, Integer::sum);
            }
        }
        for (UUID id : losers) {
            Guild g = guildOf(id);
            if (g != null) {
                guildResults.putIfAbsent(g.id(), false);
                memberCounts.merge(g.id(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Boolean> entry : guildResults.entrySet()) {
            Guild g = guildsById.get(entry.getKey());
            recordMatchResult(g, entry.getValue(), entry.getValue() ? eloDelta : -eloDelta,
                memberCounts.getOrDefault(entry.getKey(), 1));
        }
    }

    public void onQuit(UUID playerId) {
        pendingInvites.remove(playerId);
    }

    public Map<UUID, String> membershipSnapshot() {
        return Map.copyOf(guildByPlayer);
    }

    /** Chat tag prefix used by the chat renderer. */
    public String chatPrefix(UUID playerId) {
        Guild guild = guildOf(playerId);
        return guild == null ? "" : guild.displayTag() + " ";
    }
}
