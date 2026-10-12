# Super Randomizer Show

Mod de randomizer para **Necesse** (versão 1.3.3). Tudo é gerado a partir de uma **seed**: a mesma seed sempre produz exatamente a mesma randomização, e o resultado completo fica gravado num arquivo de spoiler.

## O que é randomizado

| # | Opção no config | O que faz |
|---|---|---|
| 1 | `mobSpawns` | Troca onde aparecem inimigos (`mobSpawnHostiles`), bichos (`mobSpawnCritters`), animais como ovelhas, vacas, porcos, galinhas, avestruzes e pinguins (`mobSpawnAnimals`), NPCs de vilas e estruturas (`mobSpawnTownNPCs`, com a lista 1.1 `townNPCPool`) e chefes invocados por itens ou incursões (lista 1.2 `bossPool`; com a lista vazia, nenhum chefe troca). Cada um só troca com outro do mesmo grupo. Os animais e as criaturas colocadas na criação do mundo só mudam em mundos novos ou em áreas ainda não exploradas. |
| 2 | `drops` / `dropQuantities` / `randomItemDrop` | Troca a tabela de loot entre inimigos, chefes, bichos, rochas/minérios, árvores, plantas, flores e paredes. Mobs que dropam algo só trocam com mobs que também dropam (um safrinho nunca fica com o drop vazio de uma borboleta). Os objetos só trocam com outros do mesmo tipo que também dropam algo: flores, cogumelos e canteiros de flores entre si, arbustos de fruta entre si, plantas como taboa, junco e coral entre si, paredes entre si. Grama comum, teia de aranha, rochas decorativas e a segunda metade das rochas grandes não entram, para nada ficar sem drop nem dropar em dobro. Objetos colocados pelo jogador devolvem o próprio item ao serem quebrados, como no jogo normal. Cada item dropado também recebe um multiplicador fixo de quantidade (`dropQuantityMin` a `dropQuantityMax`), que nunca zera um drop. Além disso, todo mob morto tem uma chance (`randomItemDropChance`, 1% por padrão) de dropar também um item aleatório de todo o jogo. |
| 3 | `recipes` | Embaralha os ingredientes das receitas. Com `recipesSameStation = true`, a troca só acontece entre receitas da mesma estação de trabalho. O nível de embaralhamento (`recipesShuffleLevel`, de 1% a 100%) é a parte das receitas que troca de ingredientes; as outras ficam como no jogo normal. |
| 4 | `shops` / `shopPrices` | Embaralha as lojas dos NPCs de cidade. Modo `shopsMode`: `items` (padrão) troca cada item vendido e comprado por outro; `npcs` mantém as lojas inteiras e só troca quem vende o quê (o ferreiro passa a vender o que o fazendeiro vendia). Com `shopPrices`, os preços de compra e venda de cada item são multiplicados por um fator fixo entre `shopPricesMin` e `shopPricesMax`. |
| 5.1 | `weaponDamage` | Dano das armas: `multiplier` (fator aleatório entre `weaponDamageMin` e `weaponDamageMax`), `shuffle` (troca o dano entre armas de força parecida) ou `chaos` (troca com qualquer arma do mesmo tipo de dano). |
| 5.2 | `enemyDamage` | Dano dos inimigos: `multiplier` (fator aleatório entre `enemyDamageMin` e `enemyDamageMax`) ou `preswap` (padrão): o inimigo que trocou de lugar bate com a força do inimigo que estava ali antes. As barras de mínimo/máximo valem nos dois modos; no `preswap` elas multiplicam o dano do inimigo original, e com 1.0 e 1.0 o dano fica igual ao dele. O mod mede o dano base de cada inimigo ao iniciar, e a tabela fica no spoiler. |
| 5.3 | `bossDamage` | Dano dos chefes, com os mesmos modos e barras (`bossDamageMode`, `bossDamageMin`, `bossDamageMax`). No `preswap`, o chefe invocado no lugar de outro bate com a força do chefe original, multiplicada pelas barras. |
| 6 | `enemyHealth` / `villagerHealth` / `critterHealth` / `animalHealth` / `bossHealth` | Vida máxima de inimigos (6.1), aldeões (6.2), bichos (6.3), animais (6.4) e chefes (6.5). Cada grupo tem os modos `multiplier` (fator aleatório) ou `preswap` (padrão): o mob que trocou de lugar fica com a vida do mob que estava ali antes. As barras de mínimo/máximo (`...HealthMin`/`...HealthMax`, padrão 0,8 a 1,2) valem nos dois modos. |
| 7 | `enemyArmor` / `villagerArmor` / `critterArmor` / `animalArmor` / `bossArmor` | **Defesa** (armadura) de inimigos (7.1), aldeões (7.2), bichos (7.3), animais (7.4) e chefes (7.5). Cada golpe perde metade da armadura em dano. Modos `multiplier` ou `preswap` (padrão): o mob que trocou de lugar fica com a defesa do mob que estava ali antes. As barras (`...ArmorMin`/`...ArmorMax`, padrão 0,8 a 1,2) valem nos dois modos. |
| 8 | `enemyKnockback` / `villagerKnockback` / `critterKnockback` / `animalKnockback` / `bossKnockback` | **Empurrão**: o quanto o mob é jogado para trás ao levar um golpe (1,0 = normal, 0 = não se move), nos mesmos grupos (8.1 a 8.5), modos e barras da defesa. |
| 9 | `critChance` | Crítico extra em cada arma (`weaponCritMin` a `weaponCritMax`). O crítico que armaduras, acessórios e bônus de conjunto já davam é multiplicado, e cada um deles ainda pode ganhar até `equipmentCritBonusMax` a mais. |
| 10 | `armorSetBonuses` | Bônus de conjunto. Modo `armorSetBonusMode`: `swap` troca os bônus entre as armaduras; `double` (padrão) é o **bônus de conjunto duplo**: cada conjunto mantém o próprio bônus e ganha também o de outro conjunto (a descrição do capacete mostra os dois). |
| 11 | `equipmentEffects` | Troca os efeitos entre acessórios e entre peças de armadura do mesmo slot. Cada peça mantém o próprio valor de armadura. Modo `equipmentEffectsMode`: `all` (padrão) troca com qualquer um; `class` só troca entre itens da mesma classe (corpo a corpo, distância, magia, invocação ou geral, lida dos efeitos do acessório e do capacete do conjunto); `progression` só troca entre itens achados na mesma fase do jogo (acessórios ordenados por raridade e valor, armaduras pela defesa, cada um trocando só com os vizinhos nessa ordem). |
| 12 | `equipmentDamage` / `enchantScrolls` | Multiplica o % de dano extra que os equipamentos dão. Com `enchantScrolls`, os **pergaminhos** também são randomizados: os valores de cada encantamento são multiplicados por um fator fixo entre `enchantScrollsMin` e `enchantScrollsMax`. |
| 13 | `visuals` | Troca as texturas de itens, armas, armaduras, cosméticos, mobs, NPCs e projéteis. Só troca entre imagens compatíveis, então um mob vira outro mob inteiro e um capacete vira outro capacete. As pastas usadas ficam em `visualFolders` (por padrão, só mobs e projéteis). |
| 13b | `funnyBuggedSwap` | **Troca Bugada Divertida** (ligada por padrão): as partes dos mobs (corpo, braços, sombra) trocam separadas umas das outras, criando misturas como um slime com braços de zumbi. Funciona mesmo com `visuals` desligado. |
| 13c | `visualRotation` | **Rotação de visuais** (ligada por padrão; pode afetar o desempenho): a seed gera 3 opções de troca de visual, que se alternam a cada 6 minutos de jogo (pelo tempo do mundo, então no multiplayer todos veem a mesma). |
| 14 | `projectiles` / `projectileSpeed` | Troca os projéteis de armas, inimigos e chefes. O substituto mantém a posição, a direção, a velocidade, o alcance e o dano do original. Projéteis de teste dos desenvolvedores nunca entram no sorteio. Com `projectileSpeed`, cada tipo de projétil voa com um multiplicador fixo de velocidade entre `projectileSpeedMin` e `projectileSpeedMax` (padrão 1,0 e 1,0). |
| 15 | `translations` | Embaralha os textos do jogo. Modos `translationMode`: `entries` troca textos inteiros dentro da mesma categoria; `words` troca as palavras de lugar; `entriesall` usa frases de todas as traduções; `words2` e `words3` usam palavras de duas ou três traduções (o idioma em uso mais outras sorteadas pela seed); `wordsall` usa palavras de todas as traduções. |
| 16 | `sounds` | Troca os sons dentro de cada categoria: `music` (inclusive a música de cada bioma), `animals`, `creatures`, `environment`, `weather`, `weapons`, `impacts`, `interface`, `bosses`, `notes` e `other`. As categorias usadas ficam em `soundCategories`. |
| 16b | `soundRotation` | **Rotação de sons** (ligada por padrão; pode afetar o desempenho): a seed gera 3 opções de troca de som, que se alternam a cada 6 minutos de jogo, junto com a rotação de visuais. Na hora da troca, a música muda de faixa para a opção nova já tocar. |
| 17 | `questRewards` / `questRewardsElder` / `questRewardsJournal` | **Recompensas aleatórias**: os itens que as quests dão viram itens aleatórios de todo o jogo, tanto nos objetivos do Mestre Ancião (17.1) quanto nos desafios do diário de aventura (17.2). A janela da quest já mostra a recompensa nova. A quantidade original é mantida (até uma pilha cheia); quando a recompensa era um item único e o sorteado é empilhável, vem um décimo de pilha. As Leather Dashers do primeiro objetivo continuam vindo junto com o item sorteado, porque o objetivo seguinte pede para equipá-las. |
| 18 | `fishingBaitPower` / `fishingRodPower` / `fishingCatches` | **Pesca**. O poder de pesca de cada isca e de cada vara é multiplicado por um fator fixo entre o mínimo e o máximo (`fishingBaitPowerMin`/`fishingBaitPowerMax` e `fishingRodPowerMin`/`fishingRodPowerMax`, padrão 0,8 a 1,2). A descrição do item mostra o valor novo. A pescaria (`fishingCatchesMode`) tem dois modos: `fish` (padrão), em que cada peixe vira outro peixe, e `all`, em que cada coisa pescada vira um item qualquer do jogo. Nos dois modos a troca é sempre a mesma para cada seed (por exemplo, toda carpa vira o mesmo item) e vale também para os aldeões que pescam. O desafio do diário "pescar um peixe-gelo" continua possível: pescar o item em que o peixe-gelo se transformou também conta. |

