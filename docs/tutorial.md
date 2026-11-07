# Tutoriel nouveau joueur (Round 30 — reconstruction complète)

`src/main/java/fr/bosketball/ui/TutorialManager.java` a été **réécrit de zéro**. L'ancienne version
était une pile de minuteries concurrentes qui avançait toute seule : le joueur ne pouvait
littéralement rien faire, et l'écran de guide était cassé.

## 1. Pourquoi l'ancien tuto « ne marchait pas »

| Bug constaté | Cause réelle |
|---|---|
| Les chapitres défilent tout seuls | `Step.autoValidated()` renvoyait `true` pour **tous** les chapitres : au bout de 45 s, `softSkipWatchdog` validait le chapitre à la place du joueur, sans qu'il ait tiré, dunké ou ouvert le shop |
| Ça avance deux fois / les messages s'empilent | `completeStep` programmait un `advance()` 25 ticks plus tard **et** le watchdog pouvait valider en parallèle ; chaque chapitre envoyait 7 messages (header + 4 lignes + pied + boutons + « step complete ») |
| Écran noir de 6 s sans rien | l'intro appliquait cécité + slowness puis `teleportAsync` vers un terrain d'entraînement ; si aucun terrain n'était libre, le joueur restait aveugle et figé |
| Coins gratuits à volonté | `/tuto finish` payait le bonus de complétion sans avoir joué, et `restart()` re-payait les coins de **chaque** chapitre à chaque relecture |
| Le joueur reste sur le terrain après `/tuto stop` | `stop()` supprimait `returnLocations` **avant** d'appeler la sortie du terrain : le retour était perdu |
| HUD qui colle au joueur en match | rien n'arrêtait le tuto quand la partie démarrait : barre de boss + marqueurs restaient pendant le match |
| Lag sur le serveur vide | `hologramLocation()` relisait `stations.yml` **sur le thread principal toutes les 10 ticks** |
| Menu de guide illisible | 8 chapitres placés sur `{10,11,12,13,14,15,16,19}` : la 8ᵉ carte tombait sous la 1ʳᵉ ligne ; le texte affirmait « the tour advances by itself » |

## 2. Ce que fait la nouvelle version

* **Une machine à états par joueur.** Un chapitre n'est validé que par l'action du joueur
  (`notifyAction` depuis le moteur de jeu : `shoot`, `dunk`, `defense`, `shop`, `queue`, et `walk`
  via `PlayerMoveEvent`), ou par un clic explicite « passer ».
* **Une seule carte de chapitre** = **un seul message** chat (composant multi-lignes), plus un titre,
  une boss bar et une action bar. Zéro spam.
* **Jamais bloqué, jamais trompé.**
  * à 60 s (`SOFT_SKIP_MS`) : une **astuce** ciblée sur la mécanique du chapitre ;
  * à 120 s (`SKIP_AFTER_MS`) : un bouton `[PASSER CE CHAPITRE]` cliquable, **sans récompense** ;
  * seuls les deux chapitres de lecture (accueil, final) avancent seuls, et ils ne paient rien
    pour ça.
* **Récompenses payées une seule fois, à vie** (`paidStep`), y compris après un `restart()`.
  Le bonus de complétion reste unique (`tutorialCompleted` n'est plus remis à `false`).
* **Terrain optionnel.** Pas de terrain libre → pas de téléport, pas de cécité : le tuto démarre là
  où le joueur se trouve. Quand il y a un terrain, un **filet de sécurité** (`runTaskLater` 140 ticks)
  rend la vision et la marche quoi qu'il arrive.
* **Nettoyage systématique** : `clearPlayer` (quit), `stop` (pause), `finish`, mort
  (`PlayerDeathEvent`), changement de monde (`PlayerChangedWorldEvent`), entrée en match
  (`match-guard`, mise en pause silencieuse), `/bb reload`.
* **Performances** : position de l'holo **cachée 5 s** (`invalidateHologramCache()`), animation des
  marqueurs à 5 Hz, particules throttlées, garde `introTicks.isEmpty() && Bukkit.getOnlinePlayers().isEmpty()`.
* **Menu de guide refait** : 8 chapitres sur une grille **symétrique 4×2**
  (`{10, 12, 14, 16, 19, 21, 23, 25}`), vraie barre de progression, statut par chapitre
  (✔ / ▶ / ○), objectif, récompense, et une ligne d'état (`chapter 3/8`) qui dit la vérité.

## 3. Les 8 chapitres

| # | Titre | Validation |
|---|---|---|
| 1 | ᴡᴇʟᴄᴏᴍᴇ ᴛᴏ ʙᴏꜱᴋᴇᴛʙᴀʟʟ | lecture (clic ou 20 s) |
| 2 | ᴍᴏᴠᴇ & ʟᴏᴏᴋ | entrer dans l'anneau d'or |
| 3 | ꜱʜᴏᴏᴛɪɴɢ | un tir réussi (fenêtre verte) |
| 4 | ᴅᴜɴᴋɪɴɢ | un dunk |
| 5 | ᴅᴇꜰᴇɴꜱᴇ | sneak (crossover) ou interception |
| 6 | ᴇᴄᴏɴᴏᴍʏ | ouvrir `/shop` |
| 7 | Qᴜᴇᴜᴇ ᴜᴘ | rejoindre une file `/play` |
| 8 | ꜰɪɴᴀʟ ʙᴜᴢᴢᴇʀ | lecture → bonus de complétion |

## 4. Commandes

| Commande | Effet |
|---|---|
| `/tuto` | ouvre le guide (menu 4×2) |
| `/tuto start` / `restart` | reprend / rejoue depuis le chapitre 1 |
| `/tuto next` | valide le chapitre courant (refusé s'il n'est pas validé) |
| `/tuto skip` | passe le chapitre sans récompense |
| `/tuto show` | ré-affiche la carte du chapitre |
| `/tuto stop` | pause (progression sauvegardée, effets nettoyés) |
| `/tuto finish` | seulement si une partie de tuto est en cours |
| `/tuto diag` | état complet pour le staff |

## 5. Vérifier

```bash
python3 tools/build_plugin.py      # 144 tests, dont testRound30TutorialNeverAdvancesBehindThePlayer
```

Le test vérifie notamment : `autoValidated()` faux pour **tous** les chapitres de jeu, une astuce
pour chaque chapitre, les récompenses payées une seule fois, la grille 4×2 du menu, la mise en pause
automatique en match, et l'absence de `setTutorialCompleted(false)`.

## 6. Brand sheet

`docs/showcase/brand-sheet.png` est généré par `tools/brand_sheet.py` : il reprend **tous** les
assets du pack (57 tags, 9 rangs, 26 écrans GUI, HUD, icônes, 8 familles de ballons, palette) sans
qu'aucun chiffre ne soit écrit à la main.
