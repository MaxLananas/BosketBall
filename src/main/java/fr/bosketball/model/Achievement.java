package fr.bosketball.model;

import java.util.ArrayList;
import java.util.List;

public enum Achievement {

    FIRST_BUCKET("First Bucket", "Score your very first basket.", AchievementCategory.SCORING, ProgressStat.POINTS_SCORED, 2, 50, "icon_crossover"),
    BUCKET_50("Bucket Getter", "Score 50 total points.", AchievementCategory.SCORING, ProgressStat.POINTS_SCORED, 50, 150, "icon_crossover"),
    BUCKET_250("Walking Bucket", "Score 250 total points.", AchievementCategory.SCORING, ProgressStat.POINTS_SCORED, 250, 350, "icon_target"),
    BUCKET_1000("Bucket Machine", "Score 1,000 total points.", AchievementCategory.SCORING, ProgressStat.POINTS_SCORED, 1000, 900, "icon_medal_gold"),
    BUCKET_5000("Bucket Legend", "Score 5,000 total points.", AchievementCategory.SCORING, ProgressStat.POINTS_SCORED, 5000, 2500, "icon_trophy_gold"),
    TRIPLE_10("Downtown Debut", "Make 10 three-pointers.", AchievementCategory.SCORING, ProgressStat.THREE_POINTERS, 10, 120, "icon_target"),
    TRIPLE_100("Sniper", "Make 100 three-pointers.", AchievementCategory.SCORING, ProgressStat.THREE_POINTERS, 100, 500, "icon_target"),
    TRIPLE_500("Splash Brother", "Make 500 three-pointers.", AchievementCategory.SCORING, ProgressStat.THREE_POINTERS, 500, 1800, "icon_star_shield"),
    DEUCE_500("Midrange Merchant", "Make 500 two-pointers.", AchievementCategory.SCORING, ProgressStat.TWO_POINTERS, 500, 1200, "icon_crossover"),

    FIRST_DUNK("Poster Boy", "Land your first slam dunk.", AchievementCategory.DUNKS, ProgressStat.DUNKS, 1, 100, "icon_dunk"),
    DUNK_25("Rim Rattler", "Land 25 slam dunks.", AchievementCategory.DUNKS, ProgressStat.DUNKS, 25, 250, "icon_dunk"),
    DUNK_100("Human Highlight Reel", "Land 100 slam dunks.", AchievementCategory.DUNKS, ProgressStat.DUNKS, 100, 750, "icon_dunk"),
    DUNK_500("Meteor Rain", "Land 500 slam dunks.", AchievementCategory.DUNKS, ProgressStat.DUNKS, 500, 2200, "icon_fire"),

    FIRST_DIME("Dime Dropper", "Record your first assist.", AchievementCategory.PLAYMAKING, ProgressStat.ASSISTS, 1, 50, "icon_pass"),
    DIME_50("Table Setter", "Record 50 assists.", AchievementCategory.PLAYMAKING, ProgressStat.ASSISTS, 50, 200, "icon_pass"),
    DIME_250("Floor General", "Record 250 assists.", AchievementCategory.PLAYMAKING, ProgressStat.ASSISTS, 250, 800, "icon_team"),
    REBOUND_100("Glass Cleaner", "Grab 100 rebounds.", AchievementCategory.PLAYMAKING, ProgressStat.REBOUNDS, 100, 400, "icon_team_duo"),

    FIRST_STEAL("Pickpocket", "Record your first steal.", AchievementCategory.DEFENSE, ProgressStat.STEALS, 1, 50, "icon_defense"),
    STEAL_50("Clamps", "Record 50 steals.", AchievementCategory.DEFENSE, ProgressStat.STEALS, 50, 220, "icon_defense"),
    STEAL_250("Lockdown Island", "Record 250 steals.", AchievementCategory.DEFENSE, ProgressStat.STEALS, 250, 900, "icon_star_shield"),
    FIRST_BLOCK("Swat Team", "Record your first block.", AchievementCategory.DEFENSE, ProgressStat.BLOCKS, 1, 60, "icon_defense"),
    BLOCK_50("Rim Protector", "Record 50 blocks.", AchievementCategory.DEFENSE, ProgressStat.BLOCKS, 50, 250, "icon_star_shield"),
    BLOCK_250("Forbidden Zone", "Record 250 blocks.", AchievementCategory.DEFENSE, ProgressStat.BLOCKS, 250, 1000, "icon_star_shield"),
    ANKLES_10("Ankle Collector", "Break 10 ankles with crossovers.", AchievementCategory.DEFENSE, ProgressStat.ANKLE_BREAKERS, 10, 180, "icon_crossover"),
    ANKLES_100("Ankles In A Jar", "Break 100 ankles with crossovers.", AchievementCategory.DEFENSE, ProgressStat.ANKLE_BREAKERS, 100, 700, "icon_crossover"),

