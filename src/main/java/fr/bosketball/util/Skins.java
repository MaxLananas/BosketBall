package fr.bosketball.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

/**
 * Patched skins used by lobby NPCs and bots (the resource pack ships the textures under
 * {@code bosketball:entity/player/wide/…}). Extracted from the removed 3D locker room so the
 * holograms and defender bots keep their custom look.
 */
public final class Skins {

    private Skins() {
    }

    /** A stable, offline-safe profile wearing one of the pack's own wide skins. */
    public static ResolvableProfile buildCustomPatchedSkin(String fallbackName, String skinTextureId) {
        String safeName = fallbackName == null || fallbackName.isBlank()
            ? "BB_NPC"
            : (fallbackName.length() > 16 ? fallbackName.substring(0, 16) : fallbackName);
        String texture = skinTextureId == null || skinTextureId.isBlank() ? "defender_bot" : skinTextureId;
        UUID staticUuid = UUID.nameUUIDFromBytes(
            ("bosketball_npc:" + safeName + ":" + texture).getBytes(StandardCharsets.UTF_8));
        try {
            return ResolvableProfile.resolvableProfile()
                .uuid(staticUuid)
                .name(safeName)
                .skinPatch(sp -> sp
                    .body(Key.key("bosketball", "entity/player/wide/" + texture))
                    .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC))
                .build();
        } catch (LinkageError | RuntimeException ignored) {
            return ResolvableProfile.resolvableProfile().uuid(staticUuid).name(safeName).build();
        }
    }
}