## Configuração

### Pelo menu do jogo (mais fácil)

No menu principal, abra **Mods**, clique em **Super Randomizer Show** e depois no botão **Configurar randomizer**, no fim do painel da direita. A tela de configuração tem:

- a seed, com um botão **Sortear**, que pede confirmação antes de trocar;
- uma aba para cada uma das 18 seções, na borda de cima da janela (o número da seção; passe o mouse para ver o nome), mais a aba **Avançado**; cada aba mostra só a sua seção, e quando uma opção está desligada as sub-opções dela ficam acinzentadas;
- caixas para escolher quais chefes e quais NPCs de cidade podem trocar entre si, com o nome de cada um;
- controles deslizantes para os valores (multiplicadores, porcentagens e a chance do item aleatório) e listas para escolher os modos;
- uma seção **Avançado** com o spoiler e as listas de mobs e itens que nunca devem ser trocados;
- os botões **Salvar**, **Restaurar padrão** (mantém a seed), **Reiniciar jogo** e **Voltar**. Se você voltar sem salvar, o jogo pergunta se quer salvar.

### Pelo arquivo

O arquivo de configuração é criado na primeira vez que o jogo abre com o mod:

`%APPDATA%\Necesse\cfg\mods\josezito.srs.cfg`

Cada opção vem com um comentário explicando o que faz. Para desligar uma opção, troque `true` por `false`.