    MATCH_1("First Tip-Off", "Complete your first match.", AchievementCategory.CAREER, ProgressStat.MATCHES_PLAYED, 1, 50, "icon_battle"),
    MATCH_25("Regular", "Complete 25 matches.", AchievementCategory.CAREER, ProgressStat.MATCHES_PLAYED, 25, 200, "icon_battle"),
    MATCH_100("Veteran", "Complete 100 matches.", AchievementCategory.CAREER, ProgressStat.MATCHES_PLAYED, 100, 600, "icon_medal_gold"),
    MATCH_500("Iron Man", "Complete 500 matches.", AchievementCategory.CAREER, ProgressStat.MATCHES_PLAYED, 500, 2000, "icon_trophy_gold"),
    WIN_1("First Win", "Win your first match.", AchievementCategory.CAREER, ProgressStat.MATCHES_WON, 1, 100, "icon_check"),
    WIN_10("Winning Habit", "Win 10 matches.", AchievementCategory.CAREER, ProgressStat.MATCHES_WON, 10, 250, "icon_check"),
    WIN_50("Serial Winner", "Win 50 matches.", AchievementCategory.CAREER, ProgressStat.MATCHES_WON, 50, 700, "icon_trophy_gold"),
    WIN_200("Dynasty", "Win 200 matches.", AchievementCategory.CAREER, ProgressStat.MATCHES_WON, 200, 2400, "icon_crown"),
    STREAK_3("Heating Up", "Win 3 matches in a row.", AchievementCategory.CAREER, ProgressStat.WIN_STREAK_BEST, 3, 150, "icon_fire"),
    STREAK_8("On Fire", "Win 8 matches in a row.", AchievementCategory.CAREER, ProgressStat.WIN_STREAK_BEST, 8, 500, "icon_fire"),
    STREAK_15("Unstoppable", "Win 15 matches in a row.", AchievementCategory.CAREER, ProgressStat.WIN_STREAK_BEST, 15, 1400, "icon_fire"),
    MVP_1("Man Of The Match", "Earn your first MVP award.", AchievementCategory.CAREER, ProgressStat.MVPS, 1, 200, "icon_trophy_gold"),
    MVP_10("MVP Collector", "Earn 10 MVP awards.", AchievementCategory.CAREER, ProgressStat.MVPS, 10, 600, "icon_trophy_gold"),
    MVP_50("MVP Dynasty", "Earn 50 MVP awards.", AchievementCategory.CAREER, ProgressStat.MVPS, 50, 2000, "icon_podium"),

    CASUAL_10("Casual Friday", "Play 10 casual matches.", AchievementCategory.CAREER, ProgressStat.CASUAL_MATCHES, 10, 180, "icon_training"),
    RANKED_25("Ranked Grinder", "Play 25 ranked matches.", AchievementCategory.CAREER, ProgressStat.RANKED_MATCHES, 25, 300, "icon_arena"),
    CONTEST_1("Sharpshooter", "Complete a 3-Point Contest.", AchievementCategory.SCORING, ProgressStat.THREE_POINT_CONTESTS, 1, 100, "icon_target"),
    CONTEST_20("Contest Champion", "Score 20+ in a 3-Point Contest.", AchievementCategory.SCORING, ProgressStat.THREE_POINT_CONTEST_SCORE, 20, 400, "icon_medal_gold"),

    COINS_1000("Pocket Change", "Earn 1,000 Coins in total.", AchievementCategory.ECONOMY, ProgressStat.COINS_EARNED, 1000, 150, "icon_coin_stack"),
    COINS_10000("Big Spender", "Earn 10,000 Coins in total.", AchievementCategory.ECONOMY, ProgressStat.COINS_EARNED, 10000, 800, "icon_coin_stack"),
    COINS_50000("Arena Mogul", "Earn 50,000 Coins in total.", AchievementCategory.ECONOMY, ProgressStat.COINS_EARNED, 50000, 2500, "icon_gem_ruby"),
    CASES_10("Crate Opener", "Open 10 Crates in the lobby.", AchievementCategory.ECONOMY, ProgressStat.QUEUE_JOINS, 10, 200, "icon_chest"),

