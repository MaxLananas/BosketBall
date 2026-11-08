package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.Achievement;
import fr.bosketball.model.Achievement.AchievementCategory;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerQuest;
import fr.bosketball.social.OfflineMail;
import fr.bosketball.ui.kit.MenuItems;
import fr.bosketball.ui.kit.MenuSpec;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Progression and staff lists share the same row, empty and disabled vocabulary. */
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
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_QUESTS, GuiMenuManager.tr(player, "gui.title.quests", "QUESTS")));
        MenuSpec.Builder spec = MenuSpec.builder(6, "Quests");
        int claimable = profile.unclaimedQuestCount();
        spec.item(4, MenuItems.info("ref_trophy", "Quest board", List.of(
            MenuItems.row("Claimable", claimable), MenuItems.row("Balance", Msg.count(profile.coins()) + " coins"),
            MenuItems.muted("Daily resets every 24h; weekly every 7d; contracts expire after 6h."))));
        board(player, holder, spec, profile.dailyQuests(), new int[]{10, 11, 12, 13}, "Daily", now);
        board(player, holder, spec, profile.weeklyQuests(), new int[]{19, 20, 21, 22}, "Weekly", now);
        board(player, holder, spec, profile.contracts(), new int[]{28, 29}, "Contract", now);
        int cost = (int) fr.bosketball.progression.ProgressService.CONTRACT_REROLL_COST;
        boolean openDaily = profile.dailyQuests().stream().anyMatch(q -> q != null && !q.isComplete());
        boolean openContract = !profile.contracts().isEmpty();
        spec.item(31, reroll(player, profile, "Reroll contract", openContract, cost,
            () -> plugin.progressService().rerollContract(profile, System.currentTimeMillis())));
        spec.item(32, reroll(player, profile, "Reroll daily", openDaily, cost,
            () -> plugin.progressService().rerollDailyQuest(profile, System.currentTimeMillis())));
        if (claimable == 0) {
            spec.item(47, MenuItems.disabled("ref_check", "Claim all", "No completed quest is waiting.", "Finish an objective, then return here."));
        } else {
            spec.item(47, MenuItems.action("ref_check", "Claim all rewards", MenuItems.lore("Ready: " + claimable), click -> {
                int earned = plugin.progressService().claimCompletedQuests(player, profile);
                Msg.send(player, earned > 0 ? Msg.success("Quests claimed: +" + earned + " coins.") : Msg.warn("No completed quest to claim yet."));
                openQuests(player);
            }));
        }
        spec.item(33, MenuItems.action("ref_play", "Play a match",
            MenuItems.lore("Every objective progresses from the court."), click -> plugin.guiMenu().openPlayModes(player)));
        plugin.guiMenu().show(player, holder, spec);
    }

    private void board(Player player, GuiMenuManager.BosketHolder holder, MenuSpec.Builder spec,
                       List<PlayerQuest> quests, int[] slots, String label, long now) {
        if (quests.isEmpty()) {
            spec.item(slots[0], MenuItems.empty("No " + label.toLowerCase(java.util.Locale.ROOT) + " quest is active.", "Play a match",
                click -> plugin.guiMenu().openPlayModes(player)));
            return;
        }
        for (int i = 0; i < quests.size() && i < slots.length; i++) {
            PlayerQuest quest = quests.get(i);
            if (quest == null) {
                continue;
            }
            List<String> lore = new ArrayList<>(List.of(
                MenuItems.row("Board", label), MenuItems.muted(quest.description()),
                MenuItems.row("Progress", quest.progress() + "/" + quest.target()), quest.pipMeter(),
                MenuItems.row("Coins", quest.rewardCoins()), MenuItems.row("XP", quest.rewardXp())));
            long remaining = quest.remainingMillis(now);
            if (remaining > 0) {
                lore.add(MenuItems.row("Expires in", formatDuration(remaining)));
            }
            if (quest.claimed()) {
                spec.item(slots[i], MenuItems.disabled(quest.iconModel(), quest.title(), "Already claimed.", "New objectives arrive on the next reset."));
            } else if (!quest.isComplete()) {
                spec.item(slots[i], MenuItems.disabled(quest.iconModel(), quest.title(),
                    "Progress " + quest.progress() + "/" + quest.target() + ".", "Keep playing to finish this objective."));
            } else {
                lore.add(MenuItems.muted("Click to claim this reward."));
                spec.item(slots[i], MenuItems.action(quest.iconModel(), "Claim " + quest.title(), lore, click -> {
                    claimQuest(player, quest);
                    openQuests(player);
                }));
            }
        }
    }

    private void claimQuest(Player player, PlayerQuest quest) {
        if (quest.claimed() || !quest.isComplete()) {
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        quest.setClaimed(true);
        quest.setRewarded(true);
        profile.addEarnedCoins(quest.rewardCoins());
        profile.addXp(quest.rewardXp());
        plugin.statsManager().markDirty(player.getUniqueId());
        plugin.statsManager().saveProfileAsync(profile);
        plugin.progressService().evaluateAchievements(profile, true);
        Msg.playPersonalPackSound(player, "ui.click", 1.0f, 1.45f);
        Msg.send(player, Msg.success("Quest claimed: +" + quest.rewardCoins() + " coins, +" + quest.rewardXp() + " XP."));
    }

    private MenuSpec.Item reroll(Player player, PlayerProfile profile, String label, boolean available, int cost,
                                 java.util.function.Supplier<PlayerQuest> roll) {
        if (!available) {
            return MenuItems.disabled("icon_reload", label, "No open objective to replace.", "Finish or wait for the next board.");
        }
        if (profile.coins() < cost) {
            return MenuItems.unaffordable(label, cost, profile.coins(), "Play matches to earn coins.");
        }
        return MenuItems.action("icon_reload", label, MenuItems.price(cost, profile.coins()), click -> {
            PlayerQuest fresh = roll.get();
            Msg.send(player, fresh == null ? Msg.error("Cannot reroll right now.") : Msg.success("New objective: " + fresh.title()));
            openQuests(player);
        });
    }

    public void openAchievements(Player player, String categoryFilter, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        AchievementCategory filter = null;
        for (AchievementCategory category : AchievementCategory.values()) {
            if (category.name().equalsIgnoreCase(categoryFilter)) {
                filter = category;
                break;
            }
        }
        List<Achievement> list = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            if (filter == null || achievement.category() == filter) {
                list.add(achievement);
            }
        }
        MenuSpec.Page<Achievement> page = MenuSpec.Page.of(list, requestedPage, 21);
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.ACHIEVEMENTS);
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_ACHIEVEMENTS, GuiMenuManager.tr(player, "gui.title.achievements", "ACHIEVEMENTS")));
        holder.setView(filter == null ? "" : filter.name());
        holder.setPage(page.index());
        MenuSpec.Builder spec = MenuSpec.builder(6, "Achievements");
        int unlocked = 0;
        for (Achievement achievement : Achievement.values()) {
            if (profile.hasAchievement(achievement)) unlocked++;
        }
        spec.item(4, MenuItems.info("ref_trophy", "Achievements", List.of(
            MenuItems.row("Unlocked", unlocked + "/" + Achievement.values().length),
            MenuItems.row("Filter", filter == null ? "All" : filter.title()))));
        int[] contentSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < page.entries().size(); i++) {
            Achievement achievement = page.entries().get(i);
            boolean has = profile.hasAchievement(achievement);
            long current = plugin.progressService().careerValue(profile, achievement.stat());
            List<String> lore = List.of(MenuItems.muted(achievement.description()), MenuItems.row("Category", achievement.category().title()),
                MenuItems.row("Progress", Math.min(current, achievement.target()) + "/" + achievement.target()),
                pipString(achievement.completionRatio(current)), MenuItems.row("Reward", Msg.count(achievement.rewardCoins()) + " coins"));
            spec.item(contentSlots[i], has ? MenuItems.info(achievement.iconModel(), achievement.displayName(), lore).selected()
                : MenuItems.disabled(achievement.iconModel(), achievement.displayName(), "Not unlocked yet.", "Keep playing to reach the target."));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No achievement matches this filter.", "Show all achievements", click -> openAchievements(player, null, 0)));
        }
        int[] tabSlots = {36, 37, 38, 39, 40, 41, 42};
        AchievementCategory[] categories = AchievementCategory.values();
        for (int i = 0; i < categories.length && i < tabSlots.length; i++) {
            AchievementCategory category = categories[i];
            String next = category == filter ? null : category.name();
            spec.item(tabSlots[i], MenuItems.tab(category.iconModel(), "Show " + category.title(), Achievement.byCategory(category).size(),
                category == filter, click -> openAchievements(player, next, 0)));
        }
        spec.pagination(page, click -> openAchievements(player, holder.view(), page.previous()), click -> openAchievements(player, holder.view(), page.next()));
        plugin.guiMenu().show(player, holder, spec);
    }

    public void openMailbox(Player player) {
        openMailbox(player, 0);
    }

    public void openMailbox(Player player, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        List<OfflineMail> mails = plugin.socialService().mails(profile);
        MenuSpec.Page<OfflineMail> page = MenuSpec.Page.of(mails, requestedPage, MenuSpec.GRID.length);
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.MAILBOX);
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_MAILBOX, GuiMenuManager.tr(player, "gui.title.inbox", "INBOX")));
        holder.setPage(page.index());
        MenuSpec.Builder spec = MenuSpec.builder(6, "Mailbox");
        int unread = plugin.socialService().unreadCount(profile);
        spec.item(4, MenuItems.info("icon_message", "Mailbox", List.of(MenuItems.row("Unread", unread), MenuItems.row("Messages", mails.size()))));
        long now = System.currentTimeMillis();
        for (int i = 0; i < page.entries().size(); i++) {
            OfflineMail mail = page.entries().get(i);
            int index = page.start() + i;
            List<String> lore = new ArrayList<>(mail.body());
            lore.add(MenuItems.row("Kind", MenuItems.plain(mail.kind().smallCapsTitle())));
            lore.add(MenuItems.row("Age", mail.ageLabel(now)));
            lore.add(MenuItems.muted(mail.hasAction() ? "Click to act." : mail.read() ? "Already read." : "Click to mark as read."));
            spec.item(MenuSpec.GRID[i], MenuSpec.Item.action(mail.kind().iconModel(), MenuItems.button("Read " + mail.subject()), lore, click -> {
                plugin.socialService().markRead(profile, index);
                if (mail.hasAction() && mail.actionCommand() != null && !mail.actionCommand().isBlank()) {
                    player.closeInventory();
                    player.performCommand(mail.actionCommand().startsWith("/") ? mail.actionCommand().substring(1) : mail.actionCommand());
                    return;
                }
                openMailbox(player, holder.page());
            }));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No offline messages are waiting.", "Open friends", click -> plugin.guiMenu().openFriendsRich(player)));
        }
        spec.item(47, unread == 0 ? MenuItems.disabled("ref_check", "Mark all read", "Nothing is unread.")
            : MenuItems.action("ref_check", "Mark all read", MenuItems.lore("Clear every unread badge."), click -> {
                plugin.socialService().markAllRead(profile);
                plugin.statsManager().saveProfileAsync(profile);
                openMailbox(player, holder.page());
            }));
        spec.item(50, mails.isEmpty() ? MenuItems.disabled("icon_delete", "Clear mailbox", "The mailbox is already empty.")
            : MenuItems.action("icon_delete", "Clear mailbox", MenuItems.lore("Delete every message. This cannot be undone."), click -> {
                plugin.socialService().clearMail(profile);
                plugin.statsManager().saveProfileAsync(profile);
                openMailbox(player, 0);
            }));
        spec.pagination(page, click -> openMailbox(player, page.previous()), click -> openMailbox(player, page.next()));
        plugin.guiMenu().show(player, holder, spec);
    }

    public void openReportsPanel(Player staff) {
        openReportsPanel(staff, 0);
    }

    public void openReportsPanel(Player staff, int requestedPage) {
        var reports = plugin.reportService().openReports();
        MenuSpec.Page<fr.bosketball.moderation.ReportService.Report> page = MenuSpec.Page.of(reports, requestedPage, 21);
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.REPORTS);
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_REPORTS, GuiMenuManager.tr(staff, "gui.title.reports", "REPORTS")));
        holder.setPage(page.index());
        MenuSpec.Builder spec = MenuSpec.builder(6, "Reports");
        var sanctions = plugin.reportService().recentSanctions(8);
        List<String> log = new ArrayList<>();
        log.add(MenuItems.row("Open reports", reports.size()));
        log.add(MenuItems.row("Recent sanctions", sanctions.size()));
        for (int i = 0; i < Math.min(3, sanctions.size()); i++) {
            var sanction = sanctions.get(i);
            log.add(MenuItems.muted(sanction.playerName() + " " + MenuItems.SEPARATOR + " " + sanction.action()));
        }
        if (sanctions.isEmpty()) log.add(MenuItems.muted("No sanctions logged yet."));
        spec.item(4, MenuItems.info("ref_alert", "Staff reports", log));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        long now = System.currentTimeMillis();
        for (int i = 0; i < page.entries().size(); i++) {
            var report = page.entries().get(i);
            spec.item(slots[i], MenuItems.row("ref_alert", "Report #" + report.id(), report.reporterName() + " reported " + report.reportedName(),
                report.reason() + " " + MenuItems.SEPARATOR + " " + report.ageLabel(now) + " ago", click -> {
                    plugin.reportService().resolve(report, staff.getName(), click.right() ? "DISMISSED" : "RESOLVED");
                    Msg.send(staff, Msg.success("Report #" + report.id() + (click.right() ? " dismissed." : " resolved.")));
                    openReportsPanel(staff, holder.page());
                }));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No open reports need review.", "View warnings", click -> openWarningsPanel(staff, null, 0)));
        }
        spec.item(40, MenuItems.action("ref_clock", "View moderation log", MenuItems.lore("Recent sanctions: " + sanctions.size()),
            click -> Msg.send(staff, Msg.info(sanctions.isEmpty() ? "No sanctions logged yet." : "Recent sanctions: " + sanctions.size()))));
        spec.pagination(page, click -> openReportsPanel(staff, page.previous()), click -> openReportsPanel(staff, page.next()));
        plugin.guiMenu().show(staff, holder, spec);
    }

    public void openWarningsPanel(Player staff, UUID focus) {
        openWarningsPanel(staff, focus, 0);
    }

    public void openWarningsPanel(Player staff, UUID focus, int requestedPage) {
        var warns = plugin.warnService();
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.WARNINGS);
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_WARNINGS, GuiMenuManager.tr(staff, "gui.title.warnings", "WARNINGS")));
        holder.setTargetPlayerId(focus);
        MenuSpec.Builder spec = MenuSpec.builder(6, "Warnings");
        long now = System.currentTimeMillis();
        if (focus == null) {
            List<UUID> warned = warns.warnedPlayers();
            MenuSpec.Page<UUID> page = MenuSpec.Page.of(warned, requestedPage, 21);
            holder.setPage(page.index());
            spec.item(4, MenuItems.info("icon_star_shield", "Warnings", List.of(
                MenuItems.row("Warned players", warned.size()), MenuItems.row("Total warnings", warns.totalWarnings()))));
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < page.entries().size(); i++) {
                UUID id = page.entries().get(i);
                var list = warns.of(id);
                String detail = list.isEmpty() ? "No warnings" : list.get(0).reason() + " " + MenuItems.SEPARATOR + " " + list.get(0).ageLabel(now) + " ago";
                spec.item(slots[i], MenuItems.row("icon_star_shield", warns.nameOf(id), list.size() + " warnings", detail,
                    click -> openWarningsPanel(staff, id, 0)));
            }
            if (page.empty()) {
                spec.item(22, MenuItems.empty("No player currently has a warning.", "View reports", click -> openReportsPanel(staff, 0)));
            }
            spec.pagination(page, click -> openWarningsPanel(staff, null, page.previous()), click -> openWarningsPanel(staff, null, page.next()));
        } else {
            var list = warns.of(focus);
            MenuSpec.Page<fr.bosketball.moderation.WarnService.Warn> page = MenuSpec.Page.of(list, requestedPage, 21);
            holder.setPage(page.index());
            String name = warns.nameOf(focus);
            spec.item(4, MenuItems.info("icon_star_shield", name, MenuItems.lore("Warnings: " + list.size())));
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < page.entries().size(); i++) {
                var warn = page.entries().get(i);
                spec.item(slots[i], MenuItems.row("icon_star_shield", "Warning #" + (list.size() - page.start() - i), warn.reason(),
                    warn.staffName() + " " + MenuItems.SEPARATOR + " " + warn.ageLabel(now) + " ago", click -> {
                        boolean removed = warns.unwarn(focus, warn.id());
                        Msg.send(staff, removed ? Msg.success("Warning removed.") : Msg.warn("That warning no longer exists."));
                        openWarningsPanel(staff, warns.count(focus) > 0 ? focus : null, 0);
                    }));
            }
            if (page.empty()) {
                spec.item(22, MenuItems.empty(name + " has no warning.", "View all warned players", click -> openWarningsPanel(staff, null, 0)));
            }
            spec.item(40, list.isEmpty() ? MenuItems.disabled("ref_arrow_left", "Remove newest warning", "No warning is left.")
                : MenuItems.action("ref_arrow_left", "Remove newest warning", MenuItems.lore("Remove only the latest warning."), click -> {
                    boolean removed = warns.unwarnLast(focus);
                    Msg.send(staff, removed ? Msg.success("Newest warning removed.") : Msg.warn("No warning left to remove."));
                    openWarningsPanel(staff, warns.count(focus) > 0 ? focus : null, 0);
                }));
            spec.item(41, list.isEmpty() ? MenuItems.disabled("icon_delete", "Clear warnings", "No warning is left.")
                : MenuItems.action("icon_delete", "Clear all warnings", MenuItems.lore("Remove every warning for " + name + "."), click -> {
                    int removed = warns.clear(focus);
                    Msg.send(staff, Msg.success("Removed " + removed + " warnings from " + name + "."));
                    openWarningsPanel(staff, null, 0);
                }));
            spec.item(47, MenuItems.action("ref_friends", "View all warned players", MenuItems.lore("Return to the overview."),
                click -> openWarningsPanel(staff, null, 0)));
            spec.pagination(page, click -> openWarningsPanel(staff, focus, page.previous()), click -> openWarningsPanel(staff, focus, page.next()));
        }
        plugin.guiMenu().show(staff, holder, spec);
    }

    public void openFriends(Player player) {
        openFriends(player, 0);
    }

    public void openFriends(Player player, int requestedPage) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(other -> other.getUniqueId().equals(player.getUniqueId()));
        online.sort((a, b) -> {
            int sa = plugin.socialService().statusOf(a.getUniqueId()).ordinal();
            int sb = plugin.socialService().statusOf(b.getUniqueId()).ordinal();
            return sa != sb ? Integer.compare(sa, sb) : a.getName().compareToIgnoreCase(b.getName());
        });
        MenuSpec.Page<Player> page = MenuSpec.Page.of(online, requestedPage, MenuSpec.GRID.length);
        GuiMenuManager.BosketHolder holder = new GuiMenuManager.BosketHolder(GuiMenuManager.MenuType.FRIENDS_RICH);
        holder.setTitle(Glyphs.customGuiOverlayTitle(Glyphs.GUI_OVERLAY_FRIENDS, GuiMenuManager.tr(player, "gui.title.friends", "FRIENDS")));
        holder.setPage(page.index());
        MenuSpec.Builder spec = MenuSpec.builder(6, "Friends");
        spec.item(4, MenuItems.info("ref_friends", "Friends and party", List.of(
            MenuItems.row("Friends online", plugin.socialManager().countOnlineFriends(player.getUniqueId())),
            MenuItems.row("Party size", plugin.socialManager().partySizeOrOne(player.getUniqueId())))));
        for (int i = 0; i < page.entries().size(); i++) {
            Player other = page.entries().get(i);
            var status = plugin.socialService().statusOf(other.getUniqueId());
            boolean friend = profile.isFriend(other.getUniqueId());
            String subtitle = MenuItems.plain(status.smallCapsTitle()) + " " + MenuItems.SEPARATOR + " " + (friend ? "Friend" : "Not a friend");
            MenuSpec.Item row = MenuItems.row(other.getName(), subtitle, friend ? "Click to interact; shift-click to invite; right-click to remove." : "Click to send a friend request.", click -> {
                if (!friend) {
                    plugin.socialManager().sendOrAcceptFriendRequest(player, other);
                } else if (click.shift()) {
                    if (plugin.gameManager().isInMatch(player.getUniqueId())) {
                        Msg.send(player, Msg.warn("You cannot change your party while in a match."));
                    } else {
                        plugin.socialManager().inviteToParty(player, other);
                    }
                } else if (click.right()) {
                    plugin.socialManager().removeFriend(player, other.getUniqueId(), other.getName());
                } else {
                    plugin.guiMenu().openPlayerInteractMenu(player, other);
                    return;
                }
                openFriends(player, holder.page());
            });
            spec.item(MenuSpec.GRID[i], MenuItems.head(plugin.guiMenu().headIcon(other.getUniqueId(), row.name(), row.lore()), row));
        }
        if (page.empty()) {
            spec.item(22, MenuItems.empty("No other player is online right now.", "Add a friend", click -> plugin.guiMenu().openAddFriendOnlineMenu(player)));
        }
        spec.item(40, MenuItems.action("icon_party_invites", "Invite to party", MenuItems.lore("Browse online players."),
            click -> plugin.guiMenu().openPartyInviteMenu(player)));
        spec.item(41, MenuItems.action("icon_message", "Open mailbox", MenuItems.lore("Unread: " + plugin.socialService().unreadCount(profile)),
            click -> openMailbox(player, 0)));
        spec.pagination(page, click -> openFriends(player, page.previous()), click -> openFriends(player, page.next()));
        plugin.guiMenu().show(player, holder, spec);
    }

    private static String pipString(double ratio) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < filled ? '▪' : '·');
        return Palette.of(ratio >= 1.0 ? Palette.SUCCESS : Palette.GOLD, sb.toString());
    }

    private static String formatDuration(long millis) {
        long sec = millis / 1000L;
        if (sec < 60) return sec + "s";
        long min = sec / 60;
        if (min < 60) return min + "m";
        return (min / 60) + "h " + (min % 60) + "m";
    }

    public static String duration(long millis) {
        return formatDuration(millis);
    }
}
