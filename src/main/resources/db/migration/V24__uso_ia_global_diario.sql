-- Teto global de uso da IA por dia e por recurso (KELLY-SEC-002).
-- Antes, o teto era conferido somando uso_ia_diario na hora e reservando depois, em duas operações:
-- requisições simultâneas liam o mesmo total e todas passavam do teto.
-- Com uma linha por dia e recurso, a reserva vira um único UPDATE condicional e atômico no Postgres,
-- no mesmo padrão da cota individual (INSERT ... ON CONFLICT DO UPDATE ... WHERE quantidade < teto).
CREATE TABLE uso_ia_global_diario (
    data DATE NOT NULL,
    recurso VARCHAR(20) NOT NULL,
    quantidade INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_uso_ia_global_diario PRIMARY KEY (data, recurso),
    CONSTRAINT ck_uso_ia_global_diario_recurso CHECK (recurso IN ('CORRECAO', 'TRANSCRICAO', 'JULIA')),
    CONSTRAINT ck_uso_ia_global_diario_quantidade CHECK (quantidade >= 0)
);

-- Carga inicial com o uso que já existe, para o deploy no meio do dia não zerar o teto de hoje.
-- Copia todas as datas (não só CURRENT_DATE): a data de uso_ia_diario vem do relógio da aplicação,
-- que pode estar em outro fuso que o do banco.
INSERT INTO uso_ia_global_diario (data, recurso, quantidade)
SELECT data, 'CORRECAO', SUM(qtd_correcoes) FROM uso_ia_diario GROUP BY data HAVING SUM(qtd_correcoes) > 0
UNION ALL
SELECT data, 'TRANSCRICAO', SUM(qtd_transcricoes) FROM uso_ia_diario GROUP BY data HAVING SUM(qtd_transcricoes) > 0
UNION ALL
SELECT data, 'JULIA', SUM(qtd_mensagens_julia) FROM uso_ia_diario GROUP BY data HAVING SUM(qtd_mensagens_julia) > 0;