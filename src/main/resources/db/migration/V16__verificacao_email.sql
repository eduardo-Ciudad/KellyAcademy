-- Verificação de e-mail no cadastro

-- Quando o e-mail da conta foi confirmado. NULL = ainda não confirmado (login bloqueado
-- quando EMAIL_HABILITADO=true). As contas que já existem (piloto) entram como verificadas
-- na data de criação, para ninguém ficar trancado para fora.
ALTER TABLE usuarios
    ADD COLUMN email_verificado_em TIMESTAMPTZ;

UPDATE usuarios
SET email_verificado_em = criado_em
WHERE email_verificado_em IS NULL;

-- Tokens de uso único enviados por e-mail. Guarda só o SHA-256 do token (64 hex):
-- o valor em claro existe apenas no link do e-mail.
CREATE TABLE tokens_usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ,
    CONSTRAINT uk_tokens_usuario_hash UNIQUE (token_hash),
    CONSTRAINT ck_tokens_usuario_tipo CHECK (tipo IN ('VERIFICACAO_EMAIL')),
    CONSTRAINT ck_tokens_usuario_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tokens_usuario_expiracao CHECK (expira_em > criado_em),
    CONSTRAINT ck_tokens_usuario_uso CHECK (usado_em IS NULL OR usado_em >= criado_em)
);

-- Invalidar os tokens ativos de um usuário (ao reenviar o link)
CREATE INDEX idx_tokens_usuario_ativos
    ON tokens_usuario (usuario_id, tipo)
    WHERE usado_em IS NULL;

-- Limpeza diária dos expirados
CREATE INDEX idx_tokens_usuario_expira_em
    ON tokens_usuario (expira_em);