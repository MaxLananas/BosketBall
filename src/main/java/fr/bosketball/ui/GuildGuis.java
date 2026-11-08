package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.social.Guild;
import fr.bosketball.social.GuildService;
import fr.bosketball.ui.kit.MenuItems;
import fr.bosketball.ui.kit.MenuSpec;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Guild screens are data; the shared menu renderer owns placement and navigation. */
public final class GuildGuis {
    private enum Screen { HOME, ROSTER, INVITES, SETTINGS, LADDER, BROWSE, CONFIRM_LEAVE, CONFIRM_DISBAND }

    private final BosketBallPlugin plugin;

    public GuildGuis(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        show(player, Screen.HOME, 0);
    }

    public void restore(Player player, String view, int page) {
        Screen screen;
        try {
            screen = Screen.valueOf(view);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            screen = Screen.HOME;
        }
        show(player, screen, page);
    }

    private void show(Player player, Screen screen, int requestedPage) {
        GuildService service = plugin.guildService();
        Guild guild = service.guildOf(player.getUniqueId());
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.GUILDS);
        holder.setView(screen.name());
        String caption = switch (screen) {
            case HOME -> "GUILDS";
            case ROSTER -> "ROSTER";
            case INVITES -> "INVITATIONS";
            case SETTINGS -> "GUILD SETTINGS";
            case LADDER -> "GUILD LADDER";
            case BROWSE -> "OPEN GUILDS";
            case CONFIRM_LEAVE -> "LEAVE GUILD";
            case CONFIRM_DISBAND -> "DISBAND GUILD";
        };
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_GUILD,
            GuiMenuManager.tr(player, "gui.guild." + screen.name().toLowerCase(java.util.Locale.ROOT), caption)));
        MenuSpec.Builder spec = MenuSpec.builder(6, caption);
        switch (screen) {
            case HOME -> home(player, spec, service, guild);
            case ROSTER -> roster(player, holder, spec, service, guild, requestedPage);
            case INVITES -> invites(player, holder, spec, service, guild, requestedPage);
            case SETTINGS -> settings(player, spec, service, guild);
            case LADDER -> ladder(player, holder, spec, service, guild, requestedPage);
            case BROWSE -> browse(player, holder, spec, service, requestedPage);
            case CONFIRM_LEAVE -> confirm(player, spec, guild, false);
            case CONFIRM_DISBAND -> confirm(player, spec, guild, true);
        }
        plugin.guiMenu().show(player, holder, spec);
    }

    private void home(Player player, MenuSpec.Builder spec, GuildService service, Guild guild) {
        UUID id = player.getUniqueId();
        if (guild == null) {
            spec.item(4, MenuItems.info("icon_guild", "Guild hub", MenuItems.lore(
                "You are not in a guild yet.", "Pending invitations: " + service.invitesFor(id).size(),
                "Creation cost: " + Msg.count(service.createCostCoins()) + " coins")));
            spec.item(19, MenuItems.action("icon_party_invites", "Review invitations",
                MenuItems.lore("Invitations: " + service.invitesFor(id).size()), click -> show(player, Screen.INVITES, 0)));
            int balance = plugin.statsManager().getOrCreate(player).coins();
            if (balance < service.createCostCoins()) {
                spec.item(21, MenuItems.unaffordable("Create a guild", service.createCostCoins(), balance, "Play matches to earn coins."));
            } else {
                spec.item(21, MenuItems.action("icon_new_guild", "Create a guild", List.of(
                    MenuItems.row("Cost", Msg.count(service.createCostCoins()) + " coins"),
                    MenuItems.row("Max members", service.maxMembers())), click -> {
                        player.closeInventory();
                        plugin.socialManager().beginGuildNamePrompt(player);
                    }));
            }
            spec.item(23, MenuItems.action("icon_podium", "View guild ladder", MenuItems.lore("Every guild ranked by ELO."),
                click -> show(player, Screen.LADDER, 0)));
            spec.item(31, MenuItems.action("icon_team", "Browse open guilds", MenuItems.lore("Join without an invitation."),
                click -> show(player, Screen.BROWSE, 0)));
            return;
        }
        Guild.Role role = guild.roleOf(id);
        boolean officer = role != null && role.canInvite();
        spec.item(4, MenuItems.info("icon_guild", guild.name(), List.of(
            MenuItems.row("Members", guild.members().size() + "/" + service.maxMembers()),
            MenuItems.row("ELO", guild.elo()), MenuItems.row("Rank", "#" + service.placementOf(guild)),
            MenuItems.row("Record", guild.matchesWon() + "W " + MenuItems.SEPARATOR + " " + guild.losses() + "L"),
            MenuItems.row("MOTD", guild.motd().isBlank() ? "None" : guild.motd()))));
        spec.item(20, MenuItems.action("ref_friends", "View roster", MenuItems.lore("Members: " + guild.members().size()),
            click -> show(player, Screen.ROSTER, 0)));
        spec.item(22, MenuItems.action("icon_party_invites", officer ? "Invite players" : "Review invitations",
            MenuItems.lore("Pending: " + service.invitesFor(id).size()), click -> show(player, Screen.INVITES, 0)));
        if (officer) {
            spec.item(24, MenuItems.action("ref_gear", "Edit guild settings", MenuItems.lore("Recruitment, MOTD and tag."),
                click -> show(player, Screen.SETTINGS, 0)));
        } else {
            spec.item(24, MenuItems.disabled("ref_gear", "Guild settings", "Only leaders and officers can edit settings."));
        }
        spec.item(30, MenuItems.info("ref_trophy", "Guild trophies", MenuItems.lore("Trophies: " + guild.trophies())));
        spec.item(32, MenuItems.action("icon_podium", "View guild ladder", MenuItems.lore("Rank: #" + service.placementOf(guild)),
            click -> show(player, Screen.LADDER, 0)));
        spec.item(40, MenuItems.action("ref_arrow_left", "Leave guild", MenuItems.lore("Review the change before confirming."),
            click -> show(player, Screen.CONFIRM_LEAVE, 0)));
    }

    private void roster(Player player, GuiMenuManager.BosketHolder holder, MenuSpec.Builder spec,
                        GuildService service, Guild guild, int requestedPage) {
        if (guild == null) {
            spec.item(22, MenuItems.error("You are not in a guild.", "Browse open guilds", click -> show(player, Screen.BROWSE, 0)));
            return;
        }
        UUID viewer = player.getUniqueId();
        Guild.Role viewerRole = guild.roleOf(viewer);
        boolean leader = viewerRole == Guild.Role.LEADER;
        boolean officer = viewerRole != null && viewerRole.canKick();
        spec.item(4, MenuItems.info("ref_friends", "Roster", MenuItems.lore("Members: " + guild.members().size())));
        List<Guild.Member> members = guild.sortedMembers();
        MenuSpec.Page<Guild.Member> page = MenuSpec.Page.of(members, requestedPage, MenuSpec.GRID.length);
        holder.setPage(page.index());
        for (int i = 0; i < page.entries().size(); i++) {
            Guild.Member member = page.entries().get(i);
            boolean online = Bukkit.getPlayer(member.uuid()) != null;
            boolean self = member.uuid().equals(viewer);
            boolean canAct = officer && !self && member.role().weight() < (viewerRole == null ? 0 : viewerRole.weight());
            String subtitle = MenuItems.plain(member.role().smallCapsTitle()) + " " + MenuItems.SEPARATOR + " " + (online ? "Online" : "Offline");
            String detail = "Contributed " + member.contributedElo() + " ELO " + MenuItems.SEPARATOR + " " + member.matchesPlayed() + " matches";
            MenuSpec.Item row = canAct
                ? MenuItems.row(member.name(), subtitle, detail, click -> {
                    memberAction(player, service, member.name(), click.shift(), click.right());
                    show(player, Screen.ROSTER, holder.page());
                })
                : MenuItems.info("ui_invisible", member.name() + (self ? " (you)" : ""), MenuItems.lore(subtitle, detail));
            spec.item(MenuSpec.GRID[i], MenuItems.head(plugin.guiMenu().headIcon(member.uuid(), row.name(), row.lore()), row));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("The roster is empty.", "Browse open guilds", click -> show(player, Screen.BROWSE, 0)));
        }
        if (officer) {
            spec.item(40, MenuItems.action("icon_new_guild", "Invite a player", MenuItems.lore("They have 5 minutes to accept."), click -> {
                player.closeInventory();
                plugin.socialManager().beginGuildInvitePrompt(player);
            }));
        }
        spec.pagination(page, click -> show(player, Screen.ROSTER, page.previous()), click -> show(player, Screen.ROSTER, page.next()));
    }

    private void invites(Player player, GuiMenuManager.BosketHolder holder, MenuSpec.Builder spec,
                         GuildService service, Guild guild, int requestedPage) {
        UUID id = player.getUniqueId();
        List<GuildService.InviteView> invites = service.invitesFor(id);
        spec.item(4, MenuItems.info("icon_party_invites", "Invitations", MenuItems.lore("Invitations expire after 5 minutes.")));
        MenuSpec.Page<GuildService.InviteView> page = MenuSpec.Page.of(invites, requestedPage, MenuSpec.GRID.length);
        holder.setPage(page.index());
        for (int i = 0; i < page.entries().size(); i++) {
            GuildService.InviteView view = page.entries().get(i);
            Guild target = view.guild();
            spec.item(MenuSpec.GRID[i], MenuItems.row("icon_party_invites", target.name(), "Invited by " + view.inviterName(),
                "Expires in " + GuildService.formatRemaining(view.remainingMillis()), click -> {
                    report(player, click.right() ? service.declineInvite(player, target.name()) : service.acceptInvite(player, target.name()));
                    show(player, Screen.INVITES, holder.page());
                }));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No invitations right now.", "Browse open guilds", click -> show(player, Screen.BROWSE, 0)));
        }
        if (guild != null && guild.canManageRoster(id)) {
            spec.item(40, MenuItems.action("icon_new_guild", "Invite a player", MenuItems.lore("Type the player name in chat."), click -> {
                player.closeInventory();
                plugin.socialManager().beginGuildInvitePrompt(player);
            }));
        }
        spec.pagination(page, click -> show(player, Screen.INVITES, page.previous()), click -> show(player, Screen.INVITES, page.next()));
    }

    private void settings(Player player, MenuSpec.Builder spec, GuildService service, Guild guild) {
        if (guild == null) {
            spec.item(22, MenuItems.error("You are not in a guild.", "Browse open guilds", click -> show(player, Screen.BROWSE, 0)));
            return;
        }
        Guild.Role role = guild.roleOf(player.getUniqueId());
        boolean leader = role == Guild.Role.LEADER;
        boolean officer = role != null && role.canEditSettings();
        spec.item(4, MenuItems.info("ref_gear", "Guild settings", MenuItems.lore("Your role: " + (role == null ? "None" : MenuItems.plain(role.smallCapsTitle())))));
        if (!officer) {
            spec.item(22, MenuItems.disabled("ref_gear", "Guild settings", "Only leaders and officers can edit settings."));
            spec.item(40, MenuItems.action("ref_friends", "View roster",
                MenuItems.lore("See who is in the guild."), click -> show(player, Screen.ROSTER, 0)));
            return;
        }
        spec.item(20, MenuItems.action(guild.openJoin() ? "ref_check" : "ref_lock",
            guild.openJoin() ? "Close recruitment" : "Open recruitment", MenuItems.lore(guild.openJoin() ? "Anyone may join." : "Invitation only."), click -> {
                report(player, service.setOpenJoin(player, !guild.openJoin()));
                show(player, Screen.SETTINGS, 0);
            }));
        spec.item(22, MenuItems.action("ref_medal_gold", "Edit MOTD", MenuItems.lore(guild.motd().isBlank() ? "No MOTD set." : guild.motd()), click -> {
            player.closeInventory();
            plugin.socialManager().beginGuildMotdPrompt(player);
        }));
        spec.item(24, MenuItems.action("ref_clock", "Edit description", MenuItems.lore(guild.description().isBlank() ? "No description set." : guild.description()), click -> {
            player.closeInventory();
            plugin.socialManager().beginGuildDescriptionPrompt(player);
        }));
        if (leader) {
            spec.item(30, MenuItems.action("icon_prev", "Choose previous color", MenuItems.lore("Current: " + guild.hexColor()), click -> {
                report(player, service.cycleColor(player, -1));
                show(player, Screen.SETTINGS, 0);
            }));
            spec.item(32, MenuItems.action("icon_next", "Choose next color", MenuItems.lore("Colors: " + Guild.PALETTE.size()), click -> {
                report(player, service.cycleColor(player, 1));
                show(player, Screen.SETTINGS, 0);
            }));
            spec.item(40, MenuItems.action("ref_bag", "Rename tag", MenuItems.lore("Current tag: " + guild.displayTag()), click -> {
                player.closeInventory();
                plugin.socialManager().beginGuildTagPrompt(player);
            }));
            spec.item(47, MenuItems.action("icon_delete", "Disband guild", MenuItems.lore("Deletes the guild for everyone."), click -> show(player, Screen.CONFIRM_DISBAND, 0)));
        } else {
            spec.item(31, MenuItems.disabled("icon_guild", "Guild color", "Only the leader can change the color."));
            spec.item(40, MenuItems.disabled("ref_bag", "Rename tag", "Only the leader can rename the tag."));
        }
    }

    private void ladder(Player player, GuiMenuManager.BosketHolder holder, MenuSpec.Builder spec,
                        GuildService service, Guild mine, int requestedPage) {
        List<Guild> top = service.topByElo(Integer.MAX_VALUE);
        MenuSpec.Page<Guild> page = MenuSpec.Page.of(top, requestedPage, MenuSpec.GRID.length);
        holder.setPage(page.index());
        spec.item(4, MenuItems.info("icon_podium", "Guild ladder", MenuItems.lore("Guilds: " + top.size(),
            mine == null ? "Your guild: None" : "Your rank: #" + service.placementOf(mine))));
        for (int i = 0; i < page.entries().size(); i++) {
            Guild guild = page.entries().get(i);
            int place = page.start() + i + 1;
            boolean own = mine != null && mine.id().equals(guild.id());
            MenuSpec.Item row = MenuItems.row("icon_guild", "#" + place + " " + guild.name(), guild.elo() + " ELO",
                guild.members().size() + " members " + MenuItems.SEPARATOR + " Leader " + guild.leaderName(),
                click -> Msg.send(player, Msg.info(own ? "This is your guild." : guild.name() + " is ranked #" + place + ".")));
            spec.item(MenuSpec.GRID[i], own ? row.selected() : row);
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No guilds have been founded yet.", "Create a guild", click -> {
                player.closeInventory();
                plugin.socialManager().beginGuildNamePrompt(player);
            }));
        }
        spec.pagination(page, click -> show(player, Screen.LADDER, page.previous()), click -> show(player, Screen.LADDER, page.next()));
    }

    private void browse(Player player, GuiMenuManager.BosketHolder holder, MenuSpec.Builder spec,
                        GuildService service, int requestedPage) {
        List<Guild> open = service.joinable();
        MenuSpec.Page<Guild> page = MenuSpec.Page.of(open, requestedPage, MenuSpec.GRID.length);
        holder.setPage(page.index());
        spec.item(4, MenuItems.info("icon_team", "Open guilds", MenuItems.lore("Joining is instant and free.")));
        for (int i = 0; i < page.entries().size(); i++) {
            Guild guild = page.entries().get(i);
            spec.item(MenuSpec.GRID[i], MenuItems.row("icon_guild", guild.name(), guild.elo() + " ELO",
                guild.members().size() + "/" + service.maxMembers() + " members", click -> {
                    report(player, service.joinOpen(player, guild));
                    show(player, Screen.HOME, 0);
                }));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No guild is recruiting right now.", "Create a guild", click -> {
                player.closeInventory();
                plugin.socialManager().beginGuildNamePrompt(player);
            }));
        }
        spec.pagination(page, click -> show(player, Screen.BROWSE, page.previous()), click -> show(player, Screen.BROWSE, page.next()));
    }

    private void confirm(Player player, MenuSpec.Builder spec, Guild guild, boolean disband) {
        if (guild == null) {
            spec.item(22, MenuItems.error("You are not in a guild.", "Browse open guilds", click -> show(player, Screen.BROWSE, 0)));
            return;
        }
        spec.item(22, MenuItems.info(disband ? "icon_delete" : "ref_arrow_left", disband ? "Disband guild?" : "Leave guild?",
            MenuItems.lore(disband ? "This deletes the guild for everyone and cannot be undone." : "You can be invited again later.")));
        spec.item(29, MenuItems.action(disband ? "icon_delete" : "ref_check", disband ? "Confirm disband" : "Confirm leave",
            MenuItems.lore("This changes the guild immediately."), click -> {
                report(player, disband ? plugin.guildService().disband(player) : plugin.guildService().leave(player));
                plugin.guiMenu().openMainHub(player);
            }));
        spec.item(33, MenuItems.action("ref_cross", "Cancel", MenuItems.lore("Keep the guild as it is."), click -> plugin.guiMenu().navigateBack(player)));
    }

    private void memberAction(Player actor, GuildService service, String memberName, boolean shift, boolean right) {
        Guild guild = service.guildOf(actor.getUniqueId());
        if (guild == null) {
            return;
        }
        if (right && shift) {
            report(actor, service.kick(actor, memberName));
            return;
        }
        if (right) {
            Msg.send(actor, Msg.warn("Shift-right-click " + memberName + " to remove them from the guild."));
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
        report(actor, target.role() == Guild.Role.OFFICER ? service.demote(actor, memberName) : service.promote(actor, memberName));
    }

    private static void report(Player player, GuildService.ActionResult result) {
        Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
    }
}
