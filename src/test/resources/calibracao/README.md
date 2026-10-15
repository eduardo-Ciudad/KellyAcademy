# Conjunto de calibração da correção

Redações com **nota conhecida** (corrigidas por humanos) usadas para medir se a correção por IA
está dando a nota certa. Roda antes de trocar prompt ou modelo (issues #24, #25, #26, #28).

## Formato (`<id>.json`)

```json
{
  "id": "enem2025-a-760",
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

## Tolerância

A nota oficial do ENEM é a **média de dois corretores**, então aparecem 20, 60, 100, 140, 180.
A IA sempre responde em múltiplos de 40. Por isso:

- **Acerto por competência**: erro absoluto ≤ 20.
- **Meta (#28)**: erro médio no total ≤ 40 e nenhum 1000 em redação oficial abaixo de 940.

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
