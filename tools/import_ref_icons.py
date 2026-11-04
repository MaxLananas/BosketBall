#!/usr/bin/env python3
"""Import the curated reference icon sheet into the BosketBall pack.

The 207 sprites at the repository root are a classic 16x16 GUI icon pack (1px dark
outline, saturated flats, top-left highlight). They are excellent menu furniture and
this tool is the only supported way to bring them in:

  * every source must be a 16x16 PNG,
  * the texture lands in resourcepack/assets/bosketball/textures/item/icons/<name>.png,
  * a matching item model is generated in models/item/<name>.json, so
    GuiMenuManager.customIcon("<name>", ...) resolves without any extra wiring,
  * docs/ref-icons.md is rewritten as the catalogue.

Run: python3 tools/import_ref_icons.py
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RP = ROOT / "resourcepack" / "assets" / "bosketball"
TEX_DIR = RP / "textures" / "item" / "icons"
MODEL_DIR = RP / "models" / "item"
ITEM_DIR = RP / "items"
DOC = ROOT / "docs" / "ref-icons.md"

# source file (root sheet) -> pack icon name
ICONS = {
    "accept.png": "ref_check",
    "cancel.png": "ref_cross",
    "back.png": "ref_arrow_left",
    "next.png": "ref_arrow_right",
    "point_left.png": "ref_arrow_big_left",
    "point_right.png": "ref_arrow_big_right",
    "exit.png": "ref_exit",
    "leave.png": "ref_leave",
    "locked.png": "ref_lock",
    "locked_free.png": "ref_lock_open",
    "unlocked_week.png": "ref_calendar_check",
    "trophy.png": "ref_trophy",
    "altinkupa.png": "ref_cup_gold",
    "bronzkupa.png": "ref_cup_bronze",
    "gumuskupa.png": "ref_cup_silver",
    "altinmadalya.png": "ref_medal_gold",
    "gumusmadalya.png": "ref_medal_silver",
    "star.png": "ref_star",
    "gem.png": "ref_gem",
    "saat.png": "ref_clock",
    "clock.png": "ref_clock_blue",
    "gear.png": "ref_gear",
    "home.png": "ref_home",
    "compass_00.png": "ref_compass",
    "coins.png": "ref_coins",
    "sandik.png": "ref_chest",
    "gift.png": "ref_gift",
    "shop.png": "ref_shop",
    "chat_bubble_orange.png": "ref_chat",
    "heart.png": "ref_heart",
    "profile.png": "ref_profile",
    "friends.png": "ref_friends",
    "friend_add.png": "ref_friend_add",
    "crown.png": "ref_crown",
    "dice.png": "ref_dice",
    "anahtar.png": "ref_key",
    "skull.png": "ref_skull",
    "music_note.png": "ref_music",
    "auras.png": "ref_auras",
    "hats.png": "ref_hats",
    "canta.png": "ref_bag",
    "quest_chest.png": "ref_quest_chest",
    "profile_2.png": "ref_profile_alt",
    "upgrade.png": "ref_upgrade",
    "fotograf.png": "ref_camera",
    "gadgets.png": "ref_gadgets",
    "play.png": "ref_play",
    "spectator.png": "ref_spectator",
    "info.png": "ref_info",
    "exclamation.png": "ref_alert",
    "requeue.png": "ref_requeue",
    "requeue_random.png": "ref_requeue_random",
    "leave_queue.png": "ref_leave_queue",
    "iptaletmek.png": "ref_cancel_queue",
    "invite.png": "ref_invite",
    "party_invites.png": "ref_party_invites",
    "party_remove.png": "ref_party_remove",
    "party_unavailable.png": "ref_party_off",
    "trade.png": "ref_trade",
    "social.png": "ref_social",
    "friend_remove.png": "ref_friend_remove",
    "discord.png": "ref_discord",
    "twitchh.png": "ref_twitch",
    "youtubee.png": "ref_youtube",
    "instagram.png": "ref_instagram",
    "twitter.png": "ref_twitter",
    "settings.png": "ref_settings",
    "catalogue.png": "ref_catalogue",
    "rewards.png": "ref_rewards",
    "claim_free.png": "ref_claim",
    "unclaim_free.png": "ref_unclaim",
    "quest_log.png": "ref_quest_log",
    "quest_daily_common.png": "ref_quest_daily",
    "quest_world_unavaliable.png": "ref_quest_off",
    "weeks_week_1_locked.png": "ref_week_locked",
    "weeks_week_1_unlocked.png": "ref_week_open",
    "shop_bank.png": "ref_bank",
    "shop_clothing.png": "ref_clothing",
    "accessories.png": "ref_accessories",
    "infinibag.png": "ref_infinibag",
    "fast_travel.png": "ref_fast_travel",
    "gamepad.png": "ref_gamepad",
    "hub_info.png": "ref_hub_info",
    "hub_profile.png": "ref_hub_profile",
    "hub_rewards.png": "ref_hub_rewards",
    "locked_premiun.png": "ref_premium_lock",
    "blocked_premiun.png": "ref_premium_block",
    "unclaim_premiun.png": "ref_premium_unclaim",
    "locked_week.png": "ref_locked_week",
    "winner.png": "ref_winner",
    "reached.png": "ref_reached",
    "checkpoint.png": "ref_checkpoint",
    "championscore.png": "ref_champion",
    "region.png": "ref_region",
    "overworld.png": "ref_overworld",
    "kilic.png": "ref_sword",
    "kupa.png": "ref_cup",
    "buyutec.png": "ref_search",
    "copkutusu.png": "ref_trash",
    "heykel.png": "ref_statue",
    "trampet.png": "ref_horn",
    "telefonkulubesi.png": "ref_phone",
    "mahkeme.png": "ref_court",
    "planor.png": "ref_glider",
    "uzayli.png": "ref_alien",
    "zombi.png": "ref_zombie",
    "dragon.png": "ref_dragon",
    "dinazor.png": "ref_dino",
    "amongus.png": "ref_amongus",
    "noelbaba.png": "ref_santa",
    "carneval.png": "ref_carnival",
    "carneval_unavailable.png": "ref_carnival_off",
    "clown.png": "ref_clown",
    "azrail.png": "ref_reaper",
    "admin.png": "ref_admin",
    "uye.png": "ref_member",
    "asker.png": "ref_soldier",
    "blacksmith.png": "ref_blacksmith",
    "barber.png": "ref_barber",
    "apartment_editor.png": "ref_apartment",
    "archery.png": "ref_archery",
    "emote_check.png": "ref_emote_check",
    "emote_clock.png": "ref_emote_clock",
    "emote_cross.png": "ref_emote_cross",
    "emote_down.png": "ref_emote_down",
    "emote_up.png": "ref_emote_up",
    "emote_left.png": "ref_emote_left",
    "emote_right.png": "ref_emote_right",
    "emote_exit.png": "ref_emote_exit",
    "emote_heart.png": "ref_emote_heart",
    "emote_home.png": "ref_emote_home",
    "emote_i.png": "ref_emote_info",
    "emote_lock.png": "ref_emote_lock",
    "emote_message.png": "ref_emote_message",
    "emote_minus.png": "ref_emote_minus",
    "emote_plus.png": "ref_emote_plus",
    "emote_music.png": "ref_emote_music",
    "emote_off.png": "ref_emote_off",
    "emote_on.png": "ref_emote_on",
    "emote_pause.png": "ref_emote_pause",
    "emote_play.png": "ref_emote_play",
    "emote_question.png": "ref_emote_question",
    "emote_returnt.png": "ref_emote_return",
    "emote_settings.png": "ref_emote_settings",
    "emote_skip_left.png": "ref_emote_skip_left",
    "emote_skip_right.png": "ref_emote_skip_right",
    "emote_social.png": "ref_emote_social",
    "emote_sound.png": "ref_emote_sound",
    "emote_sound_off.png": "ref_emote_sound_off",
    "emote_star.png": "ref_emote_star",
    "emote_triangle_left.png": "ref_emote_tri_left",
    "emote_triangle_right.png": "ref_emote_tri_right",
    "emote_exclamation.png": "ref_emote_alert",
    "faces.png": "ref_faces",
    "eyes.png": "ref_eyes",
    "grin.png": "ref_face_grin",
    "joy.png": "ref_face_joy",
    "laughing.png": "ref_face_laugh",
    "nerd.png": "ref_face_nerd",
    "angry.png": "ref_face_angry",
    "dead.png": "ref_face_dead",
    "sunglasses.png": "ref_face_cool",
    "sus.png": "ref_face_sus",
    "thinking.png": "ref_face_think",
    "thumbs_up.png": "ref_thumb_up",
    "thumbs_down.png": "ref_thumb_down",
    "wisdom.png": "ref_wisdom",
}

DISPLAY = {
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
}


def harden_alpha(img: Image.Image) -> Image.Image:
    """Reference sheets ship soft edges; Minecraft pixel art wants 0/255 alpha only."""
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 128 else (r, g, b, 0)
    return img


def build_model(name: str) -> dict:
    return {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"bosketball:item/icons/{name}"},
        "display": DISPLAY,
    }


def build_item_def(name: str) -> dict:
    return {
        "model": {"type": "minecraft:model", "model": f"bosketball:item/{name}"},
        "hand_animation_on_swap": False,
    }


def main() -> None:
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    imported = []
    for source_name, icon_name in ICONS.items():
        source = ROOT / source_name
        assert source.is_file(), f"missing reference sprite: {source_name}"
        img = Image.open(source).convert("RGBA")
        assert img.size == (16, 16), f"{source_name}: expected 16x16, got {img.size}"
        img = harden_alpha(img)
        alphas = {px[3] for px in img.getdata()}
        assert alphas == {0, 255} or alphas == {0} or alphas == {255}, f"{source_name}: alpha {alphas}"
        assert 0 in alphas and 255 in alphas, f"{source_name}: sprite must stand free on transparency"
        img.save(TEX_DIR / f"{icon_name}.png")
        (MODEL_DIR / f"{icon_name}.json").write_text(json.dumps(build_model(icon_name), indent=2) + "\n")
        (ITEM_DIR / f"{icon_name}.json").write_text(json.dumps(build_item_def(icon_name), indent=2) + "\n")
        imported.append((icon_name, source_name))

    lines = [
        "# Reference icon sheet — imported sprites",
        "",
        "These 16x16 sprites come from the reference GUI icon sheet at the repository root.",
        "They are imported by `tools/import_ref_icons.py`, which also generates their item models,",
        "so any menu can use `GuiMenuManager.customIcon(\"<name>\", ...)`.",
        "",
        "| Icon model | Source sprite |",
        "|---|---|",
    ]
    for icon_name, source_name in imported:
        lines.append(f"| `{icon_name}` | `{source_name}` |")
    lines.append("")
    DOC.parent.mkdir(parents=True, exist_ok=True)
    DOC.write_text("\n".join(lines))
    print(f"Imported {len(imported)} reference icons into {TEX_DIR.relative_to(ROOT)}")
    print(f"Catalogue: {DOC.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
