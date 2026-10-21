-- Repertório (pesquisa V4): 4 problemas sociais novos na taxonomia.
-- Fonte: docs/repertorio/curadoria-v4.json. Os macroeixos já existem (V8); aqui só entram os problemas.
-- Upsert por slug, no mesmo padrão da V8.

WITH dados (macroeixo_slug, slug, nome) AS (
    VALUES
    ('raca-genero',          'violencia-genero',               'Violência de gênero'),
    ('educacao',             'altas-habilidades-superdotacao', 'Altas habilidades e superdotação na escola'),
    ('tecnologia-sociedade', 'desinformacao-deepfakes',        'Desinformação e conteúdos sintéticos'),
    ('pcd-neurodiversidade', 'deficiencia-visual',             'Deficiência visual e acessibilidade')
)
INSERT INTO problemas_sociais (macroeixo_id, slug, nome)
SELECT m.id, d.slug, d.nome
FROM dados d
JOIN macroeixos m ON m.slug = d.macroeixo_slug
ON CONFLICT (slug) DO UPDATE
    SET nome = EXCLUDED.nome,
        macroeixo_id = EXCLUDED.macroeixo_id;