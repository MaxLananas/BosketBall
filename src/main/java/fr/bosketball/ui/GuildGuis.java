package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.social.Guild;
import fr.bosketball.social.GuildService;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class GuildGuis {

    private enum Screen {
        HOME,
        ROSTER,
        INVITES,
        SETTINGS,
        LADDER
    }

    private static final int BACK_SLOT = 53;

    private final BosketBallPlugin plugin;

    public GuildGuis(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        show(player, Screen.HOME, 0);
    }

    private void show(Player player, Screen screen, int page) {
        GuildService service = plugin.guildService();
        Guild guild = service.guildOf(player.getUniqueId());
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(
            GuiMenuManager.MenuType.GUILDS);
        String caption = switch (screen) {
            case HOME -> GuiMenuManager.tr(player, "gui.title.guilds", "GUILDS");
            case ROSTER -> GuiMenuManager.tr(player, "gui.guild.roster", "ROSTER");
            case INVITES -> GuiMenuManager.tr(player, "gui.guild.invites", "INVITATIONS");
            case SETTINGS -> GuiMenuManager.tr(player, "gui.guild.settings", "GUILD SETTINGS");
            case LADDER -> GuiMenuManager.tr(player, "gui.guild.ladder", "GUILD LADDER");
        };
        Inventory inv = Bukkit.createInventory(holder, 54,
            Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_GUILD, caption));
        holder.setInventory(inv);
        fillBackdrop(inv);

        switch (screen) {
            case HOME:
                if (guild == null) {
                    homeNoGuild(player, inv, holder, service);
                } else {
                    homeInGuild(player, inv, holder, service, guild);
                }
                break;
            case ROSTER:
                roster(player, inv, holder, service, guild);
                break;
            case INVITES:
                inviteScreen(player, inv, holder, service, guild);
                break;
            case SETTINGS:
                settings(player, inv, holder, service, guild);
                break;
            case LADDER:
                ladder(player, inv, holder, service, page);
                break;
            default:
                break;
        }
        player.openInventory(inv);
    }

    private void homeNoGuild(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                             GuildService service) {
        List<GuildService.InviteView> invites = service.invitesFor(player.getUniqueId());
        inv.setItem(4, GuiMenuManager.customIcon("icon_guild",
            "<#FBBF24><bold>ɢᴜɪʟᴅ ʜᴜʙ</bold></#FBBF24>",
            List.of(
                "<gray>You are not in a guild yet.</gray>",
                "<gray>A guild is a persistent team: shared tag,</gray>",
                "<gray>shared ELO and its own leaderboard.</gray>",
                "",
                "<gray>Pending invitations:</gray> <white>" + invites.size() + "</white>",
                "<gray>Open guilds:</gray> <white>" + service.joinable().size() + "</white>",
                "",
                "<gray>Creation cost:</gray> <gold>" + service.createCostCoins()
                    + " Coins</gold>")));

        holder.slotStringMap().put(19, "GUILD_INVITES");
        inv.setItem(19, GuiMenuManager.customIcon("icon_party_invites",
            "<#38BDF8><bold>ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ</bold></#38BDF8>",
            invites.isEmpty()
                ? List.of("<gray>No invitations right now.</gray>",
                    "<gray>A leader or officer must invite you.</gray>")
                : inviteLore(invites, "Click to review and accept")));

        holder.slotStringMap().put(21, "GUILD_CREATE");
        inv.setItem(21, GuiMenuManager.customIcon("icon_new_guild",
            "<#4ADE80><bold>ᴄʀᴇᴀᴛᴇ ᴀ ɢᴜɪʟᴅ</bold></#4ADE80>",
            List.of(
                "<gray>Found your own guild and become its leader.</gray>",
                "",
                "<gray>Cost:</gray> <gold>" + service.createCostCoins() + " Coins</gold>",
                "<gray>Max members:</gray> <white>" + service.maxMembers() + "</white>",
                "",
                "<#4ADE80>▸ Click, then type the name in chat</#4ADE80>")));

        holder.slotStringMap().put(23, "GUILD_LADDER");
        inv.setItem(23, GuiMenuManager.customIcon("ref_cup_gold",
            "<#FDE047><bold>ɢᴜɪʟᴅ ʟᴀᴅᴅᴇʀ</bold></#FDE047>",
            List.of("<gray>Every guild ranked by guild ELO.</gray>",
                "",
                "<yellow>▸ Click to browse")));

        holder.slotStringMap().put(31, "GUILD_BROWSE");
        inv.setItem(31, GuiMenuManager.customIcon("icon_team",
            "<#A855F7><bold>ᴏᴘᴇɴ ɢᴜɪʟᴅꜱ</bold></#A855F7>",
            List.of("<gray>Guilds currently taking new members.</gray>",
                "<gray>Anyone may join these without an invite.</gray>",
                "",
                "<#A855F7>▸ Click to browse & join</#A855F7>")));

        inv.setItem(BACK_SLOT, backButton(player));
        holder.slotStringMap().put(BACK_SLOT, "BACK");
    }

    private void homeInGuild(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                             GuildService service, Guild guild) {
        UUID id = player.getUniqueId();
        Guild.Role role = guild.roleOf(id);
        boolean leader = guild.isLeader(id);
        boolean officer = role != null && role.canInvite();

        inv.setItem(4, GuiMenuManager.customIcon("icon_guild",
            guild.displayNameAndTag(),
            List.of(
                "<gray>Members:</gray> <white>" + guild.members().size()
                    + "/" + service.maxMembers() + "</white>",
                "<gray>Guild ELO:</gray> <#FBBF24>" + guild.elo() + "</#FBBF24>"
                    + " <dark_gray>•</dark_gray> <gray>Rank:</gray> <white>#"
                    + service.placementOf(guild) + "</white>",
                "<gray>Record:</gray> <#4ADE80>" + guild.matchesWon() + "W</#4ADE80>"
                    + "<gray>/</gray><#F87171>" + guild.losses() + "L</#F87171>"
                    + " <dark_gray>(" + Math.round(guild.winRate() * 100) + "% WR)</dark_gray>",
                "<gray>Your rank:</gray> " + (role == null ? "<white>—</white>" : role.coloredTitle()),
                "",
                "<gray>MOTD:</gray> <white>"
                    + (guild.motd().isBlank() ? "—" : guild.motd()) + "</white>")));

        holder.slotStringMap().put(20, "GUILD_ROSTER");
        inv.setItem(20, GuiMenuManager.customIcon("ref_friends",
            "<#38BDF8><bold>ʀᴏꜱᴛᴇʀ</bold></#38BDF8>",
            List.of("<gray>Everyone in the guild, online first.</gray>",
                "<gray>Members:</gray> <white>" + guild.members().size()
                    + "/" + service.maxMembers() + "</white>",
                "<gray>Officers:</gray> <white>" + guild.officerCount()
                    + "/" + Guild.MAX_OFFICERS + "</white>",
                "",
                "<#38BDF8>▸ Click to open the roster</#38BDF8>")));

        holder.slotStringMap().put(22, "GUILD_INVITES");
        inv.setItem(22, GuiMenuManager.customIcon("icon_party_invites",
            "<#4ADE80><bold>ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ</bold></#4ADE80>",
            officer
                ? List.of("<gray>Invite players and review pending invites.</gray>",
                    "<gray>Your pending invites:</gray> <white>"
                        + service.invitesFor(id).size() + "</white>",
                    "",
                    "<#4ADE80>▸ Click to invite someone</#4ADE80>")
                : List.of("<gray>Only leaders and officers can invite.</gray>",
                    "<gray>Your pending invites:</gray> <white>"
                        + service.invitesFor(id).size() + "</white>",
                    "",
                    "<gray>▸ Click to review your invitations</gray>")));

        holder.slotStringMap().put(24, "GUILD_SETTINGS");
        inv.setItem(24, GuiMenuManager.customIcon("ref_gear",
            "<#FBBF24><bold>ꜱᴇᴛᴛɪɴɢꜱ</bold></#FBBF24>",
            List.of("<gray>Recruitment, MOTD, description & colour.</gray>",
                "<gray>Recruitment:</gray> " + (guild.openJoin()
                    ? "<#4ADE80>OPEN</#4ADE80>" : "<#F87171>INVITE ONLY</#F87171>"),
                "",
                officer
                    ? "<yellow>▸ Click to edit</yellow>"
                    : "<gray>▸ Leaders and officers only</gray>")));

        inv.setItem(30, GuiMenuManager.customIcon("ref_trophy",
            "<#FDE047><bold>ɢᴜɪʟᴅ ᴛʀᴏᴘʜɪᴇꜱ</bold></#FDE047>",
            List.of("<gray>Trophies earned with the guild:</gray>",
                "<#FDE047>" + guild.trophies() + "</#FDE047>",
                "",
                "<gray>Guild ELO:</gray> <white>" + guild.elo() + "</white>")));

        holder.slotStringMap().put(32, "GUILD_LADDER");
        inv.setItem(32, GuiMenuManager.customIcon("ref_cup_gold",
            "<#38BDF8><bold>ɢᴜɪʟᴅ ʟᴀᴅᴅᴇʀ</bold></#38BDF8>",
            List.of("<gray>See how your guild ranks against</gray>",
                "<gray>every other guild on the server.</gray>",
                "",
                "<gray>Position:</gray> <white>#" + service.placementOf(guild) + "</white>",
                "",
                "<#38BDF8>▸ Click to open</#38BDF8>")));

        holder.slotStringMap().put(45, "GUILD_LEAVE");
        inv.setItem(45, GuiMenuManager.customIcon("ref_arrow_left",
            "<#F87171><bold>ʟᴇᴀᴠᴇ ᴛʜᴇ ɢᴜɪʟᴅ</bold></#F87171>",
            List.of(leader && guild.members().size() > 1
                    ? "<gray>Leadership passes to the next member.</gray>"
                    : "<gray>You can be invited again later.</gray>",
                "",
                "<red>▸ Shift-click to confirm</red>")));

        inv.setItem(BACK_SLOT, backButton(player));
        holder.slotStringMap().put(BACK_SLOT, "BACK");
    }

    private void roster(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                        GuildService service, Guild guild) {
        if (guild == null) {
            holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
            inv.setItem(BACK_SLOT, backButton(player));
            return;
        }
        UUID viewer = player.getUniqueId();
        Guild.Role viewerRole = guild.roleOf(viewer);
        boolean leader = viewerRole == Guild.Role.LEADER;
        boolean officer = viewerRole != null && viewerRole.canKick();

        inv.setItem(4, GuiMenuManager.customIcon("ref_friends",
            "<#38BDF8><bold>ʀᴏꜱᴛᴇʀ</bold></#38BDF8>",
            List.of("<gray>" + guild.displayNameAndTag() + "</gray>",
                "<gray>Members:</gray> <white>" + guild.members().size()
                    + "/" + service.maxMembers() + "</white>",
                "<gray>Officers:</gray> <white>" + guild.officerCount()
                    + "/" + Guild.MAX_OFFICERS + "</white>",
                "<gray>Guild ELO:</gray> <#FBBF24>" + guild.elo() + "</#FBBF24>")));

        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };
        List<Guild.Member> members = guild.sortedMembers();
        for (int i = 0; i < members.size() && i < slots.length; i++) {
            Guild.Member member = members.get(i);
            boolean online = Bukkit.getPlayer(member.uuid()) != null;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Rank:</gray> " + member.role().coloredTitle());
            lore.add("<gray>Status:</gray> " + (online
                ? "<#4ADE80>Online</#4ADE80>" : "<#64748B>Offline</#64748B>"));
            lore.add("<gray>Guild ELO earned:</gray> <white>"
                + (member.contributedElo() >= 0 ? "+" : "") + member.contributedElo() + "</white>");
            lore.add("<gray>Matches with the guild:</gray> <white>"
                + member.matchesPlayed() + "</white>");
            lore.add("<gray>Joined:</gray> <white>"
                + java.time.Instant.ofEpochMilli(member.joinedAtMillis())
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
                + "</white>");

            boolean canAct = officer && !member.uuid().equals(viewer)
                && member.role().weight() < (viewerRole == null ? 0 : viewerRole.weight());
            boolean isSelf = member.uuid().equals(viewer);
            if (canAct) {
                lore.add("");
                if (leader && member.role() == Guild.Role.MEMBER) {
                    lore.add("<#4ADE80>▸ Left-click: promote to officer</#4ADE80>");
                    lore.add("<#FBBF24>▸ Shift-left-click: transfer leadership</#FBBF24>");
                } else if (leader && member.role() == Guild.Role.OFFICER) {
                    lore.add("<#FBBF24>▸ Left-click: demote to member</#FBBF24>");
                    lore.add("<#FBBF24>▸ Shift-left-click: transfer leadership</#FBBF24>");
                }
                lore.add("<#F87171>▸ Shift-right-click: remove from guild</#F87171>");
                holder.slotStringMap().put(slots[i], "GUILD_MEMBER:" + member.name());
            } else if (isSelf) {
                lore.add("");
                lore.add("<gray>▸ This is you</gray>");
            }
            inv.setItem(slots[i], plugin.guiMenu().headIcon(member.uuid(), member.name(),
                "<white><bold>" + member.name() + "</bold></white>", lore));
        }

        holder.slotStringMap().put(49, "GUILD_INVITES");
        inv.setItem(49, GuiMenuManager.customIcon("icon_party_invites",
            "<#4ADE80><bold>ɪɴᴠɪᴛᴇ ᴀ ᴘʟᴀʏᴇʀ</bold></#4ADE80>",
            officer
                ? List.of("<gray>Send a guild invitation.</gray>",
                    "<#4ADE80>▸ Click, then type the player name</#4ADE80>")
                : List.of("<gray>Only leaders and officers can invite.</gray>")));

        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the guild hub.</gray>")));
    }

    private void inviteScreen(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                              GuildService service, Guild guild) {
        UUID id = player.getUniqueId();
        List<GuildService.InviteView> invites = service.invitesFor(id);
        inv.setItem(4, GuiMenuManager.customIcon("icon_party_invites",
            "<#4ADE80><bold>ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ</bold></#4ADE80>",
            invites.isEmpty()
                ? List.of("<gray>No pending invitations.</gray>",
                    "<gray>Invitations expire after 5 minutes.</gray>")
                : inviteLore(invites, "Click one to accept")));

        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < invites.size() && i < slots.length; i++) {
            GuildService.InviteView view = invites.get(i);
            Guild target = view.guild();
            holder.slotStringMap().put(slots[i], "GUILD_ACCEPT:" + target.name());
            inv.setItem(slots[i], GuiMenuManager.customIcon("icon_guild",
                target.displayNameAndTag(),
                List.of(
                    "<gray>Invited by:</gray> <white>" + view.inviterName() + "</white>",
                    "<gray>Members:</gray> <white>" + target.members().size()
                        + "/" + service.maxMembers() + "</white>",
                    "<gray>Guild ELO:</gray> <#FBBF24>" + target.elo() + "</#FBBF24>",
                    "<gray>Expires in:</gray> <white>"
                        + GuildService.formatRemaining(view.remainingMillis()) + "</white>",
                    "",
                    "<#4ADE80>▸ Left-click to join</#4ADE80>",
                    "<#F87171>▸ Right-click to decline</#F87171>")));
        }
        if (invites.isEmpty()) {
            inv.setItem(22, GuiMenuManager.customIcon("ref_lock",
                "<#64748B><bold>ɴᴏ ɪɴᴠɪᴛᴀᴛɪᴏɴ</bold></#64748B>",
                List.of("<gray>Ask a guild leader or officer to invite you.</gray>",
                    "<gray>You can also join an open guild from the hub.</gray>")));
        }

        if (guild != null && guild.canManageRoster(id)) {
            holder.slotStringMap().put(40, "GUILD_INVITE");
            inv.setItem(40, GuiMenuManager.customIcon("icon_new_guild",
                "<#4ADE80><bold>ɪɴᴠɪᴛᴇ ᴀ ᴘʟᴀʏᴇʀ</bold></#4ADE80>",
                List.of("<gray>Invite someone to " + guild.displayName() + ".</gray>",
                    "<gray>They have 5 minutes to accept.</gray>",
                    "",
                    "<#4ADE80>▸ Click, then type the name</#4ADE80>")));
        }

        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the guild hub.</gray>")));
    }

    private void settings(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                          GuildService service, Guild guild) {
        if (guild == null) {
            holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
            inv.setItem(BACK_SLOT, backButton(player));
            return;
        }
        UUID id = player.getUniqueId();
        Guild.Role role = guild.roleOf(id);
        boolean leader = role == Guild.Role.LEADER;
        boolean officer = role != null && role.canEditSettings();

        inv.setItem(4, GuiMenuManager.customIcon("ref_gear",
            "<#FBBF24><bold>ɢᴜɪʟᴅ ꜱᴇᴛᴛɪɴɢꜱ</bold></#FBBF24>",
            List.of("<gray>" + guild.displayNameAndTag() + "</gray>",
                "<gray>Your permission:</gray> "
                    + (role == null ? "<white>—</white>" : role.coloredTitle()),
                officer ? "" : "<gray>Only leaders and officers may edit.</gray>")));

        holder.slotStringMap().put(20, "GUILD_OPEN_TOGGLE");
        inv.setItem(20, GuiMenuManager.customIcon(
            guild.openJoin() ? "ref_check" : "ref_lock",
            "<#38BDF8><bold>ʀᴇᴄʀᴜɪᴛᴍᴇɴᴛ</bold></#38BDF8>",
            List.of("<gray>Current:</gray> " + (guild.openJoin()
                    ? "<#4ADE80>OPEN — anyone may join</#4ADE80>"
                    : "<#F87171>INVITE ONLY</#F87171>"),
                "",
                "<yellow>▸ Click to toggle</yellow>")));

        holder.slotStringMap().put(22, "GUILD_MOTD");
        inv.setItem(22, GuiMenuManager.customIcon("ref_medal_gold",
            "<#4ADE80><bold>ᴍᴏᴛᴅ</bold></#4ADE80>",
            List.of("<gray>Shown to every member on login.</gray>",
                "<gray>Current:</gray> <white>"
                    + (guild.motd().isBlank() ? "—" : guild.motd()) + "</white>",
                "",
                "<yellow>▸ Click, then type the message</yellow>")));

        holder.slotStringMap().put(24, "GUILD_DESCRIPTION");
        inv.setItem(24, GuiMenuManager.customIcon("ref_clock",
            "<#A855F7><bold>ᴅᴇꜱᴄʀɪᴘᴛɪᴏɴ</bold></#A855F7>",
            List.of("<gray>What your guild is about.</gray>",
                "<gray>Current:</gray> <white>"
                    + (guild.description().isBlank() ? "—" : guild.description()) + "</white>",
                "",
                "<yellow>▸ Click, then type the text</yellow>")));

        holder.slotStringMap().put(30, "GUILD_COLOR_PREV");
        inv.setItem(30, GuiMenuManager.customIcon("icon_prev",
            "<#CBD5E1><bold>‹ ᴄᴏʟᴏᴜʀ</bold></#CBD5E1>",
            List.of("<gray>Previous guild colour.</gray>",
                "<gray>Current:</gray> <" + guild.hexColor() + ">"
                    + guild.hexColor().substring(1) + "</" + guild.hexColor() + ">")));

        holder.slotStringMap().put(31, "GUILD_COLOR_NEXT");
        inv.setItem(31, GuiMenuManager.customIcon("icon_guild",
            "<" + guild.hexColor() + "><bold>ɢᴜɪʟᴅ ᴄᴏʟᴏᴜʀ</bold></" + guild.hexColor() + ">",
            List.of("<gray>Tag preview:</gray> " + guild.displayTag(),
                "<gray>Name preview:</gray> " + guild.displayName(),
                "",
                leader ? "<yellow>▸ Use the arrows to change it</yellow>"
                    : "<gray>▸ Only the leader can change it</gray>")));

        holder.slotStringMap().put(32, "GUILD_COLOR_NEXT");
        inv.setItem(32, GuiMenuManager.customIcon("icon_next",
            "<#CBD5E1><bold>ᴄᴏʟᴏᴜʀ ›</bold></#CBD5E1>",
            List.of("<gray>Next guild colour.</gray>",
                "<gray>Available:</gray> <white>" + Guild.PALETTE.size() + " colours</white>")));

        holder.slotStringMap().put(40, "GUILD_RENAME");
        inv.setItem(40, GuiMenuManager.customIcon("ref_bag",
            "<#F87171><bold>ʀᴇɴᴀᴍᴇ ᴛᴀɢ</bold></#F87171>",
            List.of("<gray>Current tag:</gray> " + guild.displayTag(),
                leader ? "<gray>▸ Click, then type the new tag</gray>"
                    : "<gray>▸ Only the leader can rename the tag</gray>")));

        if (leader) {
            holder.slotStringMap().put(49, "GUILD_DISBAND");
            inv.setItem(49, GuiMenuManager.customIcon("icon_delete",
                "<#F87171><bold>ᴅɪꜱʙᴀɴᴅ ᴛʜᴇ ɢᴜɪʟᴅ</bold></#F87171>",
                List.of("<gray>Deletes the guild for everyone.</gray>",
                    "<gray>This cannot be undone.</gray>",
                    "",
                    "<red>▸ Shift-click twice to confirm</red>")));
        }

        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the guild hub.</gray>")));
    }

    private void ladder(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                        GuildService service, int page) {
        List<Guild> top = service.topByElo(Integer.MAX_VALUE);
        int perPage = 21;
        int pages = Math.max(1, (top.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(pages - 1, page));
        Guild mine = service.guildOf(player.getUniqueId());

        inv.setItem(4, GuiMenuManager.customIcon("ref_cup_gold",
            "<#FDE047><bold>ɢᴜɪʟᴅ ʟᴀᴅᴅᴇʀ</bold></#FDE047>",
            List.of("<gray>Top guilds by guild ELO.</gray>",
                "<gray>Guilds:</gray> <white>" + top.size() + "</white>",
                mine == null
                    ? "<gray>Your guild:</gray> <white>None</white>"
                    : "<gray>Your guild:</gray> " + mine.displayNameAndTag()
                        + " <gray>#" + service.placementOf(mine) + "</gray>")));

        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };
        int start = current * perPage;
        for (int i = 0; i < perPage && start + i < top.size(); i++) {
            Guild guild = top.get(start + i);
            int place = start + i + 1;
            String model = place == 1 ? "ref_trophy"
                : place == 2 ? "icon_medal_gold"
                : place == 3 ? "icon_star_shield" : "icon_guild";
            List<String> lore = new ArrayList<>();
            lore.add("<gray>ELO:</gray> <#FBBF24>" + guild.elo() + "</#FBBF24>");
            lore.add("<gray>Members:</gray> <white>" + guild.members().size()
                + "/" + service.maxMembers() + "</white>");
            lore.add("<gray>Record:</gray> <#4ADE80>" + guild.matchesWon() + "W</#4ADE80>"
                + "<gray>/</gray><#F87171>" + guild.losses() + "L</#F87171>");
            lore.add("<gray>Leader:</gray> <white>" + guild.leaderName() + "</white>");
            if (mine != null && mine.id().equals(guild.id())) {
                lore.add("");
                lore.add("<#4ADE80>▸ This is your guild</#4ADE80>");
            }
            inv.setItem(slots[i], GuiMenuManager.customIcon(model,
                "<" + guild.hexColor() + "><bold>#" + place + "</bold></" + guild.hexColor() + "> "
                    + guild.displayNameAndTag(), lore));
        }
        if (top.isEmpty()) {
            inv.setItem(22, GuiMenuManager.customIcon("ref_lock",
                "<#64748B><bold>ɴᴏ ɢᴜɪʟᴅꜱ ʏᴇᴛ</bold></#64748B>",
                List.of("<gray>No guild has been founded yet.</gray>",
                    "<gray>Be the first — the hub has a Create button.</gray>")));
        }

        if (current > 0) {
            holder.slotStringMap().put(48, "GUILD_LADDER_PAGE:" + (current - 1));
            inv.setItem(48, GuiMenuManager.customIcon("icon_prev",
                "<#CBD5E1><bold>‹ ᴘʀᴇᴠ</bold></#CBD5E1>",
                List.of("<gray>Page " + current + " of " + pages + "</gray>")));
        }
        if (current + 1 < pages) {
            holder.slotStringMap().put(50, "GUILD_LADDER_PAGE:" + (current + 1));
            inv.setItem(50, GuiMenuManager.customIcon("icon_next",
                "<#CBD5E1><bold>ɴᴇxᴛ ›</bold></#CBD5E1>",
                List.of("<gray>Page " + (current + 2) + " of " + pages + "</gray>")));
        }

        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the guild hub.</gray>")));
    }

    public void handle(Player player, String key, InventoryClickEvent event) {
        GuildService service = plugin.guildService();
        if (key == null) {
            return;
        }
        boolean shift = event != null && event.isShiftClick();
        boolean right = event != null && event.isRightClick();

        switch (key) {
            case "BACK" -> {

                player.closeInventory();
                plugin.guiMenu().navigateBackFromSubMenu(player);
            }
            case "GUILD_HOME" -> show(player, Screen.HOME, 0);
            case "GUILD_ROSTER" -> show(player, Screen.ROSTER, 0);
            case "GUILD_INVITES" -> show(player, Screen.INVITES, 0);
            case "GUILD_SETTINGS" -> show(player, Screen.SETTINGS, 0);
            case "GUILD_LADDER" -> show(player, Screen.LADDER, 0);
            case "GUILD_BROWSE" -> browseOpenGuilds(player);
            case "GUILD_CREATE" -> {
                player.closeInventory();
                plugin.socialManager().beginGuildNamePrompt(player);
            }
            case "GUILD_INVITE" -> {
                player.closeInventory();
                plugin.socialManager().beginGuildInvitePrompt(player);
            }
            case "GUILD_MOTD" -> {
                player.closeInventory();
                plugin.socialManager().beginGuildMotdPrompt(player);
            }
            case "GUILD_DESCRIPTION" -> {
                player.closeInventory();
                plugin.socialManager().beginGuildDescriptionPrompt(player);
            }
            case "GUILD_RENAME" -> {
                player.closeInventory();
                plugin.socialManager().beginGuildTagPrompt(player);
            }
            case "GUILD_OPEN_TOGGLE" -> {
                Guild guild = service.guildOf(player.getUniqueId());
                if (guild != null) {
                    report(player, service.setOpenJoin(player, !guild.openJoin()));
                }
                show(player, Screen.SETTINGS, 0);
            }
            case "GUILD_COLOR_PREV" -> {
                report(player, service.cycleColor(player, -1));
                show(player, Screen.SETTINGS, 0);
            }
            case "GUILD_COLOR_NEXT" -> {
                report(player, service.cycleColor(player, 1));
                show(player, Screen.SETTINGS, 0);
            }
            case "GUILD_LEAVE" -> {
                if (shift) {
                    report(player, service.leave(player));
                    show(player, Screen.HOME, 0);
                } else {
                    Msg.send(player, Msg.warn("Shift-click Leave guild to confirm."));
                }
            }
            case "GUILD_DISBAND" -> {
                if (shift) {
                    String result = service.disband(player).message();
                    Msg.send(player, Msg.warn(result));
                    player.closeInventory();
                } else {
                    Msg.send(player, Msg.warn("Shift-click Disband guild to confirm."));
                }
            }
            default -> {
                if (key.startsWith("GUILD_LADDER_PAGE:")) {
                    show(player, Screen.LADDER, parseIndex(key, "GUILD_LADDER_PAGE:"));
                    return;
                }
                if (key.startsWith("GUILD_ACCEPT:")) {
                    String guildName = key.substring("GUILD_ACCEPT:".length());
                    if (right) {
                        report(player, service.declineInvite(player, guildName));
                    } else {
                        report(player, service.acceptInvite(player, guildName));
                    }
                    show(player, Screen.HOME, 0);
                    return;
                }
                if (key.startsWith("GUILD_JOIN:")) {
                    Guild guild = service.byName(key.substring("GUILD_JOIN:".length()));
                    report(player, service.joinOpen(player, guild));
                    show(player, Screen.HOME, 0);
                    return;
                }
                if (key.startsWith("GUILD_MEMBER:")) {
                    memberAction(player, service, key.substring("GUILD_MEMBER:".length()),
                        shift, right);
                    show(player, Screen.ROSTER, 0);
                }
            }
        }
    }

    private void memberAction(Player actor, GuildService service, String memberName,
                              boolean shift, boolean right) {
        Guild guild = service.guildOf(actor.getUniqueId());
        if (guild == null) {
            return;
        }
        if (right && shift) {
            report(actor, service.kick(actor, memberName));
            return;
        }
        if (right) {
            Msg.send(actor, Msg.warn("Shift-right-click <white>" + memberName
                + "</white> to remove them from the guild."));
            return;
        }
        if (shift) {
            report(actor, service.transferLeadership(actor, memberName));
            return;
        }
        Guild.Member target = guild.byName(memberName);
        if (target == null) {
            return;
        }
        if (target.role() == Guild.Role.OFFICER) {
            report(actor, service.demote(actor, memberName));
        } else {
            report(actor, service.promote(actor, memberName));
        }
    }

    private void browseOpenGuilds(Player player) {
        List<Guild> open = plugin.guildService().joinable();
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(
            GuiMenuManager.MenuType.GUILDS);
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(
            Glyphs.GUI_OVERLAY_GUILD, GuiMenuManager.tr(player, "gui.guild.open", "OPEN GUILDS")));
        holder.setInventory(inv);
        fillBackdrop(inv);
        inv.setItem(4, GuiMenuManager.customIcon("icon_team",
            "<#A855F7><bold>ᴏᴘᴇɴ ɢᴜɪʟᴅꜱ</bold></#A855F7>",
            List.of("<gray>Guilds recruiting right now.</gray>",
                "<gray>Found:</gray> <white>" + open.size() + "</white>",
                "",
                "<gray>Joining is instant and free.</gray>")));

        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < open.size() && i < slots.length; i++) {
            Guild guild = open.get(i);
            holder.slotStringMap().put(slots[i], "GUILD_JOIN:" + guild.name());
            inv.setItem(slots[i], GuiMenuManager.customIcon("icon_guild",
                guild.displayNameAndTag(),
                List.of("<gray>Members:</gray> <white>" + guild.members().size()
                        + "/" + plugin.guildService().maxMembers() + "</white>",
                    "<gray>Guild ELO:</gray> <#FBBF24>" + guild.elo() + "</#FBBF24>",
                    "<gray>Leader:</gray> <white>" + guild.leaderName() + "</white>",
                    guild.description().isBlank() ? "" : "<gray>" + guild.description() + "</gray>",
                    "",
                    "<#4ADE80>▸ Click to join</#4ADE80>")));
        }
        if (open.isEmpty()) {
            inv.setItem(22, GuiMenuManager.customIcon("ref_lock",
                "<#64748B><bold>ɴᴏ ᴏᴘᴇɴ ɢᴜɪʟᴅ</bold></#64748B>",
                List.of("<gray>No guild is recruiting right now.</gray>",
                    "<gray>Ask a leader for an invitation.</gray>")));
        }
        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the guild hub.</gray>")));
        player.openInventory(inv);
    }

    private static void report(Player player, GuildService.ActionResult result) {
        Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
    }

    private static int parseIndex(String raw, String prefix) {
        try {
            return Integer.parseInt(raw.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static List<String> inviteLore(List<GuildService.InviteView> invites, String hint) {
        List<String> lore = new ArrayList<>();
        for (GuildService.InviteView view : invites) {
            lore.add(view.guild().displayTag() + " <white>" + view.guild().name() + "</white>"
                + " <dark_gray>• " + view.inviterName() + "</dark_gray>");
        }
        lore.add("");
        lore.add("<yellow>▸ " + hint + "</yellow>");
        return lore;
    }

    private ItemStack backButton(Player player) {
        return GuiMenuManager.customIcon("ref_arrow_left",
            "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the hub.</gray>"));
    }

    private static void fillBackdrop(Inventory inv) {
        ItemStack filler = GuiMenuManager.customIcon("ui_invisible", " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }
}
