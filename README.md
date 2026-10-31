# Kelly Academy · API

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-green?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)
![CI](https://github.com/eduardo-Ciudad/KellyAcademy/actions/workflows/ci.yml/badge.svg)

Backend da [Kelly Academy](https://kellyacademy.com.br), plataforma de treino da redação do ENEM com correção por IA. O aluno escreve ou fotografa a redação, recebe a nota por competência com os problemas apontados, estuda repertórios ligados aos temas e acompanha a evolução.

Produto próprio da [CiudadLab](https://ciudadlab.com.br), desenvolvido por mim. O frontend está em [kellyacademy-frontend](https://github.com/eduardo-Ciudad/kellyacademy-frontend).

## O problema

O público é o estudante que já consegue escrever uma redação, mas não entende por que a nota não sobe. Uma nota sozinha não ajuda. O que ajuda é saber em qual competência ele perde pontos e por quê. E a IA, sozinha, tende a dar notas altas demais, então a correção precisa de regras que não dependam só do modelo.

## O que o sistema faz

* **Correção por IA:** nota nas 5 competências do ENEM, com os problemas encontrados e a análise de cada uma, a partir de um banco de temas ou de um tema escrito pelo próprio aluno
* **Redação manuscrita:** o aluno envia a foto, a IA transcreve e ele revisa o texto antes de enviar para a correção
* **Repertório:** pensadores, artigos da Constituição e dados com fonte, organizados por macroeixo e problema social, com busca sem acento
* **Possíveis temas do ENEM 2026:** lista própria que o aluno adiciona aos seus temas
* **Julia:** assistente que explica a correção de uma redação específica, num chat vinculado a ela
* **Evolução:** histórico das notas por competência
* **Planos Grátis e Pro:** assinatura pelo Stripe (Checkout e webhook), limites diferentes por plano e concessão manual do Pro pelo admin
* **Contas:** cadastro com verificação de e-mail, recuperação, redefinição e troca de senha
* **Painel admin:** visão geral, lista e detalhe de usuários, conceder ou revogar Pro, zerar a cota do dia e reenviar a verificação

## Decisões técnicas

**Arquitetura hexagonal.** O domínio não conhece Spring, JPA, Gemini nem Stripe. Os casos de uso dependem de portas (`AvaliacaoIAPort`, `PagamentoGatewayPort`, `EnvioEmailPort`...), e cada integração é um adapter. Pagamento e e-mail têm um adapter "desligado", então a API sobe e roda os testes sem nenhuma conta externa configurada.

**A nota da IA passa por regras da grade do INEP.** O prompt pede que o modelo liste os problemas e as evidências antes da nota, num JSON estruturado. Depois, a classe de domínio `RegrasDaGrade` confere a nota contra as regras objetivas da grade e só pode limitar, nunca aumentar. Toda limitação aparece para o aluno com o motivo. As consultas usadas para acompanhar a qualidade da correção estão em [`docs/METRICAS_CORRECAO.md`](docs/METRICAS_CORRECAO.md).

**Custo de IA sob controle.** Cada recurso (correção, transcrição, Julia) tem limite por aluno, que vem do plano, e um teto global diário somando todos os alunos. A vaga no teto global é reservada de forma atômica no banco, antes da chamada, e devolvida se a chamada falhar. Um `Semaphore` por recurso limita as chamadas simultâneas ao Gemini. Correção, transcrição e Julia usam chaves de projetos separados do Google Cloud, para uma cota não derrubar a outra.

**Plano consultado em um lugar só.** Os casos de uso perguntam ao `PlanoDoUsuario` o que o aluno pode fazer, em vez de espalhar regras de plano pelo código. O plano considera a assinatura do Stripe e a concessão manual do admin.

**Sessões que podem ser derrubadas.** O JWT carrega uma versão de sessão. Trocar ou redefinir a senha incrementa essa versão e invalida todos os tokens antigos. O webhook do Stripe valida a assinatura e guarda os eventos já processados, para não aplicar o mesmo evento duas vezes.

## Arquitetura

```
Frontend (Vercel) ──HTTPS──► Nginx ──► API Spring Boot (Docker, VPS) ──► PostgreSQL
                                              │
                         Gemini (3 projetos) · Stripe · SMTP
```

```
src/main/java/eduar/studymindredacao
├── domain
│   ├── model         # Redação, avaliação, repertório, plano, assinatura...
│   ├── service       # Regras puras: grade do INEP, análise do texto
│   ├── port          # Interfaces que o domínio exige do mundo externo
│   ├── julia         # Domínio da assistente
│   └── exception
├── application
│   └── usecase       # Um serviço por caso de uso
├── adapter
│   ├── in/web        # Controllers, DTOs e segurança da API
│   └── out
│       ├── ia        # Cliente Gemini, prompts e parsers
│       ├── pagamento # Stripe e adapter desligado
│       ├── email     # SMTP e adapter desligado
│       ├── persistence
│       └── security  # JWT, BCrypt, tokens aleatórios
└── config            # Configuração e validação das variáveis na subida
```

## Stack

* Java 17, Spring Boot 4.1, Spring Security, Spring Data JPA
* PostgreSQL 16 e Flyway (26 migrations escritas à mão, incluindo o conteúdo do Repertório)
* Gemini para correção, transcrição e Julia
* Stripe Java SDK
* JWT com java-jwt e BCrypt
* JUnit 5 e Mockito
* Docker Compose e GitHub Actions

## Como rodar

### Com Docker

```bash
git clone https://github.com/eduardo-Ciudad/KellyAcademy.git
cd KellyAcademy
cp .env.example .env   # preencha os valores
docker compose up -d --build
```

O Postgres cria o banco na primeira subida e as migrations rodam sozinhas. A API escuta na porta definida em `API_PORTA_HOST`.

### Sem Docker

Pré-requisitos: Java 17 e PostgreSQL. Exporte as variáveis do `.env.example` e rode:

```bash
./mvnw spring-boot:run
```

A configuração é validada na subida: se uma variável obrigatória faltar, a API não sobe e diz qual é.

### Testes

```bash
./mvnw verify
```

Os testes de repositório usam um PostgreSQL real, porque a busca do Repertório depende de `unaccent` e de operadores JSONB.

### Variáveis de ambiente

Estão todas no [`.env.example`](.env.example), comentadas.

| Grupo | O que configura |
|---|---|
| Banco | `DB_*` |
| API e segurança | `API_*`, `JWT_*`, `CORS_ORIGENS`, `FORWARD_HEADERS` |
| Cadastro | `CADASTRO_ABERTO`, `CADASTRO_EMAILS_PERMITIDOS` |
| IA | `GEMINI_*`, `JULIA_*`, `TRANSCRICAO_*`, `LIMITE_*`, `IA_CONCORRENCIA_*` |
| Pagamento | `PAGAMENTO_HABILITADO`, `STRIPE_*` |
| E-mail | `EMAIL_HABILITADO`, `MAIL_*`, `APP_FRONTEND_URL` |

## Endpoints principais

| Área | Rotas |
|---|---|
| Autenticação | `POST /auth/cadastro`, `/auth/login`, `/auth/verificar-email`, `/auth/reenviar-verificacao`, `/auth/esqueci-senha`, `/auth/redefinir-senha` · `PUT /usuarios/me/senha` |
| Redações | `POST /redacoes`, `GET /redacoes`, `GET /redacoes/{id}` · `POST /transcricoes` (multipart) |
| Temas | `GET /temas`, `GET /temas/possiveis`, `POST /temas/personalizados` · `PUT` e `DELETE /usuarios/me/temas/{temaId}` |
| Repertório | `GET /repertorios`, `GET /repertorios/{codigo}`, `GET /macroeixos` |
| Julia | `GET /redacoes/{id}/julia`, `POST /redacoes/{id}/julia/mensagens`, `DELETE /julia/conversas` |
| Aluno | `GET /usuarios/me`, `/me/plano`, `/me/correcoes-hoje`, `/me/transcricoes-hoje`, `/me/evolucao` |
| Assinatura | `POST /assinaturas/checkout`, `POST /webhooks/stripe` |
| Admin | `GET /admin/visao-geral`, `/admin/usuarios`, `/admin/usuarios/{id}` · `POST` de concessão e revogação do Pro, zerar cota e reenviar verificação |

As coleções do Postman de cada área estão em [`postman/`](postman).

## Testes

São mais de 1.300 testes em 194 arquivos. Eles cobrem as regras do domínio (grade do INEP, limites por plano, validações), os casos de uso com as portas simuladas, os parsers das respostas do Gemini, o webhook do Stripe e a segurança de cada rota.

O CI no GitHub Actions roda `./mvnw verify` contra um PostgreSQL 16 e valida o build da imagem Docker.

## Documentação

* [`docs/repertorio/`](docs/repertorio): curadoria do conteúdo do Repertório e como ele entra no banco
* [`docs/METRICAS_CORRECAO.md`](docs/METRICAS_CORRECAO.md): consultas para acompanhar a qualidade da correção
* [`docs/SECURITY_AUDIT_V2.md`](docs/SECURITY_AUDIT_V2.md): auditoria de segurança e status dos achados

## Autor

Eduardo · [CiudadLab](https://ciudadlab.com.br) · [LinkedIn](https://www.linkedin.com/in/eduardociudadf) · [GitHub](https://github.com/eduardo-Ciudad)