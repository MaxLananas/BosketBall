# Réduire le ping des joueurs français quand le serveur est à Los Angeles

Le serveur reste où il est. Ce document liste, dans l'ordre, ce qui **marche vraiment**, ce qui
ne sert à rien, et comment le vérifier au lieu de deviner.

## 1. La limite physique, d'abord

Paris ↔ Los Angeles ≈ 9 000 km de fibre. Un aller-retour ne peut pas descendre sous
**~140–160 ms**, quel que soit l'hébergeur, la box du joueur ou le plugin miracle vendu sur
Internet. Si vos joueurs français sont à 145 ms avec 0 % de perte, **le réseau fait déjà son
travail** : ce qu'ils ressentent comme « ping de malade » est alors de la **perte, de la gigue ou
des rafales de paquets** (chunks, entités, chat), pas la latence brute.

Donc : mesurer avant de changer quoi que ce soit.

## 2. Mesurer (5 minutes)

Côté joueur français, dans un terminal (ou `ping.pe`, qui teste depuis plusieurs pays) :

```bash
mtr -rwc 100 <ip-du-serveur>      # regarde la colonne Loss% et la dernière ligne
ping -c 100 <ip-du-serveur>       # mdev = gigue, packet loss = perte
```

Trois verdicts possibles :

| Ce que vous voyez | Diagnostic | Action |
|---|---|---|
| ~145 ms, 0 % perte, gigue < 10 ms | la route est bonne | passer au §3 (le problème est ailleurs) |
| ~145 ms mais jeu saccadé, chunks lents | rafales / débit / TCP | §3 |
| > 200 ms, ou perte sur un saut intermédiaire | **route cassée** (peering) | §4 (relais) |

Côté serveur, `/bb net <joueur>` (déjà dans le plugin) donne le ping mesuré et le verdict par
joueur, et `/bb pack test` chronomètre chaque route de téléchargement du pack.

## 3. Si la latence est normale mais que le jeu « pique »

C'est le cas le plus fréquent sur un hôte américain : la latence est correcte, mais chaque paquet
perdu coûte une retransmission TCP, et Minecraft (TCP) déteste ça.

### 3.1 TCP BBR sur la machine du serveur — le gain le plus net

CUBIC (le défaut) interprète une perte comme une congestion et divise son débit. Sur un lien
transatlantique avec 0,5 % de perte, cela suffit à faire ramper les chunks. BBR non.

```bash
# /etc/sysctl.d/99-bosketball-net.conf
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
net.ipv4.tcp_slow_start_after_idle = 0
net.ipv4.tcp_mtu_probing = 1
net.ipv4.tcp_notsent_lowat = 131072
```

```bash
sysctl --system && sysctl net.ipv4.tcp_congestion_control   # doit afficher bbr
```

> Sur un hébergement de jeu type Pterodactyl, vous n'avez pas la main sur ces réglages : demandez
> à l'hôte d'appliquer BBR (5 minutes pour lui), ou passez par le relais du §4, qui applique ces
> réglages sur **votre** machine.

`tcp_mtu_probing` mérite d'être là : le dossier de diagnostic réseau
([`network-diagnostics.md`](network-diagnostics.md), §5) a déjà montré un cas où l'MTU cassait les
gros paquets, ce qui ressemble exactement à « le pack met 10 ans » ou « les chunks n'arrivent pas ».

### 3.2 Moins de paquets, pas plus de débit

Déjà en place dans les configs livrées (`view-distance=6`, `simulation-distance=4`,
`network-compression-threshold=256`, `region-file-compression=lz4`). Si le serveur rame au moment
des connexions, regardez en plus :

```yaml
# server.properties
max-joins-per-tick: 3
```

```yaml
# config/paper-world-defaults.yml
chunks:
  chunk-loading:
    autosave-interval: 6000
entities:
  tracking-range:
    player: 48
    mob: 48
    other: 48
```

Ne descendez pas plus bas sans mesurer : un `view-distance` trop court se voit tout de suite en jeu
et n'améliore pas la latence, seulement la bande passante.

### 3.3 Le pack de ressources (le vrai « 10 ans »)

