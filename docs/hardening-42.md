# Round 42 — le passage « jusqu'à l'os » sur 10 000 lignes tirées au hasard

Le propriétaire a demandé : prendre 10 000 lignes au hasard et les poncer. Le tirage a été fait
une fois, avec une graine fixe (`random.seed(42)` sur la liste des 122 fichiers du plugin), sans
regarder les résultats, puis **plus une ligne n'a été ajoutée ni retirée au lot** : ce qui suit est
l'audit complet de ce tirage, pas d'une sélection confortable.

## 1. Le lot exact

30 fichiers, **10 473 lignes** :

| Lignes | Fichier |
|---|---|
| 1170 | `physics/BasketballEntity.java` |
| 1141 | `stats/StatsManager.java` |
| 1059 | `server/ServerProtectionManager.java` |
| 837 | `social/GuildService.java` |
| 773 | `i18n/Lang.java` |
| 749 | `ui/CinematicCameraManager.java` |
| 740 | `server/PermissionManager.java` |
| 374 | `game/MatchmakingEngine.java` |
| 370 | `animation/AnimationKinematics.java` |
| 349 | `social/SocialService.java` |
| 348 | `server/PingDiagnostics.java` |
| 306 | `arena/MapEditorManager.java` |
| 226 | `ui/BosketChatRenderer.java` |
| 209 | `util/Perms.java` |
| 199 | `season/SeasonMath.java` |
| 180 | `arena/HoopDisplaySet.java` |
| 153 | `arena/PracticeArenaPool.java` |
| 148 | `ui/BossBarHudManager.java` |
| 146 | `ops/OpsAudit.java` |
| 140 | `integration/IntegrationService.java` |
| 139 | `moderation/StaffToolsManager.java` |
| 129 | `command/GgCommand.java` |
| 117 | `season/SeasonRecord.java` |
| 113 | `game/bot/BotProfile.java` |
| 78 | `util/ChatSpy.java` |
| 77 | `model/TeamSide.java` |
| 71 | `model/ShotReleaseQuality.java` |
| 57 | `physics/MovementMath.java` |
| 46 | `util/Viewers.java` |
| 29 | `model/ProgressStat.java` |

`config/AtomicYamlWriter.java` a été ajouté **en plus** : c'est le primitif d'écriture utilisé par
`StatsManager` et `SocialService`, on ne peut pas durcir les deux sans lui.

## 2. La brique commune : `util/Guards`

Une passe « robustesse » qui recopie le même `Math.max(min, Math.min(max, v))` dans vingt fichiers
n'est pas une passe de robustesse, c'est vingt occasions de se tromper. Trois familles de défauts
revenaient partout, elles ont donc un seul endroit :

| Méthode | Ce qu'elle empêche | Trouvé dans |
|---|---|---|
| `clampLoHi(...)` | `Math.clamp` **lève** `IllegalArgumentException` quand `min > max` | caméra cinématique (par tick), Matchmaking, saison |
| `finite(...)`, `unit(...)` | un `NaN` se propage et rend **tout** comparaison fausse | animation, physique, tir |
| `saturatingMultiplyAdd(...)` | un `long` qui déborde **revient de l'autre côté** | fenêtre de saison |
| `truncate(...)` | une troncature qui coupe une paire de substitution | tags/description de guilde, diagnostic ping |

Tout est pur (aucun Bukkit), donc testable : `round42GuardsNeverThrowAndNeverLie`.

## 3. Ce qui a été corrigé, fichier par fichier

