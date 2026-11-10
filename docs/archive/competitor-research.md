# Étude concurrentielle — les plus gros serveurs Minecraft (novembre 2026)

> Objectif : comprendre **pourquoi** Cytooxien, DonutSMP, Hypixel & co. donnent cette impression de « fou »,
> puis lister **ce qui manque à BosketBall ou ce qui y est mal fait**.
> Toutes les données de population sont datées du **4 novembre 2026** (livecounts.nl, 02:36 UTC) sauf mention contraire.

---

## 1. Méthode

| Étape | Ce qui a été fait |
|---|---|
| 1 | Sites officiels, wikis, pages boutique et notes de version de 20 serveurs |
| 2 | Relevé de population live + classements historiques (livecounts.nl) |
| 3 | Audit de la stack technique 2026 (Nexo, CraftEngine, ItemsAdder, Oraxen, ModelEngine, MythicMobs, PacketEvents) |
| 4 | Lecture des dépôts GitHub publics / topics (`wynncraft`, contributeurs d'API) |
| 5 | Audit `grep` du code BosketBall (21 packages) pour confirmer présence/absence réelle de chaque fonctionnalité |

**Limite honnête** : aucun de ces serveurs ne publie son code. Ce qui est « vérifiable » ici, ce sont les
comportements observables (site, wiki, boutique, API, captures, retour joueurs), pas leurs internes.

---

## 2. Le plateau — 20 serveurs

### 2.1 Tableau de synthèse

| # | Serveur | Édition | Pop. live (4/11/26) | Signature | À voler chez eux |
|---|---|---|---|---|---|
| 1 | **Cytooxien** | Java | ~1 000–3 000 (1,5 M uniques depuis 2011) | La qualité de finition allemande | Rotation de mini-jeux votée + notée, Rubies, realms VIP |
| 2 | **Hypixel** | Java | **18 698** | L'écosystème | API publique, mods tiers, loot chests, saisons |
| 3 | **DonutSMP** | Java + Bedrock | **18 427** (pic 47 612) | La boucle économique | Monde unique, `/sell`, hôtel des ventes, shards |
| 4 | **The Hive** | Bedrock | **9 273** | Le hub Bedrock | Saisons cosmétiques, onboarding console |
| 5 | **CraftLandia** | Java | **7 075** | L'échelle régionale (BR) | Localisation profonde |
| 6 | **CubeCraft** | Bedrock + Java | **6 670** | Le crossplay | EggWars/Lucky Islands, un seul compte deux plateformes |
| 7 | **Lifeboat** | Bedrock | **4 203** | Le casual accessible | Simplicité d'entrée |
| 8 | **Minehut** | Java | **3 737** | L'UGC | Serveurs hébergés par les joueurs |
| 9 | **ManaCube** | Bedrock + Java | **3 040** | Le multi-gamemodes | Monétisation assumée, saisons courtes |
| 10 | **Wynncraft** | Java | ~1 500–5 000 | L'audace technique | Monde 4 400×5 500 fait main, API + Wynntils |
| 11 | **MCC Island** (Noxcrew) | Java | ~1 000–4 000 | Le polish d'UI | Game Passes, achievements, présentation TV |
| 12 | **Origin Realms** | Java | ~500–2 000 | Le « tout maison » | Worldgen custom, blocs custom, realms perso |
| 13 | **Paladium** | Java + Bedrock | ~70–224 (pic 1 500) | L'écosystème FR | Launcher maison, modpack, resets saisonniers |
| 14 | **GommeHD** | Java | ~2 000–8 000 | La longévité DE | Rangs premium, BedWars/KBFFA |
| 15 | **PikaNetwork** | Java | ~2 000–5 000 | L'accessibilité | Practice/Duels, audience crackée |
| 16 | **Jartex** | Java | ~1 500–5 000 | Le volume | Prison/Skyblock, économie agressive |
| 17 | **Minemen Club** | Java | ~300–1 500 | L'esport pur | Ladders, anti-fluff, crédibilité compétitive |
| 18 | **Mineplex** | Java | ~500–2 000 | La relance | Roadmap publique, Plex Pass |
| 19 | **DiamondFire** | Java | ~500–1 500 | La plateforme UGC | Programmation de mini-jeux in-game |
| 20 | **2b2t** | Java | ~200–800 | La légende | Permanence du monde (80 TB, 935 k uniques) |

### 2.2 Fiches détaillées — les 8 qui comptent pour nous

#### ① Cytooxien — la référence absolue
- **Depuis 2011**, 1,5 M+ de joueurs uniques, toujours sur la dernière version de Minecraft, architecture multi-serveurs dynamique (les instances se montent et se démontent à la demande).
- **Minecraft Party** : plateau façon Mario Party, **40+ mini-jeux, des centaines de maps**, dés bronze/argent/or, boutique à pièces, **double-coins** events.
- **Le vote du premier mini-jeu** : Premium = 1 vote, Premium+ = 2–3 votes. C'est *le* détail qui transforme un lobby en rendez-vous social.
- **La note 1–5 étoiles en fin de partie pèse réellement sur la rotation** des maps, et la rotation est **re-curatée tous les quelques mois**. Le contenu tourne, donc ne sent jamais le vieux.
- Modes : Random Item Battle (loot tables par map, maps en équipe), Cactus Tycoon, Only Up (2 000 blocs), Smash, Timejump, Duels, BedWars, JumpWorld, KnockbackFFA, Infection, Skyblock, Citybuild, Factions, Creative + événements Masterbuilder.
- Boutique : **Premium 10 €/mois** (30+ features), **Premium+ 17,50 €/mois** (50+), monnaie dure **Rubies** (450 pour 7,50 € → 4 200), clés de coffre au trésor, œufs mystères, boosts de realm, cosmétiques cadeaux d'anniversaire, rang créateur avec `/vanish`, `/joinme` (toutes les 20 min), **realm personnel 50 joueurs** et **5 % d'affiliation**.
- **Verdict** : ce n'est pas un serveur, c'est une **plateforme de contenu** avec une cadence de production. C'est exactement le niveau d'exigence que tu admires — et il ne vient pas d'un plugin magique, il vient de *process*.

#### ② Hypixel — le roi de l'écosystème
- 18 698 joueurs live, 22–24 k de moyenne sur 7 jours, record historique >200 k.
- BedWars est devenu le produit phare, mais la vraie force est ailleurs : **API publique documentée** (`api.hypixel.net`), **des dizaines de mods tiers** (NEU, SkyblockAddons, trackers de Bazaar…), loot chests, événements saisonniers, Watchdog.
- **Verdict** : un serveur qui expose ses données laisse sa communauté construire la couche d'outils qu'il n'a pas le temps d'écrire. BosketBall n'expose rien.

#### ③ DonutSMP — la machine à croissance
- 18 427 live, **pic historique 47 612**, ~35 k slots, 1.21.9, **Java + Bedrock**. Il a même dépassé Hypixel le 24 septembre 2026 (29 633).
- Philosophie inverse de Cytooxien : **un seul monde survie partagé, pas de lobby**, `/sell`, **hôtel des ventes**, **ordres d'achat**, **shards** comme seconde monnaie, events hebdomadaires, économie lifesteal. Positionnement « pas de clutter inutile ».
- **Verdict** : la croissance vient du **contenu créateur** (TikTok/YouTube) adossé à une boucle économique lisible en 30 secondes. Zéro mini-jeu, 18 k joueurs.

#### ④ Wynncraft — l'audace technique
- MMORPG depuis 2013 : monde **4 400×5 500 fait main**, 100+ quêtes, classes, **mobs custom simulés par des armor stands pilotés par des commandes move**, musique en note blocks, monde découpé en **shards de 50 joueurs qui se voient encore**.
- **API officielle + Wynntils** (mod client) + une constellation d'outils tiers (bots Discord, bases d'items, wrappers Python/PHP publiés sur GitHub).
- **Verdict** : ils n'ont pas attendu un plugin ; ils ont écrit leur moteur. Et ils ont ouvert l'API, ce qui a créé la couche communautaire.

