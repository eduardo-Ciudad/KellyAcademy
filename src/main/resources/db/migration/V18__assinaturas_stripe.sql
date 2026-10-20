-- Assinatura do plano Pro via Stripe (cartão, renovação automática)

-- Cliente no Stripe (cus_...). Um por usuário, criado no primeiro checkout e reaproveitado depois:
-- assim todas as assinaturas e faturas do aluno ficam no mesmo cliente do Stripe.
ALTER TABLE usuarios
    ADD COLUMN stripe_customer_id VARCHAR(255);

ALTER TABLE usuarios
    ADD CONSTRAINT uk_usuarios_stripe_customer UNIQUE (stripe_customer_id);

-- Espelho local das assinaturas do Stripe. Quem escreve aqui é o webhook: o Stripe é a fonte da verdade,
-- e esta tabela existe para o backend saber se o aluno é Pro sem chamar o Stripe a cada requisição.
CREATE TABLE assinaturas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    stripe_subscription_id VARCHAR(255) NOT NULL,
    ciclo VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    periodo_atual_fim TIMESTAMPTZ NOT NULL,
    cancelar_no_fim_do_periodo BOOLEAN NOT NULL DEFAULT false,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_assinaturas_stripe_subscription UNIQUE (stripe_subscription_id),
    CONSTRAINT ck_assinaturas_ciclo CHECK (ciclo IN ('MENSAL', 'ANUAL')),
    CONSTRAINT ck_assinaturas_status CHECK (status IN (
        'INCOMPLETE', 'INCOMPLETE_EXPIRED', 'TRIALING', 'ACTIVE',
        'PAST_DUE', 'CANCELED', 'UNPAID', 'PAUSED'
    )),
    CONSTRAINT ck_assinaturas_atualizacao CHECK (atualizado_em >= criado_em)
);

-- "Este aluno é Pro?": consulta por usuário a cada correção
CREATE INDEX idx_assinaturas_usuario ON assinaturas (usuario_id);

-- Idempotência do webhook: o Stripe pode entregar o mesmo evento mais de uma vez (retry).
-- Antes de processar, o backend tenta inserir o id do evento; se já existe, ignora.
CREATE TABLE eventos_stripe (
    id VARCHAR(255) PRIMARY KEY,
    tipo VARCHAR(100) NOT NULL,
    processado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Limpeza futura dos eventos antigos (o Stripe só reenvia por até 3 dias)
CREATE INDEX idx_eventos_stripe_processado_em ON eventos_stripe (processado_em);