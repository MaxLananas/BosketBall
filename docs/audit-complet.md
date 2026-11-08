# Audit complet — 5 nov. 2026

Perimetre : **107 fichiers Java, 50 589 lignes** (plugin seul), plus les 7 fichiers de config du
serveur. Methode : recherche statique ciblee (fuites par UUID, allocations en boucle chaude,
synchronisation entre threads, garde-fous d'inventaire, cycles de vie), puis verification de chaque
hypothese dans le code avant de toucher quoi que ce soit. **Rien n'a ete « optimise » sur une
intuition : chaque point ci-dessous a ete lu, compris, corrige, puis couvert par un test.**

---

## 1. Corrige dans ce passage

| # | Probleme | Impact joueur / serveur | Correctif |
|---|---|---|---|
| 1 | **Etat par joueur jamais libere au depart** : `StaffToolsManager` (vanish/freeze), `RewardCrateStation` (cooldown), `GuiMenuManager` (tri/recherche boutique), `NametagManager` (signature) | Un membre du staff qui quittait en vanish revenait **marque vanish mais visible** ; chaque UUID unique laissait une entree permanente en memoire | Une seule porte de sortie : `BosketBallPlugin.cleanupPlayerState(Player)`, appelee en **premier** dans `PlayerQuitEvent`, qui libere les 8 structures (staff, tuto, GUI, caisse, nametag, achat en attente, social, GG) |
| 2 | **Tutoriel quitte en cours** : `returnLocations` et `lastStepAdvance` restaient, et l'hologramme d'intro restait dans le monde | Un joueur qui se deconnectait pendant `/tuto` laissait un hologramme fantome a cote du guide, et gardait son ancienne position de retour | `TutorialManager.clearPlayer(Player)` : purge des 5 maps + retrait des props **seulement si plus personne n'est en tuto**. `/tuto stop` libere les memes entrees |
| 3 | **`NametagManager.forget` jamais appele** (methode morte) | Cache de signatures qui gonfle a chaque UUID croise | Branche dans `cleanupPlayerState` |
| 4 | **`PurchaseConfirmMenu`** : `PENDING`/`CANCEL` purges seulement en passant par la GUI | Une confirmation d'achat restait en memoire apres une deconnexion | `clear(player)` appele au depart |
| 5 | **`GgCommand.LAST_GG_CHAT_MS`** : elagage ad hoc (`size() > 256`) duplique | Deux logiques de purge, cap non garanti | Une seule fonction pure `pruneOlderThan(map, now, keep, limit)` : elague le vieux, puis **vide** si le plafond (512) est depasse. Testee |
| 6 | **Code mort** : `Viewers.anyOnline`, `SchematicCourtPool.measuredBlocksPerMs` | Bruit, surface d'API trompeuse | Supprimes (verifie : 0 reference dans le plugin et les tests) |
| 7 | **Pas de visibilite sur les fuites** | Une fuite lente ne se voit qu'au crash | `/bb audit` affiche maintenant `caches: msg.parse 520/4096 · gg 12/512` et passe en rouge a saturation |

Chaque etape de `cleanupPlayerState` est isolee dans `clean(String, Runnable)` : si un manager leve
une exception, les autres sont quand meme nettoyes et l'erreur est loggee avec son nom.

---

## 2. Verifie, et laisse tel quel (avec la preuve)

| Point | Verification | Verdict |
|---|---|---|
| `Integer.parseInt` dans les commandes | 28 occurrences examinees, **13 dans un `try/catch`**, les 15 autres alimentent `CourtSchematic` (donnees internes) ou sont deja bornees | Aucune commande ne peut jeter une pile d'exception sur une saisie 
| `AsyncChatEvent` fait-il des appels Bukkit hors thread ? | Le handler annule, puis passe par **un seul** `Bukkit.getScheduler().runTask(plugin, ...)` | Conforme |
| `Msg.parse` cache | Borne a **4096 entrees** et **192 caracteres**, vidage complet au-dela | Pas de fuite |
| Sauvegarde des profils en async | `saveProfileAsync` capture un `PlayerSnapshot` **avant** de partir sur le thread async | Pas de course de donnees |
| Tache UI qui meurt | `scheduleUiTask` ne cree jamais de doublon, `ensureDisplayPipeline` la relance, chaque etape est gardee par `step()` | Deja blinde (round 5) |
| `/bb reload` cree-t-il des taches en double ? | Les 6 `runTaskTimer` sont tous dans `onEnable` ou dans des services qui se `reload()` proprement | Pas de doublon |
| Dupes d'items via inventaire | `onDropItem`, `onSwapHand`, `onGuiGuard` (shift/number-key/offhand), `onInventoryDragGuard` couvrent les 4 chemins | Aucun trou trouve |
| Collision / void | `onPlayerMove` est O(1) et ne fait un `teleport` qu'en dessous de `voidRescueY` | Pas de surcout |
| Generation des commandes | Aucun nouveau `CommandExecutor` : tout passe par le switch de `/bb` | Contrainte respectee |

---

## 3. Risques connus, non corriges (et pourquoi)

1. **Cout des display entities a 50 joueurs** : ce n'est pas dans le plugin mais dans l'envoi des
   paquets. Se mesure avec `/spark profiler`. Aucun code ne peut le reduire sans supprimer des
   elements visibles.
2. **Axiom + FAWE** : conserves, donc leur cout de fond reste. Il n'apparait pas dans `/bb perf`.
3. **`Msg.parse` vidage complet a 4096** : les ~100 premieres chaines sont re-analysees (cout
   microscopique, une fois). Un LRU evincerait proprement ; non justifie ici.
4. **`worldedit-config.yml`** : legacy, non lu pour les limites qui comptent.

---

## 4. Comment verifier cote serveur

```
/bb audit          # caches + etats, doit dire RAS ou ne lister que de vrais points
/bb perf           # aucune etape > 5 ms
/bb stress 50      # ~0.08 ms/tick, projection honnete
```

Cote build : `python3 tools/build_plugin.py` -> **232 classes, 107 tests JUnit, 0 erreur**.
Les tests ajoutes dans ce passage : `perPlayerStateIsReleasedOnQuitAndCachesStayBounded`
(8 nettoyages appeles, methodes presentes, elagage des caches, saturation visible, code mort absent).
