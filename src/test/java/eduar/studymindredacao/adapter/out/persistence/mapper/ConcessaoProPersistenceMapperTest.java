package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.domain.model.ConcessaoPro;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConcessaoProPersistenceMapperTest {
    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 10, 27, 17, 0, 0, 0, ZoneOffset.ofHours(-3));

    @Test
    void idaEVoltaPreservaTodosOsCamposDeUmaRevogada() {
        var original = new ConcessaoPro(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Testador do piloto",
                INICIO, INICIO.plusDays(30), INICIO.plusDays(2), UUID.randomUUID(), "Fim do piloto");

        var voltou = ConcessaoProPersistenceMapper.toDomain(ConcessaoProPersistenceMapper.toEntity(original));

        assertThat(voltou).isEqualTo(original);
    }

    @Test
    void novaAbertaViraEntitySemIdESemRevogacao() {
        var nova = new ConcessaoPro(null, UUID.randomUUID(), UUID.randomUUID(), "Testador do piloto",
                INICIO, INICIO.plusDays(30), null, null, null);

        var entity = ConcessaoProPersistenceMapper.toEntity(nova);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getRevogadaEm()).isNull();
        assertThat(entity.getRevogadaPor()).isNull();
        assertThat(entity.getMotivoRevogacao()).isNull();
    }
}
