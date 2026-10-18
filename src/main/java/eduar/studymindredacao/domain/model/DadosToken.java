package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.Role;

import java.util.UUID;

/**
 * O que vem dentro do JWT. {@code versaoSessao} é comparada com a do banco a cada requisição: se a senha foi
 * trocada depois que o token foi emitido, as versões não batem e o token deixa de valer.
 */
public record DadosToken(UUID usuarioId, String email, Role role, int versaoSessao) {
    public DadosToken {
        Validacoes.requererNaoNulo(usuarioId, "usuarioId");
        Validacoes.validarTextoObrigatorio(email, "email", 150);
        Validacoes.requererNaoNulo(role, "role");
        if (versaoSessao < 0) {
            throw new IllegalArgumentException("versaoSessao não pode ser negativa");
        }
    }

    /** Token emitido antes da versão de sessão existir: conta como versão 0, a mesma de quem nunca trocou a senha. */
    public DadosToken(UUID usuarioId, String email, Role role) {
        this(usuarioId, email, role, 0);
    }
}
