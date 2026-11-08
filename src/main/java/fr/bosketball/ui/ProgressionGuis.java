package fr.bosketball.ui;
import fr.bosketball.util.Palette;
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
        inv.setItem(4, GuiMenuManager.customIcon("ref_trophy",
            "<" + Palette.GOLD_SOFT + "><bold>ǫᴜᴇꜱᴛ ʙᴏᴀʀᴅ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>Complete objectives to earn Coins & XP.</#94A3B8>",
                "<#94A3B8>Daily board resets every 24h, weekly every 7d.</#94A3B8>",
                "<#94A3B8>Contracts expire after 6h.</#94A3B8>",
                "",
                "<" + Palette.GOLD_SOFT + ">Coin balance:</" + Palette.GOLD_SOFT + "> " + Msg.coinsBadge(profile.coins()),
                "<" + Palette.SUCCESS + ">Claimable now:</" + Palette.SUCCESS + "> <#F8FAFC>" + profile.unclaimedQuestCount() + "</#F8FAFC>"
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
            "<" + Palette.ICE + "><bold>ʀᴇʀᴏʟʟ ᴄᴏɴᴛʀᴀᴄᴛ</bold></" + Palette.ICE + ">",
            List.of(
                "<#94A3B8>Swap your first contract for a new one.</#94A3B8>",
                "",
                "<#94A3B8>Cost:</#94A3B8> " + Msg.coinsBadge((int) fr.bosketball.progression.ProgressService.CONTRACT_REROLL_COST),
                "<#FDE047>▸ Click to reroll</#FDE047>"
            )));
        inv.setItem(32, GuiMenuManager.customIcon("icon_reload",
            "<" + Palette.GOLD + "><bold>ʀᴇʀᴏʟʟ ᴅᴀɪʟʏ</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>Swap your first unfinished daily quest</#94A3B8>",
                "<#94A3B8>for a fresh objective.</#94A3B8>",
                "",
                "<#94A3B8>Cost:</#94A3B8> " + Msg.coinsBadge((int) fr.bosketball.progression.ProgressService.CONTRACT_REROLL_COST),
                "<#FDE047>▸ Click to reroll</#FDE047>"
            )));
        inv.setItem(49, GuiMenuManager.customIcon("ref_check",
            "<" + Palette.SUCCESS + "><bold>ᴄʟᴀɪᴍ ᴀʟʟ</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Claim every completed quest at once.</#94A3B8>",
                "<" + Palette.SUCCESS + ">Ready:</" + Palette.SUCCESS + "> <#F8FAFC>" + profile.unclaimedQuestCount() + "</#F8FAFC>",
                "<#FDE047>▸ Click to claim</#FDE047>")));
        inv.setItem(53, backButton());
        player.openInventory(inv);
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }
    private ItemStack questItem(PlayerQuest quest, String boardLabel, long now) {
        if (quest == null) {
            return GuiMenuManager.customIcon("ref_lock", "<#94A3B8>Empty slot</#94A3B8>", List.of());
        }
        List<String> lore = new ArrayList<>();
        lore.add("<#64748B>" + boardLabel + "</#64748B>");
        lore.add("<#94A3B8>" + quest.description() + "</#94A3B8>");
        lore.add("");
        lore.add("<#94A3B8>Progress:</#94A3B8> <#F8FAFC>" + quest.progress() + "/" + quest.target() + "</#F8FAFC>");
        lore.add(quest.pipMeter());
        lore.add("");
        lore.add("<#94A3B8>Reward:</#94A3B8> " + Msg.coinsBadge(quest.rewardCoins())
            + " <#64748B>•</#64748B> <#F8FAFC>" + quest.rewardXp() + " XP</#F8FAFC>");
        long remaining = quest.remainingMillis(now);
        if (remaining > 0) {
            lore.add("<#94A3B8>Expires in:</#94A3B8> <#F8FAFC>" + formatDuration(remaining) + "</#F8FAFC>");
        }
        lore.add("");
        if (quest.claimed()) {
            lore.add("<" + Palette.DIM + ">✔ Already claimed</" + Palette.DIM + ">");
        } else if (quest.isComplete()) {
            lore.add("<" + Palette.SUCCESS + "><bold>✔ COMPLETE — click CLAIM ALL</bold></" + Palette.SUCCESS + ">");
        } else {
            lore.add("<#94A3B8>▸ Keep playing to progress</#94A3B8>");
        }
        String name = (quest.isComplete() && !quest.claimed() ? "<" + Palette.SUCCESS + ">" : "<#F8FAFC>") + "<bold>"
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
        inv.setItem(4, GuiMenuManager.customIcon("ref_trophy",
            "<" + Palette.GOLD_SOFT + "><bold>ᴀᴄʜɪᴇᴠᴇᴍᴇɴᴛꜱ</bold></" + Palette.GOLD_SOFT + ">",
            List.of(
                "<#94A3B8>Unlocked:</#94A3B8> <#F8FAFC>" + unlocked + "/" + Achievement.values().length + "</#F8FAFC>",
                "<#94A3B8>Filter:</#94A3B8> <#F8FAFC>" + (filter == null ? "All" : filter.title()) + "</#F8FAFC>",
                "",
                "<#94A3B8>Every achievement pays Coins once.</#94A3B8>"
            )));

        int[] tabSlots = {36, 37, 38, 39, 40, 41, 42};
        AchievementCategory[] cats = AchievementCategory.values();
        for (int i = 0; i < cats.length && i < tabSlots.length; i++) {
            AchievementCategory cat = cats[i];
            boolean active = cat == filter;
            inv.setItem(tabSlots[i], GuiMenuManager.customIcon(cat.iconModel(),
                (active ? "<" + Palette.SUCCESS + "><bold>" : "<#94A3B8><bold>") + cat.title().toUpperCase(java.util.Locale.ROOT) + "</bold>",
                List.of("<#94A3B8>" + Achievement.byCategory(cat).size() + " achievements</#94A3B8>",
                    active ? "<" + Palette.SUCCESS + ">▸ Showing this category</" + Palette.SUCCESS + ">" : "<#FDE047>▸ Click to filter</#FDE047>")));
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
            lore.add("<#94A3B8>" + a.description() + "</#94A3B8>");
            lore.add("<#64748B>" + a.category().title() + "</#64748B>");
            lore.add("");
            lore.add("<#94A3B8>Progress:</#94A3B8> <#F8FAFC>" + Math.min(current, a.target()) + "/" + a.target() + "</#F8FAFC>");
            lore.add(pipString(a.completionRatio(current)));
            lore.add("");
            lore.add(has ? "<" + Palette.SUCCESS + "><bold>✔ UNLOCKED</bold></" + Palette.SUCCESS + ">"
                : "<" + Palette.GOLD_SOFT + ">Reward:</" + Palette.GOLD_SOFT + "> " + Msg.coinsBadge(a.rewardCoins()));
            inv.setItem(contentSlots[i], GuiMenuManager.customIcon(a.iconModel(),
                (has ? "<" + Palette.SUCCESS + "><bold>" : "<#94A3B8><bold>") + a.displayName() + "</bold>", lore));
        }
        if (safePage > 0) {
            inv.setItem(48, GuiMenuManager.customIcon("icon_prev", "<#FDE047>« Previous page</#FDE047>",
                List.of("<#94A3B8>Page " + safePage + "/" + (maxPage + 1) + "</#94A3B8>")));
            holder.slotStringMap().put(48, "PREV");
        }
        if (safePage < maxPage) {
            inv.setItem(50, GuiMenuManager.customIcon("icon_next", "<#FDE047>Next page »</#FDE047>",
                List.of("<#94A3B8>Page " + (safePage + 2) + "/" + (maxPage + 1) + "</#94A3B8>")));
            holder.slotStringMap().put(50, "NEXT");
        }
        inv.setItem(53, backButton());
        player.openInventory(inv);
    }
    private static String pipString(double ratio) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 10);
        StringBuilder sb = new StringBuilder(ratio >= 1.0 ? "<" + Palette.SUCCESS + ">" : "<" + Palette.GOLD + ">");
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? '▰' : '▱');
        }
        sb.append(ratio >= 1.0 ? "</" + Palette.SUCCESS + ">" : "</" + Palette.GOLD + ">");
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
            "<" + Palette.ICE + "><bold>ᴍᴀɪʟʙᴏx</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Notifications you missed while offline.</#94A3B8>",
                "<#94A3B8>Friend requests, party invites, rewards & staff notices.</#94A3B8>",
                "",
                "<#94A3B8>Unread:</#94A3B8> <#F8FAFC>" + plugin.socialService().unreadCount(profile) + "</#F8FAFC>")));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        long now = System.currentTimeMillis();
        for (int i = 0; i < mails.size() && i < slots.length; i++) {
            OfflineMail mail = mails.get(i);
            List<String> lore = new ArrayList<>();
            lore.addAll(mail.body());
            lore.add("");
            lore.add("<#64748B>" + mail.kind().smallCapsTitle() + " • " + mail.ageLabel(now) + "</#64748B>");
            if (mail.hasAction()) {
                lore.add("<#FDE047>▸ Click to act</#FDE047>");
            } else if (!mail.read()) {
                lore.add("<" + Palette.SUCCESS + ">▸ Click to mark as read</" + Palette.SUCCESS + ">");
            }
            holder.slotStringMap().put(slots[i], "MAIL:" + i);
            inv.setItem(slots[i], GuiMenuManager.customIcon(mail.kind().iconModel(),
                "<" + mail.kind().hexColor() + "><bold>" + Msg.escapeMini(mail.subject())
                    + "</bold></" + mail.kind().hexColor() + ">",
                lore));
        }
        inv.setItem(48, GuiMenuManager.customIcon("ref_check", "<" + Palette.SUCCESS + "><bold>ᴍᴀʀᴋ ᴀʟʟ ʀᴇᴀᴅ</bold></" + Palette.SUCCESS + ">",
            List.of("<#94A3B8>Clear every unread badge.</#94A3B8>")));
        inv.setItem(50, GuiMenuManager.customIcon("icon_delete", "<" + Palette.DANGER + "><bold>ᴄʟᴇᴀʀ ᴀʟʟ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Delete every message.</#94A3B8>")));
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
        headerLore.add("<#94A3B8>Open reports:</#94A3B8> <#F8FAFC>" + reports.size() + "</#F8FAFC>");
        headerLore.add("<#94A3B8>Recent sanctions:</#94A3B8> <#F8FAFC>" + plugin.reportService().recentSanctions(8).size() + "</#F8FAFC>");
        headerLore.add("");
        inv.setItem(4, GuiMenuManager.customIcon("icon_star_shield",
            "<" + Palette.DANGER + "><bold>ꜱᴛᴀꜰꜰ ʀᴇᴘᴏʀᴛꜱ</bold></" + Palette.DANGER + ">", headerLore));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        long now = System.currentTimeMillis();
        for (int i = 0; i < reports.size() && i < slots.length; i++) {
            var report = reports.get(i);
            holder.slotStringMap().put(slots[i], "REPORT:" + report.id());
            inv.setItem(slots[i], GuiMenuManager.customIcon("icon_star_shield",
                "<" + Palette.DANGER + "><bold>REPORT #" + report.id() + "</bold></" + Palette.DANGER + ">",
                List.of(
                    "<#94A3B8>Reporter:</#94A3B8> <#F8FAFC>" + Msg.escapeMini(report.reporterName()) + "</#F8FAFC>",
                    "<#94A3B8>Reported:</#94A3B8> <#F8FAFC>" + Msg.escapeMini(report.reportedName()) + "</#F8FAFC>",
                    "<#94A3B8>Reason:</#94A3B8> <#F8FAFC>" + Msg.escapeMini(report.reason()) + "</#F8FAFC>",
                    "<#64748B>" + report.ageLabel(now) + " ago</#64748B>",
                    "",
                    "<#FDE047>▸ Left-click: mark resolved</#FDE047>",
                    "<#F87171>▸ Right-click: dismiss</#F87171>"
                )));
        }
        List<String> sanctionLore = new ArrayList<>();
        for (var s : plugin.reportService().recentSanctions(8)) {
            sanctionLore.add("<#64748B>" + s.playerName() + " — " + s.action() + " (" + s.staff() + ")</#64748B>");
        }
        if (sanctionLore.isEmpty()) {
            sanctionLore.add("<#94A3B8>No sanctions logged yet.</#94A3B8>");
        }
        inv.setItem(49, GuiMenuManager.customIcon("ref_clock",
            "<" + Palette.GOLD + "><bold>ᴍᴏᴅᴇʀᴀᴛɪᴏɴ ʟᴏɢ</bold></" + Palette.GOLD + ">", sanctionLore));
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
                "<" + Palette.DANGER + "><bold>ᴡᴀʀɴɪɴɢꜱ</bold></" + Palette.DANGER + ">",
                List.of(
                    "<#94A3B8>Warned players:</#94A3B8> <#F8FAFC>" + warned.size() + "</#F8FAFC>",
                    "<#94A3B8>Total warnings:</#94A3B8> <#F8FAFC>" + warns.totalWarnings() + "</#F8FAFC>",
                    "",
                    "<#94A3B8>Click a player to review their warnings.</#94A3B8>",
                    "<#64748B>Usage: /warn <player> <reason></#64748B>")));
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < warned.size() && i < slots.length; i++) {
                java.util.UUID id = warned.get(i);
                List<fr.bosketball.moderation.WarnService.Warn> list = warns.of(id);
                List<String> lore = new ArrayList<>();
                lore.add("<#94A3B8>Warnings:</#94A3B8> <#F87171><bold>" + list.size() + "</bold></#F87171>");
                if (!list.isEmpty()) {
                    lore.add("<#94A3B8>Last:</#94A3B8> <#F8FAFC>" + Msg.escapeMini(list.get(0).reason()) + "</#F8FAFC>");
                    lore.add("<#64748B>" + list.get(0).ageLabel(now) + " ago by "
                        + Msg.escapeMini(list.get(0).staffName()) + "</#64748B>");
                }
                lore.add("");
                lore.add("<#FDE047>▸ Click to open the warning record</#FDE047>");
                holder.slotStringMap().put(slots[i], "WARNPLAYER:" + id);
                inv.setItem(slots[i], GuiMenuManager.customIcon("icon_star_shield",
                    "<" + Palette.DANGER + "><bold>" + Msg.escapeMini(warns.nameOf(id)) + "</bold></" + Palette.DANGER + ">", lore));
            }
            if (warned.isEmpty()) {
                inv.setItem(22, GuiMenuManager.customIcon("icon_star_shield",
                    "<" + Palette.SUCCESS + "><bold>ᴄʟᴇᴀɴ ʀᴇᴄᴏʀᴅꜱ</bold></" + Palette.SUCCESS + ">",
                    List.of("<#94A3B8>No player currently has a warning.</#94A3B8>")));
            }
        } else {
            List<fr.bosketball.moderation.WarnService.Warn> list = warns.of(focus);
            String name = Msg.escapeMini(warns.nameOf(focus));
            inv.setItem(4, GuiMenuManager.customIcon("icon_star_shield",
                "<" + Palette.DANGER + "><bold>" + name + "</bold></" + Palette.DANGER + ">",
                List.of(
                    "<#94A3B8>Warnings:</#94A3B8> <#F87171><bold>" + list.size() + "</bold></#F87171>",
                    "",
                    "<#FDE047>▸ Left-click : remove the newest warning</#FDE047>",
                    "<#F87171>▸ Right-click : clear every warning</#F87171>")));
            holder.slotStringMap().put(4, "WARNTARGET:" + focus);
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < list.size() && i < slots.length; i++) {
                var warn = list.get(i);
                holder.slotStringMap().put(slots[i], "WARNENTRY:" + focus + ":" + warn.id());
                inv.setItem(slots[i], GuiMenuManager.customIcon("icon_star_shield",
                    "<" + Palette.GOLD + "><bold>ᴡᴀʀɴ #" + (list.size() - i) + "</bold></" + Palette.GOLD + ">",
                    List.of(
                        "<#94A3B8>Reason:</#94A3B8> <#F8FAFC>" + Msg.escapeMini(warn.reason()) + "</#F8FAFC>",
                        "<#94A3B8>By:</#94A3B8> <#F8FAFC>" + Msg.escapeMini(warn.staffName()) + "</#F8FAFC>",
                        "<#64748B>" + warn.ageLabel(now) + " ago</#64748B>",
                        "",
                        "<#F87171>▸ Click to remove this warning</#F87171>")));
            }
            if (list.isEmpty()) {
                inv.setItem(22, GuiMenuManager.customIcon("icon_star_shield",
                    "<" + Palette.SUCCESS + "><bold>ᴄʟᴇᴀɴ ʀᴇᴄᴏʀᴅ</bold></" + Palette.SUCCESS + ">",
                    List.of("<#94A3B8>" + name + " has no warning.</#94A3B8>")));
            }
            inv.setItem(48, GuiMenuManager.customIcon("ref_arrow_left",
                "<" + Palette.ICE + "><bold>‹ ᴀʟʟ ᴡᴀʀɴᴇᴅ ᴘʟᴀʏᴇʀꜱ</bold></" + Palette.ICE + ">",
                List.of("<#94A3B8>Back to the warnings overview.</#94A3B8>")));
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
            lore.add("<#94A3B8>Status:</#94A3B8> " + status.coloredLabel());
            if (otherProfile != null) {
                lore.add("<#94A3B8>Rank:</#94A3B8> <#F8FAFC>" + otherProfile.effectiveRank().plainDisplayName() + "</#F8FAFC>"
                    + " <#64748B>(" + otherProfile.elo() + " ELO)</#64748B>");
                lore.add("<#94A3B8>Record:</#94A3B8> <" + Palette.SUCCESS + ">" + otherProfile.wins() + "W</" + Palette.SUCCESS + "><#94A3B8>/</#94A3B8><" + Palette.DANGER + ">"
                    + otherProfile.losses() + "L</" + Palette.DANGER + ">");
            }
            lore.add("");
            lore.add(friend ? "<" + Palette.SUCCESS + ">✔ Friend</" + Palette.SUCCESS + ">" : "<#FDE047>▸ Click to add friend</#FDE047>");
            if (status.isAvailable()) {
                lore.add("<#FDE047>▸ Shift-click to invite to party</#FDE047>");
            }
            holder.slotStringMap().put(slots[i], "FRIEND:" + other.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(other);
                skull.displayName(Msg.parse(status.coloredSymbol() + " <#F8FAFC><bold>"
                    + Msg.escapeMini(other.getName()) + "</bold></#F8FAFC>"));
                List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(Msg.parse(line));
                }
                skull.lore(loreComponents);
                head.setItemMeta(skull);
            }
            inv.setItem(slots[i], head);
        }
        inv.setItem(4, GuiMenuManager.customIcon("ref_friends",
            "<" + Palette.SUCCESS + "><bold>ꜰʀɪᴇɴᴅꜱ & ᴘᴀʀᴛʏ</bold></" + Palette.SUCCESS + ">",
            List.of(
                "<#94A3B8>Friends online:</#94A3B8> <#F8FAFC>" + plugin.socialManager().countOnlineFriends(player.getUniqueId()) + "</#F8FAFC>",
                "<#94A3B8>Party size:</#94A3B8> <#F8FAFC>" + plugin.socialManager().partySizeOrOne(player.getUniqueId()) + "</#F8FAFC>",
                "",
                "<#94A3B8>Statuses update live in the [TAB] list.</#94A3B8>"
            )));
        inv.setItem(48, GuiMenuManager.customIcon("icon_party_invites",
            "<" + Palette.GOLD + "><bold>ɪɴᴠɪᴛᴇ ᴛᴏ ᴘᴀʀᴛʏ</bold></" + Palette.GOLD + ">",
            List.of("<#94A3B8>Open the party invite browser.</#94A3B8>")));
        inv.setItem(50, GuiMenuManager.customIcon("icon_message",
            "<" + Palette.ICE + "><bold>ᴍᴀɪʟʙᴏx</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Unread:</#94A3B8> <#F8FAFC>" + plugin.socialService().unreadCount(profile) + "</#F8FAFC>")));
        inv.setItem(53, backButton());
        holder.slotStringMap().put(48, "PARTY_INVITE");
        holder.slotStringMap().put(50, "MAILBOX");
        player.openInventory(inv);
    }

    private static ItemStack backButton() {
        return GuiMenuManager.customIcon("ref_arrow_left", "<" + Palette.DANGER + "><bold>‹ ʙᴀᴄᴋ</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Return to the previous menu.</#94A3B8>"));
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
