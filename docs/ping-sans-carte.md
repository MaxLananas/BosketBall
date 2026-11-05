# Baisser le ping des joueurs français : le tutoriel zéro euro, zéro carte bancaire

Serveur à Los Angeles, joueurs en France. Tout ce qui suit est **gratuit**, **sans carte
bancaire**, et faisable **sans déménager le serveur**. Fais les étapes dans l'ordre : chacune se
teste en 2 minutes, et on s'arrête dès que ça suffit.

> Rappel utile : Paris ↔ Los Angeles, c'est 9 000 km de fibre. **140–160 ms de ping est le
> plancher physique.** Personne ne descendra en dessous. Ce qu'on peut supprimer, c'est la
> **perte de paquets**, la **gigue** et les **rafales** — c'est ça qui donne la sensation de
> « ping de malade » et de rubber-banding.

---

## Étape 0 — Mesurer (2 minutes, gratuit)

1. Depuis un PC en France, ouvre <https://ping.pe> et colle l'IP de ton serveur : tu vois le ping
   et la perte depuis plusieurs pays.
2. En jeu, en tant qu'admin : `/bb net <pseudo>` → le plugin affiche ping, gigue, perte ressentie,
   et maintenant une ligne **Host TCP** (voir étape 2).

Trois résultats possibles :

| Résultat | Diagnostic | Va à |
|---|---|---|
| ~145 ms, 0 % de perte | le réseau va bien, le problème est ailleurs | étape 1 |
| ~145 ms mais saccades, chunks lents | perte / rafales | étape 2 |
| > 200 ms ou perte sur un saut | route cassée (peering) | étape 3 |

---

## Étape 1 — Le pack de ressources en CDN (déjà fait, 0 €)

Un joueur qui téléchargeait le pack à 12 ko/s ne jouait pas : c'est souvent **ça** le « ping
catastrophique ». Le pack est maintenant servi par **jsDelivr** (CDN mondial, POP à Paris,
trafic illimité) + `raw.githubusercontent`, voir [`resource-pack-mirrors.md`](resource-pack-mirrors.md).

Vérification : `/bb pack test` doit afficher les deux routes en `OK` avec plusieurs Mo/s.

---

## Étape 2 — Réglages serveur, 100 % gratuits, dans ton contrôle (10 minutes)

### 2.1 Ce que tu peux modifier toi-même (pas besoin de l'hébergeur)

Dans le panneau de ton hébergeur (fichiers FTP / gestionnaire de fichiers) :

```properties
# server.properties
max-joins-per-tick=3            # évite le pic de paquets quand un groupe se connecte
network-compression-threshold=256
view-distance=6                # déjà réglé
simulation-distance=4          # déjà réglé
```

```yaml
# config/paper-world-defaults.yml  (ou spigot.yml selon la version)
entities:
  tracking-range:
    player: 48
    mob: 48
    other: 48
```

Redémarre, puis refais `/bb net <pseudo>` : moins de micro-freezes à la connexion.

### 2.2 BBR : le vrai levier, et c'est gratuit — mais c'est l'hébergeur qui l'active

Cubic (le réglage par défaut) comprend une perte de paquet comme « le réseau est saturé » et divise
son débit. Sur un lien transatlantique avec 0,5 % de perte, tes chunks rampent. **BBR** ignore ce
raisonnement et s'adapte à la latence réelle.

Tu peux le **vérifier toi-même** sans rien installer : `/bb net <pseudo>` affiche désormais

```
▸ Host TCP: cubic • qdisc fq_codel • rmem_max 212992 • mtu-probing 0  (CUBIC: ask the host ...)
```
ou
```
▸ Host TCP: bbr • qdisc fq • rmem_max 16777216 • mtu-probing 1  (long-haul friendly)
```

Si c'est `cubic`, envoie ce ticket (copier-coller) :

> Bonjour,
> Notre serveur Minecraft (IP : … , port : …) héberge des joueurs européens et le lien vers
> Los Angeles souffre de perte de paquets. Pouvez-vous activer, sur la machine qui héberge notre
> instance, les réglages réseau suivants ?
>
> ```
> net.core.default_qdisc = fq
> net.ipv4.tcp_congestion_control = bbr
> net.ipv4.tcp_mtu_probing = 1
> net.ipv4.tcp_slow_start_after_idle = 0
> net.core.rmem_max = 16777216
> net.core.wmem_max = 16777216
> ```
> Ce sont des réglages noyau standards (BBR est dans Linux depuis 4.9), sans risque, réversibles,
> et c'est le gain le plus efficace sur un lien transatlantique. Merci !