### `ui/CinematicCameraManager` — 60 exceptions par seconde
`clampInsideBarrier` appelait `Math.clamp(pos.getX(), minX + 0.9, maxX - 0.9)`. Dès qu'un terrain
fait moins de 1,8 bloc de large — ou que le plafond est à moins de 2,3 blocs au-dessus du sol — le
minimum dépasse le maximum et **`Math.clamp` lève une exception**. L'appel est dans une tâche
planifiée par tick : des milliers de traces de pile par seconde, impossible d'en sortir sans
redémarrer. Passage à `Guards.clampLoHi` (milieu de l'intervalle), plus des gardes `null` sur
l'arène et sur `lookAt`.

### `stats/StatsManager` + `config/AtomicYamlWriter` — la perte de données silencieuse
1. **Profil corrompu = profil remis à zéro.** `loadSafe()` avale l'erreur de syntaxe et renvoie une
   configuration **vide** ; le parseur en faisait un joueur neuf (0 coin, 0 ELO, aucun historique)…
   puis le sauvetage suivant écrasait le fichier. Un `loadStrict()` a été ajouté : le fichier est
   mis en quarantaine (`players/_quarantine/`) et les octets restent récupérables.
2. **Index des pseudos perdu.** `indexDirty` était un `volatile boolean` remis à `false` *après* la
   prise du snapshot : un renommage dans cette fenêtre était marqué puis immédiatement démarqué.
   C'est un `AtomicBoolean.getAndSet(false)`, et le snapshot est pris *après* la remise à zéro.
3. **Fichier temporaire partagé.** `writeTextAtomically` écrivait dans `<fichier>.tmp` : deux
   écrivains du même profil (thread IO qui vide la file pendant que l'arrêt écrit, ou un reload qui
   croise un save) se tronquaient mutuellement. Le nom est unique par écriture, et le temporaire est
   supprimé en cas d'échec (il restait sinon pour toujours).
4. **Arrêt qui ne dit rien.** `shutdownAndSaveSync()` attendait 5 s et **ignorait le dépassement** :
   le thread principal écrivait alors les mêmes fichiers que le thread IO encore dessus. Le
   dépassement est journalisé, la file est vidée (`shutdownNow` + attente), puis on écrit.
5. **Tampons orphelins.** Un profil marqué *dirty* mais déchargé entre-temps disparaissait sans un
   mot : compteur `orphanedDirtyCount()`.
6. En-têtes YAML : `# # ====` → `# ====` (le préfixe n'est plus ajouté deux fois).

### `physics/BasketballEntity` — un `NaN` condamnait le ballon
Un état non fini rend tous les tests de collision faux : le ballon traverse le sol, et
`teleport(NaN)` inonde la console. `tick()` vérifie `isStateFinite()` une fois et, sinon,
`recoverFromInvalidState()` replace le ballon au centre avec une immunité de ramassage. `airDrag`
est également borné (une valeur `NaN` ou `> 1` dans la config accélérait le ballon à l'infini).

### `animation/AnimationKinematics` — un mauvais tick empoisonnait le rig
Neuf entrées venaient d'entités vivantes. Un seul `NaN` construisait des quaternions invalides que
le client refuse… pour toute la durée du `NaN`. Tout est assaini à l'entrée, `speedNorm` est borné
à `[0, 1.38]` (une vitesse négative faisait marcher le cycle à l'envers), `landingTicks` borné à 6,
`rotateYaw(null, …)` renvoie un vecteur neutre.

### `model/ShotReleaseQuality` — la fenêtre verte qui s'inverse
Le bonus de distance poussait le bord bas au-delà du bord haut à partir de ~70 blocs : la zone
« green » devenait **vide**, donc tout tir long était `VERY_EARLY`. La fenêtre se referme maintenant
sans jamais s'inverser (`MIN_GREEN_WIDTH = 0.02`), et un `charge` `NaN` n'est plus un tir parfait
gratuit (`timingPenaltyBlocks` renvoyait 0 pour `NaN`, parce que `NaN < lo` est faux).

### `i18n/Lang` — la langue du joueur ignorée, et une injection de placeholders
1. `Locale_.byCode` ne découpait que sur `-`. Or le client envoie `fr_fr`, `es_mx`, `en_us` avec un
   **souligné**, et certains proxies un code à trois lettres : la méthode répondait `EN` à tous les
   clients français et espagnols. Normalisation `_`, région, code à 3 lettres.
2. `Lang.of(...)` recopiait **toute** la table anglaise (plusieurs centaines d'entrées) à chaque
   message. Une instance immuable par locale, construite une fois.
3. Les remplacements se faisaient par `String.replace` successifs, dans l'ordre de la map : un pseudo
   ou un nom de guilde valant `{amount}` était **réinterprété** par l'entrée suivante. Remplacement
   en une passe sur le gabarit uniquement, valeurs insérées telles quelles.

### `util/Perms` — le grant parapluie ignoré
L'ancien code s'arrêtait sur `isPermissionSet(node)`. Cette API est aussi vraie pour un nœud qui n'a
qu'un **défaut `plugin.yml`** (c'est le cas de tous nos nœuds) : offrir `bosketball.admin` à un
non-op ne remontait donc jamais la hiérarchie, et `bosketball.admin.elo` répondait « non ». Le
parcours exact → parents → joker est désormais prioritaire ; un **refus explicite** gagne encore,
mais seulement pour un nœud sans défaut déclaré (les deux cas sont indistinguables autrement).

### `server/PermissionManager` — O(depth²) par `/perm check`
`explain()` appelait `effectiveAllows()` (qui reconstruit toute la carte effective) à chaque nœud
parent. La carte est maintenant construite une fois et parcourue par une fonction statique.

### `game/MatchmakingEngine` — un ban de 9 ans par accident
`multiplier = base^min(4, count)` avec `base` lu dans la config : à `dodge-penalty-multiplier: 10`,
le cinquième refus donnait 30 000 s × 10 000 ≈ **9,5 ans**. Base bornée à 10, exposant à 4 et
verrouillage plafonné à 7 jours. La fenêtre ELO est aussi bornée (`Math.max(0, …)`).

### `ui/BossBarHudManager` — la barre qui disparaît après un relog
La `BossBar` est mise en cache par UUID, mais une boss bar est **affichée par entité**. Après une
reconnexion, l'objet existait toujours (signature identique) et le code se contentait de
`existing.name(...)` : le joueur ne voyait plus la barre jusqu'à un `clearPlayer`. Quatre
`showBossBar(existing)` idempotents, plus une garde `usable(player)` sur tous les points d'entrée.

### `arena/PracticeArenaPool` — deux règles d'espacement
L'allocation calculait la grille avec `cfg.gridSpacingBlocks()` brut, alors que
`computeGridSlotCenter()` borne à 96 blocs : une config à 50 produisait des **courts de practice qui
se chevauchent**, alors que le helper public promettait le contraire. Une seule règle désormais.

### `social/SocialService` — boîte aux lettres effacée
`YamlConfiguration.loadConfiguration` renvoie une config **vide** sur un fichier cassé, et le
`persist` suivant écrivait ce vide : toutes les lettres étaient perdues. Lecture stricte, copie
`.corrupt-*.bak`, message console, boîte neuve.

### `social/Guild` + `GuildService` — le tag de couleur qui fait planter le chat
`hexColor()` formatait `colorRgb` sans masque : une couleur chargée avec alpha (`0xFF38BDF8`) ou
négative donnait `#FF38BDF8`, et MiniMessage lève sur `<#FF38BDF8>` — la ligne de chat disparaissait.
Masque `0xFFFFFF`, valeur mise en cache et invalidée à chaque changement, `chatTag()`/`chatTagClose()`
pour que personne ne reconstruise un tag à la main. Le message de `/guild color` utilisait
`hexColor().substring(1)`, qui **lève** sur une valeur vide.

### `season/SeasonMath` + `season/SeasonRecord`
Fenêtre de saison : durée bornée, index borné, multiplication **saturante** (`(index-1) * duration`
débordait vers 1970) ; `formattedWindow(-1, -1)` renvoie `—` au lieu de `01/01/1970` ; `softReset`
borne l'ELO avant de soustraire (débordement sur `Integer.MIN_VALUE`) ; `daysToMillis` plafonné à
10 ans ; `progressPercent` protège sa soustraction. Compteurs de saison saturants (un total négatif
s'affichait tel quel), taux de victoire borné à 100 %.

### `util/Viewers`, `physics/MovementMath`, `command/GgCommand`, `ops/OpsAudit`
- `Viewers.anyWithin` : un rayon `NaN`/négatif coupait silencieusement toute l'animation du lobby.
- `MovementMath` : une vélocité `NaN` était réinjectée dans `setVelocity()` (joueur bloqué jusqu'à
  la reconnexion) ; force et lift bornés.
- `GgCommand.pruneOlderThan` : un horodatage **dans le futur** (remise à l'heure de l'horloge)
  n'était jamais élagué.
- `OpsAudit.audit(null)` levait une NPE ; un TPS `NaN` (démarrage) est maintenant explicitement
  écarté au lieu de dépendre d'une comparaison fausse.

### `arena/MapEditorManager`, `moderation/StaffToolsManager`, `integration/IntegrationService`
- Sessions d'éditeur : `sessions` était un `HashMap` sans sortie, une entrée par admin pour toute la
  durée de vie du serveur. `forget(UUID)` + `pruneDeadSessions()` (arène supprimée) + gardes `null`.
- `onMove` : `to` vérifié, et un changement de monde n'est plus « épinglé » (le joueur restait collé
  à l'ancienne position d'un autre monde).
- Deltas ELO Discord : à 64 entrées la **totalité** de la map était vidée, y compris les matchs en
  cours (récap à 0 ELO). Élagage par âge (6 h), jamais par volume.

### `server/PingDiagnostics`
`average()`, `min()` et `max()` construisaient une `ArrayList` complète à chaque appel (chemin
par joueur/par seconde) pour une somme qui n'en a pas besoin : accès direct à l'anneau, somme en
`long` (pas de débordement avec une fenêtre pleine de pings élevés).

### `arena/HoopDisplaySet`, `game/bot/BotProfile`, `model/TeamSide`
- `setModel(null)` levait dans la boucle de tick (le panneau restait figé) ; `spawn()` garde
  `rimCenter` ; l'affichage du chrono est borné à 99 s (sinon `-1` ou 3 chiffres).
- `BotProfile.pick` construisait deux streams imbriqués par bot : une seule passe, liste pré-allouée.
- `TeamSide.byName` (jamais `null`, inconnu = spectateur) et `plainName` sans `charAt` sur une
  chaîne vide.

## 4. Ce qui a été vérifié et trouvé sain (pas de changement)

Pour que le lecteur sache que l'audit a bien eu lieu, voici ce qui a été contrôlé sans défaut :

- **`ServerProtectionManager`** : la liste noire est construite une fois (`EnumSet`, `null`-safe),
  l'extraction de commande gère `/`, l'espace, le `:` et la casse ; rien à corriger.
- **`util/ChatSpy`** : relais sans doublon, corps vide ignoré, `alreadySeen` optionnel.
- **`model/ProgressStat`**, **`util/Perms.NodeSpec`** : purs, sans état.
- **`ui/BosketChatRenderer`** : division protégée (`totalGames > 0`), guilde optionnelle, aucun
  accès disque.
- **`season/SeasonMath.parseInstant`** : les cinq formats sont essayés dans l'ordre, toute exception
  est locale au format.
- **`arena/PracticeArenaPool`** (hors espacement) : générateur vide, monde créé une seule fois,
  index borné par `maxInstances`.

## 5. Couverture de test

Huit tests ajoutés, **175 au total** (tous verts) :

| Test | Ce qu'il verrouille |
|---|---|
| `round42GuardsNeverThrowAndNeverLie` | bornes croisées, `NaN`, saturation, troncature de surrogate |
| `round42SeasonWindowSurvivesCorruptDates` | 25 combinaisons index × durée, `softReset` sur `Integer.MIN_VALUE` |
| `round42ShotReleaseWindowNeverInverts` | 480 distances × 2 états : fenêtre non vide et « green » atteignable |
| `round42AnimationSurvivesNonFiniteInput` | toutes les entrées `NaN` → six os finis, `rotateYaw(null)` |
| `round42LangIsCachedAndCannotBeInjected` | instance unique, `fr_fr`/`FRA`/`de_de`, valeurs non réexaminées |
| `round42GuildColourIsAlwaysATag` | cinq couleurs hostiles → toujours `#RRGGBB` valide |
| `round42StorageAndTickGuardsAreInPlace` | hachages, écritures atomiques, caméra, boss bar, perms, practice |
| `round42SmallGuardsAreCorrect` | cache GG à horloge inversée, audit `null`, dash `NaN`, compteurs saturants |

Le pack de ressources n'est **pas** touché : même SHA-1 `481a087f`, aucun tag `pack-…` à pousser.
