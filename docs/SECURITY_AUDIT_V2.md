# Re-auditoria de segurança — StudyMind Redação (backend)

Data: 2026-10-12  
Escopo: estado atual do repositório e histórico Git alcançável por `--all`.  
Método: análise estática e comandos locais somente leitura. Não houve chamada à produção, ao Gemini ou a qualquer serviço externo. Nenhum código, configuração, migration ou dependência foi alterado.

## 1. Resumo

**Pode abrir para os testadores somente se a API ficar atrás do proxy, ligada em `127.0.0.1`, com TLS, limite de corpo e rate limit no proxy.**
O bloqueador no código é o teto global de IA não atômico (SEC-10): chamadas concorrentes podem ultrapassar 30/dia e ocupar as threads durante até duas tentativas de 60 s.
O rate limit local melhorou o risco anterior, mas pode ser zerado ao atingir 10.000 chaves e depende criticamente da configuração do proxy (SEC-11).
O upload tem limite total, mas multiplica memória por cópias/Base64 e só reconhece prefixos, encaminhando arquivo/EXIF integral ao Gemini (SEC-13).
Cadastro fechado, cotas individuais atômicas, consultas SQL parametrizadas, escape de `LIKE`, isolamento das listagens e filtros de tema estão corretos.
Não foi encontrado segredo real no estado atual ou no histórico Git alcançável.
CVEs e comportamento real de proxy/Tomcat não foram confirmados sem rede/deploy; o Maven Wrapper local não iniciou porque seu JAR não está presente.

## 2. Status dos achados anteriores

| ID | Status | Evidência e conclusão |
|---|---|---|
| SEC-01 | **Corrigido** | Cadastro é fechado por padrão em `application.properties:34-36`; `CadastrarUsuarioService.java:38-44` normaliza o e-mail, aplica a allowlist antes da consulta ao banco e só então testa duplicidade. Ainda depende de `CADASTRO_ABERTO=false` no deploy. |
| SEC-02 | **Parcial** | `AuthController.java:43-56` limita cadastro por IP e login por IP/e-mail. Persistem reset global, estado local e problemas de proxy/lockout descritos em SEC-11. |
| SEC-03 | **Corrigido** | `LimiteTamanhoCorpoFilter.java:24-48` rejeita `Content-Length` acima de 64 KiB/11 MiB; multipart também tem `max-file-size=5MB` e `max-request-size=11MB` (`application.properties:29-32`). Requisição chunked ainda depende do parser/proxy; ver SEC-13 e pendências. |
| SEC-04 | **Aberto** | TTL continua 120 min (`application.properties:18-20`), sem revogação; o filtro confia integralmente nas claims e não consulta usuário (`JwtAuthenticationFilter.java:39-52`). |
| SEC-05 | **Aberto** | As instruções e os dados continuam no mesmo conteúdo `user` (`GeminiClient.java:106-125`). A transcrição acrescentou defesa textual (`transcricao-v1.txt:3-4`), mas texto adversarial da imagem não é uma fronteira de confiança. |
| SEC-06 | **Corrigido** | Todas as mensagens externas encontradas passam por `LogSeguro.limpar` (`GeminiClient.java:100-101`, `GeminiTranscricaoParser.java:38`, `EnviarRedacaoService.java:95`, `TranscreverRedacaoService.java:70,84`); o sanitizador remove controles e limita 300 caracteres (`LogSeguro.java:14-21`). |
| SEC-07 | **Corrigido** | `.gitignore:37-40` cobre `.env`, `.env.*` e `application-local.properties`; `.dockerignore:7-13` exclui `target`, docs, Postman e `.env*`. |
| SEC-08 | **Aberto** | `resposta_bruta_json` permanece no banco (`V1__create_table_usuarios_perfils_redacoes_e_avaliacoes.sql:45`) sem retenção/exclusão de conta visível. Ver SEC-18. |
| SEC-09 | **Parcial** | Docker e limites agora existem; a imagem roda sem root (`Dockerfile:16-24`) e o banco liga em loopback (`docker-compose.yml:17-19`). TLS, proxy, firewall, backups e CVEs continuam não confirmados; ver pendências. |

## 3. Mapa de rotas e autorização

