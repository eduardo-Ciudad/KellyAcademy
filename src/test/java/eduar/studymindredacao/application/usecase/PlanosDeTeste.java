package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ConsultarPlanoService de verdade, com o repositório de assinaturas simulado:
 * o exigir(...) roda a regra real em vez de um mock que não lança nada.
 */
final class PlanosDeTeste {
    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FIM_DISTANTE = OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private PlanosDeTeste() {
    }

    /** Todo aluno sem assinatura: plano Grátis. */
    static ConsultarPlanoService gratis() {
        var assinaturas = mock(AssinaturaRepositoryPort.class);
        when(assinaturas.listarPorUsuario(any())).thenReturn(List.of());
        return new ConsultarPlanoService(assinaturas, semConcessoes(), Clock.systemUTC());
    }

    /** Todo aluno com uma assinatura ativa até 2099: plano Pro. */
    static ConsultarPlanoService pro() {
        var assinaturas = mock(AssinaturaRepositoryPort.class);
        when(assinaturas.listarPorUsuario(any())).thenAnswer(chamada -> List.of(
                Assinatura.nova(chamada.getArgument(0, UUID.class), "sub_teste", CicloAssinatura.MENSAL,
                        StatusAssinatura.ACTIVE, FIM_DISTANTE, false, INICIO)
        ));
        return new ConsultarPlanoService(assinaturas, semConcessoes(), Clock.systemUTC());
    }

    private static ConcessaoProRepositoryPort semConcessoes() {
        var concessoes = mock(ConcessaoProRepositoryPort.class);
        when(concessoes.buscarAberta(any())).thenReturn(Optional.empty());
        return concessoes;
    }
}
