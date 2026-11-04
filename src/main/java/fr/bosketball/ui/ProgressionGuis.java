package fr.bosketball.ui;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.Achievement;
import fr.bosketball.model.Achievement.AchievementCategory;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerQuest;
import fr.bosketball.social.OfflineMail;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class ProgressionGuis {
    private final BosketBallPlugin plugin;
    public ProgressionGuis(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public void openQuests(Player player) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        long now = System.currentTimeMillis();
        plugin.progressService().ensureBoards(profile, now);
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.QUESTS);
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(
            Glyphs.GUI_OVERLAY_QUESTS, GuiMenuManager.tr(player, "gui.title.quests", "QUESTS")));
        holder.setInventory(inv);
        fillBackdrop(inv);
        inv.setItem(4, GuiMenuManager.customIcon("icon_trophy_gold",
            "<#FFD43B><bold>ǫᴜᴇꜱᴛ ʙᴏᴀʀᴅ</bold></#FFD43B>",
            List.of(
                "<gray>Complete objectives to earn Coins & XP.</gray>",
                "<gray>Daily board resets every 24h, weekly every 7d.</gray>",
                "<gray>Contracts expire after 6h.</gray>",
                "",
                "<#FDE047>Coin balance:</#FDE047> " + Msg.coinsBadge(profile.coins()),
                "<#4ADE80>Claimable now:</#4ADE80> <white>" + profile.unclaimedQuestCount() + "</white>"
            )));

        int[] dailySlots = {10, 11, 12, 13};
        List<PlayerQuest> daily = profile.dailyQuests();
        for (int i = 0; i < daily.size() && i < dailySlots.length; i++) {
            inv.setItem(dailySlots[i], questItem(daily.get(i), "ᴅᴀɪʟʏ", now));
        }

        int[] weeklySlots = {19, 20, 21, 22};
        List<PlayerQuest> weekly = profile.weeklyQuests();
        for (int i = 0; i < weekly.size() && i < weeklySlots.length; i++) {
            inv.setItem(weeklySlots[i], questItem(weekly.get(i), "ᴡᴇᴇᴋʟʏ", now));
        }

        int[] contractSlots = {28, 29};
        List<PlayerQuest> contracts = profile.contracts();
        for (int i = 0; i < contracts.size() && i < contractSlots.length; i++) {
            inv.setItem(contractSlots[i], questItem(contracts.get(i), "ᴄᴏɴᴛʀᴀᴄᴛ", now));
        }
        inv.setItem(31, GuiMenuManager.customIcon("icon_reload",
            "<#38BDF8><bold>ʀᴇʀᴏʟʟ ᴄᴏɴᴛʀᴀᴄᴛ</bold></#38BDF8>",
            List.of(
                "<gray>Swap your first contract for a new one.</gray>",
                "",
                "<gray>Cost:</gray> " + Msg.coinsBadge((int) fr.bosketball.progression.ProgressService.CONTRACT_REROLL_COST),
                "<yellow>▸ Click to reroll</yellow>"
            )));
        inv.setItem(49, GuiMenuManager.customIcon("icon_check",
            "<#4ADE80><bold>ᴄʟᴀɪᴍ ᴀʟʟ</bold></#4ADE80>",
            List.of("<gray>Claim every completed quest at once.</gray>",
                "<#4ADE80>Ready:</#4ADE80> <white>" + profile.unclaimedQuestCount() + "</white>",
                "<yellow>▸ Click to claim</yellow>")));
        inv.setItem(53, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }
    private ItemStack questItem(PlayerQuest quest, String boardLabel, long now) {
        if (quest == null) {
            return GuiMenuManager.customIcon("icon_lock", "<gray>Empty slot</gray>", List.of());
        }
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + boardLabel + "</dark_gray>");
        lore.add("<gray>" + quest.description() + "</gray>");
        lore.add("");
        lore.add("<gray>Progress:</gray> <white>" + quest.progress() + "/" + quest.target() + "</white>");
        lore.add(quest.pipMeter());
        lore.add("");
        lore.add("<gray>Reward:</gray> " + Msg.coinsBadge(quest.rewardCoins())
            + " <dark_gray>•</dark_gray> <white>" + quest.rewardXp() + " XP</white>");
        long remaining = quest.remainingMillis(now);
        if (remaining > 0) {
            lore.add("<gray>Expires in:</gray> <white>" + formatDuration(remaining) + "</white>");
        }
        lore.add("");
        if (quest.claimed()) {
            lore.add("<#64748B>✔ Already claimed</#64748B>");
        } else if (quest.isComplete()) {
            lore.add("<#4ADE80><bold>✔ COMPLETE — click CLAIM ALL</bold></#4ADE80>");
        } else {
            lore.add("<gray>▸ Keep playing to progress</gray>");
        }
        String name = (quest.isComplete() && !quest.claimed() ? "<#4ADE80>" : "<white>") + "<bold>"
            + quest.title() + "</bold>";
        return GuiMenuManager.customIcon(quest.iconModel(), name, lore);
    }

    public void openAchievements(Player player, String categoryFilter, int page) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        AchievementCategory filter = null;
        if (categoryFilter != null) {
            for (AchievementCategory c : AchievementCategory.values()) {
                if (c.name().equalsIgnoreCase(categoryFilter)) {
                    filter = c;
                    break;
                }
            }
        }
        List<Achievement> list = new ArrayList<>();
        for (Achievement a : Achievement.values()) {
            if (filter == null || a.category() == filter) {
                list.add(a);
            }
        }
        int perPage = 21;
        int maxPage = Math.max(0, (list.size() - 1) / perPage);
        int safePage = Math.max(0, Math.min(page, maxPage));
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.ACHIEVEMENTS);
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(
            Glyphs.GUI_OVERLAY_ACHIEVEMENTS,
            GuiMenuManager.tr(player, "gui.title.achievements", "ACHIEVEMENTS")));
        holder.setInventory(inv);
        holder.slotStringMap().put(1000, filter == null ? "" : filter.name());
        holder.slotStringMap().put(1001, String.valueOf(safePage));
        fillBackdrop(inv);
        int unlocked = 0;
        for (Achievement a : Achievement.values()) {
            if (profile.hasAchievement(a)) {
                unlocked++;
            }
        }
        inv.setItem(4, GuiMenuManager.customIcon("icon_trophy_gold",
            "<#FFD43B><bold>ᴀᴄʜɪᴇᴠᴇᴍᴇɴᴛꜱ</bold></#FFD43B>",
            List.of(
                "<gray>Unlocked:</gray> <white>" + unlocked + "/" + Achievement.values().length + "</white>",
                "<gray>Filter:</gray> <white>" + (filter == null ? "All" : filter.title()) + "</white>",
                "",
                "<gray>Every achievement pays Coins once.</gray>"
            )));

        int[] tabSlots = {36, 37, 38, 39, 40, 41, 42};
        AchievementCategory[] cats = AchievementCategory.values();
        for (int i = 0; i < cats.length && i < tabSlots.length; i++) {
            AchievementCategory cat = cats[i];
            boolean active = cat == filter;
            inv.setItem(tabSlots[i], GuiMenuManager.customIcon(cat.iconModel(),
                (active ? "<#4ADE80><bold>" : "<gray><bold>") + cat.title().toUpperCase(java.util.Locale.ROOT) + "</bold>",
                List.of("<gray>" + Achievement.byCategory(cat).size() + " achievements</gray>",
                    active ? "<#4ADE80>▸ Showing this category</#4ADE80>" : "<yellow>▸ Click to filter</yellow>")));
        }
        int[] contentSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < perPage; i++) {
            int index = safePage * perPage + i;
            if (index >= list.size()) {
                break;
            }
            Achievement a = list.get(index);
            boolean has = profile.hasAchievement(a);
            long current = plugin.progressService().careerValue(profile, a.stat());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + a.description() + "</gray>");
            lore.add("<dark_gray>" + a.category().title() + "</dark_gray>");
            lore.add("");
            lore.add("<gray>Progress:</gray> <white>" + Math.min(current, a.target()) + "/" + a.target() + "</white>");
            lore.add(pipString(a.completionRatio(current)));
            lore.add("");
            lore.add(has ? "<#4ADE80><bold>✔ UNLOCKED</bold></#4ADE80>"
                : "<#FDE047>Reward:</#FDE047> " + Msg.coinsBadge(a.rewardCoins()));
            inv.setItem(contentSlots[i], GuiMenuManager.customIcon(a.iconModel(),
                (has ? "<#4ADE80><bold>" : "<gray><bold>") + a.displayName() + "</bold>", lore));
        }
        if (safePage > 0) {
            inv.setItem(48, GuiMenuManager.customIcon("icon_prev", "<yellow>« Previous page</yellow>",
                List.of("<gray>Page " + safePage + "/" + (maxPage + 1) + "</gray>")));
            holder.slotStringMap().put(48, "PREV");
        }
        if (safePage < maxPage) {
            inv.setItem(50, GuiMenuManager.customIcon("icon_next", "<yellow>Next page »</yellow>",
                List.of("<gray>Page " + (safePage + 2) + "/" + (maxPage + 1) + "</gray>")));
            holder.slotStringMap().put(50, "NEXT");
        }
        inv.setItem(53, backButton());
        player.openInventory(inv);
    }
    private static String pipString(double ratio) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 10);
        StringBuilder sb = new StringBuilder(ratio >= 1.0 ? "<#4ADE80>" : "<#FBBF24>");
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? '▰' : '▱');
        }
        sb.append(ratio >= 1.0 ? "</#4ADE80>" : "</#FBBF24>");
        return sb.toString();
    }

    public void openMailbox(Player player) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        List<OfflineMail> mails = plugin.socialService().mails(profile);
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.MAILBOX);
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_MAILBOX,
            GuiMenuManager.tr(player, "gui.title.inbox", "INBOX")));
        holder.setInventory(inv);
        fillBackdrop(inv);
        inv.setItem(4, GuiMenuManager.customIcon("icon_message",
            "<#38BDF8><bold>ᴍᴀɪʟʙᴏx</bold></#38BDF8>",
            List.of("<gray>Notifications you missed while offline.</gray>",
                "<gray>Friend requests, party invites, rewards & staff notices.</gray>",
                "",
                "<gray>Unread:</gray> <white>" + plugin.socialService().unreadCount(profile) + "</white>")));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        long now = System.currentTimeMillis();
        for (int i = 0; i < mails.size() && i < slots.length; i++) {
            OfflineMail mail = mails.get(i);
            List<String> lore = new ArrayList<>();
            lore.addAll(mail.body());
            lore.add("");
            lore.add("<dark_gray>" + mail.kind().smallCapsTitle() + " • " + mail.ageLabel(now) + "</dark_gray>");
            if (mail.hasAction()) {
                lore.add("<yellow>▸ Click to act</yellow>");
            } else if (!mail.read()) {
                lore.add("<#4ADE80>▸ Click to mark as read</#4ADE80>");
            }
            holder.slotStringMap().put(slots[i], "MAIL:" + i);
            inv.setItem(slots[i], GuiMenuManager.customIcon(mail.kind().iconModel(),
                "<" + mail.kind().hexColor() + "><bold>" + mail.subject() + "</bold></" + mail.kind().hexColor() + ">",
                lore));
        }
        inv.setItem(48, GuiMenuManager.customIcon("icon_check", "<#4ADE80><bold>ᴍᴀʀᴋ ᴀʟʟ ʀᴇᴀᴅ</bold></#4ADE80>",
            List.of("<gray>Clear every unread badge.</gray>")));
        inv.setItem(50, GuiMenuManager.customIcon("icon_delete", "<#F87171><bold>ᴄʟᴇᴀʀ ᴀʟʟ</bold></#F87171>",
            List.of("<gray>Delete every message.</gray>")));
        inv.setItem(53, backButton());
        holder.slotStringMap().put(48, "READ_ALL");
        holder.slotStringMap().put(50, "CLEAR_ALL");

        player.openInventory(inv);
    }

    public void openReportsPanel(Player staff) {
        var reports = plugin.reportService().openReports();
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.REPORTS);
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_REPORTS,
            GuiMenuManager.tr(staff, "gui.title.reports", "REPORTS")));
        holder.setInventory(inv);
        fillBackdrop(inv);
        List<String> headerLore = new ArrayList<>();
        headerLore.add("<gray>Open reports:</gray> <white>" + reports.size() + "</white>");
        headerLore.add("<gray>Recent sanctions:</gray> <white>" + plugin.reportService().recentSanctions(8).size() + "</white>");
        headerLore.add("");
        inv.setItem(4, GuiMenuManager.customIcon("icon_star_shield",
            "<#F87171><bold>ꜱᴛᴀꜰꜰ ʀᴇᴘᴏʀᴛꜱ</bold></#F87171>", headerLore));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        long now = System.currentTimeMillis();
        for (int i = 0; i < reports.size() && i < slots.length; i++) {
            var report = reports.get(i);
            holder.slotStringMap().put(slots[i], "REPORT:" + report.id());
            inv.setItem(slots[i], GuiMenuManager.customIcon("icon_star_shield",
                "<#F87171><bold>REPORT #" + report.id() + "</bold></#F87171>",
                List.of(
                    "<gray>Reporter:</gray> <white>" + report.reporterName() + "</white>",
                    "<gray>Reported:</gray> <white>" + report.reportedName() + "</white>",
                    "<gray>Reason:</gray> <white>" + report.reason() + "</white>",
                    "<dark_gray>" + report.ageLabel(now) + " ago</dark_gray>",
                    "",
                    "<yellow>▸ Left-click: mark resolved</yellow>",
                    "<red>▸ Right-click: dismiss</red>"
                )));
        }
        List<String> sanctionLore = new ArrayList<>();
        for (var s : plugin.reportService().recentSanctions(8)) {
            sanctionLore.add("<dark_gray>" + s.playerName() + " — " + s.action() + " (" + s.staff() + ")</dark_gray>");
        }
        if (sanctionLore.isEmpty()) {
            sanctionLore.add("<gray>No sanctions logged yet.</gray>");
        }
        inv.setItem(49, GuiMenuManager.customIcon("icon_history",
            "<#FBBF24><bold>ᴍᴏᴅᴇʀᴀᴛɪᴏɴ ʟᴏɢ</bold></#FBBF24>", sanctionLore));
        inv.setItem(53, backButton());
        staff.openInventory(inv);
    }

    public void openWarningsPanel(Player staff, java.util.UUID focus) {
        var warns = plugin.warnService();
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.WARNINGS);
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_WARNINGS,
            GuiMenuManager.tr(staff, "gui.title.warnings", "WARNINGS")));
        holder.setInventory(inv);
        fillBackdrop(inv);
        long now = System.currentTimeMillis();

        if (focus == null) {

            List<java.util.UUID> warned = warns.warnedPlayers();
            inv.setItem(4, GuiMenuManager.customIcon("icon_star_shield",
                "<#F87171><bold>ᴡᴀʀɴɪɴɢꜱ</bold></#F87171>",
                List.of(
                    "<gray>Warned players:</gray> <white>" + warned.size() + "</white>",
                    "<gray>Total warnings:</gray> <white>" + warns.totalWarnings() + "</white>",
                    "",
                    "<gray>Click a player to review their warnings.</gray>",
                    "<dark_gray>Usage: /warn <player> <reason></dark_gray>")));
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < warned.size() && i < slots.length; i++) {
                java.util.UUID id = warned.get(i);
                List<fr.bosketball.moderation.WarnService.Warn> list = warns.of(id);
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Warnings:</gray> <red><bold>" + list.size() + "</bold></red>");
                if (!list.isEmpty()) {
                    lore.add("<gray>Last:</gray> <white>" + list.get(0).reason() + "</white>");
                    lore.add("<dark_gray>" + list.get(0).ageLabel(now) + " ago by "
                        + list.get(0).staffName() + "</dark_gray>");
                }
                lore.add("");
                lore.add("<yellow>▸ Click to open the warning record</yellow>");
                holder.slotStringMap().put(slots[i], "WARNPLAYER:" + id);
                inv.setItem(slots[i], GuiMenuManager.customIcon("icon_star_shield",
                    "<#F87171><bold>" + warns.nameOf(id) + "</bold></#F87171>", lore));
            }
            if (warned.isEmpty()) {
                inv.setItem(22, GuiMenuManager.customIcon("icon_star_shield",
                    "<#4ADE80><bold>ᴄʟᴇᴀɴ ʀᴇᴄᴏʀᴅꜱ</bold></#4ADE80>",
                    List.of("<gray>No player currently has a warning.</gray>")));
            }
        } else {
            List<fr.bosketball.moderation.WarnService.Warn> list = warns.of(focus);
            String name = warns.nameOf(focus);
            inv.setItem(4, GuiMenuManager.customIcon("icon_star_shield",
                "<#F87171><bold>" + name + "</bold></#F87171>",
                List.of(
                    "<gray>Warnings:</gray> <red><bold>" + list.size() + "</bold></red>",
                    "",
                    "<yellow>▸ Left-click : remove the newest warning</yellow>",
                    "<red>▸ Right-click : clear every warning</red>")));
            holder.slotStringMap().put(4, "WARNTARGET:" + focus);
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < list.size() && i < slots.length; i++) {
                var warn = list.get(i);
                holder.slotStringMap().put(slots[i], "WARNENTRY:" + focus + ":" + warn.id());
                inv.setItem(slots[i], GuiMenuManager.customIcon("icon_star_shield",
                    "<#FBBF24><bold>ᴡᴀʀɴ #" + (list.size() - i) + "</bold></#FBBF24>",
                    List.of(
                        "<gray>Reason:</gray> <white>" + warn.reason() + "</white>",
                        "<gray>By:</gray> <white>" + warn.staffName() + "</white>",
                        "<dark_gray>" + warn.ageLabel(now) + " ago</dark_gray>",
                        "",
                        "<red>▸ Click to remove this warning</red>")));
            }
            if (list.isEmpty()) {
                inv.setItem(22, GuiMenuManager.customIcon("icon_star_shield",
                    "<#4ADE80><bold>ᴄʟᴇᴀɴ ʀᴇᴄᴏʀᴅ</bold></#4ADE80>",
                    List.of("<gray>" + name + " has no warning.</gray>")));
            }
            inv.setItem(48, GuiMenuManager.customIcon("icon_back",
                "<#38BDF8><bold>‹ ᴀʟʟ ᴡᴀʀɴᴇᴅ ᴘʟᴀʏᴇʀꜱ</bold></#38BDF8>",
                List.of("<gray>Back to the warnings overview.</gray>")));
            holder.slotStringMap().put(48, "WARNALL");
        }
        inv.setItem(53, backButton());
        staff.openInventory(inv);
    }

    public void openFriends(Player player) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.FRIENDS_RICH);
        Inventory inv = Bukkit.createInventory(holder, 54, Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_FRIENDS,
            GuiMenuManager.tr(player, "gui.title.friends", "FRIENDS")));
        holder.setInventory(inv);
        fillBackdrop(inv);

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort((a, b) -> {
            int sa = plugin.socialService().statusOf(a.getUniqueId()).ordinal();
            int sb = plugin.socialService().statusOf(b.getUniqueId()).ordinal();
            return sa != sb ? Integer.compare(sa, sb) : a.getName().compareToIgnoreCase(b.getName());
        });
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

        online.removeIf(o -> o.getUniqueId().equals(player.getUniqueId()));
        for (int i = 0; i < online.size() && i < slots.length; i++) {
            Player other = online.get(i);
            var status = plugin.socialService().statusOf(other.getUniqueId());
            PlayerProfile otherProfile = plugin.statsManager().getIfPresent(other.getUniqueId());
            boolean friend = profile.isFriend(other.getUniqueId());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Status:</gray> " + status.coloredLabel());
            if (otherProfile != null) {
                lore.add("<gray>Rank:</gray> <white>" + otherProfile.effectiveRank().plainDisplayName() + "</white>"
                    + " <dark_gray>(" + otherProfile.elo() + " ELO)</dark_gray>");
                lore.add("<gray>Record:</gray> <#4ADE80>" + otherProfile.wins() + "W</#4ADE80><gray>/</gray><#F87171>"
                    + otherProfile.losses() + "L</#F87171>");
            }
            lore.add("");
            lore.add(friend ? "<#4ADE80>✔ Friend</#4ADE80>" : "<yellow>▸ Click to add friend</yellow>");
            if (status.isAvailable()) {
                lore.add("<yellow>▸ Shift-click to invite to party</yellow>");
            }
            holder.slotStringMap().put(slots[i], "FRIEND:" + other.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(other);
                skull.displayName(Msg.parse(status.coloredSymbol() + " <white><bold>" + other.getName() + "</bold></white>"));
                List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(Msg.parse(line));
                }
                skull.lore(loreComponents);
                head.setItemMeta(skull);
            }
            inv.setItem(slots[i], head);
        }
        inv.setItem(4, GuiMenuManager.customIcon("icon_friends",
            "<#4ADE80><bold>ꜰʀɪᴇɴᴅꜱ & ᴘᴀʀᴛʏ</bold></#4ADE80>",
            List.of(
                "<gray>Friends online:</gray> <white>" + plugin.socialManager().countOnlineFriends(player.getUniqueId()) + "</white>",
                "<gray>Party size:</gray> <white>" + plugin.socialManager().partySizeOrOne(player.getUniqueId()) + "</white>",
                "",
                "<gray>Statuses update live in the [TAB] list.</gray>"
            )));
        inv.setItem(48, GuiMenuManager.customIcon("icon_party_invites",
            "<#FBBF24><bold>ɪɴᴠɪᴛᴇ ᴛᴏ ᴘᴀʀᴛʏ</bold></#FBBF24>",
            List.of("<gray>Open the party invite browser.</gray>")));
        inv.setItem(50, GuiMenuManager.customIcon("icon_message",
            "<#38BDF8><bold>ᴍᴀɪʟʙᴏx</bold></#38BDF8>",
            List.of("<gray>Unread:</gray> <white>" + plugin.socialService().unreadCount(profile) + "</white>")));
        inv.setItem(53, backButton());
        holder.slotStringMap().put(48, "PARTY_INVITE");
        holder.slotStringMap().put(50, "MAILBOX");
        player.openInventory(inv);
    }

    private static ItemStack backButton() {
        return GuiMenuManager.customIcon("icon_back", "<#F87171><bold>‹ ʙᴀᴄᴋ</bold></#F87171>",
            List.of("<gray>Return to the previous menu.</gray>"));
    }
    private static void fillBackdrop(Inventory inv) {
        ItemStack filler = GuiMenuManager.customIcon("ui_invisible", " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }
    private static String formatDuration(long millis) {
        long sec = millis / 1000L;
        if (sec < 60) {
            return sec + "s";
        }
        long min = sec / 60;
        if (min < 60) {
            return min + "m";
        }
        return (min / 60) + "h " + (min % 60) + "m";
    }

    public static String duration(long millis) {
        return formatDuration(millis);
    }
}