`SecurityConfig.java:37-40` libera **somente** `POST /auth/cadastro`, `POST /auth/login` e `/error`; todo o resto exige autenticação. Não há Actuator, Springdoc/Swagger nem H2 no `pom.xml` ou nas propriedades. Não há regra por `Role`: todas as rotas autenticadas aceitam qualquer role presente no JWT. Hoje não há operação administrativa, e cadastro sempre cria `ALUNO`, então isso não produz escalada concreta; se surgir rota administrativa, ela precisará de regra explícita.

| Método | Caminho | Acesso | Origem do usuário | Recurso/isolamento |
|---|---|---|---|---|
| POST | `/auth/cadastro` | Pública | Corpo; IP via `getRemoteAddr()` | Cria `ALUNO`, sujeito à allowlist e rate limit |
| POST | `/auth/login` | Pública | Corpo; IP via `getRemoteAddr()` | Busca usuário por e-mail normalizado e emite JWT |
| GET | `/usuarios/me` | Autenticada | Claims do JWT | Retorna o próprio principal (`id`, e-mail, role) |
| GET | `/usuarios/me/correcoes-hoje` | Autenticada | `usuario.id()` | `findByUsuario_IdAndData` (`UsoIADiarioJpaRepository.java:14`) |
| GET | `/usuarios/me/transcricoes-hoje` | Autenticada | `usuario.id()` | Mesma linha de uso diário do próprio usuário |
| GET | `/usuarios/me/evolucao` | Autenticada | `usuario.id()` | Redações vêm de `findByUsuario_Id...` (`RedacaoJpaRepository.java:12`) |
| PUT | `/usuarios/me/temas/{temaId}` | Autenticada | `usuario.id()` | Exige tema existente, ativo e `PREVISAO`; insert usa `(usuarioId, temaId)` |
| DELETE | `/usuarios/me/temas/{temaId}` | Autenticada | `usuario.id()` | Exige tema existente e `PREVISAO` (pode inativo); delete filtra ambos IDs |
| POST | `/redacoes` | Autenticada | `usuario.id()` | Cria redação e chama Gemini, com cotas individual/global |
| GET | `/redacoes` | Autenticada | `usuario.id()` | Repository filtra por dono; lista completa, sem paginação |
| GET | `/redacoes/{id}` | Autenticada | `usuario.id()` | Busca por ID e só depois filtra dono no serviço; ver SEC-17 |
| POST | `/transcricoes` | Autenticada | `usuario.id()` | 1–2 partes `imagens`, chama Gemini e não persiste imagem/texto |
| GET | `/repertorios` | Autenticada | Apenas autenticação | Catálogo global ativo, filtros e paginação |
| GET | `/repertorios/{codigo}` | Autenticada | Apenas autenticação | Detalhe global ativo por código |
| GET | `/temas` | Autenticada | `usuario.id()` | Temas globais + previsões adicionadas pelo usuário |
| GET | `/temas/possiveis` | Autenticada | `usuario.id()` | Catálogo global e flag de vínculo do usuário |
| GET | `/macroeixos` | Autenticada | Apenas autenticação | Taxonomia global |
| qualquer | `/error` | Pública | N/A | Dispatcher interno de erro; não expõe recurso de negócio |

### Variações de caminho e códigos de resposta

- Os matchers públicos têm método e caminho exatos (`SecurityConfig.java:38`). Barra final, maiúsculas ou outro método não ganham `permitAll`; no máximo deixam de mapear ou caem em autenticação. A normalização/rejeição de `//`, `%2f`, `;jsessionid` e encoding depende do `StrictHttpFirewall`/Tomcat da versão efetiva; não foi feito teste HTTP dinâmico. Mesmo que uma forma alternativa seja aceita pelo MVC, `anyRequest().authenticated()` é a regra residual.
- Token ausente/inválido produz 401 (`SecurityConfig.java:42-44`). Não há regra de autorização que produza 403 por role; 403 é usado para cadastro fechado (`GlobalExceptionHandler.java:131-134`). Recurso inexistente ou alheio retorna 404.
- Com allowlist fechada, alguém fora da lista sempre recebe 403 antes da consulta. Quem conhece/controla um e-mail permitido distingue conta existente por 409 (`CadastrarUsuarioService.java:39-44`); isso é enumeração limitada aos endereços da allowlist, não enumeração arbitrária.
- `PUT` revela três estados a um autenticado: 404 para inexistente/inativo, 400 para existente mas não `PREVISAO`, 204 para previsão válida (`GerenciarMeusTemasService.java:37-43`). IDs/tipos de previsões já são publicados por `/temas/possiveis`; não há dado de outro usuário.

