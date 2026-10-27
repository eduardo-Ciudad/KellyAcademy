package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.UsuarioNaoEncontradoException;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.admin.FiltroUsuariosAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.AtividadeJulia;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.CompetenciaMaisFraca;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.RedacaoAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.UsoHoje;
import eduar.studymindredacao.domain.model.admin.UsuarioResumoAdmin;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;
import eduar.studymindredacao.domain.port.PainelAdminQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsultarUsuariosAdminServiceTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 27, 15, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 27);
    private static final UUID ALUNO = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final PoliticaJulia JULIA = PoliticaJulia.de(10, 500, 6, 24, 20, 100);

    @Mock
    private PainelAdminQueryPort painel;
    @Mock
    private AssinaturaRepositoryPort assinaturas;
    @Mock
    private ConcessaoProRepositoryPort concessoes;

    private final Clock clock = Clock.fixed(AGORA.toInstant(), SAO_PAULO);

    private ConsultarUsuariosAdminService comPlano(ConsultarPlanoService planos) {
        return new ConsultarUsuariosAdminService(painel, assinaturas, concessoes, planos, JULIA, clock);
    }

    private static UsuarioResumoAdmin resumo() {
        return new UsuarioResumoAdmin(ALUNO, "Ana", "ana@exemplo.com", Role.ALUNO, Plano.GRATIS, true, 2,
                600.0, AGORA.minusDays(1), AGORA.minusDays(10));
    }

    @BeforeEach
    void padroes() {
        when(painel.buscarResumo(eq(ALUNO), any(), any())).thenReturn(Optional.of(resumo()));
        when(painel.melhorNota(ALUNO)).thenReturn(Optional.empty());
        when(painel.mediasPorCompetenciaDoUsuario(ALUNO)).thenReturn(Map.of());
        when(painel.usoDoUsuarioNoDia(eq(ALUNO), any())).thenReturn(Map.of());
        when(painel.atividadeJulia(ALUNO)).thenReturn(new AtividadeJulia(0, 0));
        when(painel.ultimasRedacoes(eq(ALUNO), anyInt())).thenReturn(List.of());
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of());
        when(assinaturas.buscarStripeCustomerId(ALUNO)).thenReturn(Optional.empty());
        when(concessoes.listarPorUsuario(ALUNO)).thenReturn(List.of());
    }

    // ---------- listar ----------

    @Test
    void listarRepassaOFiltroComAgoraEOsStatusQueDaoAcesso() {
        var filtro = new FiltroUsuariosAdmin("ana", Plano.PRO, true, 1, 10);
        var pagina = new Pagina<>(List.of(resumo()), 1, 10, 11);
        when(painel.listarUsuarios(eq(filtro), eq(AGORA), any())).thenReturn(pagina);

        assertThat(comPlano(PlanosDeTeste.gratis()).listar(filtro)).isSameAs(pagina);
        verify(painel).listarUsuarios(eq(filtro), eq(AGORA),
                org.mockito.ArgumentMatchers.argThat(status -> !status.isEmpty()
                        && status.stream().allMatch(StatusAssinatura::concedeAcesso)));
    }

    @Test
    void listarExigeFiltro() {
        assertThatThrownBy(() -> comPlano(PlanosDeTeste.gratis()).listar(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- detalhar ----------

    @Test
    void idInexistenteEhNaoEncontrado() {
        UUID outro = UUID.randomUUID();
        when(painel.buscarResumo(eq(outro), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comPlano(PlanosDeTeste.gratis()).detalhar(outro))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    @Test
    void alunoSemNadaTemCamposVaziosEUsoZerado() {
        var detalhe = comPlano(PlanosDeTeste.gratis()).detalhar(ALUNO);

        assertThat(detalhe.usuario()).isEqualTo(resumo());
        assertThat(detalhe.melhorNota()).isNull();
        assertThat(detalhe.competenciaMaisFraca()).isNull();
        assertThat(detalhe.assinatura()).isNull();
        assertThat(detalhe.stripeCustomerId()).isNull();
        assertThat(detalhe.concessaoAberta()).isNull();
        assertThat(detalhe.concessoes()).isEmpty();
        assertThat(detalhe.redacoes()).isEmpty();
        assertThat(detalhe.usoHoje()).containsExactly(
                new UsoHoje(RecursoIA.CORRECAO, 0, 3),
                new UsoHoje(RecursoIA.TRANSCRICAO, 0, 2),
                new UsoHoje(RecursoIA.JULIA, 0, 20));
    }

    @Test
    void usoDeHojeConsultaODiaDeSaoPauloEUsaOsLimitesDoPro() {
        when(painel.usoDoUsuarioNoDia(ALUNO, HOJE)).thenReturn(Map.of(
                RecursoIA.CORRECAO, 4, RecursoIA.TRANSCRICAO, 1, RecursoIA.JULIA, 7));

        var detalhe = comPlano(PlanosDeTeste.pro()).detalhar(ALUNO);

        assertThat(detalhe.usoHoje()).containsExactly(
                new UsoHoje(RecursoIA.CORRECAO, 4, 10),
                new UsoHoje(RecursoIA.TRANSCRICAO, 1, 10),
                new UsoHoje(RecursoIA.JULIA, 7, 20));
    }

    @Test
    void repassaNotasJuliaRedacoesEStripe() {
        var redacao = new RedacaoAdmin(UUID.randomUUID(), AGORA.minusDays(1), "Tema X", OrigemRedacao.DIGITADO,
                StatusRedacao.AVALIADA, 720);
        when(painel.melhorNota(ALUNO)).thenReturn(Optional.of(880));
        when(painel.atividadeJulia(ALUNO)).thenReturn(new AtividadeJulia(2, 9));
        when(painel.ultimasRedacoes(ALUNO, 20)).thenReturn(List.of(redacao));
        when(assinaturas.buscarStripeCustomerId(ALUNO)).thenReturn(Optional.of("cus_123"));

        var detalhe = comPlano(PlanosDeTeste.gratis()).detalhar(ALUNO);

        assertThat(detalhe.melhorNota()).isEqualTo(880);
        assertThat(detalhe.julia()).isEqualTo(new AtividadeJulia(2, 9));
        assertThat(detalhe.redacoes()).containsExactly(redacao);
        assertThat(detalhe.stripeCustomerId()).isEqualTo("cus_123");
    }

    @Test
    void assinaturaEhAMaisRecenteMesmoForaDeOrdem() {
        var antiga = Assinatura.nova(ALUNO, "sub_antiga", CicloAssinatura.MENSAL, StatusAssinatura.CANCELED,
                AGORA.minusMonths(2), false, AGORA.minusMonths(3));
        var nova = Assinatura.nova(ALUNO, "sub_nova", CicloAssinatura.ANUAL, StatusAssinatura.PAST_DUE,
                AGORA.plusMonths(11), false, AGORA.minusMonths(1));
        when(assinaturas.listarPorUsuario(ALUNO)).thenReturn(List.of(antiga, nova));

        assertThat(comPlano(PlanosDeTeste.gratis()).detalhar(ALUNO).assinatura()).isSameAs(nova);
    }

    @Test
    void concessaoAbertaEhANaoRevogadaEOHistoricoVemInteiro() {
        var revogada = ConcessaoPro.conceder(ALUNO, ADMIN, "Primeira rodada", 7, AGORA.minusDays(20), SAO_PAULO)
                .revogar(ADMIN, "Fim da rodada", AGORA.minusDays(15));
        var aberta = ConcessaoPro.conceder(ALUNO, ADMIN, "Segunda rodada", 30, AGORA.minusDays(1), SAO_PAULO);
        when(concessoes.listarPorUsuario(ALUNO)).thenReturn(List.of(aberta, revogada));

        var detalhe = comPlano(PlanosDeTeste.gratis()).detalhar(ALUNO);

        assertThat(detalhe.concessaoAberta()).isSameAs(aberta);
        assertThat(detalhe.concessoes()).containsExactly(aberta, revogada);
    }

    @Test
    void soConcessoesRevogadasNaoTemAberta() {
        var revogada = ConcessaoPro.conceder(ALUNO, ADMIN, "Primeira rodada", 7, AGORA.minusDays(20), SAO_PAULO)
                .revogar(ADMIN, "Fim da rodada", AGORA.minusDays(15));
        when(concessoes.listarPorUsuario(ALUNO)).thenReturn(List.of(revogada));

        assertThat(comPlano(PlanosDeTeste.gratis()).detalhar(ALUNO).concessaoAberta()).isNull();
    }

    // ---------- competência mais fraca ----------

    @Test
    void competenciaMaisFracaEhAMenorMedia() {
        assertThat(ConsultarUsuariosAdminService.competenciaMaisFraca(
                Map.of(1, 160.0, 2, 120.0, 3, 80.0, 4, 140.0, 5, 100.0)))
                .isEqualTo(new CompetenciaMaisFraca(3, 80.0));
    }

    @Test
    void empateFicaComACompetenciaDeMenorNumero() {
        assertThat(ConsultarUsuariosAdminService.competenciaMaisFraca(Map.of(5, 80.0, 2, 80.0, 4, 120.0)))
                .isEqualTo(new CompetenciaMaisFraca(2, 80.0));
    }

    @Test
    void semMediasNaoHaCompetenciaMaisFraca() {
        assertThat(ConsultarUsuariosAdminService.competenciaMaisFraca(Map.of())).isNull();
        var comNulo = new HashMap<Integer, Double>();
        comNulo.put(1, null);
        assertThat(ConsultarUsuariosAdminService.competenciaMaisFraca(comNulo)).isNull();
    }

    @Test
    void detalharUsaACompetenciaMaisFracaDoAluno() {
        when(painel.mediasPorCompetenciaDoUsuario(ALUNO)).thenReturn(Map.of(1, 200.0, 2, 40.0));

        assertThat(comPlano(PlanosDeTeste.gratis()).detalhar(ALUNO).competenciaMaisFraca())
                .isEqualTo(new CompetenciaMaisFraca(2, 40.0));
    }
}
