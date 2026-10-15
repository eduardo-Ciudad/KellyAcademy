# Métricas da correção por IA

Consultas de leitura para acompanhar a qualidade da correção (issue #27).
Rodar no Postgres da VPS (`docker compose exec db psql -U <usuario> -d <banco>`).

As notas da IA ficam no `resposta_bruta_json` da avaliação. As notas gravadas em
`avaliacao_competencias` já passaram pelas regras da grade (`RegrasDaGrade`), que só limitam.
Toda limitação também aparece nos problemas da competência como "Nota limitada a ... pela grade do INEP".

## 1. Visão geral

```sql
SELECT count(*)                                                   AS correcoes,
       count(*) FILTER (WHERE a.nota_total = 1000)                AS notas_1000,
       round(100.0 * count(*) FILTER (WHERE a.nota_total = 1000)
             / nullif(count(*), 0), 1)                            AS pct_1000,
       count(*) FILTER (WHERE EXISTS (
           SELECT 1 FROM avaliacao_competencias ac
           WHERE ac.avaliacao_id = a.id
             AND ac.problemas::text LIKE '%Nota limitada a%'))    AS com_nota_limitada,
       round(100.0 * count(*) FILTER (WHERE EXISTS (
           SELECT 1 FROM avaliacao_competencias ac
           WHERE ac.avaliacao_id = a.id
             AND ac.problemas::text LIKE '%Nota limitada a%'))
             / nullif(count(*), 0), 1)                            AS pct_limitada
FROM avaliacoes a
WHERE NOT a.anulada
  AND a.avaliado_em >= now() - interval '30 days';
```

## 2. Limitações por competência (nota da IA x nota final)

```sql
WITH ia AS (
    SELECT a.id                                                  AS avaliacao_id,
           regexp_replace(c->>'competencia', '\D', '', 'g')::int AS numero,
           (c->>'nota')::int                                     AS nota_ia
    FROM avaliacoes a,
         jsonb_array_elements(a.resposta_bruta_json->'avaliacao_por_competencia') c
    WHERE NOT a.anulada
      AND a.avaliado_em >= now() - interval '30 days'
)
SELECT ac.numero                                                   AS competencia,
       count(*)                                                    AS correcoes,
       count(*) FILTER (WHERE ac.nota < ia.nota_ia)                AS limitadas,
       round(100.0 * count(*) FILTER (WHERE ac.nota < ia.nota_ia)
             / nullif(count(*), 0), 1)                             AS pct_limitadas,
       round(avg(ia.nota_ia - ac.nota) FILTER (WHERE ac.nota < ia.nota_ia), 0) AS queda_media
FROM ia
JOIN avaliacao_competencias ac
  ON ac.avaliacao_id = ia.avaliacao_id AND ac.numero = ia.numero
GROUP BY ac.numero
ORDER BY ac.numero;
```

Competência com `pct_limitadas` alto indica que o prompt não está levando a IA a aplicar
aquele critério sozinha: vale revisar o prompt antes de depender só da regra.

## 3. Incoerências (nota abaixo de 200 sem problema listado)

```sql
SELECT ac.numero AS competencia, count(*) AS incoerentes
FROM avaliacoes a
JOIN avaliacao_competencias ac ON ac.avaliacao_id = a.id
WHERE NOT a.anulada
  AND ac.nota < 200
  AND ac.problemas = '[]'::jsonb
  AND a.avaliado_em >= now() - interval '30 days'
GROUP BY ac.numero
ORDER BY ac.numero;
```

Também ficam no log da API: `Correção incoerente na redação ...` e `Grade limitou C... da redação ...`.
