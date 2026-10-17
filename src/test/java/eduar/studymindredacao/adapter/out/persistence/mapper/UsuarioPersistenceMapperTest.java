package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioPersistenceMapperTest {
    private static final OffsetDateTime CRIADO = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void idaEVoltaPreservaTodosOsCamposComEmailVerificado() {
        var original = new Usuario(UUID.randomUUID(), "Ana", "ana@exemplo.com", "hash", Role.ALUNO,
                CRIADO, CRIADO.plusMinutes(3));

        var entity = UsuarioPersistenceMapper.toEntity(original);
        var devolta = UsuarioPersistenceMapper.toDomain(entity);

        assertThat(entity.getEmailVerificadoEm()).isEqualTo(CRIADO.plusMinutes(3));
        assertThat(devolta).isEqualTo(original);
        assertThat(devolta.emailVerificado()).isTrue();
    }

    @Test
    void emailNaoVerificadoContinuaNuloNaIdaENaVolta() {
        var original = new Usuario(UUID.randomUUID(), "Ana", "ana@exemplo.com", "hash", Role.ALUNO, CRIADO, null);

        var entity = UsuarioPersistenceMapper.toEntity(original);

        assertThat(entity.getEmailVerificadoEm()).isNull();
        assertThat(UsuarioPersistenceMapper.toDomain(entity).emailVerificado()).isFalse();
    }
}
