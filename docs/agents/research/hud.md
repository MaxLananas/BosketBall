# Le HUD : une file, un contrat de largeur, une seule voix

Recherche et décisions de la passe « HUD » (branche `agent/hud`). Ce document explique *pourquoi* le
code est écrit comme il l'est ; il est en français, comme tous les documents d'ingénierie.

---

## 1. Le problème d'origine

Chaque sous-système écrivait directement dans la barre d'action :

```java
player.sendActionBar(Msg.parse("..."));
```

Si bien que le dernier écrivain du tick gagnait, et que personne ne savait ce que le joueur lisait
réellement. Trois bugs en sont sortis, tous reproduits en test :

1. **Deux courses dans le même tick.** Une décision d'arbitre (24 s écoulées) et un contre se
   produisaient au même tick ; le contre écrasait la décision 15 ms après que le joueur l'avait lue.
2. **Deux baux indépendants.** `MatchSession.actionBarNoticeTicks` (compteur de ticks, 10 à 28) et
   `Msg.RAIL_UNTIL` (horodatage) prétendaient tous deux posséder la ligne. Un `0` dans le premier et
   une expiration dans le second ne pouvaient pas être d'accord.
3. **Des phrases plus larges que leur propre pilule.** Rien ne mesurait le texte ; un pseudo de 16
   caractères suffisait à faire déborder l'artwork du pack.

## 2. Une seule file : `Hunt`

`util/Hunt.java` est la seule porte vers la barre d'action. Une `Frame` porte sa surface, son texte,
son horodatage d'affichage, son expiration et un compteur de fusions.

| Règle | Comportement |
| --- | --- |
| Bail minimum | Une frame tient la ligne pendant `minDisplayMs` de sa surface (`HudSurface`). |
| Priorité | Une priorité strictement supérieure peut couper un bail en cours ; une priorité inférieure ne le peut jamais. |
| Même surface | La surface se rafraîchit elle-même : nouvelle valeur, bail relancé, rien en file. Un compteur ne fait jamais la queue derrière lui-même. |
| Fusion | À l'expiration, un message de priorité inférieure arrivé pendant l'affichage est fusionné derrière le séparateur de la maison, s'il tient dans `MERGE_MAX_CHARS` (56) et `MAX_LINE_PX` (300). Sinon il reprend la ligne seul, compté dans `droppedTotal`. |
| Silence | `tick` n'envoie que si le texte a changé : un paquet économisé n'est pas un message perdu. |

Les priorités sont déclarées dans `util/HudSurface.java` (surface, priorité, bail, budget de
caractères) : `CINEMATIC 100`, `RULE 90`, `TUTORIAL 85`, `CONTEST 80`, `SHOT_METER 70`, `PROGRESS 60`,
`COACH 50`, `LOBBY 30`, `AMBIENT 10`. Il n'y a **pas** de surface pour l'horloge : les 24 secondes
sont dessinées sur l'affichage du panneau du terrain (`Arena.tickDisplays`), pas dans la barre
d'action, et une surface sans appelant est une seconde façon d'écrire la même ligne. Une priorité n'est pas une opinion : c'est la réponse à « qui a
le droit d'interrompre qui ». L'arbitre interrompt un contre ; un contre n'interrompt jamais l'arbitre.

### Les compteurs, remplacés par une surface

Le HUD de visée est redessiné à chaque tick. Il ne passe plus par
`if (!Msg.railOnScreen(p)) p.sendActionBar(...)`, mais par `Msg.sendHudMeter(...)` : la barre de
charge et la fenêtre de dunk sur `SHOT_METER` (bail de 120 ms, budget de 44 caractères), la boussole
d'état sur `AMBIENT` (priorité 10, **sans bail**) — parce que la boussole est dessinée pour un
porteur qui n'a pas de ballon en l'air et ne doit jamais interrompre une lecture de charge. Une
surface se rafraîchit elle-même et un composant déjà construit conserve son dessin : la file ne
décide que de la ligne, jamais du rendu.

`MatchSession.actionBarNoticeTicks` a été supprimé : onze écritures et un décrément, tous remplacés
par le bail de la file. Il ne reste plus un seul `sendActionBar(` hors de `Msg`.

## 3. Un contrat de largeur : `HudWidth`

