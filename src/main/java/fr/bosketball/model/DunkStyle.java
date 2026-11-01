package fr.bosketball.model;

import org.bukkit.util.Vector;

/**
 * 5 Signature 3D Slam Dunk Flight Animations!
 * Computes real-time 3D local ball offset relative to the dunking player at normalized flight progress t in [0..1].
 */
public enum DunkStyle {
    TOMAHAWK(
        "⚡ ᴛᴏᴍᴀʜᴀᴡᴋ ᴘᴏᴡᴇʀ",
        "ᴀʀᴍᴇ ʟᴇ ʙᴀʟʟᴏɴ ᴅᴇʀʀɪᴇʀᴇ ʟᴀ ɴᴜǫᴜᴇ ᴇᴛ ꜰʀᴀᴘᴘᴇ ʟ'ᴀɴɴᴇᴀᴜ",
        0
    ),
    WINDMILL(
        "🌀 ᴡɪɴᴅᴍɪʟʟ 360",
        "ᴍᴏᴜʟɪɴᴇᴛ ᴄᴏᴍᴘʟᴇᴛ ᴠᴇʀᴛɪᴄᴀʟ ᴀᴠᴀɴᴛ ʟ'ɪᴍᴘᴀᴄᴛ",
        1100
    ),
    SPIN_360(
        "💫 ʜᴇʟɪᴄᴏᴘᴛᴇʀᴇ 360°",
        "ʀᴏᴛᴀᴛɪᴏɴ ᴄᴏᴍᴘʟᴇᴛᴇ ᴇɴ ʟ'ᴀɪʀ ᴀᴠᴇᴄ ᴛʀᴀɪɴᴇᴇ ᴄʏᴀɴ",
        1300
    ),
    REVERSE_BASELINE(
        "🌙 ʀᴇᴠᴇʀꜱᴇ ᴀᴄʀᴏʙᴀᴛɪǫᴜᴇ",
        "ᴅᴜɴᴋ ɪɴᴠᴇʀꜱᴇ ᴅᴏꜱ ᴀᴜ ᴘᴀɴɪᴇʀ ᴇɴ ꜰɪɴ ᴅᴇ ᴄᴏᴜʀꜱᴇ",
        1550
    ),
    METEOR_JAM(
        "☄ ᴍᴇᴛᴇᴏʀ ᴘᴏꜱᴛᴇʀɪᴢᴇʀ",
        "ꜱᴀᴜᴛ ꜱᴛʀᴀᴛᴏꜱᴘʜᴇʀɪǫᴜᴇ ᴇɴꜰʟᴀᴍᴍᴇ + ᴏɴᴅᴇ ᴅᴇ ᴄʜᴏᴄ",
        1800
    );

    private final String displayName;
    private final String descriptionSmallCaps;
    private final int unlockElo;

    DunkStyle(String displayName, String descriptionSmallCaps, int unlockElo) {
        this.displayName = displayName;
        this.descriptionSmallCaps = descriptionSmallCaps;
        this.unlockElo = unlockElo;
    }

    public String displayName() {
        return displayName;
    }

    public String descriptionSmallCaps() {
        return descriptionSmallCaps;
    }

    public int unlockElo() {
        return unlockElo;
    }

    /**
     * Computes world-space offset from player's waist/chest to the 3D basketball at dunk progress t in [0..1].
     */
    public Vector computeBallOffset(double t, double forwardYawRad) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double fx = -Math.sin(forwardYawRad);
        double fz = Math.cos(forwardYawRad);
        double rx = Math.cos(forwardYawRad);
        double rz = Math.sin(forwardYawRad);

        double forward;
        double side;
        double up;

        switch (this) {
            case TOMAHAWK -> {
                // Cocked far behind head at t=0.45, slammed violently forward at t=1.0
                double cock = Math.sin(clamped * Math.PI);
                forward = (clamped < 0.55) ? (-0.35 * cock) : (0.75 * ((clamped - 0.55) / 0.45));
                side = 0.22 * (1.0 - clamped);
                up = 1.65 + 0.65 * Math.sin(clamped * Math.PI * 0.85);
            }
            case WINDMILL -> {
                // Full vertical circular sweep along right side of body
                double angle = clamped * Math.PI * 2.0 - Math.PI * 0.5;
                forward = 0.52 * Math.cos(angle) + 0.25 * clamped;
                side = 0.35 * (1.0 - 0.6 * clamped);
                up = 1.35 + 0.68 * Math.sin(angle) + 0.35 * clamped;
            }
            case SPIN_360 -> {
                double orbit = clamped * Math.PI * 2.0;
                forward = 0.55 * Math.cos(orbit);
                side = 0.55 * Math.sin(orbit);
                up = 1.25 + 0.85 * clamped;
            }
            case REVERSE_BASELINE -> {
                forward = -0.35 * Math.sin(clamped * Math.PI * 0.7) + 0.45 * clamped;
                side = -0.28 * Math.sin(clamped * Math.PI);
                up = 1.30 + 0.90 * Math.sin(clamped * Math.PI * 0.65);
            }
            case METEOR_JAM -> {
                // Two-hand high overhead meteor slam
                forward = -0.20 * (1.0 - clamped) + 0.70 * Math.pow(clamped, 2.2);
                side = 0.0;
                up = 1.85 + 0.85 * Math.sin(clamped * Math.PI * 0.75);
            }
            default -> {
                forward = 0.5;
                side = 0.2;
                up = 1.6;
            }
        }

        return new Vector(
            fx * forward + rx * side,
            up,
            fz * forward + rz * side
        );
    }

    public static DunkStyle byNameOrDefault(String raw, DunkStyle fallback) {
        if (raw == null) {
            return fallback;
        }
        for (DunkStyle style : values()) {
            if (style.name().equalsIgnoreCase(raw.trim())) {
                return style;
            }
        }
        return fallback;
    }
}
