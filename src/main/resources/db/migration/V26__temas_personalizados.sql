-- V26__temas_personalizados.sql
ALTER TABLE temas DROP CONSTRAINT ck_temas_origem;
ALTER TABLE temas
    ADD CONSTRAINT ck_temas_origem CHECK (origem IN ('ENEM_OFICIAL', 'AUTORAL', 'PREVISAO', 'PERSONALIZADO'));

ALTER TABLE temas ADD COLUMN criado_por_id UUID REFERENCES usuarios(id);

-- Tema personalizado sempre tem dono; os outros nunca têm
ALTER TABLE temas ADD CONSTRAINT ck_temas_personalizado_dono
    CHECK ((origem = 'PERSONALIZADO') = (criado_por_id IS NOT NULL));

-- O mesmo aluno não repete o mesmo título (garante contra duplo clique e corrida)
CREATE UNIQUE INDEX uq_temas_personalizado_titulo
    ON temas (criado_por_id, lower(titulo))
    WHERE origem = 'PERSONALIZADO';