    FRIEND_1("Not Alone", "Add your first friend.", AchievementCategory.SOCIAL, ProgressStat.FRIENDS_ADDED, 1, 80, "icon_friend_add"),
    FRIEND_10("Squad Goals", "Have 10 friends.", AchievementCategory.SOCIAL, ProgressStat.FRIENDS_ADDED, 10, 350, "icon_friends"),
    PARTY_10("Chemistry", "Play 10 matches with a Party.", AchievementCategory.SOCIAL, ProgressStat.PARTY_MATCHES, 10, 400, "icon_party_invites"),
    TUTORIAL_DONE("Graduate", "Finish the BosketBall tutorial.", AchievementCategory.SOCIAL, ProgressStat.QUEUE_JOINS, 1, 75, "icon_book"),

    TOP_3("Podium Finish", "Reach the server Top 3 in ELO.", AchievementCategory.MASTERY, ProgressStat.MATCHES_WON, 25, 1200, "icon_podium"),
    CENTURION("Centurion", "Play 100 ranked matches.", AchievementCategory.MASTERY, ProgressStat.RANKED_MATCHES, 100, 1500, "icon_star_shield"),
    COMPLETIONIST("Completionist", "Play every competitive mode at least 10 times.", AchievementCategory.MASTERY, ProgressStat.MATCHES_PLAYED, 250, 3000, "icon_podium");

    private final String displayName;
    private final String description;
    private final AchievementCategory category;
    private final ProgressStat stat;
    private final int target;
    private final int rewardCoins;
    private final String iconModel;

    Achievement(
        String displayName,
        String description,
        AchievementCategory category,
        ProgressStat stat,
        int target,
        int rewardCoins,
        String iconModel
    ) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.stat = stat;
        this.target = target;
        this.rewardCoins = rewardCoins;
        this.iconModel = iconModel;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public AchievementCategory category() {
        return category;
    }

    public ProgressStat stat() {
        return stat;
    }

    public int target() {
        return target;
    }

    public int rewardCoins() {
        return rewardCoins;
    }

    public String iconModel() {
        return iconModel;
    }

    public double completionRatio(long currentValue) {
        return target <= 0 ? 1.0 : Math.min(1.0, (double) currentValue / (double) target);
    }

    public boolean isUnlockedBy(long currentValue) {
        return currentValue >= target;
    }

    public static List<Achievement> byCategory(AchievementCategory category) {
        List<Achievement> out = new ArrayList<>();
        for (Achievement a : values()) {
            if (a.category == category) {
                out.add(a);
            }
        }
        return out;
    }

    public static Achievement byNameOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        for (Achievement a : values()) {
            if (a.name().equalsIgnoreCase(raw.trim())) {
                return a;
            }
        }
        return null;
    }

    public enum AchievementCategory {
        SCORING("ꜱᴄᴏʀɪɴɢ", "Scoring", "icon_target"),
        DUNKS("ᴅᴜɴᴋꜱ", "Dunks", "icon_dunk"),
        PLAYMAKING("ᴘʟᴀʏᴍᴀᴋɪɴɢ", "Playmaking", "icon_pass"),
        DEFENSE("ᴅᴇꜰᴇɴꜱᴇ", "Defense", "icon_defense"),
        CAREER("ᴄᴀʀᴇᴇʀ", "Career", "icon_battle"),
        ECONOMY("ᴇᴄᴏɴᴏᴍʏ", "Economy", "icon_coin_stack"),
        SOCIAL("ꜱᴏᴄɪᴀʟ", "Social", "icon_friends"),
        MASTERY("ᴍᴀꜱᴛᴇʀʏ", "Mastery", "icon_trophy_gold");

        private final String smallCapsTitle;
        private final String title;
        private final String iconModel;

        AchievementCategory(String smallCapsTitle, String title, String iconModel) {
            this.smallCapsTitle = smallCapsTitle;
            this.title = title;
            this.iconModel = iconModel;
        }

        public String smallCapsTitle() {
            return smallCapsTitle;
        }

        public String title() {
            return title;
        }

        public String iconModel() {
            return iconModel;
        }
    }
}