## 4. Achados novos

| ID | Severidade | Título | Arquivo:linha | Bloqueia o deploy? |
|---|---|---|---|---|
| SEC-10 | Alta | Teto global não é atômico e não limita concorrência/threads de IA | `EnviarRedacaoService.java:71-78`; `TranscreverRedacaoService.java:57-64` | **sim** |
| SEC-11 | Média | Rate limit pode ser zerado, causar lockout dirigido e depende de IP de proxy confiável | `LimitadorDeTentativas.java:31-53`; `AuthController.java:45-56` | sim se exposto sem limite no proxy |
| SEC-12 | Média | Login permite enumeração temporal de e-mails existentes | `AutenticarUsuarioService.java:26-33` | não |
| SEC-13 | Média | Upload multiplica memória e valida só prefixo; arquivo/EXIF integral vai ao Gemini | `TranscricaoController.java:44-55`; `ImagemRedacao.java:20-38`; `TipoImagem.java:26-39` | não para piloto restrito |
| SEC-14 | Média | Compose omite os três parâmetros novos de transcrição | `docker-compose.yml:32-45`; `application.properties:27-28,56` | não |
| SEC-15 | Média | Consultas autenticadas potencialmente caras não têm rate limit e histórico é ilimitado | `RedacaoController.java:45-47`; `RepertorioJpaRepository.java:34-52` | não |
| SEC-16 | Baixa | Limite BCrypt é contado em caracteres, mas a biblioteca limita bytes UTF-8 | `CadastroRequest.java:10`; `CadastrarUsuarioService.java:58-62` | não |
| SEC-17 | Baixa | Detalhe da redação não filtra proprietário na consulta ao banco | `RedacaoRepositoryAdapter.java:40-44`; `ConsultarRedacoesService.java:56-64` | não |
| SEC-18 | Baixa | Não há retenção, exportação nem exclusão de conta/dados pessoais | `V1__create_table_usuarios_perfils_redacoes_e_avaliacoes.sql:1-55`; controllers | não |
| SEC-19 | Baixa | Código de repertório não tem validação de formato | `RepertorioController.java:47-50`; `RepertorioJpaRepository.java:58` | não |
| SEC-20 | Informativa | Verificação de CVEs e limites efetivos do container não confirmada | `pom.xml:5-93`; `application.properties` | não confirmado |

## 5. Detalhes dos achados

### SEC-10 — Teto global não é atômico e não limita concorrência/threads de IA

**Evidência**

```java
if (usoRepository.somarCorrecoesDoDia(hoje) >= limiteGlobal) { ... }
if (!usoRepository.reservarCorrecao(usuarioId, hoje, limiteDiario)) { ... }
```

A soma global e a reserva individual são transações separadas. A reserva individual é corretamente atômica (`UsoIADiarioJpaRepository.java:17-25,52-60`), mas não há uma linha/contador global bloqueado. O mesmo padrão aparece na transcrição. Também não há semáforo de concorrência por usuário ou global. O cliente faz até duas tentativas, cada uma com read timeout de 60 s (`GeminiClient.java:30,47-52,76-103`).

**Cenário concreto:** várias contas permitidas disparam simultaneamente correções/transcrições quando o total está em 29. Todas leem 29, reservam em linhas de usuários diferentes e chamam o Gemini. Um único usuário só consegue três reservas, mas contas diferentes ultrapassam arbitrariamente o teto conforme a simultaneidade. Requisições síncronas lentas ocupam threads do Tomcat; sem limite de concorrência, dezenas/centenas podem esgotar o pool antes de o total observado alcançar o teto.

**Impacto:** custo acima do teto, latência e indisponibilidade de toda a API. O comentário “podem passar do teto em 1” (`EnviarRedacaoService.java:72-73`) só seria verdadeiro com serialização; com N requisições paralelas, o excesso pode ser N−1.

**Recomendação:** reservar a cota global atomicamente na mesma operação/transaction que a individual (contador global por data, `UPDATE ... WHERE total < limite`, ou advisory/row lock); desfazer ambas em toda falha. Adicionar limite de chamadas simultâneas por usuário e global, fila/bulkhead e timeout total. No proxy, limitar conexões/requisições nesses dois endpoints.

**Esforço:** M.

### SEC-11 — Rate limit pode ser zerado, causar lockout dirigido e depende de IP de proxy confiável

