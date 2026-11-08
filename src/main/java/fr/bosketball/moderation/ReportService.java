package fr.bosketball.moderation;

import fr.bosketball.util.Palette;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.util.Msg;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class ReportService {

    public static final class Report {
        private static final AtomicLong SEQ = new AtomicLong(1);

        private final long id;
        private final String reporterName;
        private final String reportedName;
        private final String reason;
        private final long createdAtMillis;
        private String status = "OPEN";
        private String handledBy;

        public Report(String reporterName, String reportedName, String reason) {
            this.id = SEQ.getAndIncrement();
            this.reporterName = reporterName;
            this.reportedName = reportedName;
            this.reason = reason;
            this.createdAtMillis = System.currentTimeMillis();
        }

        public long id() {
            return id;
        }

        public String reporterName() {
            return reporterName;
        }

        public String reportedName() {
            return reportedName;
        }

        public String reason() {
            return reason;
        }

        public long createdAtMillis() {
            return createdAtMillis;
        }

        public String status() {
            return status;
        }

        public void setStatus(String status, String handledBy) {
            this.status = status;
            this.handledBy = handledBy;
        }

        public String handledBy() {
            return handledBy;
        }

        public String ageLabel(long now) {
            long min = Math.max(0L, now - createdAtMillis) / 60_000L;
            return min < 60 ? min + "m" : (min / 60) + "h";
        }
    }

    public record Sanction(String playerName, String action, String reason, long durationMinutes, long atMillis, String staff) {
    }

    private final BosketBallPlugin plugin;
    private final List<Report> reports = new ArrayList<>();
    private final List<Sanction> sanctions = new ArrayList<>();
    private final Map<UUID, Long> lastReportMillis = new ConcurrentHashMap<>();
    private final File file;

    public ReportService(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
        load();
    }

    public Report fileReport(Player reporter, String reportedName, String reason) {
        long now = System.currentTimeMillis();
        Long last = lastReportMillis.get(reporter.getUniqueId());
        if (last != null && now - last < 30_000L) {
            Msg.send(reporter, Msg.warn("You already filed a report recently. Please wait a moment."));
            return null;
        }
        lastReportMillis.put(reporter.getUniqueId(), now);

        Report report = new Report(reporter.getName(), reportedName,
            reason == null || reason.isBlank() ? "No reason provided" : reason.trim());
        synchronized (reports) {
            reports.add(0, report);
            while (reports.size() > 200) {
                reports.remove(reports.size() - 1);
            }
        }
        saveAsync();
        broadcastToStaff(report);
        if (plugin.integration() != null) {
            plugin.integration().onReport(report.reporterName(), report.reportedName(), report.reason());
        }
        Msg.send(reporter, Msg.success("Report sent to the staff team. <gray>Thank you!</gray>"));
        return report;
    }

    private void broadcastToStaff(Report report) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.isOp() && !staff.hasPermission("bosketball.staff") && !staff.hasPermission("bosketball.admin")) {
                continue;
            }
            staff.sendMessage(Component.text()
                .append(Msg.parse("<" + Palette.DANGER + "><bold> REPORT #" + report.id() + "</bold></" + Palette.DANGER + "> "))
                .append(Msg.parse("<white>" + Msg.escapeMini(report.reporterName()) + "</white> <gray>→</gray> <white>"
                    + Msg.escapeMini(report.reportedName()) + "</white> <dark_gray>•</dark_gray> <gray>"
                    + Msg.escapeMini(report.reason()) + "</gray> "))
                .append(Component.text("[OPEN]", net.kyori.adventure.text.format.Style.style(
                        TextColor.color(0x38BDF8), TextDecoration.BOLD))
                    .hoverEvent(HoverEvent.showText(Msg.parse("<aqua>Open the staff reports panel</aqua>")))
                    .clickEvent(ClickEvent.runCommand("/bb reports")))
                .build());
        }
    }

    public void logSanction(String playerName, String action, String reason, long durationMinutes) {
        Sanction sanction = new Sanction(playerName, action, reason, durationMinutes,
            System.currentTimeMillis(), "SYSTEM");
        synchronized (sanctions) {
            sanctions.add(0, sanction);
            while (sanctions.size() > 300) {
                sanctions.remove(sanctions.size() - 1);
            }
        }
        saveAsync();
    }

    public void logSanctionBy(String staffName, String playerName, String action, String reason, long durationMinutes) {
        Sanction sanction = new Sanction(playerName, action, reason, durationMinutes,
            System.currentTimeMillis(), staffName);
        synchronized (sanctions) {
            sanctions.add(0, sanction);
            while (sanctions.size() > 300) {
                sanctions.remove(sanctions.size() - 1);
            }
        }
        saveAsync();
    }

    public List<Report> openReports() {
        synchronized (reports) {
            List<Report> out = new ArrayList<>();
            for (Report r : reports) {
                if ("OPEN".equals(r.status())) {
                    out.add(r);
                }
            }
            return out;
        }
    }

    public List<Sanction> recentSanctions(int limit) {
        synchronized (sanctions) {
            return new ArrayList<>(sanctions.subList(0, Math.min(limit, sanctions.size())));
        }
    }

    public void resolve(Report report, String staffName, String status) {
        if (report != null) {
            report.setStatus(status, staffName);
            saveAsync();
        }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNow);
    }

    public synchronized void saveNow() {
        YamlConfiguration yml = new YamlConfiguration();
        List<Map<String, Object>> reportMaps = new ArrayList<>();
        synchronized (reports) {
            for (Report r : reports) {
                reportMaps.add(Map.of(
                    "id", r.id(),
                    "reporter", r.reporterName(),
                    "reported", r.reportedName(),
                    "reason", r.reason(),
                    "at", r.createdAtMillis(),
                    "status", r.status(),
                    "handled-by", r.handledBy() == null ? "" : r.handledBy()
                ));
            }
        }
        yml.set("reports", reportMaps);
        List<Map<String, Object>> sanctionMaps = new ArrayList<>();
        synchronized (sanctions) {
            for (Sanction s : sanctions) {
                sanctionMaps.add(Map.of(
                    "player", s.playerName(),
                    "action", s.action(),
                    "reason", s.reason() == null ? "" : s.reason(),
                    "duration", s.durationMinutes(),
                    "at", s.atMillis(),
                    "staff", s.staff()
                ));
            }
        }
        yml.set("sanctions", sanctionMaps);
        AtomicYamlWriter.writeYamlAtomically(file,
            "# BosketBall — Player reports & moderation log\n", yml, plugin.getLogger());
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = AtomicYamlWriter.loadSafe(file, plugin.getLogger());
        ConfigurationSection reportSection = yml.getConfigurationSection("reports");
        if (reportSection != null) {
            List<Map<?, ?>> list = yml.getMapList("reports");
            synchronized (reports) {
                for (Map<?, ?> map : list) {
                    Report r = new Report(
                        String.valueOf(map.get("reporter")),
                        String.valueOf(map.get("reported")),
                        String.valueOf(map.get("reason"))
                    );
                    Object status = map.get("status");
                    if (status != null) {
                        r.setStatus(String.valueOf(status), String.valueOf(map.get("handled-by")));
                    }
                    reports.add(r);
                }
            }
        }
    }
}
