# Texte, couleurs et balises (zéro bug de syntaxe)

Tous les messages du plugin passent par `Msg.parse()`, qui répare la chaîne **avant** de la parser.

## `MiniSyntax.repair()` — ce qui est corrigé automatiquement

| Cas | Avant | Maintenant |
|---|---|---|
| Balise fermante orpheline | `✔ 3/8 SHOOTING</bold>` s'affichait tel quel | `</bold>` supprimé |
| Balise non fermée | `<bold>Hi` | `</bold>` ajouté à la fin |
| Couleur fermée = couleur ouverte | `<gray>x</red>` | fermeture réordonnée |
| Balise inconnue | `<player>`, `<1-365>` → `ParsingException` (message perdu) | échappée, affichée littéralement |
| `<` utilisé comme texte | `5 < 6` → exception | affiché `5 < 6` |
| Hex dynamiques | `<#` + hex + `>` | conservés, jamais imprimés |
| Ajout de fermeture | `<!shadow>` | refermé par `</!shadow>` (forme exacte) |
| Parse impossible | exception | repli sur le texte brut nettoyé (le message n'est jamais perdu) |

## Règles de code

1. **Jamais** de balise MiniMessage dans `Component.text(...)` — utiliser `Msg.parse(...)`.
2. Les textes destinés au client passent par `Msg.*` (`Msg.send`, `Msg.title`, `Msg.sendFramedCard`…).
3. Les en-têtes de cartes (`Glyphs.composeDividerHeader`) sont du texte brut : ils sont nettoyés et
   passés en petites capitales, jamais interprétés.
4. Les noms d'items GUI passent par `GuiMenuManager.customIcon(...)` → `Msg.parse(...)`.

## Garde-fous automatiques (tests)

- `brokenMarkupIsRepairedInsteadOfLeaking` — cas hostiles : `</bold>`, `<#4ADE80>`, `<player>`,
  `<2.7m`, `<gray>x</red>`, `5 < 6`, hover imbriqué, `<bold>a</bold></not_a_tag>`.
- `everyStringLiteralWithMarkupParsesWithoutLeaking` — **tous** les littéraux de `src/main/java`
  sont parsés : aucun ne doit laisser `</tag>`, `<#RRGGBB>` ou une balise connue dans le texte final.
- `theThreeReportedSyntaxBugsStayFixed` — interdit `Component.text("` suivi de balise, `<2.7m`,
  et vérifie que `Msg.parse` répare bien en amont.

Résultat : 116 tests, dont ces trois garde-fous, à chaque build.