**Evidência**

```java
if (janelas.size() >= MAXIMO_DE_CHAVES) { ... }
if (janelas.size() >= MAXIMO_DE_CHAVES) {
    janelas.clear();
}
```

As chaves usam `http.getRemoteAddr()` e e-mail antes da validação de senha (`AuthController.java:45-56`). A configuração padrão é `server.forward-headers-strategy=none` (`application.properties:40-41`). O estado vive em um `ConcurrentHashMap` local.

**Cenários concretos:**

1. Atrás do proxy com `none`, todas as tentativas podem aparecer com o IP do proxy: dez tentativas agregadas bloqueiam o login de todos por um minuto e cinco cadastros bloqueiam por uma hora.
2. Com `native` e a porta da API acessível diretamente em `0.0.0.0`, a confiança em headers encaminhados precisa ser testada; se o container aceitar `X-Forwarded-For` de qualquer origem, o atacante troca o header para burlar o IP.
3. Um atacante que conhece o e-mail da vítima consome cinco tentativas por minuto continuamente e impede seu login, mesmo sem senha correta.
4. Com IPs distribuídos/forjáveis e 10.000 e-mails distintos, o atacante enche o mapa; `clear()` apaga também os bloqueios do atacante e das vítimas. Restart/deploy também zera tudo; múltiplas instâncias multiplicam o limite.

**Impacto:** bypass de brute force, DoS coletivo ou dirigido e comportamento inconsistente no deploy.

**Recomendação:** manter o limite de borda no proxy por IP; aceitar forwarded headers somente do proxy e ligar a API em `127.0.0.1`/rede privada. Na aplicação, usar armazenamento compartilhado com expiração/LRU, nunca `clear()` global, combinar conta + IP e considerar contar e-mail apenas após política que evite lockout provocado (ou aplicar atraso progressivo). Documentar que mais de uma instância exige contador compartilhado.

**Esforço:** M.

### SEC-12 — Login permite enumeração temporal de e-mails existentes

**Evidência**

```java
usuarioRepository.buscarPorEmail(email)
    .filter(u -> senhaEncoder.confere(senha, u.senhaHash()))
```

Para e-mail inexistente, `senhaEncoder.confere` não roda; para existente, roda BCrypt. A mensagem/status são iguais (401), mas o custo é materialmente diferente.

**Cenário concreto:** um atacante mede muitas tentativas para uma lista de e-mails. Respostas consistentemente mais lentas indicam conta existente, auxiliando phishing/lockout dirigido. O rate limit reduz a amostragem, mas SEC-11 permite distribuição e reset.

**Impacto:** enumeração de usuários e melhor direcionamento de ataques.

**Recomendação:** executar verificação BCrypt contra um hash dummy quando o e-mail não existir, mantendo caminho/custo similares; preservar a mesma resposta externa.

**Esforço:** P.

### SEC-13 — Upload multiplica memória e valida só prefixo; arquivo/EXIF integral vai ao Gemini

**Evidência**

```java
imagens.add(ImagemRedacao.de(arquivo.getBytes()));
// ImagemRedacao clona bytes na entrada e em bytes()
```

`TipoImagem.detectar` verifica só 3 bytes de JPEG, 8 de PNG, ou `RIFF` + `WEBP`; não decodifica estrutura, dimensões ou fim do arquivo (`TipoImagem.java:26-39`). Aceita JPEG/PNG/WebP; rejeita SVG/HEIC. Um truncado ou poliglota com prefixo válido passa e é enviado. Como a aplicação não descomprime a imagem, uma decompression bomb não explode localmente na validação, mas é encaminhada ao provedor. Metadados EXIF não são removidos.

O multipart limita 5 MiB por arquivo e 11 MiB por request (`application.properties:31-32`), inclusive sem `Content-Length` quando o resolver efetivamente aplica esses limites. Antes do controller, o container precisa parsear todas as partes; a validação “1–2” ocorre só depois. Partes de outros nomes não entram na lista, mas contam no request total. `spring.servlet.multipart.file-size-threshold`/`location` e limites Tomcat de partes/parâmetros não estão configurados: diretório temporário, limpeza e `maxPartCount` efetivos são **não confirmados** sem executar a versão empacotada.

