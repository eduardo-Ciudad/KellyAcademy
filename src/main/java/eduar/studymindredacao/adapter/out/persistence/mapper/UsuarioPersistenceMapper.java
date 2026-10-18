package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.adapter.out.persistence.entity.UsuarioEntity;
import eduar.studymindredacao.domain.model.Usuario;

public final class UsuarioPersistenceMapper {
    private UsuarioPersistenceMapper() {
    }

    public static Usuario toDomain(UsuarioEntity entity) {
        return new Usuario(
                entity.getId(),
                entity.getNome(),
                entity.getEmail(),
                entity.getSenhaHash(),
                entity.getRole(),
                entity.getCriadoEm(),
                entity.getEmailVerificadoEm(),
                entity.getVersaoSessao(),
                entity.getSenhaAlteradaEm()
        );
    }

    public static UsuarioEntity toEntity(Usuario domain) {
        var entity = new UsuarioEntity();
        entity.setId(domain.id());
        entity.setNome(domain.nome());
        entity.setEmail(domain.email());
        entity.setSenhaHash(domain.senhaHash());
        entity.setRole(domain.role());
        entity.setCriadoEm(domain.criadoEm());
        entity.setEmailVerificadoEm(domain.emailVerificadoEm());
        entity.setVersaoSessao(domain.versaoSessao());
        entity.setSenhaAlteradaEm(domain.senhaAlteradaEm());
        return entity;
    }
}