Il est désormais servi par jsDelivr + `raw.githubusercontent`, donc en quelques secondes au lieu de
5 minutes. Voir [`resource-pack-mirrors.md`](resource-pack-mirrors.md). C'est souvent **ce seul
point** qui faisait croire à un ping catastrophique : un joueur qui télécharge le pack ne joue pas.

## 4. Si la route est cassée : un relais TCP à Paris (le serveur ne bouge pas)

Un relais ne bat pas la vitesse de la lumière : il **remplace une mauvaise route par une bonne**.
Sur une route à 250 ms avec 2 % de perte, un relais bien peeré à Paris fait retomber à ~150 ms et
supprime les retransmissions. Le serveur de jeu reste à Los Angeles ; seule l'adresse d'entrée
change.

```
joueur (France) → relais Paris (10 ms) → serveur LA (140 ms) → total ≈ 150 ms
```

Deux options gratuites :

### 4.1 VM gratuite Oracle Cloud (région Paris `eu-paris-1` ou Marseille)

Toujours gratuit, mais : la région « home » se choisit à l'inscription, les instances ARM sont
parfois indisponibles (« out of host capacity »), et une carte est demandée pour vérifier le
compte (aucun débit sur l'offre gratuite).

```bash
# sur la VM (Debian/Ubuntu), relais TCP pur, sans terminer la connexion
sudo sysctl -w net.ipv4.ip_forward=1

sudo nft add table ip bb
sudo nft 'add chain ip bb prerouting { type nat hook prerouting priority dstnat; }'
sudo nft add rule ip bb prerouting tcp dport 25565 dnat to <IP_DU_SERVEUR_LA>:25565
```

Puis, dans le DNS, un enregistrement **SRV** sur votre domaine :

```
_minecraft._tcp.play.mondomaine.fr. 3600 IN SRV 0 5 25565 relais.mondomaine.fr.
```

(ou simplement donner aux joueurs l'adresse du relais). Le trafic montant revient par la même
connexion : le NAT suffit pour du Minecraft Java.

Variante `gost` si vous préférez un démon en espace utilisateur :

```bash
gost -L tcp://:25565/<IP_DU_SERVEUR_LA>:25565
```

Ajoutez au relais les réglages BBR du §3.1 : c'est là qu'ils rapportent le plus.

### 4.2 Tunnel playit.gg (le plus simple, zéro VM)

Un agent s'installe sur le serveur, vous donne une adresse `xxx.playit.gg`. Gratuit, mais vous
dépendez de leurs points de présence : à tester, cela peut être meilleur **ou** pire que la route
directe selon l'endroit où ils font atterrir le trafic.

> À l'inverse, **Cloudflare gratuit ne proxifie pas le TCP Minecraft** (il faut Spectrum, payant),
> et les accélérateurs type ExitLag/WTFast sont payants et n'aident que si la route est réellement
> cassée.

## 5. Côté joueurs : ce qui change vraiment quelque chose

* **Câble Ethernet** au lieu du Wi-Fi : c'est le premier facteur de gigue, loin devant tout le reste.
* **Rien à gagner** en changeant de DNS (8.8.8.8, Cloudflare…) : le DNS résout une adresse, il ne
  transporte pas les Paquets du jeu.
* **VPN grand public = pire** (un saut de plus), sauf si leur FAI a une route cassée : dans ce cas
  le relais du §4 fait le même travail, en mieux et pour tout le monde.
* Tester avec `mtr` **avant** d'accuser le serveur : si la perte apparaît sur un saut de leur FAI,
  aucun réglage côté serveur n'y changera rien.

## 6. Récapitulatif

| Priorité | Action | Gain attendu |
|---|---|---|
| 1 | pack via jsDelivr (§3.3) | connexion jouable en secondes, plus de faux « lag » |
| 2 | BBR + `tcp_mtu_probing` sur l'hôte (§3.1) | chunks et coups beaucoup plus fluides sous perte |
| 3 | moins de rafales (§3.2) | moins de micro-freezes à la connexion |
| 4 | relais Paris (§4) **si** la route est mauvaise | −50 à −100 ms et perte quasi nulle |
| — | changer de DNS, VPN pour les joueurs | aucun effet (ou pire) |