**Estimativa de memória:** duas fotos de 5 MiB geram ao menos o array de `getBytes` (~10 MiB), clone em `ImagemRedacao` (~10 MiB), clone para o adapter (~10 MiB), Base64 (~13,3 MiB) e JSON final de tamanho semelhante, além do multipart/objetos. O pico plausível é **45–70 MiB por upload concorrente**, sem contar buffers HTTP/Gemini; 10 uploads podem pressionar 450–700 MiB.

**Cenário concreto:** contas autenticadas enviam uploads máximos simultâneos ou arquivos com cabeçalho válido e corpo arbitrário. A API gasta memória/Base64/threads e repassa metadados de localização/dispositivo ao Gemini. Texto adversarial escrito na foto pode manipular a transcrição apesar da instrução textual; o resultado volta ao aluno e não é automaticamente corrigido, mas, se ele o enviar a `/redacoes`, torna-se entrada do segundo prompt.

**Impacto:** DoS por memória/CPU, envio desnecessário de metadados pessoais ao fornecedor e perda de integridade da transcrição.

**Recomendação:** validar/decodear o formato e dimensões com limites, re-encodar removendo EXIF, evitar clones/Base64 simultâneos e impor concorrência. Configurar/testar explicitamente threshold/local temporário, quantidade máxima de partes e limpeza. Tratar a transcrição como não confiável e manter revisão humana antes da correção.

**Esforço:** M/G.

### SEC-14 — Compose omite os três parâmetros novos de transcrição

**Evidência:** `docker-compose.yml:32-45` repassa limites de correção, mas não `LIMITE_TRANSCRICOES_DIARIAS`, `LIMITE_TRANSCRICOES_GLOBAL` nem `GEMINI_MODELO_TRANSCRICAO`. As propriedades usam defaults 3, 30 e o modelo de correção (`application.properties:27-28,54-56`). `.env.example` também não documenta essas três variáveis (`.env.example:23-38`).

**Cenário concreto:** o operador define esses valores no `.env`, acredita ter reduzido custo ou escolhido modelo próprio, mas Compose não os injeta. A aplicação silenciosamente usa 3/30 e `gemini-3.5-flash-lite`.

**Impacto:** controle de custo/modelo diferente do esperado; não há exposição direta de segredo.

**Recomendação:** documentar e repassar explicitamente as três variáveis; validar no startup valores positivos e registrar apenas a configuração não sensível efetiva.

**Esforço:** P.

### SEC-15 — Consultas autenticadas potencialmente caras não têm rate limit e histórico é ilimitado

**Evidência:** `GET /redacoes` devolve todas as redações (`RedacaoController.java:45-47`); evolução carrega todas e ordena em memória (`ConsultarEvolucaoService.java:47-59`). Cada página de repertório sempre executa busca e `count(*)` (`RepertorioRepositoryAdapter.java:35-39`). A busca aplica `unaccent(lower(nome || ideia_central || tags)) LIKE '%...%'` (`RepertorioJpaRepository.java:34-36`) sem índice funcional/trigram; V7 só cria índices de tipo, JSONB e relações (`V7__repertorio_schema.sql:97-121`).

