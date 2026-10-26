package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarPlanoServiceTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 26, 15, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID ALUNO = UUID.randomUUID();

    @Mock
    private AssinaturaRepositoryPort assinaturas;

    private ConsultarPlanoService service;

    @BeforeEach
    void criar() {
        Clock clock = Clock.fixed(AGORA.toInstant(), ZoneId.of("America/Sao_Paulo"));
        service = new ConsultarPlanoService(assinaturas, clock);
    }

    private static Assinatura assinatura(String subId, StatusAssinatura status, OffsetDateTime fim,
                                         boolean cancelarNoFim) {
        return Assinatura.nova(ALUNO, subId, CicloAssinatura.MENSAL, status, fim, cancelarNoFim,
                AGORA.minusMonths(1));
    }

    private static Assinatura vigente(StatusAssinatura status) {
        return assinatura("sub_1", status, AGORA.plusDays(10), false);
    }

    @Test
    void semAssinaturaEGratis() {
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of());

        assertThat(service.doUsuario(ALUNO)).isEqualTo(Plano.GRATIS);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAssinatura.class, names = {"ACTIVE", "TRIALING"})
    void assinaturaQueConcedeAcessoDentroDoPeriodoEPro(StatusAssinatura status) {
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(vigente(status)));

        assertThat(service.doUsuario(ALUNO)).isEqualTo(Plano.PRO);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAssinatura.class, names = {"ACTIVE", "TRIALING"}, mode = EnumSource.Mode.EXCLUDE)
    void statusQueNaoConcedeAcessoEGratisMesmoDentroDoPeriodo(StatusAssinatura status) {
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(vigente(status)));

        assertThat(service.doUsuario(ALUNO)).isEqualTo(Plano.GRATIS);
    }

    @Test
    void assinaturaAtivaComPeriodoVencidoEGratis() {
        when(assinaturas.listarPorUsuario(ALUNO))
                .thenReturn(List.of(assinatura("sub_1", StatusAssinatura.ACTIVE, AGORA.minusSeconds(1), false)));

        assertThat(service.doUsuario(ALUNO)).isEqualTo(Plano.GRATIS);
    }

    @Test
    void periodoTerminandoExatamenteAgoraJaEGratis() {
        when(assinaturas.listarPorUsuario(ALUNO))
                .thenReturn(List.of(assinatura("sub_1", StatusAssinatura.ACTIVE, AGORA, false)));

        assertThat(service.doUsuario(ALUNO)).isEqualTo(Plano.GRATIS);
    }

    @Test
    void canceladaNoFimDoPeriodoContinuaProAteOPeriodoAcabar() {
        when(assinaturas.listarPorUsuario(ALUNO))
                .thenReturn(List.of(assinatura("sub_1", StatusAssinatura.ACTIVE, AGORA.plusDays(3), true)));

        assertThat(service.doUsuario(ALUNO)).isEqualTo(Plano.PRO);
    }

    @Test
    void bastaUmaAssinaturaValidaEntreVariasParaSerPro() {
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(
                assinatura("sub_antiga", StatusAssinatura.CANCELED, AGORA.minusMonths(2), false),
                assinatura("sub_nova", StatusAssinatura.ACTIVE, AGORA.plusDays(20), false)
        ));

        assertThat(service.doUsuario(ALUNO)).isEqualTo(Plano.PRO);
    }

    @Test
    void doUsuarioExigeId() {
        assertThatThrownBy(() -> service.doUsuario(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(RecursoPro.class)
    void exigirNoGratisLancaComORecursoPedido(RecursoPro recurso) {
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of());

        assertThatThrownBy(() -> service.exigir(ALUNO, recurso))
                .isInstanceOf(RecursoExclusivoProException.class)
                .satisfies(e -> assertThat(((RecursoExclusivoProException) e).getRecurso()).isEqualTo(recurso));
    }

    @ParameterizedTest
    @EnumSource(RecursoPro.class)
    void exigirNoProPassa(RecursoPro recurso) {
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(vigente(StatusAssinatura.ACTIVE)));

        assertThatCode(() -> service.exigir(ALUNO, recurso)).doesNotThrowAnyException();
    }

    @Test
    void exigirSemRecursoLancaAntesDeConsultarAssinaturas() {
        assertThatThrownBy(() -> service.exigir(ALUNO, null)).isInstanceOf(NullPointerException.class);
    }

    // ---------- detalhar ----------

    @Test
    void detalharSemAssinaturaEGratisSemAssinaturaVigente() {
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of());

        var detalhe = service.detalhar(ALUNO);

        assertThat(detalhe.plano()).isEqualTo(Plano.GRATIS);
        assertThat(detalhe.assinaturaVigente()).isNull();
    }

    @Test
    void detalharComAssinaturaVigenteDevolveProComEla() {
        var vigente = vigente(StatusAssinatura.ACTIVE);
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(vigente));

        var detalhe = service.detalhar(ALUNO);

        assertThat(detalhe.plano()).isEqualTo(Plano.PRO);
        assertThat(detalhe.assinaturaVigente()).isSameAs(vigente);
    }

    @Test
    void detalharComDuasValidasDevolveADeMaiorPeriodo() {
        var curta = assinatura("sub_curta", StatusAssinatura.ACTIVE, AGORA.plusDays(5), true);
        var longa = assinatura("sub_longa", StatusAssinatura.ACTIVE, AGORA.plusDays(300), false);
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(curta, longa));

        assertThat(service.detalhar(ALUNO).assinaturaVigente()).isSameAs(longa);
    }

    @Test
    void detalharIgnoraAssinaturaQueNaoDaAcessoMesmoComPeriodoMaior() {
        var atrasada = assinatura("sub_atrasada", StatusAssinatura.PAST_DUE, AGORA.plusDays(300), false);
        var ativa = assinatura("sub_ativa", StatusAssinatura.ACTIVE, AGORA.plusDays(5), false);
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(atrasada, ativa));

        assertThat(service.detalhar(ALUNO).assinaturaVigente()).isSameAs(ativa);
    }

    @Test
    void detalharComPagamentoAtrasadoEGratisSemAssinatura() {
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(vigente(StatusAssinatura.PAST_DUE)));

        var detalhe = service.detalhar(ALUNO);

        assertThat(detalhe.plano()).isEqualTo(Plano.GRATIS);
        assertThat(detalhe.assinaturaVigente()).isNull();
    }

    @Test
    void detalharComAssinaturaVencidaEGratisSemAssinatura() {
        when(assinaturas.listarPorUsuario(ALUNO))
                .thenReturn(List.of(assinatura("sub_1", StatusAssinatura.ACTIVE, AGORA.minusDays(1), false)));

        assertThat(service.detalhar(ALUNO)).isEqualTo(PlanoDoUsuario.gratis());
    }

    @Test
    void detalharExigeId() {
        assertThatThrownBy(() -> service.detalhar(null)).isInstanceOf(NullPointerException.class);
    }
}
