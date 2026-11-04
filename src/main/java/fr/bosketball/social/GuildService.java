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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class GuildService {

    private static final String FILE_NAME = "guilds.yml";

    public static final long INVITE_TTL_MILLIS = 5L * 60L * 1000L;

    public record ActionResult(boolean success, String message) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message);
        }
    }

    public record InviteView(Guild guild, String inviterName, long expiresAtMillis) {
        public long remainingMillis() {
            return Math.max(0L, expiresAtMillis - System.currentTimeMillis());
        }
    }

    private record InviteKey(String guildId, UUID playerId) {
    }

    private final BosketBallPlugin plugin;
    private final Map<String, Guild> guildsById = new LinkedHashMap<>();
    private final Map<UUID, String> guildByPlayer = new ConcurrentHashMap<>();
    private final Map<InviteKey, InviteView> invites = new ConcurrentHashMap<>();
    private File file;

    public GuildService(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        guildsById.clear();
        guildByPlayer.clear();
        invites.clear();
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
            guild.setDescription(g.getString("description", ""));
            guild.setOpenJoin(g.getBoolean("open-join", false));
            guild.setColorRgb(g.getInt("color", Guild.PALETTE.getFirst()));

            ConfigurationSection roster = g.getConfigurationSection("members");
            if (roster != null) {
                for (String rawUuid : roster.getKeys(false)) {
                    ConfigurationSection m = roster.getConfigurationSection(rawUuid);
                    if (m == null) {
                        continue;
                    }
                    try {
                        UUID uuid = UUID.fromString(rawUuid);
                        Role role;
                        try {
                            role = Role.valueOf(m.getString("role", "MEMBER").toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException badRole) {
                            role = Role.MEMBER;
                        }
                        Member member = new Member(
                            uuid,
                            m.getString("name", "Unknown"),
                            role,
                            m.getLong("joined-at", System.currentTimeMillis()),
                            m.getInt("contributed-elo", 0));
                        member.setMatchesPlayed(m.getInt("matches", 0));
                        member.setLastSeenMillis(m.getLong("last-seen", 0L));
                        guild.members().add(member);
                        guildByPlayer.put(uuid, guild.id());
                    } catch (IllegalArgumentException ignored) {

                    }
                }
            }

            boolean hasLeader = guild.members().stream().anyMatch(m -> m.role() == Role.LEADER);
            if (!hasLeader && !guild.members().isEmpty()) {
                guild.members().getFirst().setRole(Role.LEADER);
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
            yaml.set(base + ".description", guild.description());
            yaml.set(base + ".open-join", guild.openJoin());
            yaml.set(base + ".color", guild.colorRgb());
            for (Member m : guild.members()) {
                String p = base + ".members." + m.uuid();
                yaml.set(p + ".name", m.name());
                yaml.set(p + ".role", m.role().name());
                yaml.set(p + ".joined-at", m.joinedAtMillis());
                yaml.set(p + ".contributed-elo", m.contributedElo());
                yaml.set(p + ".matches", m.matchesPlayed());
                yaml.set(p + ".last-seen", m.lastSeenMillis());
            }
        }
        AtomicYamlWriter.writeYamlAtomically(file,
            "BosketBall persistent guilds — do not edit while the server runs.", yaml,
            plugin.getLogger());
    }

    public boolean enabled() {
        return plugin.settings().guilds().enabled();
    }

    public int maxMembers() {
        return plugin.settings().guilds().maxMembers();
    }

    public int createCostCoins() {
        return plugin.settings().guilds().createCostCoins();
    }

    public Guild guildOf(UUID playerId) {
        String id = playerId == null ? null : guildByPlayer.get(playerId);
        return id == null ? null : guildsById.get(id);
    }

    public Guild byId(String id) {
        return id == null ? null : guildsById.get(id);
    }

    public Guild byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String needle = name.trim();
        for (Guild g : guildsById.values()) {
            if (g.name().equalsIgnoreCase(needle) || g.tag().equalsIgnoreCase(needle)) {
                return g;
            }
        }
        return null;
    }

    public List<Guild> all() {
        return new ArrayList<>(guildsById.values());
    }

    public List<Guild> joinable() {
        List<Guild> out = new ArrayList<>();
        for (Guild g : topByElo(Integer.MAX_VALUE)) {
            if (g.openJoin() && g.members().size() < maxMembers()) {
                out.add(g);
            }
        }
        return out;
    }

    public List<Guild> topByElo(int limit) {
        return guildsById.values().stream()
            .sorted(Comparator.comparingInt(Guild::elo).reversed()
                .thenComparing(Guild::name, String.CASE_INSENSITIVE_ORDER))
            .limit(Math.max(1, limit))
            .toList();
    }

    public int placementOf(Guild guild) {
        if (guild == null) {
            return -1;
        }
        List<Guild> sorted = topByElo(Integer.MAX_VALUE);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).id().equals(guild.id())) {
                return i + 1;
            }
        }
        return -1;
    }

    public List<InviteView> invitesFor(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        List<InviteView> out = new ArrayList<>();
        for (Map.Entry<InviteKey, InviteView> entry : invites.entrySet()) {
            if (!entry.getKey().playerId().equals(playerId)) {
                continue;
            }
            InviteView view = entry.getValue();
            if (view.remainingMillis() <= 0L || guildsById.get(entry.getKey().guildId()) == null) {
                invites.remove(entry.getKey());
                continue;
            }
            out.add(view);
        }
        out.sort(Comparator.comparingLong(InviteView::expiresAtMillis));
        return out;
    }

    public boolean hasInviteTo(UUID playerId, Guild guild) {
        if (playerId == null || guild == null) {
            return false;
        }
        InviteView view = invites.get(new InviteKey(guild.id(), playerId));
        return view != null && view.remainingMillis() > 0L;
    }

    public void sendInvite(Player inviter, Guild guild, String targetName) {
        if (guild == null || targetName == null) {
            return;
        }
        Player online = Bukkit.getPlayerExact(targetName);
        UUID targetId;
        String displayName;
        if (online != null) {
            targetId = online.getUniqueId();
            displayName = online.getName();
        } else {
            PlayerProfile profile = plugin.statsManager().findByNameIgnoreCase(targetName);
            if (profile == null || profile.uuid() == null) {
                return;
            }
            targetId = profile.uuid();
            displayName = profile.lastKnownName();
        }
        invites.put(new InviteKey(guild.id(), targetId),
            new InviteView(guild, inviter.getName(), System.currentTimeMillis() + INVITE_TTL_MILLIS));

        String body = "<" + guild.hexColor() + "><bold>" + guild.name() + "</bold></" + guild.hexColor()
            + "> <gray>invited you to their guild.</gray> "
            + "<yellow>/guild join " + Msg.escapeMini(guild.name()) + "</yellow> <dark_gray>("
            + (INVITE_TTL_MILLIS / 60_000L) + " min)</dark_gray>";
        if (online != null) {
            Msg.send(online, Msg.info(body));
            Msg.playPersonalPackSound(online, "ui.click", 0.8f, 1.4f);
        } else if (plugin.socialService() != null) {
            plugin.socialService().deliverToName(displayName, new OfflineMail(
                OfflineMail.Kind.GUILD, inviter.getName(),
                "Invitation to " + guild.name(),
                List.of("Guild: " + guild.displayNameAndTag(),
                    "Invited by: " + inviter.getName(),
                    "Expires in " + (INVITE_TTL_MILLIS / 60_000L) + " minutes."),
                System.currentTimeMillis(), "/guild join " + guild.name()));
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
        if (byName(name) != null) {
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
        if (!Guild.isValidTag(tag)) {
            return ActionResult.fail("Guild tag may contain only letters and digits.");
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
            + Integer.toHexString((name + tag).hashCode());
        Guild guild = new Guild(id, name, tag, System.currentTimeMillis(), cfg.startingElo());
        guild.addMember(creator.getUniqueId(), creator.getName(), Role.LEADER);
        guildsById.put(id, guild);
        guildByPlayer.put(creator.getUniqueId(), id);
        save();

        return ActionResult.ok("Guild " + guild.displayNameAndTag()
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
        if (guild.members().size() >= maxMembers()) {
            return ActionResult.fail("Your guild is full (" + maxMembers() + " members).");
        }
        PlayerProfile target = plugin.statsManager().findByNameIgnoreCase(targetName);
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (target == null && onlineTarget == null) {
            return ActionResult.fail("No player named <white>" + targetName + "</white>.");
        }
        UUID targetId = onlineTarget != null ? onlineTarget.getUniqueId() : target.uuid();
        String displayName = onlineTarget != null ? onlineTarget.getName() : target.lastKnownName();
        if (guild.hasMember(targetId)) {
            return ActionResult.fail("<white>" + displayName + "</white> is already in your guild.");
        }
        if (guildOf(targetId) != null) {
            return ActionResult.fail("<white>" + displayName + "</white> is already in another guild.");
        }
        if (hasInviteTo(targetId, guild)) {
            return ActionResult.fail("<white>" + displayName + "</white> already has a pending invite.");
        }
        sendInvite(inviter, guild, displayName);
        return ActionResult.ok("Invitation sent to <white>" + displayName + "</white>.");
    }

    public ActionResult joinOpen(Player player, Guild guild) {
        if (guild == null) {
            return ActionResult.fail("That guild no longer exists.");
        }
        if (guildOf(player.getUniqueId()) != null) {
            return ActionResult.fail("You are already in a guild — leave it first.");
        }
        if (!guild.openJoin()) {
            return ActionResult.fail(guild.displayName() + " is invitation-only.");
        }
        if (guild.members().size() >= maxMembers()) {
            return ActionResult.fail("That guild is full.");
        }
        return addToGuild(player, guild);
    }

    public ActionResult acceptInvite(Player player, String guildName) {
        List<InviteView> pending = invitesFor(player.getUniqueId());
        if (guildOf(player.getUniqueId()) != null) {
            return ActionResult.fail("You are already in a guild — leave it first.");
        }
        Guild target = null;
        if (guildName == null || guildName.isBlank()) {
            if (pending.isEmpty()) {
                return ActionResult.fail("You have no pending invitation.");
            }
            target = pending.getFirst().guild();
        } else {
            for (InviteView view : pending) {
                if (view.guild().name().equalsIgnoreCase(guildName)
                    || view.guild().tag().equalsIgnoreCase(guildName)) {
                    target = view.guild();
                    break;
                }
            }
            if (target == null) {

                Guild open = byName(guildName);
                if (open != null && open.openJoin()) {
                    return joinOpen(player, open);
                }
                return ActionResult.fail("No invitation from <white>" + guildName + "</white>.");
            }
        }
        if (target.members().size() >= maxMembers()) {
            invites.remove(new InviteKey(target.id(), player.getUniqueId()));
            return ActionResult.fail("That guild is now full.");
        }
        invites.remove(new InviteKey(target.id(), player.getUniqueId()));
        return addToGuild(player, target);
    }

    public ActionResult declineInvite(Player player, String guildName) {
        if (guildName == null) {
            List<InviteView> pending = invitesFor(player.getUniqueId());
            if (pending.isEmpty()) {
                return ActionResult.fail("You have no pending invitation.");
            }
            Guild guild = pending.getFirst().guild();
            invites.remove(new InviteKey(guild.id(), player.getUniqueId()));
            return ActionResult.ok("Declined the invitation from " + guild.displayName() + ".");
        }
        Guild guild = byName(guildName);
        if (guild == null || !hasInviteTo(player.getUniqueId(), guild)) {
            return ActionResult.fail("No invitation from <white>" + guildName + "</white>.");
        }
        invites.remove(new InviteKey(guild.id(), player.getUniqueId()));
        return ActionResult.ok("Declined the invitation from " + guild.displayName() + ".");
    }

    private ActionResult addToGuild(Player player, Guild guild) {
        guild.addMember(player.getUniqueId(), player.getName(), Role.MEMBER);
        guildByPlayer.put(player.getUniqueId(), guild.id());

        for (InviteView view : invitesFor(player.getUniqueId())) {
            invites.remove(new InviteKey(view.guild().id(), player.getUniqueId()));
        }
        save();
        broadcastToGuild(guild, "<#4ADE80>✔</#4ADE80> <white>" + player.getName()
            + "</white> joined the guild.");
        return ActionResult.ok("You joined " + guild.displayNameAndTag() + ".");
    }

    public ActionResult leave(Player player) {
        Guild guild = guildOf(player.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (guild.isLeader(player.getUniqueId()) && guild.members().size() > 1) {
            Member heir = guild.sortedMembers().stream()
                .filter(m -> !m.uuid().equals(player.getUniqueId()))
                .findFirst().orElse(null);
            if (heir == null) {
                return ActionResult.fail("Transfer leadership before leaving.");
            }

            guild.promote(heir);
            broadcastToGuild(guild, "<#FBBF24><bold>" + heir.name()
                + "</bold></#FBBF24> <gray>is now the guild leader.</gray>");
        }
        removeFromGuild(player, guild);
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
        Member target = guild.byName(targetName);
        if (target == null) {
            return ActionResult.fail("No such member: " + targetName);
        }
        if (target.uuid().equals(actor.getUniqueId())) {
            return ActionResult.fail("Use <yellow>Leave guild</yellow> to remove yourself.");
        }
        if (target.role().weight() >= actorRole.weight()) {
            return ActionResult.fail("You cannot kick a member of equal or higher rank.");
        }
        guild.removeMember(target.uuid());
        guildByPlayer.remove(target.uuid());
        save();
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            Msg.send(online, Msg.warn("You were removed from " + guild.displayName() + "."));
        }
        broadcastToGuild(guild, "<#F87171>✖</#F87171> <white>" + target.name()
            + "</white> was removed from the guild.");
        return ActionResult.ok("Kicked <white>" + target.name() + "</white> from the guild.");
    }

    public ActionResult promote(Player actor, String targetName) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (!guild.isLeader(actor.getUniqueId())) {
            return ActionResult.fail("Only the guild leader can promote members.");
        }
        Member target = guild.byName(targetName);
        if (target == null) {
            return ActionResult.fail("No such member: " + targetName);
        }
        if (target.uuid().equals(actor.getUniqueId())) {
            return ActionResult.fail("You are already the leader.");
        }
        if (target.role() == Role.OFFICER) {
            return ActionResult.fail("<white>" + target.name() + "</white> is already an officer.");
        }
        if (!guild.hasOfficerSlot()) {
            return ActionResult.fail("Your guild already has " + Guild.MAX_OFFICERS + " officers.");
        }
        guild.promote(target);
        save();
        broadcastToGuild(guild, "<#FBBF24>▲</#FBBF24> <white>" + target.name()
            + "</white> is now an officer.");
        return ActionResult.ok("<white>" + target.name() + "</white> is now an officer.");
    }

    public ActionResult demote(Player actor, String targetName) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (!guild.isLeader(actor.getUniqueId())) {
            return ActionResult.fail("Only the guild leader can demote officers.");
        }
        Member target = guild.byName(targetName);
        if (target == null) {
            return ActionResult.fail("No such member: " + targetName);
        }
        if (target.role() != Role.OFFICER) {
            return ActionResult.fail("<white>" + target.name() + "</white> is not an officer.");
        }
        guild.demote(target);
        save();
        return ActionResult.ok("<white>" + target.name() + "</white> is back to member.");
    }

    public ActionResult transferLeadership(Player actor, String targetName) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (!guild.isLeader(actor.getUniqueId())) {
            return ActionResult.fail("Only the guild leader can transfer leadership.");
        }
        Member target = guild.byName(targetName);
        if (target == null) {
            return ActionResult.fail("No such member: " + targetName);
        }
        if (target.uuid().equals(actor.getUniqueId())) {
            return ActionResult.fail("You are already the leader.");
        }
        guild.promote(target);
        save();
        broadcastToGuild(guild, "<#FBBF24><bold>" + target.name()
            + "</bold></#FBBF24> <gray>is now the guild leader.</gray>");
        return ActionResult.ok("Leadership transferred to <white>" + target.name() + "</white>.");
    }

    public ActionResult disband(Player actor) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (!guild.isLeader(actor.getUniqueId())) {
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

    public ActionResult setMotd(Player actor, String motd) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        Role role = guild.roleOf(actor.getUniqueId());
        if (role == null || !role.canEditSettings()) {
            return ActionResult.fail("Only leaders and officers can set the MOTD.");
        }
        String clean = motd == null ? "" : plain(motd.trim());
        if (clean.length() > 64) {
            clean = clean.substring(0, 64);
        }
        guild.setMotd(clean);
        save();
        return ActionResult.ok("Guild MOTD updated.");
    }

    public ActionResult setDescription(Player actor, String description) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        Role role = guild.roleOf(actor.getUniqueId());
        if (role == null || !role.canEditSettings()) {
            return ActionResult.fail("Only leaders and officers can edit the description.");
        }
        String clean = description == null ? "" : plain(description.trim());
        if (clean.length() > 96) {
            clean = clean.substring(0, 96);
        }
        guild.setDescription(clean);
        save();
        return ActionResult.ok("Guild description updated.");
    }

    public ActionResult renameTag(Player actor, String rawTag) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (!guild.isLeader(actor.getUniqueId())) {
            return ActionResult.fail("Only the leader can rename the tag.");
        }
        String tag = (rawTag == null ? "" : rawTag.trim().toUpperCase(Locale.ROOT))
            .replaceAll("[^A-Z0-9]", "");
        int max = plugin.settings().guilds().tagMaxLength();
        if (tag.length() < 2 || tag.length() > max) {
            return ActionResult.fail("Guild tag must be 2–" + max + " letters or digits.");
        }
        for (Guild other : guildsById.values()) {
            if (!other.id().equals(guild.id()) && other.tag().equalsIgnoreCase(tag)) {
                return ActionResult.fail("That tag is already taken.");
            }
            if (!other.id().equals(guild.id()) && other.name().equalsIgnoreCase(tag)) {
                return ActionResult.fail("That tag clashes with another guild's name.");
            }
        }
        guild.setTag(tag);
        save();
        broadcastToGuild(guild, "<gray>Guild tag is now " + guild.displayTag() + ".");
        return ActionResult.ok("Guild tag set to " + guild.displayTag() + ".");
    }

    public ActionResult setOpenJoin(Player actor, boolean open) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        Role role = guild.roleOf(actor.getUniqueId());
        if (role == null || !role.canEditSettings()) {
            return ActionResult.fail("Only leaders and officers can change recruitment.");
        }
        guild.setOpenJoin(open);
        save();
        return ActionResult.ok(open
            ? "Recruitment is now <#4ADE80>OPEN</#4ADE80>."
            : "Recruitment is now <#F87171>INVITE ONLY</#F87171>.");
    }

    public ActionResult cycleColor(Player actor, int step) {
        Guild guild = guildOf(actor.getUniqueId());
        if (guild == null) {
            return ActionResult.fail("You are not in a guild.");
        }
        if (!guild.isLeader(actor.getUniqueId())) {
            return ActionResult.fail("Only the leader can change the guild colour.");
        }
        guild.cycleColor(step);
        save();
        return ActionResult.ok("Guild colour set to <" + guild.hexColor() + ">"
            + guild.hexColor().substring(1) + "</" + guild.hexColor() + ">.");
    }

    public void onMatchFinished(List<UUID> winners, List<UUID> losers, int eloDelta) {
        Map<String, Set<UUID>> onWinningSide = new LinkedHashMap<>();
        Map<String, Set<UUID>> onLosingSide = new LinkedHashMap<>();
        for (UUID id : winners) {
            Guild g = guildOf(id);
            if (g != null) {
                onWinningSide.computeIfAbsent(g.id(), k -> new LinkedHashSet<>()).add(id);
            }
        }
        for (UUID id : losers) {
            Guild g = guildOf(id);
            if (g != null) {
                onLosingSide.computeIfAbsent(g.id(), k -> new LinkedHashSet<>()).add(id);
            }
        }
        Set<String> ids = new LinkedHashSet<>(onWinningSide.keySet());
        ids.addAll(onLosingSide.keySet());
        for (String id : ids) {
            Guild guild = guildsById.get(id);
            if (guild == null) {
                continue;
            }
            boolean won = onWinningSide.containsKey(id);
            int delta = won ? Math.abs(eloDelta) : -Math.abs(eloDelta);
            guild.setMatchesPlayed(guild.matchesPlayed() + 1);
            if (won) {
                guild.setMatchesWon(guild.matchesWon() + 1);
                guild.setTrophies(guild.trophies() + 1);
            }
            guild.adjustElo(delta);
            Set<UUID> contributors = won ? onWinningSide.get(id) : onLosingSide.get(id);
            for (UUID playerId : contributors) {
                Member member = guild.member(playerId);
                if (member != null) {
                    member.addContributedElo(delta);
                    member.addMatchPlayed();
                    member.setLastSeenMillis(System.currentTimeMillis());
                }
            }
        }
        save();
    }

    private void removeFromGuild(Player player, Guild guild) {
        guild.removeMember(player.getUniqueId());
        guildByPlayer.remove(player.getUniqueId());
        if (guild.members().isEmpty()) {
            guildsById.remove(guild.id());
        }
        save();
        broadcastToGuild(guild, "<#64748B>←</#64748B> <white>" + player.getName()
            + "</white> left the guild.");
    }

    public void broadcastToGuild(Guild guild, String message) {
        if (guild == null) {
            return;
        }
        for (Member m : guild.members()) {
            Player online = Bukkit.getPlayer(m.uuid());
            if (online != null) {
                Msg.send(online, message);
            }
        }
    }

    public void onQuit(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Guild guild = guildOf(playerId);
        if (guild != null) {
            Member member = guild.member(playerId);
            if (member != null) {
                member.setLastSeenMillis(System.currentTimeMillis());
            }
        }
        invites.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public Map<UUID, String> membershipSnapshot() {
        return Map.copyOf(guildByPlayer);
    }

    public String chatPrefix(UUID playerId) {
        Guild guild = guildOf(playerId);
        return guild == null ? "" : guild.displayTag() + " ";
    }

    private static String plain(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^>]*>", "").replaceAll("\\s{2,}", " ").trim();
    }

    public static String formatRemaining(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
    }
}
