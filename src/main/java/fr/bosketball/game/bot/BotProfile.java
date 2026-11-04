package fr.bosketball.game.bot;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Identity of one AI player: nickname, jersey number, difficulty and mannequin skin.
 *
 * <p>The roster is inspired by real pickup-court archetypes so a lobby full of bots reads
 * like a lobby full of humans: the scrappy point guard, the spot-up shooter, the big man.
 * Each profile carries the small behavioural deltas (tendency to shoot vs pass) that make
 * two bots on the same difficulty still feel different.</p>
 */
public final class BotProfile {

    /** Immutable roster template. */
    public record Template(
        String nickname,
        int jerseyNumber,
        String skinTexture,
        String archetype,
        double shootBias,
        double passBias,
        double driveBias
    ) {
    }

    private static final List<Template> ROSTER = List.of(
        new Template("Splash", 30, "bot_normal", "Spot-Up Sniper", 1.22, 0.72, 0.70),
        new Template("Reaper", 24, "bot_pro", "Isolation Scorer", 1.15, 0.62, 1.05),
        new Template("Crossover", 11, "bot_easy", "Ankle-Breaker", 0.85, 0.95, 1.30),
        new Template("Wall", 3, "bot_pro", "Lockdown Defender", 0.78, 1.15, 0.85),
        new Template("Skyhook", 33, "bot_normal", "Post Operator", 1.05, 0.80, 0.90),
        new Template("Flash", 7, "bot_easy", "Fast-Break Guard", 0.92, 1.28, 1.15),
        new Template("Iceman", 1, "bot_pro", "Clutch Shooter", 1.30, 0.70, 0.62),
        new Template("Bulldozer", 44, "bot_normal", "Slashing Forward", 0.90, 0.75, 1.35),
        new Template("Glove", 9, "bot_easy", "Pest Defender", 0.72, 1.10, 0.95),
        new Template("Fadeaway", 21, "bot_pro", "Mid-Range Artist", 1.18, 0.85, 0.78),
        new Template("Tempo", 5, "bot_normal", "Floor General", 0.82, 1.40, 0.88),
        new Template("Rimwrecker", 55, "bot_easy", "Above-The-Rim Finisher", 0.74, 0.68, 1.45)
    );

    private final Template template;
    private final BotDifficulty difficulty;
    private final String skinTexture;

    private BotProfile(Template template, BotDifficulty difficulty) {
        this.template = template;
        this.difficulty = difficulty;
        this.skinTexture = template.skinTexture();
    }

    /** Picks a roster entry that does not collide with the names already on court. */
    public static BotProfile pick(BotDifficulty difficulty, java.util.Collection<String> takenNames) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Template> pool = ROSTER.stream()
            .filter(t -> takenNames == null
                || takenNames.stream().noneMatch(n -> n != null && n.equalsIgnoreCase(t.nickname())))
            .toList();
        Template chosen = pool.isEmpty()
            ? ROSTER.get(rng.nextInt(ROSTER.size()))
            : pool.get(rng.nextInt(pool.size()));
        return new BotProfile(chosen, difficulty == null ? BotDifficulty.NORMAL : difficulty);
    }

    public Template template() {
        return template;
    }

    public BotDifficulty difficulty() {
        return difficulty;
    }

    public String nickname() {
        return template.nickname();
    }

    public int jerseyNumber() {
        return template.jerseyNumber();
    }

    public String archetype() {
        return template.archetype();
    }

    /** Texture id under {@code bosketball:entity/player/wide/}. */
    public String skinTexture() {
        return skinTexture;
    }

    public double shootBias() {
        return template.shootBias();
    }

    public double passBias() {
        return template.passBias();
    }

    public double driveBias() {
        return template.driveBias();
    }

    /** Nameplate: jersey number + nickname + difficulty pill, exactly like a real player tag. */
    public Component nameplate() {
        return Component.text()
            .append(Component.text("#" + template.jerseyNumber(), Style.style(
                TextColor.color(difficulty.colorRgb()), TextDecoration.BOLD)))
            .append(Component.text(" " + template.nickname(),
                Style.style(TextColor.color(0xF1F5F9), TextDecoration.BOLD)))
            .asComponent();
    }

    /** Secondary line shown through the mannequin description slot. */
    public Component subtitle() {
        return Component.text()
            .append(Component.text(difficulty.smallCapsTitle() + " AI",
                Style.style(TextColor.color(difficulty.colorRgb()), TextDecoration.BOLD)))
            .append(Component.text(" • " + archetype(), Style.style(TextColor.color(0x94A3B8))))
            .asComponent();
    }

    /** A neutral profile for a generic teammate bot. */
    public static BotProfile teammate(BotDifficulty difficulty, String preferredName) {
        Template t = ROSTER.stream()
            .filter(x -> preferredName != null && x.nickname().equalsIgnoreCase(preferredName))
            .findFirst()
            .orElseGet(() -> ROSTER.stream()
                .filter(x -> x.passBias() >= 1.1)
                .findFirst()
                .orElse(ROSTER.getFirst()));
        return new BotProfile(t, difficulty == null ? BotDifficulty.NORMAL : difficulty);
    }
}
