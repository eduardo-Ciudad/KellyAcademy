package eduar.studymindredacao.adapter.in.web.dto.admin;

import eduar.studymindredacao.domain.model.admin.UsuarioResumoAdmin;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Formato do AdminUsuarioResumo do front. A nota média vai arredondada (nota total é inteira). */
public record AdminUsuarioResumoResponse(
        UUID id,
        String nome,
        String email,
        Role role,
        Plano plano,
        boolean emailVerificado,
        long totalRedacoes,
        Integer notaMedia,
        OffsetDateTime ultimaRedacaoEm,
        OffsetDateTime criadoEm
) {
    public static AdminUsuarioResumoResponse de(UsuarioResumoAdmin u) {
        return new AdminUsuarioResumoResponse(u.id(), u.nome(), u.email(), u.role(), u.plano(), u.emailVerificado(),
                u.totalRedacoes(), Arredondamento.inteiro(u.notaMedia()), u.ultimaRedacaoEm(), u.criadoEm());
    }
}
