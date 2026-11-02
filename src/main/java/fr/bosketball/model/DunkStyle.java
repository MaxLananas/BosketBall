package fr.bosketball.model;

import org.bukkit.util.Vector;

/**
 * 5 Signature Slam Dunk Animations (unlockable with Coins).
 */
public enum DunkStyle {
    TOMAHAWK(
        "Tomahawk",
        "Cock the ball behind the head and slam it home",
        0
    ),
    WINDMILL(
        "Windmill",
        "Full vertical circular arm sweep before rim impact",
        500
    ),
    SPIN_360(
        "360° Spin",
        "Full mid-air 360° spin finish at the rim",
        850
    ),
    REVERSE_BASELINE(
        "Reverse Slam",
        "Acrobatic reverse finish underneath the basket",
        1200
    ),
    METEOR_JAM(
        "Meteor Jam",
        "Explosive high-altitude poster slam",
        1750
    );

    private final String displayName;
    private final String description;
    private final int priceCoins;

    DunkStyle(String displayName, String description, int priceCoins) {
        this.displayName = displayName;
        this.description = description;
        this.priceCoins = priceCoins;
    }

    public String displayName() {
        return displayName;
    }

    public String displayNameMm() {
        return "<#FBBF24><bold>" + displayName + "</bold></#FBBF24>";
    }

    public String descriptionSmallCaps() {
        return description;
    }

    public int defaultPriceCoins() {
        return priceCoins;
    }

    public int priceCoins() {
        return fr.bosketball.config.ShopConfig.priceOf(this, priceCoins);
    }

    public int coinPrice() {
        return priceCoins();
    }

    public int unlockElo() {
        return 0;
    }

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
                double cock = Math.sin(clamped * Math.PI);
                forward = (clamped < 0.55) ? (-0.35 * cock) : (0.75 * ((clamped - 0.55) / 0.45));
                side = 0.22 * (1.0 - clamped);
                up = 1.65 + 0.65 * Math.sin(clamped * Math.PI * 0.85);
            }
            case WINDMILL -> {
                double angle = clamped * Math.PI * 2.0 - Math.PI * 0.5;
                forward = 0.52 * Math.cos(angle) + 0.25 * clamped;
                side = 0.35 * (1.0 - 0.6 * clamped);
                up = 1.35 + 0.68 * Math.sin(angle) + 0.35 * clamped;
            }
            case SPIN_360 -> {
                double orbit = clamped * Math.PI * 2.0;
                forward = 0.55 * Math.cos(orbit);
                side = 0.55 * Math.sin(orbit);
                up = 1.30 + 0.85 * Math.sin(clamped * Math.PI * 0.75);
            }
            case REVERSE_BASELINE -> {
                forward = -0.45 * Math.sin(clamped * Math.PI * 0.8) + 0.55 * Math.pow(clamped, 3);
                side = -0.32 * Math.sin(clamped * Math.PI);
                up = 1.25 + 0.90 * Math.sin(clamped * Math.PI * 0.82);
            }
            case METEOR_JAM -> {
                double rise = Math.sin(clamped * Math.PI * 0.68);
                forward = -0.25 * (1.0 - clamped) + 0.85 * Math.pow(clamped, 2.4);
                side = 0.0;
                up = 1.75 + 0.95 * rise;
            }
            default -> {
                forward = 0.4;
                side = 0.0;
                up = 1.6;
            }
        }

        double wx = fx * forward + rx * side;
        double wz = fz * forward + rz * side;
        return new Vector(wx, up, wz);
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
