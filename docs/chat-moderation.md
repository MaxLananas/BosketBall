# Chat — canaux & escalade de modération

## Canaux

| Canal | Déclencheur | Rendu |
|---|---|---|
| Public | par défaut | `[grade] [tag] [badges] Pseudo » message` |
| Match | joueur sur le terrain (`chat.match-chat-isolated: true`) | `[MATCH] [équipe] [grade] Pseudo » message` |
| Party | `/pc` (mode party chat) | `[PARTY] [équipe/grade] Pseudo » message` |
| Guilde | `/g` (mode guilde) ou `/gchat <message>` | `ɢᴜɪʟᴅ │ [TAG] rôle Pseudo : message` |

Règles fermes :

1. Un canal privé **annule** l'événement `AsyncChatEvent` — il n'envoie **jamais** de ligne vide dans le chat public.
2. Le préfixe du chat de match réutilise `BosketChatRenderer.buildPlayerChatPrefix(...)` : pastille d'équipe **puis** pastille de grade, badges Top-3, tag, pseudo cliquable (hover carte + `/duel`).
3. `/gchat salut` envoie `salut` : le mot de sous-commande (`gchat`, `g`, `gc`) n'est jamais inclus. Sans argument, `/gchat` bascule simplement le mode guilde.
4. Le mode guilde (`/g` activé) route réellement les messages vers la guilde et **censure** les insultes avant envoi.

## Escalade déterministe

Réglages dans `config.yml` :

```yaml
moderation:
  insults-before-mute: 3   # 3 insultes  -> mute
  mute-hours: 1            # durée du mute automatique
  mutes-before-ban: 3      # 3e mute   -> ban définitif
  ban-reason: "Inappropriate language (3 mutes reached)"
```

Déroulé :

1. Chaque message censuré par le filtre = **1 insulte** (peu importe la gravité, la gravité n'influe que sur le texte censuré).
2. À la 3e insulte → mute de 1 h, compteur d'insultes remis à zéro, `mute-count` +1.
3. À la 3e insulte suivante → 2e mute de 1 h.
4. Au cycle suivant, le mute est remplacé par un **ban définitif** (raison `ban-reason` + lien Discord).
5. Le compteur `mute-count` est persisté dans `plugins/BosketBall/players/<uuid>.yml` (`moderation.mute-count`) : un redémarrage ne remet pas la sanction à zéro.

Seuils bornés : `insults-before-mute ≥ 1`, `mutes-before-ban ≥ 2` — une configuration cassée ne peut pas désactiver l'échelle.

## Commandes staff

| Commande | Effet |
|---|---|
| `/warnings` | panneau GUI des avertissements |
| `/warnings <joueur>` | fiche texte : insultes `n/3`, mutes `n/3`, mute restant, avertissements staff (4 derniers + total) |
| `/warnings <joueur> clear` | remet le compteur d'insultes à zéro |
| `/warnings <joueur> clear all` | remet insultes **et** mutes à zéro et lève le mute |
| `/warn <joueur> [raison]` | avertissement staff (compteur séparé, `warnings.yml`) |
| `/unwarn <joueur> [all]` | retire le dernier avertissement staff (ou tous) |

Les avertissements du filtre (insultes/mutes) et ceux du staff sont deux compteurs distincts : `/warnings <joueur>` affiche les deux.
