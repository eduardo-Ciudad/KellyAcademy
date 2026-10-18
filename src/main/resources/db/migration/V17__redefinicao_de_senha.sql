-- V17__redefinicao_senha.sql
-- Redefinição de senha e invalidação de sessões

-- versao_sessao: sobe a cada troca de senha. Vai dentro do JWT (claim "sv"); token com versão
-- diferente da do banco deixa de valer. Contas existentes começam em 0, e os tokens atuais
-- (sem "sv") contam como 0, então ninguém é deslogado no deploy.
-- senha_alterada_em: NULL = a senha nunca foi trocada desde o cadastro.
ALTER TABLE usuarios
    ADD COLUMN versao_sessao INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN senha_alterada_em TIMESTAMPTZ,
    ADD CONSTRAINT ck_usuarios_versao_sessao CHECK (versao_sessao >= 0);

-- Novo tipo de token de uso único
ALTER TABLE tokens_usuario
    DROP CONSTRAINT ck_tokens_usuario_tipo,
    ADD CONSTRAINT ck_tokens_usuario_tipo
        CHECK (tipo IN ('VERIFICACAO_EMAIL', 'REDEFINICAO_SENHA'));