Le pack dessine sa pilule jusqu'à `Glyphs.MAX_PILL_WIDTH = 176 px` ; ses deux capsules de 6 px
laissent **170 px** de texte (`MAX_PILL_TEXT_PX`). La constante réservait 20 px fantômes qui
n'existaient pas dans l'artwork et privaient de pilule les phrases les plus longues. Mesures :

* avance par défaut 6 px, gras +1, espace 4 ;
* `MAX_LINE_PX = 300` pour une ligne nue, `MAX_READABLE_CHARS = 44` pour la lecture en jeu.

`fit()` coupe au mot, ferme ses balises et signale la coupe par `…` ; `fitsPill()` répond oui ou non
avant l'envoi. `Msg.composeHudLine` ne dessine la pilule que si le texte y tient : une pilule plus
large que son artwork est pire que pas de pilule.

### La copie garantit sa propre largeur

`HudCopy.pillLine(...)` prend deux formes de détail : la phrase complète, puis une forme courte,
puis l'en-tête seul. Le test « la ligne légale la plus large est mesurée, pas devinée » construit un
pseudo de 16 caractères et vérifie que la ligne reste dans le budget. Un pseudo est la seule partie
non bornée d'une phrase de HUD ; il est donc la seule chose que la copie doit savoir raccourcir.

## 4. Une seule voix : `HudCopy`

Chaque action a deux participants, et chacun l'entend dans ses mots :

| Événement | Le lanceur | Le défenseur |
| --- | --- | --- |
| Contre | `Stuffed · Kobe at the rim` | `Pinned it · Max's shot` |
| Steal | `Took it · off Kobe` | `Knocked away · Max got it` |
| Ankles | `Ankles · Kobe hit the deck` | `Crossed · Max dropped you` |
| Passe décisive | `Pass · to Kobe` | `+1 AST · Kobe finished it` |

Aucune ligne ne décrit une animation (« Pass released », « Dunk started ») : le joueur voit
l'animation. La ligne dit la *situation* qui en résulte.

Contraintes de style, vérifiées par les tests existants : anglais à 100 % côté joueur, séparateur
unique `<#64748B>·</#64748B>`, aucune étoile, couleurs issues de `Palette` uniquement.

## 5. La barre latérale

`ui/SidebarLines.java` valide la liste avant le paquet : les lignes vides deviennent un
`—` discret (jamais une ligne supprimée — déplacer toutes les lignes en dessous est le
scintillement), les lignes trop larges sont coupées à `CONTENT_PX`, le délimiteur or reste premier et
dernier, et le plafond de quinze lignes du client est appliqué ici. `applyLines` remplace une ligne
disparue (`unregister` + `resetScores` dans la même passe) au lieu de la supprimer sèchement.

La tab list garde l'identité (pièces, rang, bilan, guilde) ; la barre latérale garde le match.

## 6. Ce que les tests démontrent

Treize tests `round85…` :

* priorité dans le même tick, et bail jamais écrasé de l'intérieur ;
* fusion livrée, fusion refusée quand elle déborderait, et comptée ;
* une surface ne fait pas la queue derrière elle-même ;
* le registre répond « est-ce mon message à l'écran » puis oublie ;
* chaque ligne tient dans la pilule et dans le budget de lecture ;
* la ligne légale la plus large est mesurée, pas devinée ;
* chaque action parle aux deux participants ;
* aucune ligne ne décrit une animation ;
* **seul le pipeline écrit dans la barre d'action** (grep sur les sources) ;
* deux joueurs dans le même tick gardent chacun leur ligne : un envoi immédiat n'est jamais
  ré-émis, ni effacé, par la pompe qui parcourt tous les joueurs du serveur ;
* deux messages du même tick se résolvent par la priorité documentée ;
* la barre latérale ne peut pas dessiner de trou au milieu.

## 7. Sources consultées

* BetterHud (deepwiki.com/toxicity188/betterhud) — HUD par paquets BossBar, diff d'état,
  `merge-boss-bar`, toujours prévoir un repli en chat.
* mctoolkit.net/hud-font-pack-tools — schéma du *bitmap provider*, cellules de largeur égale.
* SpigotMC, « Custom fonts/images in scoreboard & bossbar » — les packs HUD dessinent l'élément
  entier comme un seul glyphe privé.
* m1.material.io/style/color — l'accent secondaire ne doit pas concurrencer le principal ;
  beaucoup d'accents égaux détruisent la hiérarchie.
* fiveminsider.com — n'afficher que l'essentiel, un accent et un neutre, mettre à jour au
  changement et non au minuteur ; mesurer le coût en jeu.
