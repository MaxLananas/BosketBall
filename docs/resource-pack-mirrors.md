# Deux hébergeurs gratuits pour le pack de ressources (Europe + monde)

Objectif : le pack `BosketBall-ResourcePack.zip` (3 376 838 octets, SHA-1
`476ecec839d69d2c6ae8a505d97aa3161583642e`) doit pouvoir être téléchargé depuis **au moins deux
endroits dans le monde**, dont **un en France hexagonale**, pour que les joueurs français ne
téléchargent plus à 12–14 ko/s depuis un hôte de Los Angeles (voir
[`network-diagnostics.md`](network-diagnostics.md), §5).

Le pack reste **identique** partout : même ZIP, même SHA-1. Seule l'adresse change. Le serveur
intégré (port de jeu + port 8765) reste le filet de sécurité, en dernier recours.

---

## 1. Ce que fait le plugin

Dans `config.yml` :

```yaml
resource-pack:
  mirrors:
    - "https://<compte>.alwaysdata.net/BosketBall-ResourcePack.zip?v={sha1}"
    - "https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall@<commit>/releases/BosketBall-ResourcePack.zip"
```

* Les miroirs sont essayés **dans l'ordre** indiqué, avant le serveur intégré.
* `{sha1}` est remplacé par le SHA-1 du pack (pratique pour casser un cache CDN).
* `external-url` reste prioritaire **uniquement** si `mirrors` est vide (compatibilité).
* Une URL non `http(s)`, vide ou en double est ignorée et **n'est jamais envoyée** au client
  (test unitaire : `resourcePackMirrorsKeepOrderAndStayOnTheSameZip`).
* Le client vérifie le SHA-1 : si un miroir sert un ZIP différent, le pack est **refusé**. Tous les
  miroirs doivent donc être mis à jour avec le même fichier.

> Règle d'or : **un rebuild de pack = un nouveau fichier à pousser sur les deux hébergeurs.**
> Le build affiche le SHA-1 à chaque fois (`[2/5] Resource pack: … SHA-1 …`) et le recopie déjà
> dans `config.yml`. Utilisez un nom de fichier par version (`…-<sha1 court>.zip`) ou le paramètre
> `?v={sha1}` pour ne jamais servir une version périmée.

---

## 2. Quel hébergeur choisir

