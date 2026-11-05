# Display pipeline — sidebars, tab list, lobby leaderboards

Tout l'affichage vivant du serveur (sidebar, tab list, hologrammes de classement, Hall of Fame,
crate, tutoriel, caméra cinématique) était piloté par **une seule tâche répétée** :

```java
Bukkit.getScheduler().runTaskTimer(this, () -> { /* tout l'UI */ }, 1L, 1L);
```

C'est le piège classique : **Bukkit annule définitivement une tâche répétée dès qu'elle lève une
exception**, sans la relancer. Un seul NPE dans un sous-système (un joueur sans profil, un hologramme
invalide, un paste qui arrive au mauvais moment…) faisait donc disparaître **toute** la couche
d'affichage jusqu'au redémarrage suivant — sidebar, tab, leaderboards — ce qui ressemble exactement
à « mes scoreboards disparaissent à chaque reload ».

## Ce qui a changé

1. **Chaque sous-système est isolé** dans `step(nom, runnable)` : une exception est comptée et
   loggée **une fois** (stack trace), puis les autres étapes continuent. La tâche ne peut plus mourir.
2. **Watchdog** (`ensureDisplayPipeline`, toutes les 10 s) : si la tâche a été annulée, elle est
   recréée et les displays sont reconstruits immédiatement (`[Display] UI task had stopped ...`).
3. **Réconciliation au démarrage** (2 s après l'activation) et après `/bb reload` :
   hologrammes manquants re-spawnés, sidebar reconstruite pour tous les joueurs en ligne.
4. **Hologrammes auto-réparés** : les entités des stations sont `setPersistent(false)` (elles ne
   survivent pas à un redémarrage) ; `ensureAllSpawned()` recharge le chunk et re-spawn celles qui
   manquent, à chaque tick d'animation, toutes les 10 s par le watchdog et à la demande.
5. **Retour de match / sortie du locker 3D** : ces deux chemins remettaient le scoreboard principal
   (`setScoreboard(main)`) sans rien reconstruire → la sidebar ne revenait qu'au prochain tick utile.
   Elle est maintenant reconstruite dans la même frame.
6. **Sidebar par joueur protégée** : un profil cassé ne blanchit plus la sidebar de tout le monde.

## Diagnostiquer

```
/bb displays            # état live
/bb displays repair     # répare tout de suite (re-spawn + sidebar)
```

`/bb displays` affiche :

| Ligne | Signification |
|---|---|
| `UI task: alive` | la tâche tourne. `STOPPED` = elle a été annulée (le watchdog la relance sous 10 s) |
| `Fault <étape>: n` | nombre d'échecs par sous-système, avec la stack trace dans la console au 1er |
| `Lobby holograms: n · missing: m` | hologrammes configurés et entités manquantes |
| `Sidebar rendered: x/y` | joueurs en ligne ayant réellement l'objectif `bb_sidebar` |
| `Leaderboard index: n entries cached` | cache des classements utilisé par les hologrammes |

## Vérifications attendues dans les logs

Démarrage :

```
[Display] UI task alive · 6 lobby hologram(s) · 0 re-spawned · sidebar re-applied to 0 player(s)
```

Full restart avec joueurs en ligne : `sidebar re-applied to N player(s)`.
Après un `/kill @e[type=text_display]` (test) : `[Display] Re-spawned X missing lobby leaderboard/NPC entities.`

## Si un classement reste vide

Un hologramme de classement n'existe que s'il a été posé une fois (`plugins/BosketBall/holograms.yml`).
Si ce fichier est absent ou vidé, il n'y a rien à re-spawner : repose la station en jeu
(`/bb holo ...`) ou restaure le fichier — le plugin re-spawnera ensuite ses entités à chaque boot.
