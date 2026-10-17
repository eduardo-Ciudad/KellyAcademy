package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conta do aluno. {@code emailVerificadoEm} nulo = e-mail ainda não confirmado pelo link enviado no cadastro.
 */
public record Usuario(
        UUID id,
        String nome,
        String email,
        String senhaHash,
        Role role,
        OffsetDateTime criadoEm,
        OffsetDateTime emailVerificadoEm
) {
    public Usuario {
        Validacoes.validarTextoObrigatorio(nome, "nome", 150);
        Validacoes.validarTextoObrigatorio(email, "email", 150);
        Validacoes.validarTextoObrigatorio(senhaHash, "senhaHash", 255);
        email = email.trim().toLowerCase();
        if (role == null) {
            role = Role.ALUNO;
        }
    }

    public boolean emailVerificado() {
        return emailVerificadoEm != null;
    }

    /** Marca o e-mail como confirmado. Se já estava, mantém a data da primeira confirmação. */
    public Usuario comEmailVerificado(OffsetDateTime quando) {
        Validacoes.requererNaoNulo(quando, "quando");
        if (emailVerificado()) {
            return this;
        }
        return new Usuario(id, nome, email, senhaHash, role, criadoEm, quando);
    }

    @Override
    public String toString() {
        return "Usuario[id=" + id + ", email=" + email + ", role=" + role
                + ", emailVerificado=" + emailVerificado() + ", senhaHash=***]";
    }
}
