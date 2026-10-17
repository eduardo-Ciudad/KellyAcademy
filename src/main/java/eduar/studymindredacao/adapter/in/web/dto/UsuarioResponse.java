package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta do cadastro. {@code emailVerificado=false} indica ao front que deve mostrar a tela
 * "confira seu e-mail" em vez de seguir para o login.
 */
public record UsuarioResponse(
        UUID id,
        String nome,
        String email,
        Role role,
        OffsetDateTime criadoEm,
        boolean emailVerificado
) {
    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.id(),
                usuario.nome(),
                usuario.email(),
                usuario.role(),
                usuario.criadoEm(),
                usuario.emailVerificado()
        );
    }
}