Numa instalação limpa, o mod sorteia uma seed aleatória e grava no arquivo. Receitas (`recipes`), traduções (`translations`), preços (`shopPrices`) e velocidade dos projéteis (`projectileSpeed`) vêm desligados; as demais opções vêm ligadas, incluindo o bônus de conjunto duplo, os pergaminhos e as rotações de visuais e de sons. Os visuais trocam só mobs e projéteis (`visualFolders = mobs,projectiles`). Com `seed = 0`, o mod gera uma seed aleatória nova no próximo início. Para nunca randomizar certos mobs ou itens, coloque os IDs deles em `excludeMobs` e `excludeItems`, separados por vírgula.

**A randomização é montada enquanto o jogo carrega, então qualquer mudança só vale depois de reiniciar o jogo (ou o servidor).**

## Spoiler

Com `spoilerLog = true`, o resultado completo é gravado em:

`%APPDATA%\Necesse\cfg\mods\josezito.srs-spoiler.txt`

O arquivo traz a seed, a configuração usada e cada troca feita: quem aparece no lugar de quem, quem dropa o quê, as receitas novas, as lojas e os preços, o dano das armas, os bônus de conjunto, os encantamentos, as texturas (as 3 opções, com a rotação ligada), os sons, os projéteis e a velocidade deles, as recompensas das quests e a pesca.

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
