package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TokenUsuarioRepositoryPort {
    TokenUsuario salvar(TokenUsuario token);

    /**
     * Marca o token como usado numa única operação, só se ele existir com esse tipo, ainda não tiver sido usado
     * e não tiver expirado. Duas chamadas simultâneas com o mesmo hash: no máximo uma recebe o usuário.
     *
     * @return o id do dono do token, ou vazio se o token não serve
     */
    Optional<UUID> consumirValido(String tokenHash, TipoTokenUsuario tipo, OffsetDateTime agora);

    /** Marca como usados os tokens ainda válidos do usuário nesse tipo (ex.: antes de reenviar o link). */
    int invalidarAtivos(UUID usuarioId, TipoTokenUsuario tipo, OffsetDateTime agora);

    /** Apaga os tokens expirados ou usados antes do corte. Retorna quantos foram apagados. */
    int excluirAntigos(OffsetDateTime corte);
}
