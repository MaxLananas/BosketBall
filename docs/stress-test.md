# Test de charge — simuler 50 joueurs sans aucun joueur

Tu es seul sur le serveur ? Parfait : cette commande fabrique la charge à ta place.
`/bb stress` fait tourner **le vrai pipeline du plugin** pour N joueurs simulés, mesure ce que
ça coûte par tick, puis nettoie tout. Aucune connexion réseau, aucun faux `Player`, rien d'écrit
sur le disque (les profils simulés ne rentrent jamais dans `stats.yml`).

## 1. Recette console

```
/bb stress                 # 50 joueurs simulés, 400 ticks simulés (20 s de jeu) : le test de référence
/bb stress 100             # 100 joueurs simulés
/bb stress 100 1200        # 100 joueurs, 1200 ticks simulés (60 s de jeu)
/bb stress stop            # coupe net et supprime tout (alias : loadtest, simulate)
```

Durée réelle : 400 ticks simulés = **40 ticks réels = 2 s**. Les ticks simulés sont joués 10 par
tick réel, donc le serveur continue de ticker normalement pendant la mesure et le TPS affiché
reste honnête.

## 2. Ce qui est vraiment mesuré

| Simulé pour de vrai (même code que la production) | Non simulé (hors thread serveur) |
|---|---|
| Rendu de la sidebar : rang, ligne ELO, saison, party/amis, glow des grades, 15 lignes | Envoi des paquets (Netty) |
| Parsing MiniMessage + cache `Msg.parse` | Envoi des chunks |
| Churn scoreboard : objectif `bb_sidebar`, 15 teams, scores, `numberFormat` | Authentification Mojang |
| Cadence réelle : rafraîchissement toutes les 10 ticks | Latence client |
| 6 entités `ItemDisplay` par joueur simulé, tickées par le serveur | — |

Les profils simulés sont répartis sur toute l'échelle de rangs (400 → 2400 ELO, 50 ELO distincts
pour 50 joueurs), donc les branches "grade pill", "prochain rang" et "progression" sont toutes
exécutées — pas seulement le cas facile.

## 3. Lire le rapport

```
Stress test — 50 players
 ▸ Simulated players: 50 · ticks: 400 · wall: 2.01 s
 ▸ Sidebar: 2000 renders · 0.031 ms/tick at 50 players (0.0006 ms per render)
 ▸ Tick budget: 0.1 % of 50 ms → ✅ comfortable
 ▸ Display entities: 300 · avg tick: 0.42 ms (before test 0.28 ms)
 ▸ TPS: 20.00 · heap: 840 → 902 Mo
 Note: packets, chunk sending and authentication run off the server thread and are not part of this number.
```

- **`ms/tick` (sidebar)** : ce que la sidebar coûterait au thread serveur avec ce nombre de joueurs
  réels. C'est la projection à comparer aux 50 ms d'un tick.
- **`ms per render`** : coût d'un joueur pour un rafraîchissement (toutes les 0,5 s). C'est le
  chiffre qui scale linéairement : `joueurs × ms per render ÷ 10 = ms/tick`.
- **Verdict** : ≤ 5 % du tick = confortable, ≤ 20 % = visible mais sain, ≤ 50 % = lourd,
  > 50 % = trop cher.

## 4. Protocole complet (serveur vide)

```
/bb perf reset
/bb stress 50
/bb perf                 # doit rester sous 5 ms sur chaque étape
/spark profiler start --timeout 30
/bb stress 100 1200
/spark profiler stop
```

Seuils d'acceptation (identiques à `docs/perf-baseline.md`) : MSPT médian < 15 ms, p95 < 30 ms,
TPS 20.00, CPU < 60 %, heap < 2,4 Go, 0 GC vieille génération, aucune étape `/bb perf` > 5 ms.

Si la projection dépasse 20 % du tick, appliquer les leviers **dans cet ordre** :
`view-distance 5` → `schematic-courts.max-blocks-per-tick: 2000` → `holo-refresh-seconds: 20`
→ heap 4096M → 3e vCPU.

## 5. Garanties de nettoyage

- Toutes les entités portent le tag `bosketball_stress` et sont créées `persistent: false`.
- Fin normale, `/bb stress stop`, exception, `reload` ou arrêt du serveur : `purgeStressEntities()`
  balaie tous les mondes et supprime les restes (aussi appelé au démarrage, donc un crash ne peut
  pas laisser de décor fantôme).
- Le cache de rendu des profils simulés est purgé (`clearPlayerCache`), donc les runs répétés ne
  font pas grossir la mémoire.
- Aucune écriture `stats.yml` / `holograms.yml` / `config.yml`.

## 6. Limites à connaître

- La mesure est **CPU thread serveur**. Un vrai joueur ajoute en plus le coût réseau, déjà déporté
  sur Netty, et le chargement de chunks à son arrivée : le chiffre réel sera légèrement supérieur
  au premier tick, pas en régime établi.
- Les mannequins/matches ne sont pas simulés (il faudrait de vrais `Player`) ; pour la charge de
  match, `forcestart` + bots externes restent la seule voie honnête.
- Le test écrit dans les logs le résumé (`[Stress] …`), utile pour comparer deux réglages.
