package fr.bosketball.ui;

import fr.bosketball.util.Palette;
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
            "<" + Palette.GOLD + "><bold>ɢᴜɪʟᴅ ʜᴜʙ</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>You are not in a guild yet.</#94A3B8>",
                "<#94A3B8>A guild is a persistent team: shared tag,</#94A3B8>",
                "<#94A3B8>shared ELO and its own leaderboard.</#94A3B8>",
                "",
                "<#94A3B8>Pending invitations:</#94A3B8> <#F8FAFC>" + invites.size() + "</#F8FAFC>",
                "<#94A3B8>Open guilds:</#94A3B8> <#F8FAFC>" + service.joinable().size() + "</#F8FAFC>",
                "",
                "<#94A3B8>Creation cost:</#94A3B8> <#FBBF24>" + service.createCostCoins()
                    + " Coins</#FBBF24>")));

        holder.slotStringMap().put(19, "GUILD_INVITES");
        inv.setItem(19, GuiMenuManager.customIcon("icon_party_invites",
            "<" + Palette.ICE + "><bold>ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ</bold></" + Palette.ICE + ">",
            invites.isEmpty()
                ? List.of("<#94A3B8>No invitations right now.</#94A3B8>",
                    "<#94A3B8>A leader or officer must invite you.</#94A3B8>")
                : inviteLore(invites, "Click to review and accept")));

        holder.slotStringMap().put(21, "GUILD_CREATE");
        inv.setItem(21, GuiMenuManager.customIcon("icon_new_guild",
            "<" + Palette.SUCCESS + "><bold>ᴄʀᴇᴀᴛᴇ ᴀ ɢᴜɪʟᴅ</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Found your own guild and become its leader.</#94A3B8>",
                "",
                "<#94A3B8>Cost:</#94A3B8> <#FBBF24>" + service.createCostCoins() + " Coins</#FBBF24>",
                "<#94A3B8>Max members:</#94A3B8> <#F8FAFC>" + service.maxMembers() + "</#F8FAFC>",
                "",
                "<" + Palette.SUCCESS + ">▸ Click, then type the name in chat</" + Palette.SUCCESS + ">")));

        holder.slotStringMap().put(23, "GUILD_LADDER");
        inv.setItem(23, GuiMenuManager.customIcon("ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold>ɢᴜɪʟᴅ ʟᴀᴅᴅᴇʀ</bold></" + Palette.GOLD_SOFT + ">",
            List.of("<#94A3B8>Every guild ranked by guild ELO.</#94A3B8>",
                "",
                "<#FDE047>▸ Click to browse")));

        holder.slotStringMap().put(31, "GUILD_BROWSE");
        inv.setItem(31, GuiMenuManager.customIcon("icon_team",
            "<" + Palette.VIOLET + "><bold>ᴏᴘᴇɴ ɢᴜɪʟᴅꜱ</bold></" + Palette.VIOLET + ">",
            List.of("<#94A3B8>Guilds currently taking new members.</#94A3B8>",
                "<#94A3B8>Anyone may join these without an invite.</#94A3B8>",
                "",
                "<" + Palette.VIOLET + ">▸ Click to browse & join</" + Palette.VIOLET + ">")));

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
                "<#94A3B8>Members:</#94A3B8> <#F8FAFC>" + guild.members().size()
                    + "/" + service.maxMembers() + "</#F8FAFC>",
                "<#94A3B8>Guild ELO:</#94A3B8> <" + Palette.GOLD + ">" + guild.elo() + "</" + Palette.GOLD + ">"
                    + " <#64748B>·</#64748B> <#94A3B8>Rank:</#94A3B8> <#F8FAFC>#"
                    + service.placementOf(guild) + "</#F8FAFC>",
                "<#94A3B8>Record:</#94A3B8> <" + Palette.SUCCESS + ">" + guild.matchesWon() + "W</" + Palette.SUCCESS + ">"
                    + "<#94A3B8>/</#94A3B8><" + Palette.DANGER + ">" + guild.losses() + "L</" + Palette.DANGER + ">"
                    + " <#64748B>(" + Math.round(guild.winRate() * 100) + "% WR)</#64748B>",
                "<#94A3B8>Your rank:</#94A3B8> " + (role == null ? "<#F8FAFC>—</#F8FAFC>" : role.coloredTitle()),
                "",
                "<#94A3B8>MOTD:</#94A3B8> <#F8FAFC>"
                    + (guild.motd().isBlank() ? "—" : guild.motd()) + "</#F8FAFC>")));

        holder.slotStringMap().put(20, "GUILD_ROSTER");
        inv.setItem(20, GuiMenuManager.customIcon("ref_friends",
            "<" + Palette.ICE + "><bold>ʀᴏꜱᴛᴇʀ</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Everyone in the guild, online first.</#94A3B8>",
                "<#94A3B8>Members:</#94A3B8> <#F8FAFC>" + guild.members().size()
                    + "/" + service.maxMembers() + "</#F8FAFC>",
                "<#94A3B8>Officers:</#94A3B8> <#F8FAFC>" + guild.officerCount()
                    + "/" + Guild.MAX_OFFICERS + "</#F8FAFC>",
                "",
                "<" + Palette.ICE + ">▸ Click to open the roster</" + Palette.ICE + ">")));

        holder.slotStringMap().put(22, "GUILD_INVITES");
        inv.setItem(22, GuiMenuManager.customIcon("icon_party_invites",
            "<" + Palette.SUCCESS + "><bold>ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ</bold></" + Palette.SUCCESS + ">",
            officer
                ? List.of("<#94A3B8>Invite players and review pending invites.</#94A3B8>",
                    "<#94A3B8>Your pending invites:</#94A3B8> <#F8FAFC>"
                        + service.invitesFor(id).size() + "</#F8FAFC>",
                    "",
                    "<" + Palette.SUCCESS + ">▸ Click to invite someone</" + Palette.SUCCESS + ">")
                : List.of("<#94A3B8>Only leaders and officers can invite.</#94A3B8>",
                    "<#94A3B8>Your pending invites:</#94A3B8> <#F8FAFC>"
                        + service.invitesFor(id).size() + "</#F8FAFC>",
                    "",
                    "<#94A3B8>▸ Click to review your invitations</#94A3B8>")));

        holder.slotStringMap().put(24, "GUILD_SETTINGS");
        inv.setItem(24, GuiMenuManager.customIcon("ref_gear",
            "<" + Palette.GOLD + "><bold>ꜱᴇᴛᴛɪɴɢꜱ</bold></" + Palette.GOLD + ">",
            List.of("<#94A3B8>Recruitment, MOTD, description & color.</#94A3B8>",
                "<#94A3B8>Recruitment:</#94A3B8> " + (guild.openJoin()
                    ? "<" + Palette.SUCCESS + ">OPEN</" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">INVITE ONLY</" + Palette.DANGER + ">"),
                "",
                officer
                    ? "<#FDE047>▸ Click to edit</#FDE047>"
                    : "<#94A3B8>▸ Leaders and officers only</#94A3B8>")));

        inv.setItem(30, GuiMenuManager.customIcon("ref_trophy",
            "<" + Palette.GOLD_SOFT + "><bold>ɢᴜɪʟᴅ ᴛʀᴏᴘʜɪᴇꜱ</bold></" + Palette.GOLD_SOFT + ">",
            List.of("<#94A3B8>Trophies earned with the guild:</#94A3B8>",
                "<" + Palette.GOLD_SOFT + ">" + guild.trophies() + "</" + Palette.GOLD_SOFT + ">",
                "",
                "<#94A3B8>Guild ELO:</#94A3B8> <#F8FAFC>" + guild.elo() + "</#F8FAFC>")));

        holder.slotStringMap().put(32, "GUILD_LADDER");
        inv.setItem(32, GuiMenuManager.customIcon("ref_cup_gold",
            "<" + Palette.ICE + "><bold>ɢᴜɪʟᴅ ʟᴀᴅᴅᴇʀ</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>See how your guild ranks against</#94A3B8>",
                "<#94A3B8>every other guild on the server.</#94A3B8>",
                "",
                "<#94A3B8>Position:</#94A3B8> <#F8FAFC>#" + service.placementOf(guild) + "</#F8FAFC>",
                "",
                "<" + Palette.ICE + ">▸ Click to open</" + Palette.ICE + ">")));

        holder.slotStringMap().put(45, "GUILD_LEAVE");
        inv.setItem(45, GuiMenuManager.customIcon("ref_arrow_left",
            "<" + Palette.DANGER + "><bold>ʟᴇᴀᴠᴇ ᴛʜᴇ ɢᴜɪʟᴅ</bold></" + Palette.DANGER + ">",
            List.of(leader && guild.members().size() > 1
                    ? "<#94A3B8>Leadership passes to the next member.</#94A3B8>"
                    : "<#94A3B8>You can be invited again later.</#94A3B8>",
                "",
                "<#F87171>▸ Shift-click to confirm</#F87171>")));

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
            "<" + Palette.ICE + "><bold>ʀᴏꜱᴛᴇʀ</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>" + guild.displayNameAndTag() + "</#94A3B8>",
                "<#94A3B8>Members:</#94A3B8> <#F8FAFC>" + guild.members().size()
                    + "/" + service.maxMembers() + "</#F8FAFC>",
                "<#94A3B8>Officers:</#94A3B8> <#F8FAFC>" + guild.officerCount()
                    + "/" + Guild.MAX_OFFICERS + "</#F8FAFC>",
                "<#94A3B8>Guild ELO:</#94A3B8> <" + Palette.GOLD + ">" + guild.elo() + "</" + Palette.GOLD + ">")));

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
            lore.add("<#94A3B8>Rank:</#94A3B8> " + member.role().coloredTitle());
            lore.add("<#94A3B8>Status:</#94A3B8> " + (online
                ? "<" + Palette.SUCCESS + ">Online</" + Palette.SUCCESS + ">" : "<" + Palette.DIM + ">Offline</" + Palette.DIM + ">"));
            lore.add("<#94A3B8>Guild ELO earned:</#94A3B8> <#F8FAFC>"
                + (member.contributedElo() >= 0 ? "+" : "") + member.contributedElo() + "</#F8FAFC>");
            lore.add("<#94A3B8>Matches with the guild:</#94A3B8> <#F8FAFC>"
                + member.matchesPlayed() + "</#F8FAFC>");
            lore.add("<#94A3B8>Joined:</#94A3B8> <#F8FAFC>"
                + java.time.Instant.ofEpochMilli(member.joinedAtMillis())
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
                + "</#F8FAFC>");

            boolean canAct = officer && !member.uuid().equals(viewer)
                && member.role().weight() < (viewerRole == null ? 0 : viewerRole.weight());
            boolean isSelf = member.uuid().equals(viewer);
            if (canAct) {
                lore.add("");
                if (leader && member.role() == Guild.Role.MEMBER) {
                    lore.add("<" + Palette.SUCCESS + ">▸ Left-click: promote to officer</" + Palette.SUCCESS + ">");
                    lore.add("<" + Palette.GOLD + ">▸ Shift-left-click: transfer leadership</" + Palette.GOLD + ">");
                } else if (leader && member.role() == Guild.Role.OFFICER) {
                    lore.add("<" + Palette.GOLD + ">▸ Left-click: demote to member</" + Palette.GOLD + ">");
                    lore.add("<" + Palette.GOLD + ">▸ Shift-left-click: transfer leadership</" + Palette.GOLD + ">");
                }
                lore.add("<" + Palette.DANGER + ">▸ Shift-right-click: remove from guild</" + Palette.DANGER + ">");
                holder.slotStringMap().put(slots[i], "GUILD_MEMBER:" + member.name());
            } else if (isSelf) {
                lore.add("");
                lore.add("<#94A3B8>▸ This is you</#94A3B8>");
            }
            inv.setItem(slots[i], plugin.guiMenu().headIcon(member.uuid(), member.name(),
                "<#F8FAFC><bold>" + member.name() + "</bold></#F8FAFC>", lore));
        }

        holder.slotStringMap().put(49, "GUILD_INVITES");
        inv.setItem(49, GuiMenuManager.customIcon("icon_party_invites",
            "<" + Palette.SUCCESS + "><bold>ɪɴᴠɪᴛᴇ ᴀ ᴘʟᴀʏᴇʀ</bold></" + Palette.SUCCESS + ">",
            officer
                ? List.of("<#94A3B8>Send a guild invitation.</#94A3B8>",
                    "<" + Palette.SUCCESS + ">▸ Click, then type the player name</" + Palette.SUCCESS + ">")
                : List.of("<#94A3B8>Only leaders and officers can invite.</#94A3B8>")));

        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<" + Palette.DANGER + "><bold>‹ ʙᴀᴄᴋ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Return to the guild hub.</#94A3B8>")));
    }

    private void inviteScreen(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                              GuildService service, Guild guild) {
        UUID id = player.getUniqueId();
        List<GuildService.InviteView> invites = service.invitesFor(id);
        inv.setItem(4, GuiMenuManager.customIcon("icon_party_invites",
            "<" + Palette.SUCCESS + "><bold>ɪɴᴠɪᴛᴀᴛɪᴏɴꜱ</bold></" + Palette.SUCCESS + ">",
            invites.isEmpty()
                ? List.of("<#94A3B8>No pending invitations.</#94A3B8>",
                    "<#94A3B8>Invitations expire after 5 minutes.</#94A3B8>")
                : inviteLore(invites, "Click one to accept")));

        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < invites.size() && i < slots.length; i++) {
            GuildService.InviteView view = invites.get(i);
            Guild target = view.guild();
            holder.slotStringMap().put(slots[i], "GUILD_ACCEPT:" + target.name());
            inv.setItem(slots[i], GuiMenuManager.customIcon("icon_guild",
                target.displayNameAndTag(),
                List.of(
                    "<#94A3B8>Invited by:</#94A3B8> <#F8FAFC>" + view.inviterName() + "</#F8FAFC>",
                    "<#94A3B8>Members:</#94A3B8> <#F8FAFC>" + target.members().size()
                        + "/" + service.maxMembers() + "</#F8FAFC>",
                    "<#94A3B8>Guild ELO:</#94A3B8> <" + Palette.GOLD + ">" + target.elo() + "</" + Palette.GOLD + ">",
                    "<#94A3B8>Expires in:</#94A3B8> <#F8FAFC>"
                        + GuildService.formatRemaining(view.remainingMillis()) + "</#F8FAFC>",
                    "",
                    "<" + Palette.SUCCESS + ">▸ Left-click to join</" + Palette.SUCCESS + ">",
                    "<" + Palette.DANGER + ">▸ Right-click to decline</" + Palette.DANGER + ">")));
        }
        if (invites.isEmpty()) {
            inv.setItem(22, GuiMenuManager.customIcon("ref_lock",
                "<" + Palette.DIM + "><bold>ɴᴏ ɪɴᴠɪᴛᴀᴛɪᴏɴ</bold></" + Palette.DIM + ">",
                List.of("<#94A3B8>Ask a guild leader or officer to invite you.</#94A3B8>",
                    "<#94A3B8>You can also join an open guild from the hub.</#94A3B8>")));
        }

        if (guild != null && guild.canManageRoster(id)) {
            holder.slotStringMap().put(40, "GUILD_INVITE");
            inv.setItem(40, GuiMenuManager.customIcon("icon_new_guild",
                "<" + Palette.SUCCESS + "><bold>ɪɴᴠɪᴛᴇ ᴀ ᴘʟᴀʏᴇʀ</bold></" + Palette.SUCCESS + ">",
                List.of("<#94A3B8>Invite someone to " + guild.displayName() + ".</#94A3B8>",
                    "<#94A3B8>They have 5 minutes to accept.</#94A3B8>",
                    "",
                    "<" + Palette.SUCCESS + ">▸ Click, then type the name</" + Palette.SUCCESS + ">")));
        }

        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<" + Palette.DANGER + "><bold>‹ ʙᴀᴄᴋ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Return to the guild hub.</#94A3B8>")));
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
            "<" + Palette.GOLD + "><bold>ɢᴜɪʟᴅ ꜱᴇᴛᴛɪɴɢꜱ</bold></" + Palette.GOLD + ">",
            List.of("<#94A3B8>" + guild.displayNameAndTag() + "</#94A3B8>",
                "<#94A3B8>Your permission:</#94A3B8> "
                    + (role == null ? "<#F8FAFC>—</#F8FAFC>" : role.coloredTitle()),
                officer ? "" : "<#94A3B8>Only leaders and officers may edit.</#94A3B8>")));

        holder.slotStringMap().put(20, "GUILD_OPEN_TOGGLE");
        inv.setItem(20, GuiMenuManager.customIcon(
            guild.openJoin() ? "ref_check" : "ref_lock",
            "<" + Palette.ICE + "><bold>ʀᴇᴄʀᴜɪᴛᴍᴇɴᴛ</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Current:</#94A3B8> " + (guild.openJoin()
                    ? "<" + Palette.SUCCESS + ">OPEN — anyone may join</" + Palette.SUCCESS + ">"
                    : "<" + Palette.DANGER + ">INVITE ONLY</" + Palette.DANGER + ">"),
                "",
                "<#FDE047>▸ Click to toggle</#FDE047>")));

        holder.slotStringMap().put(22, "GUILD_MOTD");
        inv.setItem(22, GuiMenuManager.customIcon("ref_medal_gold",
            "<" + Palette.SUCCESS + "><bold>ᴍᴏᴛᴅ</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Shown to every member on login.</#94A3B8>",
                "<#94A3B8>Current:</#94A3B8> <#F8FAFC>"
                    + (guild.motd().isBlank() ? "—" : guild.motd()) + "</#F8FAFC>",
                "",
                "<#FDE047>▸ Click, then type the message</#FDE047>")));

        holder.slotStringMap().put(24, "GUILD_DESCRIPTION");
        inv.setItem(24, GuiMenuManager.customIcon("ref_clock",
            "<" + Palette.VIOLET + "><bold>ᴅᴇꜱᴄʀɪᴘᴛɪᴏɴ</bold></" + Palette.VIOLET + ">",
            List.of("<#94A3B8>What your guild is about.</#94A3B8>",
                "<#94A3B8>Current:</#94A3B8> <#F8FAFC>"
                    + (guild.description().isBlank() ? "—" : guild.description()) + "</#F8FAFC>",
                "",
                "<#FDE047>▸ Click, then type the text</#FDE047>")));

        holder.slotStringMap().put(30, "GUILD_COLOR_PREV");
        inv.setItem(30, GuiMenuManager.customIcon("icon_prev",
            "<" + Palette.MUTED + "><bold>‹ ᴄᴏʟᴏᴜʀ</bold></" + Palette.MUTED + ">",
            List.of("<#94A3B8>Previous guild color.</#94A3B8>",
                "<#94A3B8>Current:</#94A3B8> <" + guild.hexColor() + ">"
                    + guild.hexColor().substring(1) + "</" + guild.hexColor() + ">")));

        holder.slotStringMap().put(31, "GUILD_COLOR_NEXT");
        inv.setItem(31, GuiMenuManager.customIcon("icon_guild",
            "<" + guild.hexColor() + "><bold>ɢᴜɪʟᴅ ᴄᴏʟᴏᴜʀ</bold></" + guild.hexColor() + ">",
            List.of("<#94A3B8>Tag preview:</#94A3B8> " + guild.displayTag(),
                "<#94A3B8>Name preview:</#94A3B8> " + guild.displayName(),
                "",
                leader ? "<#FDE047>▸ Use the arrows to change it</#FDE047>"
                    : "<#94A3B8>▸ Only the leader can change it</#94A3B8>")));

        holder.slotStringMap().put(32, "GUILD_COLOR_NEXT");
        inv.setItem(32, GuiMenuManager.customIcon("icon_next",
            "<" + Palette.MUTED + "><bold>ᴄᴏʟᴏᴜʀ ›</bold></" + Palette.MUTED + ">",
            List.of("<#94A3B8>Next guild color.</#94A3B8>",
                "<#94A3B8>Available:</#94A3B8> <#F8FAFC>" + Guild.PALETTE.size() + " colors</#F8FAFC>")));

        holder.slotStringMap().put(40, "GUILD_RENAME");
        inv.setItem(40, GuiMenuManager.customIcon("ref_bag",
            "<" + Palette.DANGER + "><bold>ʀᴇɴᴀᴍᴇ ᴛᴀɢ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Current tag:</#94A3B8> " + guild.displayTag(),
                leader ? "<#94A3B8>▸ Click, then type the new tag</#94A3B8>"
                    : "<#94A3B8>▸ Only the leader can rename the tag</#94A3B8>")));

        if (leader) {
            holder.slotStringMap().put(49, "GUILD_DISBAND");
            inv.setItem(49, GuiMenuManager.customIcon("icon_delete",
                "<" + Palette.DANGER + "><bold>ᴅɪꜱʙᴀɴᴅ ᴛʜᴇ ɢᴜɪʟᴅ</bold></" + Palette.DANGER + ">",
                List.of("<#94A3B8>Deletes the guild for everyone.</#94A3B8>",
                    "<#94A3B8>This cannot be undone.</#94A3B8>",
                    "",
                    "<#F87171>▸ Shift-click twice to confirm</#F87171>")));
        }

        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<" + Palette.DANGER + "><bold>‹ ʙᴀᴄᴋ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Return to the guild hub.</#94A3B8>")));
    }

    private void ladder(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                        GuildService service, int page) {
        List<Guild> top = service.topByElo(Integer.MAX_VALUE);
        int perPage = 21;
        int pages = Math.max(1, (top.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(pages - 1, page));
        Guild mine = service.guildOf(player.getUniqueId());

        inv.setItem(4, GuiMenuManager.customIcon("ref_cup_gold",
            "<" + Palette.GOLD_SOFT + "><bold>ɢᴜɪʟᴅ ʟᴀᴅᴅᴇʀ</bold></" + Palette.GOLD_SOFT + ">",
            List.of("<#94A3B8>Top guilds by guild ELO.</#94A3B8>",
                "<#94A3B8>Guilds:</#94A3B8> <#F8FAFC>" + top.size() + "</#F8FAFC>",
                mine == null
                    ? "<#94A3B8>Your guild:</#94A3B8> <#F8FAFC>None</#F8FAFC>"
                    : "<#94A3B8>Your guild:</#94A3B8> " + mine.displayNameAndTag()
                        + " <#94A3B8>#" + service.placementOf(mine) + "</#94A3B8>")));

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
            lore.add("<#94A3B8>ELO:</#94A3B8> <" + Palette.GOLD + ">" + guild.elo() + "</" + Palette.GOLD + ">");
            lore.add("<#94A3B8>Members:</#94A3B8> <#F8FAFC>" + guild.members().size()
                + "/" + service.maxMembers() + "</#F8FAFC>");
            lore.add("<#94A3B8>Record:</#94A3B8> <" + Palette.SUCCESS + ">" + guild.matchesWon() + "W</" + Palette.SUCCESS + ">"
                + "<#94A3B8>/</#94A3B8><" + Palette.DANGER + ">" + guild.losses() + "L</" + Palette.DANGER + ">");
            lore.add("<#94A3B8>Leader:</#94A3B8> <#F8FAFC>" + guild.leaderName() + "</#F8FAFC>");
            if (mine != null && mine.id().equals(guild.id())) {
                lore.add("");
                lore.add("<" + Palette.SUCCESS + ">▸ This is your guild</" + Palette.SUCCESS + ">");
            }
            inv.setItem(slots[i], GuiMenuManager.customIcon(model,
                "<" + guild.hexColor() + "><bold>#" + place + "</bold></" + guild.hexColor() + "> "
                    + guild.displayNameAndTag(), lore));
        }
        if (top.isEmpty()) {
            inv.setItem(22, GuiMenuManager.customIcon("ref_lock",
                "<" + Palette.DIM + "><bold>ɴᴏ ɢᴜɪʟᴅꜱ ʏᴇᴛ</bold></" + Palette.DIM + ">",
                List.of("<#94A3B8>No guild has been founded yet.</#94A3B8>",
                    "<#94A3B8>Be the first — the hub has a Create button.</#94A3B8>")));
        }

        if (current > 0) {
            holder.slotStringMap().put(48, "GUILD_LADDER_PAGE:" + (current - 1));
            inv.setItem(48, GuiMenuManager.customIcon("icon_prev",
                "<" + Palette.MUTED + "><bold>‹ ᴘʀᴇᴠ</bold></" + Palette.MUTED + ">",
                List.of("<#94A3B8>Page " + current + " of " + pages + "</#94A3B8>")));
        }
        if (current + 1 < pages) {
            holder.slotStringMap().put(50, "GUILD_LADDER_PAGE:" + (current + 1));
            inv.setItem(50, GuiMenuManager.customIcon("icon_next",
                "<" + Palette.MUTED + "><bold>ɴᴇxᴛ ›</bold></" + Palette.MUTED + ">",
                List.of("<#94A3B8>Page " + (current + 2) + " of " + pages + "</#94A3B8>")));
        }

        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<" + Palette.DANGER + "><bold>‹ ʙᴀᴄᴋ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Return to the guild hub.</#94A3B8>")));
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
            Msg.send(actor, Msg.warn("Shift-right-click <#F8FAFC>" + memberName
                + "</#F8FAFC> to remove them from the guild."));
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
            "<" + Palette.VIOLET + "><bold>ᴏᴘᴇɴ ɢᴜɪʟᴅꜱ</bold></" + Palette.VIOLET + ">",
            List.of("<#94A3B8>Guilds recruiting right now.</#94A3B8>",
                "<#94A3B8>Found:</#94A3B8> <#F8FAFC>" + open.size() + "</#F8FAFC>",
                "",
                "<#94A3B8>Joining is instant and free.</#94A3B8>")));

        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < open.size() && i < slots.length; i++) {
            Guild guild = open.get(i);
            holder.slotStringMap().put(slots[i], "GUILD_JOIN:" + guild.name());
            inv.setItem(slots[i], GuiMenuManager.customIcon("icon_guild",
                guild.displayNameAndTag(),
                List.of("<#94A3B8>Members:</#94A3B8> <#F8FAFC>" + guild.members().size()
                        + "/" + plugin.guildService().maxMembers() + "</#F8FAFC>",
                    "<#94A3B8>Guild ELO:</#94A3B8> <" + Palette.GOLD + ">" + guild.elo() + "</" + Palette.GOLD + ">",
                    "<#94A3B8>Leader:</#94A3B8> <#F8FAFC>" + guild.leaderName() + "</#F8FAFC>",
                    guild.description().isBlank() ? "" : "<#94A3B8>" + guild.description() + "</#94A3B8>",
                    "",
                    "<" + Palette.SUCCESS + ">▸ Click to join</" + Palette.SUCCESS + ">")));
        }
        if (open.isEmpty()) {
            inv.setItem(22, GuiMenuManager.customIcon("ref_lock",
                "<" + Palette.DIM + "><bold>ɴᴏ ᴏᴘᴇɴ ɢᴜɪʟᴅ</bold></" + Palette.DIM + ">",
                List.of("<#94A3B8>No guild is recruiting right now.</#94A3B8>",
                    "<#94A3B8>Ask a leader for an invitation.</#94A3B8>")));
        }
        holder.slotStringMap().put(BACK_SLOT, "GUILD_HOME");
        inv.setItem(BACK_SLOT, GuiMenuManager.customIcon("ref_arrow_left",
            "<" + Palette.DANGER + "><bold>‹ ʙᴀᴄᴋ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Return to the guild hub.</#94A3B8>")));
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
            lore.add(view.guild().displayTag() + " <#F8FAFC>" + view.guild().name() + "</#F8FAFC>"
                + " <#64748B>· " + view.inviterName() + "</#64748B>");
        }
        lore.add("");
        lore.add("<#FDE047>▸ " + hint + "</#FDE047>");
        return lore;
    }

    private ItemStack backButton(Player player) {
        return GuiMenuManager.customIcon("ref_arrow_left",
            "<" + Palette.DANGER + "><bold>‹ ʙᴀᴄᴋ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Return to the hub.</#94A3B8>"));
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