Un bon hébergeur le fait en quelques minutes. Ce n'est pas payant, ça ne demande aucune carte, et
ça ne touche pas au matériel.

---

## Étape 3 — Si la route est cassée : tunnel gratuit, sans carte bancaire

C'est le seul cas où il faut un intermédiaire. Un tunnel **ne bat pas la vitesse de la lumière** :
il remplace une mauvaise route par une bonne. Sur une route à 230 ms avec de la perte, tu peux
retomber à ~150 ms.

### Option A — `playit.gg` (0 €, sans carte, 5 minutes)

Gratuit à vie : **4 ports, 2 agents, tunnels Minecraft Java/Bedrock**, aucun limite de joueurs ni de
bande passante annoncée. Les tunnels gratuits passent par leur réseau **anycast** (le choix de la
région est réservé au Premium à 3 $/mois) : il faut donc tester.

1. Crée un compte gratuit sur <https://playit.gg>.
2. Télécharge l'agent (`playit`) sur la machine du serveur et lance-le.
   ⚠️ Ça demande de **pouvoir exécuter un binaire** sur la machine : sur un hébergement Pterodactyl
   fermé, demande à l'hôte s'il autorise un process supplémentaire (sinon → Option B ou étape 4).
3. L'agent affiche un **code de réclamation** : ouvre-le, l'agent s'ajoute à ton compte.
4. Tableau de bord → **Add tunnel** → *Minecraft Java* → cible `127.0.0.1:25565`.
5. Tu obtiens une adresse du type `truc.joinmc.link` : donne-la aux joueurs (ou un enregistrement
   SRV sur ton domaine).
6. **Teste, puis compare** : `/bb net` des joueurs français avant/après. Si le tunnel route par les
   États-Unis, le ping ne bougera pas ; recrée un tunnel jusqu'à tomber sur un point européen
   (astuce connue : plusieurs tunnels gratuits, on garde le meilleur).

### Option B — TCPShield Free (0 €, sans carte, 1 To/mois)

Pensé pour les serveurs Minecraft : filtrage DDoS + proxy, offre gratuite qui couvre ~20–30 joueurs
connectés. Attention : leur montage passe par un **proxy Velocity/BungeeCord** que tu dois lancer
toi-même ; si ton hébergeur ne permet qu'un seul processus Paper, cette option ne s'applique pas.
À réserver au cas où tu peux ajouter un proxy sur la même machine.

### Ce qui n'existe pas (et qu'il ne faut pas chercher)

* **VPS gratuit sans carte bancaire** : Oracle, Google Cloud, AWS, Azure demandent tous une carte
  pour vérifier le compte. C'est la raison pour laquelle la solution « relais à Paris » décrite dans
  [`latency-europe.md`](latency-europe.md) n'est pas dans ce tutoriel.
* **Cloudflare gratuit** ne proxifie pas le TCP Minecraft (il faut Spectrum, payant).
* **VPN pour les joueurs** : un saut de plus, donc pire — sauf si leur FAI a une route cassée, et
  dans ce cas le tunnel de l'option A fait le même travail pour tout le monde.
* **Changer de DNS** (8.8.8.8, Cloudflare…) : aucun effet, le DNS ne transporte pas les paquets.

---

## Étape 4 — Le seul vrai remède s'il reste > 200 ms

Si après l'étape 2 le ping reste au-dessus de 200 ms **avec de la perte sur un saut situé chez un
transitaire**, le problème est chez l'hébergeur, pas chez toi. Deux leviers, tous deux gratuits
côté démarche :

1. Ouvrir un ticket en joignant la sortie de `/bb net` et un `mtr` (100 paquets) depuis la France,
   en demandant si un **réseau premium / meilleur peering Europe** existe sur ton offre.
2. Demander explicitement **BBR** (étape 2.2) : certains hôtes ne l'activent que sur demande.

---

## Récapitulatif

| Étape | Coût | Carte bancaire | Temps | Gain |
|---|---|---|---|---|
| Pack en CDN | 0 € | non | fait | connexion jouable en secondes |
| `max-joins-per-tick`, tracking | 0 € | non | 5 min | moins de freezes à la connexion |
| **BBR + mtu-probing** (ticket hôte) | 0 € | non | 10 min | le plus gros gain sous perte |
| Tunnel `playit.gg` | 0 € | non | 15 min | −50 à −100 ms **si** la route est cassée |
| TCPShield Free | 0 € | non | 30 min | idem, mais impose un proxy Velocity |

Commence par l'étape 2.2 : c'est gratuit, ça se vérifie en une commande (`/bb net`), et c'est ce
qui change réellement le ressenti des joueurs français.
