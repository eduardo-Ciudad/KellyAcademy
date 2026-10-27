package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.admin.UsuarioResumoAdmin;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.FaixaNota;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.MediaCompetencia;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.PontoDiario;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.UsoIA;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.PainelAdminQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsultarVisaoGeralAdminServiceTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    /** 27/10/2026 15h em São Paulo. */
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 27, 15, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 27);
    private static final PoliticaJulia JULIA = PoliticaJulia.de(10, 500, 6, 24, 20, 100);

    @Mock
    private PainelAdminQueryPort painel;

    private ConsultarVisaoGeralAdminService service;

    @BeforeEach
    void criar() {
        service = criarCom(AGORA);
        when(painel.mediaNotas(any(), any())).thenReturn(Optional.empty());
        when(painel.ultimoEventoStripe()).thenReturn(Optional.empty());
        when(painel.cadastrosPorDia(any(), any(), any())).thenReturn(Map.of());
        when(painel.correcoesPorDia(any(), any(), any())).thenReturn(Map.of());
        when(painel.usoGlobalDoDia(any())).thenReturn(Map.of());
        when(painel.contagemPorNota(any(), any())).thenReturn(Map.of());
        when(painel.mediasPorCompetencia(any(), any())).thenReturn(Map.of());
        when(painel.ultimosCadastros(anyInt(), any(), any())).thenReturn(List.of());
    }

    private ConsultarVisaoGeralAdminService criarCom(OffsetDateTime agora) {
        Clock clock = Clock.fixed(agora.toInstant(), SAO_PAULO);
        return new ConsultarVisaoGeralAdminService(painel, JULIA, 30, 40, clock);
    }

    // ---------- período ----------

    @ParameterizedTest
    @ValueSource(ints = {-7, 0, 1, 8, 31, 365})
    void recusaPeriodoForaDe7_30_90SemConsultarNada(int periodo) {
        assertThatThrownBy(() -> service.consultar(periodo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodo");
        verifyNoInteractions(painel);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 30, 90})
    void serieTemUmPontoPorDiaDoPeriodoTerminandoHoje(int periodo) {
        var visao = service.consultar(periodo);

        assertThat(visao.periodo()).isEqualTo(periodo);
        assertThat(visao.serieDiaria()).hasSize(periodo);
        assertThat(visao.serieDiaria().get(0).data()).isEqualTo(HOJE.minusDays(periodo - 1L));
        assertThat(visao.serieDiaria().get(periodo - 1).data()).isEqualTo(HOJE);
    }

    @Test
    void intervaloComecaNaMeiaNoiteDoPrimeiroDiaETerminaAgora() {
        service.consultar(7);

        var esperadoDe = OffsetDateTime.of(2026, 10, 21, 0, 0, 0, 0, ZoneOffset.ofHours(-3));
        verify(painel).contarCadastros(esperadoDe, AGORA);
        verify(painel).contarCorrecoes(esperadoDe, AGORA);
        verify(painel).cadastrosPorDia(esperadoDe, AGORA, SAO_PAULO);
    }

    @Test
    void hojeEhODiaDeSaoPauloMesmoPertoDaMeiaNoiteUtc() {
        // 01h30 UTC de 28/10 = 22h30 de 27/10 em São Paulo: "hoje" ainda é 27/10
        var quaseMeiaNoite = OffsetDateTime.of(2026, 10, 28, 1, 30, 0, 0, ZoneOffset.UTC);

        var visao = criarCom(quaseMeiaNoite).consultar(7);

        assertThat(visao.serieDiaria().get(6).data()).isEqualTo(HOJE);
        verify(painel).usoGlobalDoDia(HOJE);
        verify(painel).tokensDoDia(HOJE,
                OffsetDateTime.of(2026, 10, 27, 0, 0, 0, 0, ZoneOffset.ofHours(-3)),
                OffsetDateTime.of(2026, 10, 28, 0, 0, 0, 0, ZoneOffset.ofHours(-3)));
    }

    // ---------- números diretos ----------

    @Test
    void repassaAsContagensDoPort() {
        when(painel.contarUsuarios()).thenReturn(12L);
        when(painel.contarCadastros(any(), any())).thenReturn(3L);
        when(painel.contarAlunosAtivos(any(), any())).thenReturn(5L);
        when(painel.contarCorrecoes(any(), any())).thenReturn(40L);
        when(painel.mediaNotas(any(), any())).thenReturn(Optional.of(612.5));
        when(painel.contarAssinantesPro(any(), any())).thenReturn(2L);
        when(painel.tokensDoDia(any(), any(), any())).thenReturn(98_000L);
        when(painel.contarNoLimiteDeCorrecoes(any(), anyInt(), anyInt(), any(), any())).thenReturn(1L);

        var visao = service.consultar(30);

        assertThat(visao.geradoEm()).isEqualTo(AGORA);
        assertThat(visao.totalUsuarios()).isEqualTo(12);
        assertThat(visao.novosNoPeriodo()).isEqualTo(3);
        assertThat(visao.ativosNoPeriodo()).isEqualTo(5);
        assertThat(visao.correcoesNoPeriodo()).isEqualTo(40);
        assertThat(visao.notaMediaNoPeriodo()).isEqualTo(612.5);
        assertThat(visao.assinantesPro()).isEqualTo(2);
        assertThat(visao.tokensHoje()).isEqualTo(98_000);
        assertThat(visao.usuariosNoLimiteHoje()).isEqualTo(1);
    }

    @Test
    void semCorrecoesANotaMediaENula() {
        assertThat(service.consultar(30).notaMediaNoPeriodo()).isNull();
    }

    @Test
    void limiteDeCorrecoesUsaOsLimitesDosPlanos() {
        service.consultar(7);

        verify(painel).contarNoLimiteDeCorrecoes(eq(HOJE), eq(Plano.GRATIS.limiteDiario(RecursoIA.CORRECAO)),
                eq(Plano.PRO.limiteDiario(RecursoIA.CORRECAO)), eq(AGORA), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusComAcessoVemDoEnum() {
        service.consultar(7);

        ArgumentCaptor<Set<StatusAssinatura>> status = ArgumentCaptor.forClass(Set.class);
        verify(painel).contarAssinantesPro(eq(AGORA), status.capture());
        assertThat(status.getValue())
                .isNotEmpty()
                .allMatch(StatusAssinatura::concedeAcesso)
                .containsExactlyInAnyOrder(java.util.Arrays.stream(StatusAssinatura.values())
                        .filter(StatusAssinatura::concedeAcesso).toArray(StatusAssinatura[]::new));
    }

    // ---------- série ----------

    @Test
    void serieJuntaCadastrosECorrecoesEPreencheDiasVaziosComZero() {
        when(painel.cadastrosPorDia(any(), any(), any())).thenReturn(Map.of(HOJE.minusDays(6), 2L, HOJE, 1L));
        when(painel.correcoesPorDia(any(), any(), any())).thenReturn(Map.of(HOJE.minusDays(3), 4L));

        var serie = service.consultar(7).serieDiaria();

        assertThat(serie).containsExactly(
                new PontoDiario(HOJE.minusDays(6), 2, 0),
                new PontoDiario(HOJE.minusDays(5), 0, 0),
                new PontoDiario(HOJE.minusDays(4), 0, 0),
                new PontoDiario(HOJE.minusDays(3), 0, 4),
                new PontoDiario(HOJE.minusDays(2), 0, 0),
                new PontoDiario(HOJE.minusDays(1), 0, 0),
                new PontoDiario(HOJE, 1, 0));
    }

    // ---------- uso de IA ----------

    @Test
    void usoIaTrazOs3RecursosComOsTetosDaConfiguracao() {
        when(painel.usoGlobalDoDia(HOJE)).thenReturn(Map.of(RecursoIA.CORRECAO, 12));

        assertThat(service.consultar(7).usoIaHoje()).containsExactly(
                new UsoIA(RecursoIA.CORRECAO, 12, 30),
                new UsoIA(RecursoIA.TRANSCRICAO, 0, 40),
                new UsoIA(RecursoIA.JULIA, 0, 100));
    }

    // ---------- notas ----------

    @Test
    void distribuicaoTemAs5FaixasSempreEColocaCadaNotaNaFaixaCerta() {
        when(painel.contagemPorNota(any(), any())).thenReturn(Map.of(
                0, 1L, 160, 2L, 200, 3L, 399, 1L, 560, 4L, 600, 1L, 800, 2L, 1000, 1L));

        assertThat(service.consultar(30).distribuicaoNotas()).containsExactly(
                new FaixaNota(0, 199, 3),
                new FaixaNota(200, 399, 4),
                new FaixaNota(400, 599, 4),
                new FaixaNota(600, 799, 1),
                new FaixaNota(800, 1000, 3));
    }

    @Test
    void distribuicaoSemAvaliacoesTemFaixasZeradas() {
        assertThat(service.consultar(30).distribuicaoNotas())
                .hasSize(5)
                .allMatch(faixa -> faixa.quantidade() == 0);
    }

    @Test
    void notaForaDe0A1000NaoEntraEmNenhumaFaixa() {
        assertThat(ConsultarVisaoGeralAdminService.distribuicao(Map.of(-40, 1L, 1040, 1L)))
                .allMatch(faixa -> faixa.quantidade() == 0);
    }

    @Test
    void mediasTemAs5CompetenciasEFicamNulasSemDado() {
        when(painel.mediasPorCompetencia(any(), any())).thenReturn(Map.of(1, 140.0, 5, 96.0));

        assertThat(service.consultar(30).mediaPorCompetencia()).containsExactly(
                new MediaCompetencia(1, 140.0),
                new MediaCompetencia(2, null),
                new MediaCompetencia(3, null),
                new MediaCompetencia(4, null),
                new MediaCompetencia(5, 96.0));
    }

    // ---------- últimos cadastros e alertas ----------

    @Test
    void pedeOs5UltimosCadastros() {
        var resumo = new UsuarioResumoAdmin(UUID.randomUUID(), "Ana", "ana@exemplo.com", Role.ALUNO, Plano.GRATIS,
                true, 0, null, null, AGORA.minusDays(1));
        when(painel.ultimosCadastros(eq(5), eq(AGORA), any())).thenReturn(List.of(resumo));

        assertThat(service.consultar(7).ultimosCadastros()).containsExactly(resumo);
    }

    @Test
    void alertasUsamAsJanelasDe24hE48h() {
        var ultimoEvento = AGORA.minusHours(3);
        when(painel.contarCorrecoesComErroDesde(AGORA.minusHours(24))).thenReturn(2L);
        when(painel.contarNaoVerificadasCriadasAntesDe(AGORA.minusHours(48))).thenReturn(4L);
        when(painel.ultimoEventoStripe()).thenReturn(Optional.of(ultimoEvento));

        var alertas = service.consultar(7).alertas();

        assertThat(alertas.correcoesComErro24h()).isEqualTo(2);
        assertThat(alertas.contasSemVerificacao48h()).isEqualTo(4);
        assertThat(alertas.ultimoEventoStripeEm()).isEqualTo(ultimoEvento);
    }

    @Test
    void semEventoDoStripeOAlertaVemNulo() {
        assertThat(service.consultar(7).alertas().ultimoEventoStripeEm()).isNull();
    }
}
