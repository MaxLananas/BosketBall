# Diagnostic réseau — « pourquoi ce joueur a un ping de malade ? »

Ce document explique comment répondre à la question la plus fréquente du support :
**un joueur français a 350 ms alors que les autres joueurs français sont à 40 ms.**
La réponse n'est presque jamais « le serveur », et elle n'est pas non plus « le joueur » :
c'est un **chemin réseau** entre les deux. Ce guide donne les commandes pour le prouver
en 2 minutes, puis les actions concrètes.

---

## 1. La commande de diagnostic : `/bb net [joueur]`

```
/bb net              → soi-même (staff)
/bb net friandise    → un autre joueur en ligne
```

Alias : `/bb ping`, `/bb latency`. Permission : `bosketball.staff`.

La carte affichée contient :

| Ligne | Ce qu'elle dit |
|---|---|
| `Latency` | dernier keep-alive, moyenne, min, max et **gigue** sur les 30 dernières secondes |
| `Client` | marque du client (vanilla / Lunar / Feather / …) et view-distance demandés |
| `Pack` | `loaded` ou `downloading (route 2/3, 3376838 B)` — un téléchargement en cours |
| `Server` | TPS, MSPT du dernier tick, joueurs en ligne, `view-distance` effectif |
| `Everyone` | tous les joueurs triés par ping, la cible en jaune |
| `Verdict` | la conclusion automatique (voir §2) |

Les mesures sont prises **une fois par seconde** (`Player#getPing()`, qui est exactement
le round-trip du keep-alive, donc ce que le joueur ressent). Coût : 30 entiers par joueur,
aucun paquet envoyé, rien à installer côté client.

En plus, si un joueur reste **6 secondes consécutives au-dessus de 250 ms**, la console
écrit une ligne `[Net] …` (au maximum une toutes les 5 minutes par joueur) avec les mêmes
chiffres. Le problème devient donc visible dans les logs mêmes si personne n'a tapé de
commande à ce moment-là.

---

## 2. Lire le verdict

| Situation mesurée | Interprétation | Où agir |
|---|---|---|
| Cible ≥ 250 ms, **tous les autres < 80 ms** | `Isolated to this connection` | Côté joueur (§3) |
| Cible ≥ 250 ms **et** plusieurs autres aussi | `Shared degradation` | Lien de l'hôte / anti-DDoS (§4) |
| Ping moyen correct mais **gigue > 60 ms** | perte de paquets / lien saturé (bufferbloat) | §3 puis §4 |
| `Pack: downloading` | 3,38 Mo en cours de transfert sur un lien lent | attendre / `external-url` (§5) |
| MSPT > 40 ms **et** TPS < 19 | le serveur lui-même rame (ce n'est pas du réseau) | `/bb profiler` |

Règle simple : **la latence d'un seul joueur n'est jamais un problème serveur.**
Le serveur n'envoie pas un paquet par joueur différemment des autres : si 9 joueurs sont
à 40 ms et un à 400 ms, la différence est sur son chemin, son Wi-Fi, son VPN ou son FAI.

---

## 3. Côté joueur (à lui faire exécuter / vérifier)

1. **Test câble vs Wi-Fi.** Un Wi-Fi 2,4 GHz ou du CPL (courant porteur) donne
   typiquement 60–300 ms de gigue. Demander un test en Ethernet : si le ping tombe
   sous 80 ms, le problème est le réseau local, pas le serveur.
2. **Couper VPN / Cloudflare WARP / NordVPN / opérateur « gaming ».** Un VPN ajoute un
   détour (souvent Paris → Francfort → Paris) et du bufferbloat. Très fréquent chez les
   joueurs qui « optimisent » leur connexion.
3. **Mesurer le chemin réel** vers l'IP du serveur (Windows : WinMTR ou
   `pathping <ip>` ; macOS/Linux : `mtr -c 100 <ip>`) :
   - premier saut déjà à 150 ms → box / Wi-Fi ;
   - un saut précis explose puis les suivants restent hauts → peering FAI ↔ hébergeur ;
   - perte de 5–20 % sur un saut intermédiaire seulement → souvent un routeur qui
     déprioritise l'ICMP : vérifier si la perte continue jusqu'au dernier saut.
