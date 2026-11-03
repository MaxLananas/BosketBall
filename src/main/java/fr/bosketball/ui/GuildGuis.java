package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.social.Guild;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Full Guild hub GUI — creation, invitations, joining, roster and settings.
 *
 * <p>Everything the {@code /guild} command can do is reachable by clicking, so players
 * never have to memorise a command. Opened from the hub (slot 43, middle-click) or from
 * the 3D locker room.</p>
 */
public final class GuildGuis {

    private final BosketBallPlugin plugin;

    public GuildGuis(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  MAIN SCREEN
    // =========================================================================

    public void open(Player player) {
        var service = plugin.guildService();
        Guild guild = service.guildOf(player.getUniqueId());
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.GUILDS);
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(
            Glyphs.GUI_OVERLAY_HUB, GuiMenuManager.tr(player, "gui.title.guilds", "GUILDS")));
        holder.setInventory(inv);
        fillBackdrop(inv);

        if (guild == null) {
            openNoGuild(player, inv, holder, service);
        } else {
            openMemberView(player, inv, holder, service, guild);
        }
        player.openInventory(inv);
    }

    private void openNoGuild(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                             fr.bosketball.social.GuildService service) {
        Guild invite = service.pendingInviteGuild(player.getUniqueId());
        inv.setItem(4, GuiMenuManager.customIcon("icon_guild",
            "<#FBBF24><bold>ɢᴜɪʟᴅꜱ</bold></#FBBF24>",
            List.of(
                "<gray>You are not in a guild yet.</gray>",
                "<gray>Create one, or join an open guild below.</gray>",
                "",
                "<gray>Create cost:</gray> <gold>"
                    + plugin.settings().guilds().createCostCoins() + " Coins</gold>",
                "<gray>Max members:</gray> <white>"
                    + plugin.settings().guilds().maxMembers() + "</white>")));

        if (invite != null) {
            holder.slotStringMap().put(20, "GUILD_ACCEPT");
            inv.setItem(20, GuiMenuManager.customIcon("icon_guild",
                "<#4ADE80><bold>ᴊᴏɪɴ ɪɴᴠɪᴛᴀᴛɪᴏɴ</bold></#4ADE80>",
                List.of(
                    "<gray>Invitation from:</gray> " + invite.displayName() + " " + invite.displayTag(),
                    "<gray>Members:</gray> <white>" + invite.members().size()
                        + "/" + plugin.settings().guilds().maxMembers() + "</white>",
                    "",
                    "<#4ADE80>▸ Click to accept and join</#4ADE80>")));
        }

        holder.slotStringMap().put(22, "GUILD_CREATE");
        inv.setItem(22, GuiMenuManager.customIcon("icon_new_guild",
            "<#38BDF8><bold>ᴄʀᴇᴀᴛᴇ ᴀ ɢᴜɪʟᴅ</bold></#38BDF8>",
            List.of(
                "<gray>Build your own crew and climb the</gray>",
                "<gray>guild leaderboard together.</gray>",
                "",
                "<gray>Cost:</gray> <gold>" + plugin.settings().guilds().createCostCoins()
                    + " Coins</gold>",
                "",
                "<#38BDF8>▸ Click, then type the guild name in chat</#38BDF8>")));

        // Open guilds browser
        List<Guild> guilds = service.allGuilds();
        int[] slots = {28, 29, 30, 31, 32, 33, 34};
        int placed = 0;
        for (Guild g : guilds) {
            if (placed >= slots.length) {
                break;
            }
            if (!g.openJoin()) {
                continue;
            }
            holder.slotStringMap().put(slots[placed], "GUILD_JOIN:" + g.name());
            inv.setItem(slots[placed], GuiMenuManager.customIcon("icon_guild",
                g.displayName() + " " + g.displayTag(),
                List.of(
                    "<gray>Members:</gray> <white>" + g.members().size()
                        + "/" + plugin.settings().guilds().maxMembers() + "</white>",
                    "<gray>Guild ELO:</gray> <white>" + g.elo() + "</white>",
                    "<gray>Leader:</gray> <white>" + leaderName(g) + "</white>",
                    "",
                    "<#4ADE80>▸ Click to join this open guild</#4ADE80>")));
            placed++;
        }
        if (placed == 0) {
            inv.setItem(31, GuiMenuManager.customIcon("icon_lock",
                "<#64748B><bold>ɴᴏ ᴏᴘᴇɴ ɢᴜɪʟᴅ</bold></#64748B>",
                List.of("<gray>No guild currently accepts open applications.</gray>",
                    "<gray>Ask a leader for an invitation!</gray>")));
        }
        inv.setItem(53, GuiMenuManager.customIcon("icon_back",
            "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the hub.</gray>")));
        holder.slotStringMap().put(53, "BACK");
    }

    private void openMemberView(Player player, Inventory inv, GuiMenuManager.BosketHolder holder,
                                fr.bosketball.social.GuildService service, Guild guild) {
        boolean leader = guild.roleOf(player.getUniqueId()) == Guild.Role.LEADER;
        boolean officer = leader || guild.roleOf(player.getUniqueId()) == Guild.Role.OFFICER;
        inv.setItem(4, GuiMenuManager.customIcon("icon_guild",
            guild.displayName() + " " + guild.displayTag(),
            List.of(
                "<gray>Members:</gray> <white>" + guild.members().size()
                    + "/" + plugin.settings().guilds().maxMembers() + "</white>",
                "<gray>Guild ELO:</gray> <white>" + guild.elo() + "</white>"
                    + " <dark_gray>•</dark_gray> <gray>Rank:</gray> <white>#"
                    + service.placementOf(guild) + "</white>",
                "<gray>Record:</gray> <#4ADE80>" + guild.matchesWon() + "W</#4ADE80>"
                    + "<gray>/</gray><#F87171>" + (guild.matchesPlayed() - guild.matchesWon())
                    + "L</#F87171>",
                "<gray>MOTD:</gray> <white>" + (guild.motd().isBlank() ? "—" : guild.motd()) + "</white>",
                "",
                "<gray>Open applications:</gray> "
                    + (guild.openJoin() ? "<#4ADE80>ON</#4ADE80>" : "<#F87171>OFF</#F87171>"))));

        // Roster
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < guild.members().size() && i < slots.length; i++) {
            Guild.Member member = guild.members().get(i);
            UUID id = member.uuid();
            boolean online = Bukkit.getPlayer(id) != null;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Role:</gray> " + member.role().coloredTitle());
            lore.add("<gray>Status:</gray> " + (online
                ? "<#4ADE80>Online</#4ADE80>" : "<#64748B>Offline</#64748B>"));
            lore.add("<gray>Contributed ELO:</gray> <white>" + member.contributedElo() + "</white>");
            if (officer && member.role() != Guild.Role.LEADER) {
                lore.add("");
                lore.add("<red>▸ Click to kick this member</red>");
                holder.slotStringMap().put(slots[i], "GUILD_KICK:" + member.name());
            }
            inv.setItem(slots[i], plugin.guiMenu().headIcon(id, member.name(),
                "<white><bold>" + member.name() + "</bold></white>", lore));
        }

        if (officer) {
            holder.slotStringMap().put(29, "GUILD_INVITE");
            inv.setItem(29, GuiMenuManager.customIcon("icon_new_guild",
                "<#4ADE80><bold>ɪɴᴠɪᴛᴇ ᴀ ᴘʟᴀʏᴇʀ</bold></#4ADE80>",
                List.of("<gray>Send a guild invitation.</gray>",
                    "<yellow>▸ Click, then type the player name</yellow>")));
        }
        if (leader) {
            holder.slotStringMap().put(33, "GUILD_OPEN_TOGGLE");
            inv.setItem(33, GuiMenuManager.customIcon("icon_lock",
                "<#FBBF24><bold>ᴏᴘᴇɴ ᴀᴘᴘʟɪᴄᴀᴛɪᴏɴꜱ</bold></#FBBF24>",
                List.of("<gray>Current:</gray> " + (guild.openJoin()
                        ? "<#4ADE80>OPEN</#4ADE80>" : "<#F87171>INVITE ONLY</#F87171>"),
                    "",
                    "<yellow>▸ Click to toggle</yellow>")));
            holder.slotStringMap().put(31, "GUILD_MOTD");
            inv.setItem(31, GuiMenuManager.customIcon("icon_book",
                "<#38BDF8><bold>ꜱᴇᴛ ᴛʜᴇ ᴍᴏᴛᴅ</bold></#38BDF8>",
                List.of("<gray>Current:</gray> <white>"
                        + (guild.motd().isBlank() ? "—" : guild.motd()) + "</white>",
                    "", "<yellow>▸ Click, then type the message</yellow>")));
            holder.slotStringMap().put(49, "GUILD_DISBAND");
            inv.setItem(49, GuiMenuManager.customIcon("icon_delete",
                "<#F87171><bold>ᴅɪꜱʙᴀɴᴅ ᴛʜᴇ ɢᴜɪʟᴅ</bold></#F87171>",
                List.of("<gray>Deletes the guild for everyone.</gray>",
                    "", "<red>▸ Shift-click to confirm</red>")));
        }
        holder.slotStringMap().put(45, "GUILD_LEAVE");
        inv.setItem(45, GuiMenuManager.customIcon("icon_back",
            "<#F87171><bold>ʟᴇᴀᴠᴇ ᴛʜᴇ ɢᴜɪʟᴅ</bold></#F87171>",
            List.of("<gray>You can be re-invited later.</gray>")));
        holder.slotStringMap().put(53, "BACK");
        inv.setItem(53, GuiMenuManager.customIcon("icon_back",
            "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the hub.</gray>")));
    }

    // =========================================================================
    //  CLICK ROUTER
    // =========================================================================

    public void handle(Player player, String key, org.bukkit.event.inventory.InventoryClickEvent event) {
        var service = plugin.guildService();
        if (key == null) {
            return;
        }
        if (key.equals("BACK")) {
            open(player);
            return;
        }
        if (key.equals("GUILD_ACCEPT")) {
            var result = service.acceptInvite(player, null);
            Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            open(player);
            return;
        }
        if (key.equals("GUILD_CREATE")) {
            player.closeInventory();
            plugin.socialManager().beginGuildNamePrompt(player);
            return;
        }
        if (key.startsWith("GUILD_JOIN:")) {
            var result = service.acceptInvite(player, key.substring(11));
            Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            open(player);
            return;
        }
        if (key.startsWith("GUILD_KICK:")) {
            var result = service.kick(player, key.substring(11));
            Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            open(player);
            return;
        }
        if (key.equals("GUILD_INVITE")) {
            player.closeInventory();
            plugin.socialManager().beginGuildInvitePrompt(player);
            return;
        }
        if (key.equals("GUILD_MOTD")) {
            player.closeInventory();
            plugin.socialManager().beginGuildMotdPrompt(player);
            return;
        }
        if (key.equals("GUILD_OPEN_TOGGLE")) {
            Guild guild = service.guildOf(player.getUniqueId());
            if (guild != null) {
                service.setOpenJoin(guild, !guild.openJoin());
                Msg.send(player, Msg.success("Open applications: "
                    + (guild.openJoin() ? "<#4ADE80>ON</#4ADE80>" : "<#F87171>OFF</#F87171>")));
            }
            open(player);
            return;
        }
        if (key.equals("GUILD_LEAVE")) {
            var result = service.leave(player);
            Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            open(player);
            return;
        }
        if (key.equals("GUILD_DISBAND")) {
            if (!event.isShiftClick()) {
                Msg.send(player, Msg.warn("Shift-click again to disband the guild."));
                return;
            }
            var result = service.disband(player);
            Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            open(player);
        }
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    private static String leaderName(Guild guild) {
        for (Guild.Member member : guild.members()) {
            if (member.role() == Guild.Role.LEADER) {
                return member.name();
            }
        }
        return "?";
    }

    private static void fillBackdrop(Inventory inv) {
        ItemStack filler = GuiMenuManager.customIcon("ui_invisible", " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }
}
