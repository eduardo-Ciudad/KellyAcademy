# Conjunto de calibração da correção

Redações com **nota conhecida** (corrigidas por humanos) usadas para medir se a correção por IA
está dando a nota certa. Roda antes de trocar prompt ou modelo (issues #24, #25, #26, #28).

## Formato (`<id>.json`)

```json
{
  "id": "enem2025-a-760",
  "tipo": "oficial",
  "fonte": "ENEM 2025 — espelho oficial INEP",
  "conferido": true,
  "tema": "...",
  "texto": "parágrafos separados por \n\n",
  "notas_oficiais": { "c1": 140, "c2": 180, "c3": 120, "c4": 160, "c5": 160 },
  "total_oficial": 760
}
```

- `conferido`: `false` enquanto a transcrição do manuscrito não foi conferida linha a linha.
  O runner roda mesmo assim, mas marca a redação no relatório.
- `total_oficial` precisa ser a soma das 5 notas (o runner confere).

## Regras de transcrição

- Manter **todos** os erros do original (acento, vírgula, grafia). Não corrigir nada.
- Palavras riscadas no manuscrito **não** entram (o INEP desconsidera).
- Palavra separada por hífen no fim da linha é juntada (`so-` + `ciedade` → `sociedade`).
- **Nenhum dado pessoal**: sem nome, CPF, inscrição ou data de nascimento. Use IDs neutros.

## Régua (correção rigorosa)

A Kelly corrige pela matriz de forma **mais rigorosa que o ENEM**. A nota oficial do INEP é o
**teto**, não o alvo. A nota oficial é a média de dois corretores (múltiplos de 20) e a IA responde
em múltiplos de 40, por isso há uma folga de 20.

| Meta | Regra |
|---|---|
| Nunca acima do oficial | nenhuma execução acima do total oficial + 20 |
| Consistência | amplitude (máx − mín) de cada redação ≤ 80 |
| Sanidade inferior | nenhuma execução mais de 200 abaixo do oficial (indica erro de leitura, não rigor) |
| Teto das verificações | redações `verificacao` nunca acima de `teto_total` e nunca 1000 |

Viés negativo por competência é esperado. Viés positivo é o que se quer evitar.

## Redações de verificação

Casos sem nota oficial, usados só como teto (ex.: a redação da issue #23, que a v2 corrigiu com 1000):

```json
{ "id": "verificacao-superdotacao", "tipo": "verificacao", "tema": "...", "texto": "...", "teto_total": 960 }
```

## Privacidade

Os `.json` desta pasta estão no `.gitignore` (textos de terceiros, repositório público).
Só este README é versionado.

## Como rodar

Fica fora do `mvn test` e do CI (chama a API real e tem custo).

```bash
# Windows
set GEMINI_API_KEY=...
mvnw.cmd test -Pcalibracao

# opções
mvnw.cmd test -Pcalibracao -Dcalibracao.repeticoes=5 -Dcalibracao.modelo=gemini-3.5-flash
```

No IntelliJ: rodar `CalibracaoRunnerTest` direto, com `GEMINI_API_KEY` na Run Configuration.

Relatório em `target/calibracao/relatorio-<data>.md` e respostas brutas em `target/calibracao/respostas/`.

## Redações atuais

| ID | Fonte | Total oficial | Conferido |
|---|---|---|---|
| enem2025-a-760 | ENEM 2025, espelho INEP | 760 | pendente |
| enem2025-b-560 | ENEM 2025, espelho INEP | 560 | pendente |
| verificacao-superdotacao | Issue #23 (teto 960, sem nota oficial; nunca 1000) | — | — |
