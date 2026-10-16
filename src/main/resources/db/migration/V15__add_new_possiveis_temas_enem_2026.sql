-- Temas possíveis do ENEM 2026 — segunda pesquisa (Investigação Preliminar, corte de 15/10/2026)
-- Fonte: StudyMind_ENEM_2026_Investigacao_Preliminar.docx, seção 8 (8 formulações com dossiê).
-- A pesquisa é preliminar e não atribui pontuação; por isso os temas entram como VALE_TREINAR, depois dos 12 da V5.

WITH dados (ranking, titulo, forca, eixo, grupo_social, justificativa, argumentos, marcos_legais, agentes_intervencao) AS (
    VALUES
    (13, 'Desafios para a inclusão educacional de pessoas com deficiência visual no Brasil', 'VALE_TREINAR', 'Educação, inclusão e deficiência visual', 'Pessoas cegas, com baixa visão ou visão monocular',
     'Aprofundamento concreto do eixo de deficiência. A pesquisa preliminar classifica como prioridade média de estudo; a suposta pista em postagens do Inep no Instagram não foi verificada. Cuidado: baixa visão não é o mesmo que cegueira.',
     '["Oferta insuficiente de material acessível", "Braile e tecnologias assistivas", "Formação docente", "Diferença entre cegueira, baixa visão e visão monocular"]'::jsonb,
     '["Constituição Federal", "Lei Brasileira de Inclusão", "Recursos de acessibilidade do Inep para cegueira, baixa visão e visão monocular"]'::jsonb,
     '["MEC", "Redes de ensino", "Escolas"]'::jsonb),
    (14, 'Desafios para o acolhimento em saúde mental de jovens no Brasil', 'VALE_TREINAR', 'Saúde mental e juventude', 'Jovens',
     'Ampliação do projeto Pode Falar, integrado ao Meu SUS Digital em julho de 2026. A pesquisa preliminar o vê como eixo adicional de política pública; o estigma das doenças mentais já foi tema em 2020.',
     '["Acesso ao cuidado", "Encaminhamento", "Redes locais de cuidado", "Acesso fora das capitais", "Escuta digital não substitui atenção clínica"]'::jsonb,
     '["Projeto Pode Falar (Meu SUS Digital, 2026)", "Diretrizes do SUS"]'::jsonb,
     '["SUS", "Educação"]'::jsonb),
    (15, 'Desafios para a valorização de mestras e mestres das culturas populares no Brasil', 'VALE_TREINAR', 'Cultura e direitos culturais', 'Mestras e mestres de culturas tradicionais e populares',
     'O Decreto 12.981/2026 trouxe evidência específica sobre culturas tradicionais e populares. O recorte se diferencia da herança africana (tema de 2024), mas exige cuidado com repetição temática.',
     '["Reconhecimento", "Condições de transmissão dos saberes", "Acesso a fomento", "Risco de folclorizar e homogeneizar tradições"]'::jsonb,
     '["Decreto 12.981/2026"]'::jsonb,
     '["Ministério da Cultura", "Entes locais"]'::jsonb),
    (16, 'Obstáculos ao diagnóstico precoce de alterações visuais na infância brasileira', 'VALE_TREINAR', 'Saúde e primeira infância', 'Bebês e crianças na primeira infância',
     'Em fevereiro de 2026 o Ministério da Saúde abriu consulta pública para revisar o Guia de Triagem Ocular Neonatal. É um sinal institucional independente, mas não indica relação com a escolha do tema.',
     '["Diagnóstico e encaminhamento oportunos", "Desigualdades no encaminhamento oftalmológico", "Formação de profissionais", "Teste do Olhinho é triagem, não diagnóstico definitivo"]'::jsonb,
     '["Guia de Triagem Ocular Neonatal (consulta pública MS, 2026)", "Teste do Olhinho (reflexo vermelho)"]'::jsonb,
     '["SUS", "Maternidades", "Atenção básica"]'::jsonb),
    (17, 'Barreiras à acessibilidade digital para pessoas cegas e com baixa visão no Brasil', 'VALE_TREINAR', 'Tecnologia, acessibilidade e cidadania', 'Pessoas cegas e com baixa visão',
     'Hipótese ampla da pesquisa preliminar: nenhuma norma nova foi verificada em 2026. Vale como treino de um recorte diferente dentro do eixo de deficiência visual.',
     '["Interfaces, documentos e serviços digitais inadequados", "Acessibilidade de serviços públicos digitais", "Tecnologia assistiva não resolve barreiras estruturais"]'::jsonb,
     '["Lei Brasileira de Inclusão"]'::jsonb,
     '["Governos", "Reguladores", "Fornecedores de serviços digitais"]'::jsonb),
    (18, 'Desafios para a preservação dos conhecimentos transmitidos oralmente no Brasil', 'VALE_TREINAR', 'Cultura e patrimônio imaterial', 'Comunidades tradicionais e detentores de saberes orais',
     'Mesma âncora do Decreto 12.981/2026, eixo próximo ao das mestras e mestres de culturas populares; também se aproxima do tema de 2024.',
     '["Salvaguarda participativa", "Respeito à autoria coletiva", "Proteção do patrimônio cultural imaterial", "Risco de apropriação dos saberes"]'::jsonb,
     '["Decreto 12.981/2026"]'::jsonb,
     '["Instituições culturais", "Comunidades"]'::jsonb),
    (19, 'Desafios para a redução da desigualdade territorial no acesso a cuidados no Brasil', 'VALE_TREINAR', 'Cuidado e políticas públicas', 'Pessoas que dependem de cuidados e quem cuida delas',
     'Estados e capitais avançaram em 2026 na adesão ao Plano Brasil que Cuida. A implementação varia entre municípios de capacidade distinta.',
     '["Implementação desigual entre municípios", "Capacidade local distinta", "Não inferir resultados apenas de anúncios"]'::jsonb,
     '["Lei 15.069/2024 (Política Nacional de Cuidados)", "Plano Brasil que Cuida"]'::jsonb,
     '["Governo federal", "Estados", "Municípios"]'::jsonb),
    (20, 'Desafios para o enfrentamento da sobrecarga de cuidadores familiares no Brasil', 'VALE_TREINAR', 'Cuidado, trabalho e família', 'Cuidadores familiares',
     'A Política Nacional de Cuidados é um documento robusto, mas o recorte é vizinho do tema de 2023 (trabalho de cuidado) e do envelhecimento de 2025.',
     '["Divisão desigual dos cuidados", "Carência de rede pública", "Limites diante do tema oficial de 2023"]'::jsonb,
     '["Lei 15.069/2024 (Política Nacional de Cuidados)"]'::jsonb,
     '["Assistência social", "Saúde", "Trabalho"]'::jsonb)
),
novos AS (
    INSERT INTO temas (titulo, origem, ano)
    SELECT titulo, 'PREVISAO', 2026 FROM dados
    RETURNING id, titulo
)
INSERT INTO temas_previsao (tema_id, ranking, forca, eixo, grupo_social, justificativa, argumentos, marcos_legais, agentes_intervencao)
SELECT n.id, d.ranking, d.forca, d.eixo, d.grupo_social, d.justificativa, d.argumentos, d.marcos_legais, d.agentes_intervencao
FROM novos n
JOIN dados d ON d.titulo = n.titulo;
