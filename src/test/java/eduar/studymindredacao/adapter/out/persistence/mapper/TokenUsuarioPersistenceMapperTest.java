package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsuarioPersistenceMapperTest {
    private static final String HASH = "b".repeat(64);
    private static final OffsetDateTime CRIADO = OffsetDateTime.of(2026, 10, 16, 21, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void idaEVoltaDeTokenNovo() {
        var original = new TokenUsuario(UUID.randomUUID(), UUID.randomUUID(), TipoTokenUsuario.VERIFICACAO_EMAIL,
                HASH, CRIADO, CRIADO.plusHours(24), null);

        var entity = TokenUsuarioPersistenceMapper.toEntity(original);

        assertThat(entity.getId()).isEqualTo(original.id());
        assertThat(entity.getUsuarioId()).isEqualTo(original.usuarioId());
        assertThat(entity.getTipo()).isEqualTo(TipoTokenUsuario.VERIFICACAO_EMAIL);
        assertThat(entity.getTokenHash()).isEqualTo(HASH);
        assertThat(entity.getUsadoEm()).isNull();
        assertThat(TokenUsuarioPersistenceMapper.toDomain(entity)).isEqualTo(original);
    }

    @Test
    void idaEVoltaDeTokenUsado() {
        var original = new TokenUsuario(UUID.randomUUID(), UUID.randomUUID(), TipoTokenUsuario.VERIFICACAO_EMAIL,
                HASH, CRIADO, CRIADO.plusHours(24), CRIADO.plusMinutes(2));

        var devolta = TokenUsuarioPersistenceMapper.toDomain(TokenUsuarioPersistenceMapper.toEntity(original));

        assertThat(devolta.usadoEm()).isEqualTo(CRIADO.plusMinutes(2));
        assertThat(devolta.estaValido(CRIADO.plusMinutes(3))).isFalse();
    }

    @Test
    void toStringDaEntityNaoMostraOHash() {
        var original = TokenUsuario.novo(UUID.randomUUID(), TipoTokenUsuario.VERIFICACAO_EMAIL, HASH,
                CRIADO, java.time.Duration.ofHours(1));

        assertThat(TokenUsuarioPersistenceMapper.toEntity(original).toString()).doesNotContain(HASH);
    }
}