| Hébergeur | Pays du point de service | Gratuit | HTTPS | Quota utile pour 3,38 Mo | Carte bancaire |
|---|---|---|---|---|---|
| **alwaysdata** (plan gratuit) | **France, Paris** | oui | oui (Let's Encrypt) | 100 Mo disque, **5 Go/mois ≈ 1 480 téléchargements** | non |
| **jsDelivr** (miroir d'un dépôt GitHub) | POP Paris / Francfort / New York… | oui, illimité | oui | fichier ≤ 20 Mo, pas de limite de trafic | non |
| **Cloudflare Pages** | POP Paris (anycast) | oui, trafic illimité | oui | 25 Mo par fichier | non (compte gratuit) |
| **GitHub Releases** | Fastly, POP européens + US | oui, illimité | oui | ~2 Go par fichier | non |

Recommandation pour deux sources dans le monde :

1. **alwaysdata** → la source **France hexagone** (celle que vous demandez) ;
2. **jsDelivr** → la source **mondiale** (aucune inscription, aucun quota, POP à Paris) ;
3. le serveur intégré du plugin → filet de sécurité si les deux tombent.

Cloudflare Pages est la meilleure variante si vous préférez un simple glisser-déposer de dossier
(au lieu de SSH/SFTP) : trafic illimité, et le POP parisien sert la France aussi vite.

---

## 3. Recette A — alwaysdata, plan gratuit (serveur en France)

1. Créer un compte gratuit sur <https://www.alwaysdata.com> (aucune carte bancaire ; usage
   personnel, ce qui est notre cas pour un serveur Minecraft privé/communautaire).
   Le compte s'appelle par exemple `bosketball` → adresse `bosketball.alwaysdata.net`.
2. Dans l'interface d'administration : **Sites → Ajouter un site**, type **« Fichiers statiques »**
   (`Static files`), adresse `bosketball.alwaysdata.net`, racine du site `www/bosketball`
   (ou `/www` pour faire simple). Valider.
   * HTTPS : les certificats **Let's Encrypt** se génèrent tout seuls (menu
     **Avancé → SSL/TLS** si besoin de forcer « Let's Encrypt »).
3. Envoyer le ZIP dans le dossier du site. Deux méthodes équivalentes :

   ```bash
   # a) SFTP (adresse et identifiants affichés dans « Comptes → SSH/SFTP »)
   sftp bosketball@ssh-bosketball.alwaysdata.net
   put releases/BosketBall-ResourcePack.zip www/bosketball/BosketBall-ResourcePack.zip

   # b) SSH + scp
   scp releases/BosketBall-ResourcePack.zip \
       bosketball@ssh-bosketball.alwaysdata.net:www/bosketball/
   ```

4. Vérifier que le fichier est public et **de la bonne taille** :

   ```bash
   curl -sI https://bosketball.alwaysdata.net/BosketBall-ResourcePack.zip | grep -i -E "HTTP/|content-length|content-type"
   # attendu : HTTP/2 200, content-length: 3376838
   ```

5. Si le type MIME sort en `text/plain`, ajouter un fichier `.htaccess` à côté du ZIP :

   ```apache
   AddType application/zip .zip
   ```

> Le plan gratuit d'alwaysdata ne met pas les sites en veille : le premier octet part
> immédiatement. 5 Go/mois de trafic = ~1 480 téléchargements du pack par mois ; au-delà, il faut
> soit passer au plan supérieur, soit laisser jsDelivr absorber la charge (c'est l'intérêt d'avoir
> deux sources : vous pouvez inverser l'ordre des miroirs dans `config.yml` à tout moment).

---

## 4. Recette B — jsDelivr (source mondiale, zéro inscription)

Le ZIP est déjà versionné dans `releases/`. jsDelivr le sert gratuitement depuis un CDN mondial,
**sans limite de bande passante**, avec un POP à Paris.

* **URL épinglée sur un commit** (recommandé : le contenu ne changera jamais, donc pas de cache
  périmé) :

  ```text
  https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall@<sha_du_commit>/releases/BosketBall-ResourcePack.zip
  ```

  Récupérer `<sha_du_commit>` avec `git rev-parse HEAD` après avoir poussé le pack.

* **URL sur une release GitHub** (si vous préférez ne pas versionner le ZIP) :

  ```text
  https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall@<tag>/releases/BosketBall-ResourcePack.zip
  ```

  ou directement l'asset de release (hébergé par GitHub, POP européens inclus) :

  ```text
  https://github.com/MaxLananas/BosketBall/releases/download/<tag>/BosketBall-ResourcePack.zip
  ```

Limites à connaître : jsDelivr refuse les fichiers **> 20 Mo** (nous sommes à 3,38 Mo, largement sous la limite) et met en cache les URL pointant sur une **branche** pendant ~12 h
(d'où l'épinglage sur un commit, immuable).

---

## 5. Mettre les deux en place

```yaml
resource-pack:
  # 1) France hexagone (alwaysdata, Paris)
  # 2) monde (jsDelivr, POP Paris/Francfort/New York)
  # 3) serveur intégré du plugin = dernier recours automatique
  mirrors:
    - "https://bosketball.alwaysdata.net/BosketBall-ResourcePack.zip?v={sha1}"
    - "https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall@<sha_du_commit>/releases/BosketBall-ResourcePack.zip"
  external-sha1: "476ecec839d69d2c6ae8a505d97aa3161583642e"
```

Puis :

```bash
/bb pack test          # teste chaque route côté serveur (taille + SHA-1 + débit)
/bb pack push          # renvoie le pack à tous les joueurs connectés
```

`/bb pack test` affiche une ligne par miroir : c'est le moyen le plus rapide de voir qu'un
hébergeur répond en 40 ms au lieu de 5 000 ms, ou qu'il sert un fichier de la mauvaise taille.

Pour changer l'ordre (par exemple mettre jsDelivr en premier si le quota alwaysdata est atteint),
il suffit d'inverser les deux lignes et de redémarrer le serveur — **aucun rebuild du pack n'est
nécessaire**, le SHA-1 ne change pas.

---

## 6. Pièges à éviter

* **SHA-1 différent sur un miroir** → le client refuse le pack et retombe sur la route suivante.
  Toujours pousser le ZIP **sorti du build**, jamais un ancien.
* **Cache CDN périmé** → URL épinglée sur un commit (jsDelivr) ou `?v={sha1}` (Apache/alwaysdata).
* **HTTP simple** → Paper accepte, mais certains clients/proxys le bloquent ; rester en HTTPS.
* **Ne jamais pointer un miroir vers le mux du port de jeu** : ce chemin dépend du SNI et du port
  réel du serveur, il est déjà géré automatiquement en dernier.
* **Nom de fichier identique + CDN collant** → renommer le fichier avec le SHA-1 court
  (`BosketBall-ResourcePack-476ecec8.zip`) et adapter l'URL est la solution la plus robuste.