#### ⑤ MCC Island (Noxcrew) — le polish d'UI
- Mini-jeux dérivés du tournoi Minecraft Championship : Game Passes cosmétiques, achievements, présentation « émission TV », hub unique très design.
- **Verdict** : c'est la meilleure preuve que **la présentation EST le produit**. Même gameplay, autre emballage, autre perception de valeur.

#### ⑥ Origin Realms — le « tout maison »
- Aucun ItemsAdder/Oraxen : items = paper + CustomModelData, **blocs custom via blockstates de noteblock/champignon** (comparateurs pour l'herbe), **worldgen écrit de zéro** (structures inter-chunks, rivières souterraines, grottes custom), collaboration avec le mod Aether, realms personnels.
- **Verdict** : la limite n'est pas « quel plugin », c'est « combien de temps on investit dans le pipeline d'assets ».

#### ⑦ Paladium — le cas français
- Depuis 2015, factions moddée, **launcher maison + modpack**, Bedrock sur `:19132`, wiki, resets saisonniers, événements classés, patch notes régulières. Licence « le client est fourni » : pas besoin de posséder Java *ou* Bedrock.
- **Verdict** : le launcher est un **canal de distribution** qui vaut plus que n'importe quelle feature in-game.

#### ⑧ Minemen Club — l'anti-Cytooxien
- Practice/duels, ladders, zéro cosmétique criarde. Rien à voir visuellement… et pourtant c'est la référence compétitive.
- **Verdict** : sur un jeu de sport, la crédibilité du ladder passe avant les paillettes. Nous, on a le ladder, mais pas l'anti-triche.

---

## 3. Les 10 leviers qui produisent le « waouh »

Ce ne sont pas des features, ce sont des **systèmes**. Tous les gros serveurs en cochent 6 à 9.

| # | Levier | Qui le fait bien | Ce que ça change |
|---|---|---|---|
| L1 | **Cadence saisonnière** (reset, pass, calendrier) | Cytooxien, Hive, MCC, Hypixel | Le joueur a toujours une raison de revenir la semaine prochaine |
| L2 | **Monnaie dure + loot** (Rubies, shards, clés) | Cytooxien, DonutSMP, ManaCube | Monétisation non-P2W + sink économique |
| L3 | **Contenu voté/noté par la communauté** | Cytooxien (vote 1er mini-jeu, notes 1–5★) | Le contenu s'auto-censure : le mauvais sort, le bon reste |
| L4 | **Pipeline d'assets** (modèles, blocs, meubles, emotes) | Origin Realms, Nexo/ItemsAdder | C'est *littéralement* l'axe « textures de fou » |
| L5 | **Écosystème ouvert** (API, mods, bots) | Hypixel, Wynncraft, Paladium | La communauté produit du contenu gratuitement |
| L6 | **Présentation broadcast** (intro, caméras, overlays) | MCC Island, Cytooxien, Minemen | Donne l'impression d'un sport officiel |
| L7 | **Crossplay Bedrock** | Hive, CubeCraft, Lifeboat, Paladium | ×2 à ×3 de marché accessible (console/mobile) |
| L8 | **Programme créateur** (rang, affiliation, realm perso) | Cytooxien (5 %, `/joinme`, realm 50 j) | Croissance organique par clips |
| L9 | **Fair-play visible** (anti-cheat, modération, reports) | Hypixel (Watchdog), Minemen | Sans ça, un ladder ELO n'a aucune valeur |
| L10 | **Permanence / légende** (monde qui ne reset jamais) | 2b2t (80 TB, 935 k uniques) | Crée de l'histoire, donc du récit, donc du marketing |

---

## 4. BosketBall vs le marché — ce qui manque, ce qui est mal fait

> Base : audit `grep` du code au 4/11/2026 (21 packages, ~48 k lignes Java).
> Notation d'impact : 🔴 bloquant pour la rétention · 🟠 fort · 🟡 moyen · ⚪ polish.

### A. Rétention long terme — le trou principal

**A1 🔴 Il n'y a pas de saison classée.**
`season-name` existe dans `BosketConfig` et s'affiche dans la tab list et le Hall of Fame… mais **ce n'est qu'une chaîne de caractères**. Aucun reset, aucune récompense de fin de saison, aucune archive.
→ Conséquence : l'ELO s'aplatit, un joueur qui atteint le top 100 n'a plus d'objectif, et l'affichage « ꜱᴇᴀꜱᴏɴ ɪ » est une **promesse non tenue**.
→ À faire : saison de 8–10 semaines, soft reset (`1200 + (elo − 1200) × 0,5`), 5 matchs de placement, récompenses par palier, Hall of Fame **archivé par saison**, badge permanent.

**A2 🔴 Il n'y a pas de battle pass.** Zéro occurrence de `battlePass`/`pass` dans le code.
Cytooxien (Premium/Premium+), MCC Island (Game Passes), la Hive : tous ont une piste de progression saisonnière.
→ À faire : 50 paliers, deux pistes (gratuit / premium cosmétique), XP = matchs + défis hebdo, **cosmétique uniquement**.

**A3 🟠 Pas de collections ni de prestige.** Zéro occurrence de `prestige`.
Les cosmétiques existent (12 fichiers) mais sans rareté, sans rotation, sans collection à compléter → aucun désir.
→ À faire : collections par thème, `prestige` au niveau 100, compteur « % de la collection ».

**A4 🟡 Les défis existent (quotidiens + reroll payant) mais il n'y a ni hebdo ni saisonnier.**
→ À faire : une chaîne d'objectifs longs (« 50 dunks ») qui donne un cap à 30 jours.

### B. Compétition / esport

**B1 🔴 Il n'y a pas de tournois.** Le mot n'apparaît que dans **du texte décoratif** (`GuiMenuManager:771`, `:2577`, `Msg:193`).
Pour un jeu de sport, c'est l'absence la plus choquante : Cytooxien a des Masterbuilder events, Paladium des ranked events, DonutSMP des events hebdo.
→ À faire : brackets 8/16 équipes, seeding par ELO, BO3/BO5, overlay spectateur, publication auto des résultats sur Discord.

**B2 🟠 Pas de mode broadcast.** Le replay et la caméra cinématique existent (bien), mais pas de caméra libre, pas de multi-POV, **pas de génération automatique de clip de 15 s** sur une action.
→ Or c'est exactement ce qui alimente TikTok/YouTube chez DonutSMP.

**B3 🟠 ELO unique, pas de ladder par mode.** 3v3 et 5v5 partagent le même rating, donc le classement ne veut rien dire.
→ À faire : un rating par mode + un rating « polyvalent ».

**B4 🟡 Pas de pénalité de dodge visible / pas de file « quick play » sans ELO.**
Sur un serveur à population modeste, le matchmaking ELO est le goulot d'étranglement. Les bots (`BotBrain`) peuvent servir de **remplissage de file**, pas seulement d'entraînement.

### C. Social

**C1 🟠 Pas de chat vocal de proximité** (0 occurrence `voicechat`/Simple Voice). C'est un standard de fait sur SMP et mini-jeux en 2026.
**C2 🟠 Pas de guerres de guildes / saisons de guilde.** Les guildes existent (rôles, chat, invitations) mais n'ont **aucun enjeu compétitif** : pas de classement de guilde, pas de guerre, pas de territoire.
**C3 🟠 Pas de realm / serveur privé** pour créateurs ou communautés — c'est pourtant un avantage VIP chez Cytooxien (50 joueurs) et un pilier chez Origin Realms.
**C4 🟡 Pas de programme créateur** : pas d'affiliation, pas de `/nick`, pas de mode stream, pas de commandes dédiées OBS. Cytooxien reverse 5 %.

### D. Plateforme & portée

**D1 🔴 Pas de Bedrock / Geyser.** Zéro occurrence de `geyser|bedrock|crossplay`.
Le Hive (9 273), CubeCraft (6 670), Lifeboat (4 203), Paladium (Bedrock) : **tout le marché console/mobile nous est fermé**.
→ Attention : le pack actuel (3,5 Mo, mux Netty maison, modèles Java) **ne passera pas tel quel** sur Bedrock. Il faut un pack dédié.
**D2 🟠 Mono-serveur, pas de proxy Velocity.** Plafond de population, pas de hub permanent, pas de sharding. Cytooxien monte des instances à la demande ; Wynncraft découpe en shards de 50.
**D3 🟡 Pas de launcher/mod client.** C'est un choix défendable (zéro friction), mais ça interdit les HUD temps réel façon Wynntils.
**D4 🟡 Pas d'adaptation mobile/manette** dans les GUI — or les GUI sont notre point fort visuel.

### E. Écosystème & données

**E1 🟠 Aucune API publique, aucune télémétrie produit.** Zéro endpoint HTTP (`HttpServer|/api|apiKey` : 1 seul fichier, le host de pack), zéro `bStats`.
Hypixel et Wynncraft ont des dizaines de projets tiers ; nous, personne ne peut écrire un bot de classement.
→ À faire : endpoint JSON signé (profil, classement, match en cours) + bStats + expansion **PlaceholderAPI** (0 dépendance aujourd'hui, donc invisible dans les outils tiers).
**E2 🟠 Intégration Discord minimale.** Il y a un webhook et un digest quotidien — pas de bot, pas de rôles automatiques par rang, pas de liaison de compte.
**E3 🟡 Pas de page web de stats** (profils, historique de matchs, classements live). Tous les gros serveurs en ont une.

### F. Monétisation

**F1 🔴 Aucun store, aucun rang payant, aucune devise premium.** Zéro occurrence de `auction`, `shards`, boutique web.
Cytooxien : Premium 10 €, Premium+ 17,50 €, Rubies, clés, œufs. DonutSMP : shards. Nous : rien.
→ Un jeu compétitif propre peut monétiser **100 % cosmétique** sans P2W. C'est le modèle dominant et il est compatible avec ton éthique de jeu.
**F2 🟠 Pas de calendrier d'événements** (Halloween, anniversaire, sortie de mode). Cytooxien reskinne ses maps et sort des cosmétiques cadeaux à chaque date. Chez nous : aucune variation saisonnière des arènes.
**F3 🟡 Pas d'affiliation / parrainage.**

### G. Fair-play

**G1 🔴 L'anti-triche a été retiré** (0 occurrence `anticheat`). Sur un jeu où un ELO est en jeu, c'est une invitation.
Hypixel a Watchdog, Minemen vit de sa crédibilité. → Grim (open source, côté serveur) + checks custom ballon (vitesse, aim, téléport) + le système de report existant.

### H. Game feel / l'axe « graphismes & textures de fou »

**H1 🔴 Pas de pipeline d'assets moderne.** Aucune trace de `item_model` (composant 1.21.4), de Nexo/CraftEngine/ItemsAdder/Oraxen.
On a : pack maison, glyphes, GUI custom, vestiaire 3D, physique de ballon custom. C'est **déjà au-dessus de 90 % des serveurs**.
Mais il manque : **modèles 3D Blockbench en jeu, meubles, emotes, HUD overlay, blocs custom**. C'est précisément la couche qui produit l'effet « attends, c'est Minecraft ça ? ».
→ À faire : migrer vers `item_model` + Nexo (ou CraftEngine) pour les objets 3D, garder le pack maison pour le sonore/UI.

**H2 🟠 Pas d'emotes, pas de sprays, pas de poses de victoire** (0 occurrence).
**H3 🟠 Pas de musique/ambiance dynamique.** Les SFX ont été remasterisés, mais il n'y a pas de soundtrack par mode ni de montée d'intensité sur la dernière minute.
**H4 🟡 Les « moments » scénarisés sont partiels.** Vestiaire 3D : oui. Intro de match, hymne, caméra du buzzer beater, confettis de championnat : à vérifier/compléter.
**H5 🟡 Aucune arène saisonnière** (Halloween/Noël) — coût faible, impact visuel énorme.

### I. Onboarding & accessibilité

**I1 🟠 Pas de parcours guidé « 30 premières minutes »** (objectifs, première victoire récompensée, prêt de cosmétique). Le training existe, mais rien ne dit au nouveau quoi faire ensuite.
**I2 🟡 4 langues (EN/FR/ES/DE).** CraftLandia (7 075) prouve la valeur du régional profond (PT-BR, ES-LATAM).
**I3 🟡 Pas de mode « regarder avant de jouer »** pour un nouveau joueur.

### J. Ce qui existe mais est mal fait

| Problème | Détail |
|---|---|
| **Promesse non tenue** | « ꜱᴇᴀꜱᴏɴ ɪ » affiché sans mécanique de saison → effet inverse de l'effet recherché |
| **Ladder non crédible** | ELO sans anti-triche ni saison = classement décoratif |
| **Cosmétiques sans désir** | 12 fichiers de cosmétiques, mais pas de rareté, pas de rotation, pas de loot |
| **Menu labyrinth** | `GuiMenuManager` dépasse 2 600 lignes : beaucoup d'écrans, peu de hubs clairs. Cytooxien a 4 portes d'entrée maximum |
| **Monde mono-serveur** | Aucune permanence possible (le 2b2t tire sa légende de son monde unique) |
| **Pack lourd, dépendant du client** | 3,5 Mo + mux maison : excellent sur Java, rédhibitoire pour Bedrock/mobile |
| **Aucun canal de croissance** | Pas de créateur, pas de clip auto, pas d'API, pas de page de stats : la croissance ne peut venir que du bouche-à-oreille |

---

## 5. Feuille de route proposée (impact décroissant / effort croissant)

### Horizon 1 — 2 à 4 semaines : « ça redevient un sport »
1. **Saison classée** : reset, placements, récompenses, Hall of Fame archivé, badge (A1)
2. **Anti-triche Grim + checks ballon** (G1) — sans ça, le reste ne vaut rien
3. **bStats + PlaceholderAPI + endpoint JSON minimal** (E1)
4. **Arène saisonnière** (H5) + **10 emotes** (H2)
5. **Rating par mode** (B3)

### Horizon 2 — 1 à 2 mois : « ça devient une plateforme »
6. **Battle pass cosmétique** 50 paliers (A2)
7. **Tournois bracket + overlay** (B1, B2)
8. **Boutique web Tebex + devise premium + caisse** (F1, L2)
9. **Bot Discord + rôles liés au rang** (E2)
10. **Programme créateur** : rang, `/nick`, realm perso 20 joueurs (C3, C4)

### Horizon 3 — trimestre : « ça devient un marché »
11. **Geyser/Floodgate + pack Bedrock dédié** (D1) — double le marché adressable
12. **Velocity + sharding** (D2)
13. **Pipeline d'assets `item_model` / Nexo** (H1) : objets 3D, meubles, HUD
14. **Page web de stats + API complète** (E3)
15. **Guerres de guildes saisonnières** (C2)

---

## 6. La phrase à retenir

Cytooxien ne gagne pas parce qu'il a un meilleur plugin : il gagne parce qu'il **produit du contenu à cadence fixe, mesure ce qui plaît, et le remet en rotation**. DonutSMP gagne parce qu'il a **une boucle économique lisible en 30 secondes** et des créateurs qui la filment. Hypixel gagne parce qu'il **ouvre ses données**.

BosketBall a déjà ce que la plupart n'auront jamais : un gameplay original, un vestiaire 3D, une physique custom, des bots, des replays, un pack maison. Ce qui lui manque n'est pas du **travail**, c'est du **système** : une saison, un pass, un tournoi, une API, un anti-triche, un store, et du Bedrock.

---

## 7. Sources

- Population live et records : `livecounts.nl/minecraft-player-count/`, `livecounts.nl/blog/biggest-minecraft-servers/`
- Cytooxien : `cytooxien.net/en`, `/wiki/minecraft-party`, `/random-item-battle-release`, `/minecraft-party-coin-update`, `/14-years-anniversary`, `/en/wiki/apply-for-vip`
- DonutSMP : `donutsmp-mc.com`, `minecraft.page/server/donutsmp.net`
- Hypixel : `hypixel.net`, `developer.hypixel.net`, `apis.io/apis/hypixel/hypixel-skyblock-api/`
- Wynncraft : `en.wikipedia.org/wiki/Wynncraft`, `github.com/wynncraft`, `nori.fish`, `github.com/topics/wynncraft`
- Origin Realms : `originrealms.news/volumes/volume-10/`, `r/OriginRealms`, `spigotmc.org`
- Stack 2026 : `docs.nexomc.com`, `r/admincraft`, `minecraft.pl/wiki/plugins/catalog/itemsadder`
- Paladium : `minecraftservershq.com/server/paladium`, `9minecraft.net/paladium-pvp-launcher/`
- Mineplex / 2b2t : `mcrpg.com` (PvP roundup)
