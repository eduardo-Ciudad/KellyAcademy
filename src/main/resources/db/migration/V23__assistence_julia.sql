-- Assistente Júlia: conversa sobre a correção de uma redação.
-- A conversa vale durante a sessão. No logout (ou após 24h sem atividade) ela é ENCERRADA,
-- e o histórico fica guardado para análise de qualidade e custo (citado na Política de Privacidade).
CREATE TABLE conversas_julia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    redacao_id UUID NOT NULL REFERENCES redacoes(id) ON DELETE CASCADE,
    status VARCHAR(15) NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultima_atividade_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    encerrada_em TIMESTAMPTZ,
    CONSTRAINT ck_conversas_julia_status CHECK (status IN ('ATIVA', 'ENCERRADA')),
    CONSTRAINT ck_conversas_julia_encerramento CHECK (
        (status = 'ATIVA' AND encerrada_em IS NULL) OR
        (status = 'ENCERRADA' AND encerrada_em IS NOT NULL)
    ),
    CONSTRAINT ck_conversas_julia_atividade CHECK (ultima_atividade_em >= criada_em)
);

-- No máximo uma conversa ATIVA por aluno e redação.
-- Também serve ao "encerrar todas do usuário" no logout (usuario_id é o prefixo do índice).
CREATE UNIQUE INDEX uk_conversas_julia_ativa
    ON conversas_julia (usuario_id, redacao_id)
    WHERE status = 'ATIVA';

CREATE TABLE mensagens_julia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversa_id UUID NOT NULL REFERENCES conversas_julia(id) ON DELETE CASCADE,
    ordem SMALLINT NOT NULL,
    papel VARCHAR(10) NOT NULL,
    conteudo TEXT NOT NULL,
    tokens_entrada INT,
    tokens_saida INT,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_mensagens_julia_ordem UNIQUE (conversa_id, ordem),
    CONSTRAINT ck_mensagens_julia_ordem CHECK (ordem >= 0),
    CONSTRAINT ck_mensagens_julia_papel CHECK (papel IN ('USUARIO', 'JULIA')),
    CONSTRAINT ck_mensagens_julia_tokens CHECK (
        (tokens_entrada IS NULL OR tokens_entrada >= 0) AND
        (tokens_saida IS NULL OR tokens_saida >= 0)
    )
);

-- Contador diário de perguntas à Júlia, separado das correções e transcrições.
-- Os tokens dela NÃO entram em tokens_entrada/tokens_saida do dia: ficam por mensagem em mensagens_julia,
-- para o custo da Júlia (projeto separado no Google Cloud) não se misturar com o da correção.
ALTER TABLE uso_ia_diario
    ADD COLUMN qtd_mensagens_julia INT NOT NULL DEFAULT 0;

ALTER TABLE uso_ia_diario
    ADD CONSTRAINT ck_uso_ia_diario_qtd_mensagens_julia CHECK (qtd_mensagens_julia >= 0);