O escape está correto: `q=%`, `_` e `\` viram literais (`RepertorioRepositoryAdapter.java:61-67`), portanto **não há wildcard injection**. `page` negativo e `size` fora de 1–50 são recusados (`FiltroRepertorio.java:32-41`); `OFFSET` é calculado em `long` (`RepertorioRepositoryAdapter.java:33`), sem overflow de `int`. Uma página enorme ainda força offset/scan e count, embora não retorne muitos itens.

**Cenário concreto:** uma conta válida repete buscas de 100 caracteres ou pede páginas muito altas, forçando scan com `unaccent/lower/concat` e count; paralelamente repete evolução/histórico. Seeds atuais são pequenos, mas o custo cresce linearmente com catálogo/histórico.

**Impacto:** CPU/IO do PostgreSQL e alocação na API; hoje moderado pelo tamanho pequeno dos dados.

**Recomendação:** rate limit/cache para catálogos, teto razoável de `page` ou paginação por cursor, índice de busca apropriado (por exemplo expressão/trigram compatível com `unaccent`) e paginação para histórico/evolução.

**Esforço:** M.

### SEC-16 — Limite BCrypt é contado em caracteres, mas a biblioteca limita bytes UTF-8

**Evidência:** DTO e serviço aceitam até 72 caracteres (`CadastroRequest.java:10`; `CadastrarUsuarioService.java:58-62`). BCrypt limita a entrada a 72 **bytes**, logo 72 caracteres Unicode podem exceder esse teto.

**Cenário concreto:** usuário permitido cadastra senha longa com emoji/caracteres multibyte. Conforme o encoder, a senha pode ser rejeitada internamente e virar 500, ou sofrer comportamento de truncamento não refletido na regra anunciada.

**Impacto:** falha de cadastro/login e política enganosa; não permite acesso a outra conta por si só.

**Recomendação:** validar o comprimento UTF-8 em bytes de acordo com o encoder escolhido, ou usar esquema de pré-hash seguro/documentado antes do BCrypt; testar multibyte nos limites.

**Esforço:** P.

### SEC-17 — Detalhe da redação não filtra proprietário na consulta ao banco

**Evidência:** o repository executa `findById(id)` (`RedacaoRepositoryAdapter.java:40-44`); a comparação com `usuarioId` só ocorre depois, em memória (`ConsultarRedacoesService.java:56-60`). Listagem e evolução, ao contrário, filtram por usuário na própria consulta.

**Cenário concreto:** aluno envia UUID de redação alheia. Hoje recebe o mesmo 404 e nenhum DTO/dado alheio é retornado; portanto **não há IDOR explorável confirmado**. O risco é de regressão: logging/interceptor/enriquecimento futuro pode observar/carregar a entidade antes da autorização.

**Impacto:** defesa em profundidade fraca e maior chance de IDOR futuro.

**Recomendação:** expor `findByIdAndUsuario_Id(redacaoId, usuarioId)` no repository e usar essa consulta como única leitura do detalhe.

**Esforço:** P.

### SEC-18 — Não há retenção, exportação nem exclusão de conta/dados pessoais

**Evidência:** são persistidos nome, e-mail, hash de senha, redações, avaliações/feedback e resposta bruta Gemini, além de contadores/tokens de uso (`V1__create_table_usuarios_perfils_redacoes_e_avaliacoes.sql:1-55`). Imagens e texto transcrito não são persistidos pelo código (`TranscreverRedacaoService.java:22-27`), mas a imagem integral é enviada ao Gemini. Não existe controller/caso de uso de exportação, exclusão de conta ou job de retenção.

**Cenário concreto:** um testador pede acesso/eliminação dos dados ou abandona o piloto; redação e resposta bruta ficam indefinidamente no PostgreSQL/backups. Uma violação expõe conteúdo educacional potencialmente pessoal e uma cópia adicional derivada na resposta bruta.

**Impacto:** superfície de dados e obrigações LGPD sem mecanismo operacional visível.

**Recomendação:** definir finalidade/prazo, política de backups e processo de acesso/exportação/exclusão; remover ou expirar `resposta_bruta_json` quando não necessária; documentar o tratamento pelo Gemini e metadados de imagem.

**Esforço:** M/G.

### SEC-19 — Código de repertório não tem validação de formato

**Evidência:** `/repertorios/{codigo}` aceita qualquer `String` e consulta igualdade parametrizada (`RepertorioController.java:47-50`; `RepertorioJpaRepository.java:58`). Não há SQL injection.

**Cenário concreto:** cliente envia código muito longo ou caracteres arbitrários, causando parsing/alocação e consulta inútil. O filtro global de 64 KiB não limita URI; o limite de request-line fica no Tomcat/proxy, não no código.

**Impacto:** baixo; custo de uma consulta indexada/UNIQUE e possível pressão de request-line, dependente do proxy.

**Recomendação:** exigir regex/tamanho dos formatos suportados (`REP|EVD|CF` + hífen + dígitos) e manter limite de URI no proxy/container.

**Esforço:** P.

### SEC-20 — Verificação de CVEs e limites efetivos do container não confirmada

Versões declaradas: Spring Boot 4.1.1, Java 17, `java-jwt` 4.5.0; PostgreSQL driver e Flyway vêm do BOM (`pom.xml:5-66`). `./mvnw dependency:tree` foi tentado, mas o wrapper falhou com “Cannot start maven from wrapper”; `.mvn/wrapper/maven-wrapper.jar` não é versionado e `mvn` não está instalado. Não se baixou nada por restrição da auditoria. Sem árvore resolvida e base CVE offline, CVEs conhecidos são **não confirmados**.

Também não foram confirmados dinamicamente: versão/limites reais de Tomcat, `maxPartCount`, diretório/limpeza multipart, comportamento exato de paths codificados e headers produzidos. Recomenda-se gerar SBOM/dependency tree e executar scanner no CI com base atualizada, além de testes de integração do artefato/container.

**Esforço:** P para árvore/SBOM no CI; M para suíte dinâmica.

## 6. O que está correto

- **SQL injection:** todas as `@Query` são constantes e usam parâmetros; não há `createQuery`, `Specification`, `Sort` ou `ORDER BY` vindo do request. `LIKE` escapa `\`, `%` e `_` na ordem correta (`RepertorioRepositoryAdapter.java:61-67`).
- **Paginação:** `page < 0` e `size` fora de 1–50 dão 400; offset usa `long`. O risco restante é custo de offset/count, não overflow/injection.
- **Migrations V7–V14:** apenas V7 cria `unaccent` (`V7__repertorio_schema.sql:5`), o que exige privilégio de criação da extensão na primeira migration; não há função customizada, `GRANT`/`REVOKE` ou segredo. Seeds contêm apenas catálogo público. Idealmente a extensão é provisionada por papel administrador e a aplicação usa papel menos privilegiado depois.
- **Temas do usuário:** adicionar exige `PREVISAO` ativo; remover aceita previsão inativa deliberadamente; insert/delete usam sempre `usuarioId` autenticado e são idempotentes (`GerenciarMeusTemasService.java:26-43`; `UsuarioTemaJpaRepository.java:15-28`).
- **Uso diário/evolução/listagem:** filtros de usuário estão no repository/consulta. O detalhe isolado é a exceção de defesa em profundidade (SEC-17).
- **Cotas individuais:** `INSERT ... ON CONFLICT ... WHERE qtd < limite` é atômico por usuário/recurso; libera em falha da IA e de persistência (`EnviarRedacaoService.java:81-99`; `TranscreverRedacaoService.java:66-73`). Se a liberação falhar, a exceção original pode ganhar uma cota presa; o job de recuperação só cobre redações, não transcrições, mas falha de banco nesse ponto já é incidente operacional.
- **JWT:** algoritmo é fixo em HMAC256, secret mínimo de 32 caracteres, issuer e `exp` são verificados (`JwtTokenAdapter.java:45-86`). A biblioteca rejeita algoritmo diferente/`none`. Não há claim `nbf` emitida; não há leeway configurado. Payload: `iss`, `sub` UUID, e-mail, role, `iat`, `exp`; sem senha/chave.
- **Token inválido:** malformado/expirado/claims inválidas vira ausência de autenticação e 401. Usuário apagado ou role alterada continua válido até `exp`, pois não há consulta/revogação (SEC-04).
- **E-mail/allowlist:** cadastro, login/repository e allowlist fazem trim + lowercase; a allowlist usa `Locale.ROOT` (`PoliticaCadastro.java:21-31`). `CadastrarUsuarioService` usa locale padrão em `toLowerCase()` (`:38`), inconsistência teórica em locale turco; no deploy brasileiro não foi identificado bypass prático. Espaço/caixa não burlam a lista. Não há normalização Unicode/punycode.
- **Senha/DTO:** senha é mascarada em `toString`; hash nunca sai. `UsuarioResponse` expõe role e UUID do próprio usuário (`UsuarioResponse.java:9-17`), úteis ao cliente mas não secretos. Avaliação não expõe modelo, tokens nem resposta bruta (`AvaliacaoResponse.java:8-35`).
- **Segredos:** estado atual, Postman, ferramentas, docs, Docker e histórico `--all` foram pesquisados. Não há chave real. O único commit com `AIza` (`6e2b77d4415d6ef8755f0d15f508407b2a68a134`) contém somente o prefixo de busca `AIza` (4 caracteres) no prompt de auditoria; não há `Bearer eyJ`. Valores de CI são locais/dummy. `JWT_SECRET`, `DB_PASSWORD` e `GEMINI_API_KEY` não têm fallback e a resolução falha no startup se ausentes; o JWT também rejeita segredo curto.
- **Chave Gemini:** só é guardada no campo privado e enviada no header `x-goog-api-key` (`GeminiClient.java:36-43,78-85`); logs registram status/mensagem sanitizada, não headers/corpo/chave. Não foi encontrado `toString` que a exponha.
- **Docker:** build copia apenas wrapper/pom e `src`; runtime recebe somente o JAR e roda como usuário `app` (`Dockerfile:7-21`). `.dockerignore` exclui `.git`, `.env*`, `target`, docs e Postman. Banco é publicado apenas em loopback; API é segura somente se `API_BIND=127.0.0.1` conforme o exemplo.
- **CSRF/CORS:** não há cookie de autenticação, Basic, form login ou sessão; somente Bearer stateless, então CSRF desligado é coerente (`SecurityConfig.java:31-36`). `allowedOrigins` não habilita credentials. `*` ampliaria leitura por qualquer origem, mas não fornece token; valor vazio tende a bloquear CORS. Métodos `PATCH` estão liberados no preflight sem rota correspondente (superfície desnecessária, sem bypass). CORS não substitui firewall/autorização.
- **Erros:** Jackson inválido recebe mensagem neutra; JDBC/Hibernate recebe conflito genérico quando `DataIntegrityViolationException`; falhas de IA são neutras. `IllegalArgumentException` devolve `getMessage()` (`GlobalExceptionHandler.java:61-64`), mas nos caminhos encontrados contém mensagens de validação próprias, não SQL/chave. Não há handler genérico: exceções imprevistas seguem `/error`; não há `server.error.include-message`/`include-stacktrace`, portanto defaults do Spring Boot devem ocultar mensagem/stack. Confirmar no artefato.
- **Headers:** a configuração não desliga os headers padrão do Spring Security (cache-control, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, entre outros da versão). HSTS só aparece quando a requisição é reconhecida como HTTPS; atrás do proxy isso depende de forwarded headers. CSP não é padrão e, para API JSON, tem valor limitado; deve ser definido no frontend/proxy.
- **SQL em logs:** `hibernate.format_sql=true` apenas formata SQL se ele for logado; `show-sql`/nível `org.hibernate.SQL` não estão ativados no repositório. Não foi encontrado log de redação, token ou e-mail. O motivo livre produzido pelo Gemini para imagem inválida é logado após sanitização e pode conter trecho da imagem, embora limitado a 300 caracteres (`GeminiTranscricaoParser.java:38`).

## 7. Pendências que dependem do deploy

- Confirmar Nginx/Caddy: TLS moderno, redirecionamento HTTP→HTTPS, HSTS, limite de request/URI, timeouts, buffering multipart e rate limit/conexões especialmente para auth e IA.
- Confirmar `API_BIND=127.0.0.1` (ou rede privada), porta 8080 fechada no firewall e banco acessível só por loopback/rede interna. Nunca combinar acesso direto público com confiança indiscriminada em `X-Forwarded-*`.
- Escolher/testar `FORWARD_HEADERS` com o proxy real; verificar `getRemoteAddr()` por teste controlado e remover headers recebidos do cliente no proxy antes de definir os próprios.
- Confirmar que `CADASTRO_ABERTO=false`, allowlist mínima, CORS exato (sem `*`) e segredos fortes estão efetivos no container; Docker Compose não valida vazio antecipadamente.
- Medir pool de threads/conexões, heap e concorrência com chamadas Gemini simuladas (nunca produção); configurar bulkhead/fila.
- Inspecionar `/tmp` do container, permissões, capacidade e limpeza de multipart; confirmar limites Tomcat efetivos (`maxPartCount`, parâmetros, request line e swallowing).
- Confirmar headers reais após TLS/proxy, inclusive HSTS e ausência de detalhes/stack em 4xx/5xx.
- Definir usuário PostgreSQL de mínimo privilégio. V7 precisa instalar `unaccent`; separar papel de migration do papel de runtime se possível.
- Configurar backups criptografados, restauração testada, retenção e exclusão coerentes com LGPD; controlar acesso operacional a redações/resposta bruta.
- Gerar árvore de dependências/SBOM no CI e consultar uma base CVE atualizada. Esta auditoria não afirma ausência de CVEs.

## 8. Limitações e comandos relevantes

Foram usados `git ls-files`, `git grep`, leitura numerada de fontes, `git log --all -p --full-history`, `git log -S`, `git show-ref` e buscas mascaradas. Há somente `refs/heads/main` e `refs/remotes/origin/main`; nenhum `refs/stash` apareceu. A árvore estava limpa antes deste relatório. `./mvnw dependency:tree -DskipTests` falhou localmente por ausência/inicialização do wrapper e `mvn` não está instalado; não houve download, instalação nem acesso de rede.
