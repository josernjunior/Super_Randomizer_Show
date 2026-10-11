# Super Randomizer Show

Mod de randomizer para **Necesse** (versão 1.3.3). Tudo é gerado a partir de uma **seed**: a mesma seed sempre produz exatamente a mesma randomização, e o resultado completo fica gravado num arquivo de spoiler.

## O que é randomizado

| # | Opção no config | O que faz |
|---|---|---|
| 1 | `mobSpawns` | Troca onde aparecem inimigos (`mobSpawnHostiles`), bichos (`mobSpawnCritters`), NPCs de vilas e estruturas (`mobSpawnTownNPCs`) e chefes invocados por itens ou incursões (`mobSpawnBosses`). Cada um só troca com outro do mesmo grupo. |
| 2 | `drops` / `dropQuantities` | Troca a tabela de loot entre inimigos, chefes, bichos, rochas/minérios, árvores e plantas. Cada item dropado também recebe um multiplicador fixo de quantidade (`dropQuantityMin` a `dropQuantityMax`). |
| 3 | `recipes` | Embaralha os ingredientes das receitas. Com `recipesSameStation = true`, a troca só acontece entre receitas da mesma estação de trabalho. |
| 4 | `shops` | Embaralha os itens que os NPCs de cidade vendem e compram. |
| 5 | `weaponDamage` | Dano das armas: `multiplier` (fator aleatório entre `weaponDamageMin` e `weaponDamageMax`), `shuffle` (troca o dano entre armas de força parecida) ou `chaos` (troca com qualquer arma do mesmo tipo de dano). |
| 6 | `critChance` | Crítico extra em cada arma (`weaponCritMin` a `weaponCritMax`). O crítico que armaduras, acessórios e bônus de conjunto já davam é multiplicado, e cada um deles ainda pode ganhar até `equipmentCritBonusMax` a mais. |
| 7 | `armorSetBonuses` | Troca os bônus de conjunto entre as armaduras. |
| 8 | `equipmentEffects` | Troca os efeitos entre acessórios e entre peças de armadura do mesmo slot. Cada peça mantém o próprio valor de armadura. |
| 9 | `equipmentDamage` | Multiplica o % de dano extra que os equipamentos dão. |
| 10 | `visuals` | Troca as texturas de itens, armas, armaduras, cosméticos, mobs, NPCs e projéteis. Só troca entre imagens compatíveis, então um mob vira outro mob inteiro e um capacete vira outro capacete. As pastas usadas ficam em `visualFolders`. |
| 11 | `projectiles` | Troca os projéteis de armas, inimigos e chefes. O substituto mantém a posição, a direção, a velocidade, o alcance e o dano do original. |
| 12 | `translations` | Embaralha os textos do jogo: `entries` troca textos inteiros dentro da mesma categoria; `words` troca as palavras de lugar. |
| 13 | `sounds` | Troca os sons dentro de cada categoria: `music`, `animals`, `creatures`, `environment`, `weather`, `weapons`, `impacts`, `interface`, `bosses`, `notes` e `other`. As categorias usadas ficam em `soundCategories`. |

## Configuração

O arquivo de configuração é criado na primeira vez que o jogo abre com o mod:

`%APPDATA%\Necesse\cfg\mods\josezito.srs.cfg`

Cada opção vem com um comentário explicando o que faz. Para desligar uma opção, troque `true` por `false`. Com `seed = 0`, o mod gera uma seed nova no próximo início. Para nunca randomizar certos mobs ou itens, coloque os IDs deles em `excludeMobs` e `excludeItems`, separados por vírgula.

**A randomização é montada enquanto o jogo carrega, então qualquer mudança só vale depois de reiniciar o jogo (ou o servidor).**

## Spoiler

Com `spoilerLog = true`, o resultado completo é gravado em:

`%APPDATA%\Necesse\cfg\mods\josezito.srs-spoiler.txt`

O arquivo traz a seed, a configuração usada e cada troca feita: quem aparece no lugar de quem, quem dropa o quê, as receitas novas, as lojas, o dano das armas, os bônus de conjunto, as texturas, os sons e os projéteis.

## Comando `/srs` (no chat; precisa de permissão de admin)

| Comando | O que faz |
|---|---|
| `/srs` | Mostra a seed, quais opções estão ligadas e onde ficam os arquivos |
| `/srs seed <número>` ou `/srs seed random` | Define uma seed nova |
| `/srs toggle <opção>` | Liga ou desliga uma opção (ex.: `/srs toggle sounds`) |
| `/srs set <opção> <valor>` | Muda qualquer opção (ex.: `/srs set weaponDamageMode chaos`) |
| `/srs get <opção>` | Mostra o valor de uma opção |
| `/srs options` | Lista todas as opções |
| `/srs spoiler` | Grava o arquivo de spoiler de novo |
| `/srs reset` | Volta tudo ao padrão, mantendo a seed |
| `/srs test` | Autoteste: roda as partes randomizadas no mundo e mostra se algo falhou |

O comando grava direto no arquivo de configuração. As mudanças só valem depois de reiniciar.

## Multiplayer

O servidor e todos os jogadores precisam ter o mod e usar **a mesma configuração**. Quando alguém entra num servidor com uma configuração diferente, o mod salva automaticamente a configuração do servidor no jogo dessa pessoa e avisa no chat que ela precisa reiniciar o Necesse e entrar de novo.

## Limitações conhecidas

- O diário de aventura continua mostrando os drops originais dos mobs.
- O item de invocação do Capitão Pirata sempre invoca o Capitão Pirata, porque o código do jogo exige exatamente esse chefe. Outros itens de invocação ainda podem invocá-lo.
- Projéteis que dependem de quem os disparou (bumerangues, projéteis que invocam criaturas, bombas etc.) não entram no sorteio.
- Mobs trocados nascem nos lugares onde o mob original nasceria. Se o substituto não puder viver ali (um tubarão em terra, por exemplo), outro mob é sorteado.

## Compilar

Veja o `CLAUDE.md`. Em resumo: `./gradlew buildModJar` gera o mod em `build/jar/`, e `./gradlew runClient` abre o jogo (pela Steam) com o mod carregado.
