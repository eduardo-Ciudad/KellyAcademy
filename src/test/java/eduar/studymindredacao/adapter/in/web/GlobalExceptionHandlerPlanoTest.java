package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerPlanoTest {
    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-26T18:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(relogio);

    @ParameterizedTest
    @EnumSource(RecursoPro.class)
    void recursoExclusivoProResponde403ComCodigoERecurso(RecursoPro recurso) {
        var problema = handler.recursoExclusivoPro(new RecursoExclusivoProException(recurso));

        assertThat(problema.getStatus()).isEqualTo(403);
        assertThat(problema.getDetail()).isEqualTo("Este recurso está disponível apenas no plano Pro.");
        assertThat(problema.getProperties())
                .containsEntry("codigo", "PLANO_PRO_NECESSARIO")
                .containsEntry("recurso", recurso.name());
    }
}
