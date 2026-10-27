package eduar.studymindredacao.domain.model.admin;

import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.Role;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha de um aluno no painel admin (últimos cadastros e listagem).
 * {@code notaMedia} e {@code ultimaRedacaoEm} são nulos para quem ainda não tem redação.
 */
public record UsuarioResumoAdmin(
        UUID id,
        String nome,
        String email,
        Role role,
        Plano plano,
        boolean emailVerificado,
        long totalRedacoes,
        Double notaMedia,
        OffsetDateTime ultimaRedacaoEm,
        OffsetDateTime criadoEm
) {
    public UsuarioResumoAdmin {
        Objects.requireNonNull(id, "id é obrigatório");
        Objects.requireNonNull(role, "role é obrigatória");
        Objects.requireNonNull(plano, "plano é obrigatório");
        Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        if (totalRedacoes < 0) {
            throw new IllegalArgumentException("totalRedacoes não pode ser negativo");
        }
    }
}