4. **Upload saturé.** Sauvegarde cloud, OneDrive/Google Drive, mise à jour Windows,
   stream, autre appareil du foyer : sur ADSL (1 Mb/s d'upload), 200 ko/s suffisent à
   faire grimper le ping à 400 ms. Tester box seule, tout le reste débranché.
5. **Test croisé 4G.** Partage de connexion mobile : si le ping s'effondre, le problème
   est la ligne fixe / le peering de son opérateur (Orange/Free/SFR/Bouygues ne sont pas
   égaux vers un même hébergeur).
6. **Minecraft est 100 % TCP.** Il n'y a pas d'UDP à « ouvrir » : la moindre perte de
   paquet provoque une retransmission et un pic visible (head-of-line blocking). Un lien
   qui « marche » en navigation peut être catastrophique en jeu.

Message prêt à copier-coller au joueur :

```
Salut ! Pour qu'on trouve d'où vient ton ping :
1) /bb net <ton_pseudo> et envoie-moi la capture.
2) Tu joues en Wi-Fi, CPL ou câble ? Un VPN est-il actif ?
3) Ouvre WinMTR (ou `mtr <ip-serveur>`) et laisse tourner 100 paquets, envoie le résultat.
4) Teste en partage de connexion 4G : si c'est fluide, c'est le routage de ta ligne fixe.
```

---

## 4. Côté serveur / hôte

- `/bb net` doit montrer **TPS 20.00 et MSPT < 10 ms** : si c'est le cas, le serveur
  n'est pas en cause, point final.
- Sur la machine hôte, vers l'IP publique du joueur (si la politique de l'hébergeur
  l'autorise) : `mtr -c 100 -rw <ip>` — on voit le premier saut qui déraille.
- Retransmissions TCP : `ss -ti | grep -E "retrans|rtt"` et
  `netstat -s | grep -i retrans` pour confirmer la perte.
- **Routes du pack** : `/bb net` affiche `downloading (route 2/3 …)` si le client a
  échoué sur la première URL (mux HTTP sur le port de jeu, puis port `8765`, puis URL
  externe). Un joueur dont le FAI bloque le port 8765 retélécharge 3,38 Mo à chaque
  essai : cela sature sa ligne et ressemble à un ping catastrophique. Correctif : définir
  `resource-pack.external-url` vers un hébergement HTTPS standard (GitHub release / CDN).
- Anti-DDoS de l'hébergeur : un filtrage en mode « challenge » ajoute du RTT et coupe
  parfois les keep-alives. Vérifier qu'aucune attaque n'est en cours pendant les tests.
- `connection-throttle` de Paper ne concerne que les reconnexions : inutile ici.

Petit rappel du dimensionnement envoyé dans `docs/server-optimization/` :
`view-distance: 6`, `simulation-distance: 4`, pas de mobs, hologrammes en display
entities, `entity-tracking-range` borné à 96 (le bubble de 6 chunks) — la charge réseau
par joueur est donc faible et **identique pour tous**.

---

## 5. Étude de cas réelle (log client du 05/11, joueur « friandise », France)

Le joueur a envoyé son `latest.log` complet. La chronologie ne laisse aucune place au doute :

| Heure | Événement (log client) |
|---|---|
| 20:35:53 | connexion à `bosketball.mc-hosting.xyz` |
| 20:36:13 | début du téléchargement du pack (`downloads/<uuid>/<sha1>`), `mismatched hash` sur la copie existante |
| 20:36:55 | chat : « jss a 12% » |
| 20:38:08 | chat : « jss a 5k ms » |
| 20:40:13 | chat : « 80% » |
| 20:41:19 | **le pack est enfin monté** (`Reloading ResourceManager: … server/00000000/…`) |
| 20:41:42 | reconnexion immédiate |
| 20:42:19 | pack remonté une seconde fois |
| 20:42:51 | `Client disconnected with reason: Internal Exception: java.net.SocketException: Connection reset` |
| 20:43:03 | reconnexion |

Enseignements :

1. **Le pack a mis ~5 minutes** pour 3,4 Mo ⇒ **≈ 12–14 ko/s** de débit utile sur une ligne
   annoncée à 1 Gbit/s. Ce n'est pas un problème de taille du pack : c'est le **débit réel du
   chemin** entre ce client et l'hôte (le serveur est à Los Angeles).
2. Le ping à 5 000 ms et le `Connection reset` arrivent **pendant/après** ce téléchargement :
   même cause réseau, pas le tick serveur (le serveur tourne à 20 TPS, MSPT < 10 ms).
3. Les lignes `Missing resource … /wide/npc_*.png` et `Invalid sounds.json` apparaissent
   **avant/à l'instant** du montage du pack : textures demandées alors que le pack n'est pas
   encore chargé, et zip potentiellement tronqué par un téléchargement interrompu. Après
   montage complet, plus aucune ligne de ce type.
4. `Transferred to another server` (plus tôt dans la journée) vient d'autres serveurs du
   réseau du joueur, pas de BosketBall.

Ce qu'il faut faire, dans cet ordre :

1. `/bb net friandise` → confirmer « Isolated to this connection » (lui seul au-dessus de 250 ms).
2. `/bb pack test` → mesure le débit **depuis le serveur** sur chaque route :
   `mux port de jeu` / `port 8765` / `URL externe`. Si le mux plafonne alors qu'une URL HTTPS
   dépasse 1 Mo/s, la réponse est là.
3. Faire héberger le pack hors du port de jeu :

```yml
resource-pack:
  external-url: "https://github.com/<compte>/<dépôt>/releases/download/pack/BosketBall-ResourcePack.zip"
  external-sha1: "<sha1 exact du zip>"   # /bb pack test le vérifie pour vous
```

   Une release GitHub sert en HTTPS via un CDN mondial : plus de mux sur le port 25565, plus de
   dépendance au proxy de l'hébergeur, et le navigateur du joueur peut reprendre/tester le débit.
4. Côté joueur : test câble, VPN coupé, `mtr -c 100 <ip>` (voir §3) et **test MTU** —
   `ping -f -l 1472 <ip>` sous Windows ou `ping -M do -s 1472 <ip>` sous Linux. Un « trou noir
   MTU » classique (Freebox/4G/VPN) donne exactement ce profil : petits paquets OK (keep-alive
   passe, ping « seulement » élevé), gros paquets perdus (téléchargement à 12 ko/s, chunk
   saccadés, RST). Correctif : `iptables -t mangle -A FORWARD -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu`
   sur l'hôte, ou VPN/box réglé en 1400 octets.
5. Surveiller : le plugin journalise désormais `[Pack] <joueur> is still downloading … at most N B/s`
   au bout de 45 s, avec le débit mesuré — plus besoin d'attendre le témoignage du joueur.

## 6. Ce que le serveur envoie (et ce qui a été allégé)

| Élément | Taille / coût |
|---|---|
| Pack de ressources | **3 376 838 octets** (contre 3 784 091 avant l'optimisation round 15, −10,8 %) |
| Textures PNG | 618/619 optimisées **sans changer un seul pixel** (`tools/optimize_resourcepack.py`) |
| JSON du pack | minifiés à l'emballage (mêmes valeurs, indentation retirée) |
| Par seconde et par joueur | 1 échantillon de ping, sidebar 1×/0,5 s, tab 1×/1,5 s, nametags 1×/1 s |

Le pack est donc 407 ko plus léger à télécharger : sur une ligne ADSL à 1 Mb/s, c'est
environ **3 secondes de moins** de saturation à la connexion, et surtout moins de risque
qu'un téléchargement en arrière-plan fausse la mesure de ping.

Sur une connexion fragile, `network-compression-threshold` (256 par défaut dans
`server.properties`) peut être abaissé à 128 : les paquets d'interface (sidebar, tab, hologrammes)
pèsent 200–400 octets et passent alors compressés. Coût CPU négligeable en dessous de 30 joueurs.

Pour vérifier après un changement d'art :

```
python3 tools/optimize_resourcepack.py --check    # 0 si le pack est déjà au minimum lossless
python3 tools/optimize_resourcepack.py            # ré-optimise (idempotent, pixel-identique)
python3 tools/build_plugin.py                     # re-zippe et resynchronise le SHA-1
```

Le script décode chaque candidat et le compare **octet pour octet** aux pixels d'origine
avant de l'accepter : aucune image ne peut être dégradée par erreur.
