package fr.bosketball.game.bot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Match-scoped line selection: a player never repeats and a possession never echoes. */
public final class BotChatter {
    public enum Moment { SHOT, DUNK, DEFENSE }

    private final Map<String, String> lastLine = new HashMap<>();
    private final Set<String> usedThisPossession = new HashSet<>();
    private int teamCooldown;

    public void tick() {
        if (teamCooldown > 0) teamCooldown--;
    }

    public boolean ready() {
        return teamCooldown == 0;
    }

    public void spoken() {
        teamCooldown = 100;
    }

    public void newPossession() {
        usedThisPossession.clear();
    }

    public String choose(String player, BotDifficulty difficulty, Moment moment) {
        String[] lines = lines(difficulty, moment);
        int start = Math.floorMod(player.hashCode(), lines.length);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[(start + i) % lines.length];
            if (!line.equals(lastLine.get(player)) && !usedThisPossession.contains(line)) {
                lastLine.put(player, line);
                usedThisPossession.add(line);
                return line;
            }
        }
        return null;
    }

    private static String[] lines(BotDifficulty difficulty, Moment moment) {
        return switch (difficulty) {
            case EASY -> switch (moment) {
                case SHOT -> new String[]{"Here goes!", "Take that!", "Let it fly!", "My turn!", "Got room!"};
                case DUNK -> new String[]{"Coming through!", "Up we go!", "All the way!", "Look out!", "To the rim!"};
                case DEFENSE -> new String[]{"I'm on you!", "Stay here!", "Hands up!", "Right here!", "Not yet!"};
            };
            case NORMAL -> switch (moment) {
                case SHOT -> new String[]{"That's mine!", "Good look!", "Let it go!", "I'm up!", "Got space!"};
                case DUNK -> new String[]{"Clear the lane!", "Going up!", "Take the lane!", "Strong finish!", "Make room!"};
                case DEFENSE -> new String[]{"Got you!", "No lane!", "Stay home!", "My ball!", "Hold up!"};
            };
            case PRO -> switch (moment) {
                case SHOT -> new String[]{"Too much room.", "That's open.", "I'll take it.", "Right there.", "Good space."};
                case DUNK -> new String[]{"That's the lane.", "Coming inside.", "Meet me there.", "All the way.", "Clear out."};
                case DEFENSE -> new String[]{"Nothing easy.", "No way through.", "That's mine.", "Stay in front.", "I see you."};
            };
        };
    }
}
