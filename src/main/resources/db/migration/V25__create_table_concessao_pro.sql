CREATE TABLE concessoes_pro (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    concedida_por UUID NOT NULL REFERENCES usuarios(id),
    motivo VARCHAR(300) NOT NULL,
    inicio TIMESTAMPTZ NOT NULL DEFAULT now(),
    fim TIMESTAMPTZ NOT NULL,
    revogada_em TIMESTAMPTZ,
    revogada_por UUID REFERENCES usuarios(id),
    motivo_revogacao VARCHAR(300),
    CONSTRAINT ck_concessoes_pro_periodo CHECK (fim > inicio),
    CONSTRAINT ck_concessoes_pro_motivo CHECK (length(trim(motivo)) >= 5),
    CONSTRAINT ck_concessoes_pro_revogacao CHECK (
        (revogada_em IS NULL AND revogada_por IS NULL AND motivo_revogacao IS NULL) OR
        (revogada_em IS NOT NULL AND revogada_por IS NOT NULL AND motivo_revogacao IS NOT NULL
         AND revogada_em >= inicio)
    )
);

CREATE UNIQUE INDEX uk_concessoes_pro_ativa
    ON concessoes_pro (usuario_id)
    WHERE revogada_em IS NULL;

CREATE INDEX idx_concessoes_pro_usuario ON concessoes_pro (usuario_id, fim);