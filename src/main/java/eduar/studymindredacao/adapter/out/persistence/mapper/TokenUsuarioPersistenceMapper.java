package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.adapter.out.persistence.entity.TokenUsuarioEntity;
import eduar.studymindredacao.domain.model.TokenUsuario;

public final class TokenUsuarioPersistenceMapper {
    private TokenUsuarioPersistenceMapper() {
    }

    public static TokenUsuario toDomain(TokenUsuarioEntity entity) {
        return new TokenUsuario(
                entity.getId(),
                entity.getUsuarioId(),
                entity.getTipo(),
                entity.getTokenHash(),
                entity.getCriadoEm(),
                entity.getExpiraEm(),
                entity.getUsadoEm()
        );
    }

    public static TokenUsuarioEntity toEntity(TokenUsuario domain) {
        var entity = new TokenUsuarioEntity();
        entity.setId(domain.id());
        entity.setUsuarioId(domain.usuarioId());
        entity.setTipo(domain.tipo());
        entity.setTokenHash(domain.tokenHash());
        entity.setCriadoEm(domain.criadoEm());
        entity.setExpiraEm(domain.expiraEm());
        entity.setUsadoEm(domain.usadoEm());
        return entity;
    }
}
