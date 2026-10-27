package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.AcaoAdminConflitoException;
import eduar.studymindredacao.domain.exception.AcessoAdminNegadoException;
import eduar.studymindredacao.domain.exception.ConcessaoProConflitoException;
import eduar.studymindredacao.domain.exception.ReenvioMuitoRecenteException;
import eduar.studymindredacao.domain.exception.UsuarioNaoEncontradoException;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcoesUsuarioAdminServiceTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 27, 15, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID ALUNO = UUID.randomUUID();
    private static final String MOTIVO = "Testador do piloto";

    @Mock
    private UsuarioRepositoryPort usuarios;
    @Mock
    private ConcessaoProRepositoryPort concessoes;
    @Mock
    private ConsultarPlanoService consultarPlano;
    @Mock
    private UsoIADiarioRepositoryPort usoDiario;
    @Mock
    private VerificacaoEmailService verificacaoEmail;
    @Mock
    private TokenUsuarioRepositoryPort tokens;

    private AcoesUsuarioAdminService service;

    private static Usuario conta(UUID id, Role role, boolean verificado) {
        return new Usuario(id, "Nome", id + "@exemplo.com", "hash", role, AGORA.minusDays(30),
                verificado ? AGORA.minusDays(30) : null);
    }

    @BeforeEach
    void criar() {
        service = new AcoesUsuarioAdminService(usuarios, concessoes, consultarPlano, usoDiario, verificacaoEmail,
                tokens, Clock.fixed(AGORA.toInstant(), SAO_PAULO));
        when(usuarios.buscarPorId(ADMIN)).thenReturn(Optional.of(conta(ADMIN, Role.ADMIN, true)));
        when(usuarios.buscarPorId(ALUNO)).thenReturn(Optional.of(conta(ALUNO, Role.ALUNO, false)));
        when(consultarPlano.detalhar(any())).thenReturn(PlanoDoUsuario.gratis());
        when(concessoes.buscarAberta(any())).thenReturn(Optional.empty());
        when(concessoes.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));
        when(verificacaoEmail.verificacaoObrigatoria()).thenReturn(true);
        when(tokens.ultimaEmissao(any(), any())).thenReturn(Optional.empty());
    }

    // ---------- quem pode agir ----------

    @Test
    void adminRebaixadoNoBancoEhBarradoEmTodasAsAcoes() {
        UUID exAdmin = UUID.randomUUID();
        when(usuarios.buscarPorId(exAdmin)).thenReturn(Optional.of(conta(exAdmin, Role.ALUNO, true)));

        assertThatThrownBy(() -> service.concederPro(exAdmin, ALUNO, 30, MOTIVO))
                .isInstanceOf(AcessoAdminNegadoException.class);
        assertThatThrownBy(() -> service.revogarPro(exAdmin, ALUNO, MOTIVO))
                .isInstanceOf(AcessoAdminNegadoException.class);
        assertThatThrownBy(() -> service.zerarCotaHoje(exAdmin, ALUNO))
                .isInstanceOf(AcessoAdminNegadoException.class);
        assertThatThrownBy(() -> service.reenviarVerificacao(exAdmin, ALUNO))
                .isInstanceOf(AcessoAdminNegadoException.class);
        verifyNoInteractions(concessoes, usoDiario, verificacaoEmail);
    }

    @Test
    void contaApagadaOuIdNuloNaoAge() {
        UUID apagada = UUID.randomUUID();
        when(usuarios.buscarPorId(apagada)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.zerarCotaHoje(apagada, ALUNO)).isInstanceOf(AcessoAdminNegadoException.class);
        assertThatThrownBy(() -> service.zerarCotaHoje(null, ALUNO)).isInstanceOf(AcessoAdminNegadoException.class);
        verifyNoInteractions(usoDiario);
    }

    @Test
    void alunoInexistenteEhNaoEncontrado() {
        UUID inexistente = UUID.randomUUID();
        when(usuarios.buscarPorId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.concederPro(ADMIN, inexistente, 30, MOTIVO))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
        assertThatThrownBy(() -> service.zerarCotaHoje(ADMIN, null))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
        verifyNoInteractions(concessoes, usoDiario);
    }

    // ---------- conceder Pro ----------

    @Test
    void concederSalvaAConcessaoComOAdminOPrazoEOMotivo() {
        var salva = service.concederPro(ADMIN, ALUNO, 30, "  " + MOTIVO + " ");

        assertThat(salva.usuarioId()).isEqualTo(ALUNO);
        assertThat(salva.concedidaPor()).isEqualTo(ADMIN);
        assertThat(salva.motivo()).isEqualTo(MOTIVO);
        assertThat(salva.inicio()).isEqualTo(AGORA);
        assertThat(salva.fim()).isEqualTo(OffsetDateTime.of(2026, 11, 27, 0, 0, 0, 0, ZoneOffset.ofHours(-3)));
        verify(concessoes).salvar(salva);
    }

    @Test
    void concederParaAPropriaContaEhPermitido() {
        var salva = service.concederPro(ADMIN, ADMIN, 7, MOTIVO);

        assertThat(salva.usuarioId()).isEqualTo(ADMIN);
        assertThat(salva.concedidaPor()).isEqualTo(ADMIN);
    }

    @Test
    void prazoOuMotivoInvalidoEh400AntesDeOlharOEstado() {
        assertThatThrownBy(() -> service.concederPro(ADMIN, ALUNO, 0, MOTIVO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.concederPro(ADMIN, ALUNO, 30, "ab"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(concessoes);
    }

    @Test
    void quemJaEhProPelaAssinaturaNaoRecebeConcessao() {
        var assinatura = Assinatura.nova(ALUNO, "sub_1", CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE,
                AGORA.plusDays(10), false, AGORA.minusDays(20));
        when(consultarPlano.detalhar(ALUNO)).thenReturn(PlanoDoUsuario.pro(assinatura));

        assertThatThrownBy(() -> service.concederPro(ADMIN, ALUNO, 30, MOTIVO))
                .isInstanceOf(ConcessaoProConflitoException.class)
                .hasMessageContaining("assinatura");
        verify(concessoes, never()).salvar(any());
    }

    @Test
    void comConcessaoVigenteEhConflito() {
        var vigente = ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 30, AGORA.minusDays(2), SAO_PAULO);
        when(concessoes.buscarAberta(ALUNO)).thenReturn(Optional.of(vigente));

        assertThatThrownBy(() -> service.concederPro(ADMIN, ALUNO, 30, MOTIVO))
                .isInstanceOf(ConcessaoProConflitoException.class)
                .hasMessageContaining("ativa");
        verify(concessoes, never()).salvar(any());
    }

    @Test
    void concessaoVencidaEhFechadaAntesDaNova() {
        var vencida = ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 7, AGORA.minusDays(30), SAO_PAULO);
        when(concessoes.buscarAberta(ALUNO)).thenReturn(Optional.of(vencida));

        service.concederPro(ADMIN, ALUNO, 30, MOTIVO);

        ArgumentCaptor<ConcessaoPro> salvas = ArgumentCaptor.forClass(ConcessaoPro.class);
        InOrder ordem = inOrder(concessoes);
        ordem.verify(concessoes, org.mockito.Mockito.times(2)).salvar(salvas.capture());
        var fechada = salvas.getAllValues().get(0);
        var nova = salvas.getAllValues().get(1);
        assertThat(fechada.revogadaEm()).isEqualTo(AGORA);
        assertThat(fechada.revogadaPor()).isEqualTo(ADMIN);
        assertThat(fechada.motivoRevogacao()).isEqualTo("Expirada");
        assertThat(nova.revogada()).isFalse();
        assertThat(nova.inicio()).isEqualTo(AGORA);
    }

    // ---------- revogar Pro ----------

    @Test
    void revogarMarcaAConcessaoAberta() {
        var aberta = ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 30, AGORA.minusDays(2), SAO_PAULO);
        when(concessoes.buscarAberta(ALUNO)).thenReturn(Optional.of(aberta));

        var revogada = service.revogarPro(ADMIN, ALUNO, "Fim do piloto");

        assertThat(revogada.revogadaEm()).isEqualTo(AGORA);
        assertThat(revogada.revogadaPor()).isEqualTo(ADMIN);
        assertThat(revogada.motivoRevogacao()).isEqualTo("Fim do piloto");
        verify(concessoes).salvar(revogada);
    }

    @Test
    void revogarSemConcessaoAbertaEhConflito() {
        assertThatThrownBy(() -> service.revogarPro(ADMIN, ALUNO, "Fim do piloto"))
                .isInstanceOf(ConcessaoProConflitoException.class);
        verify(concessoes, never()).salvar(any());
    }

    @Test
    void revogarExigeMotivoValido() {
        var aberta = ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 30, AGORA.minusDays(2), SAO_PAULO);
        when(concessoes.buscarAberta(ALUNO)).thenReturn(Optional.of(aberta));

        assertThatThrownBy(() -> service.revogarPro(ADMIN, ALUNO, " "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(concessoes, never()).salvar(any());
    }

    // ---------- zerar cota ----------

    @Test
    void zerarCotaUsaODiaDeSaoPaulo() {
        when(usoDiario.zerarContadores(ALUNO, LocalDate.of(2026, 10, 27))).thenReturn(true);

        assertThat(service.zerarCotaHoje(ADMIN, ALUNO)).isTrue();
    }

    @Test
    void zerarCotaPertoDaMeiaNoiteUtcAindaEhODiaDeSaoPaulo() {
        // 01h30 UTC de 28/10 = 22h30 de 27/10 em São Paulo
        var quaseMeiaNoite = OffsetDateTime.of(2026, 10, 28, 1, 30, 0, 0, ZoneOffset.UTC);
        var noite = new AcoesUsuarioAdminService(usuarios, concessoes, consultarPlano, usoDiario, verificacaoEmail,
                tokens, Clock.fixed(quaseMeiaNoite.toInstant(), SAO_PAULO));

        noite.zerarCotaHoje(ADMIN, ALUNO);

        verify(usoDiario).zerarContadores(ALUNO, LocalDate.of(2026, 10, 27));
    }

    @Test
    void zerarSemUsoNoDiaDevolveFalso() {
        when(usoDiario.zerarContadores(any(), any())).thenReturn(false);

        assertThat(service.zerarCotaHoje(ADMIN, ALUNO)).isFalse();
    }

    // ---------- reenviar verificação ----------

    @Test
    void reenviaParaAlunoPendente() {
        service.reenviarVerificacao(ADMIN, ALUNO);

        verify(verificacaoEmail).emitirPara(conta(ALUNO, Role.ALUNO, false));
    }

    @Test
    void emailJaVerificadoEhConflito() {
        when(usuarios.buscarPorId(ALUNO)).thenReturn(Optional.of(conta(ALUNO, Role.ALUNO, true)));

        assertThatThrownBy(() -> service.reenviarVerificacao(ADMIN, ALUNO))
                .isInstanceOf(AcaoAdminConflitoException.class)
                .hasMessageContaining("verificado");
        verify(verificacaoEmail, never()).emitirPara(any());
    }

    @Test
    void verificacaoDesligadaEhConflito() {
        when(verificacaoEmail.verificacaoObrigatoria()).thenReturn(false);

        assertThatThrownBy(() -> service.reenviarVerificacao(ADMIN, ALUNO))
                .isInstanceOf(AcaoAdminConflitoException.class)
                .hasMessageContaining("desligada");
        verify(verificacaoEmail, never()).emitirPara(any());
    }

    @Test
    void linkEnviadoHaMenosDe5MinutosEh429ComOTempoQueFalta() {
        when(tokens.ultimaEmissao(ALUNO, TipoTokenUsuario.VERIFICACAO_EMAIL))
                .thenReturn(Optional.of(AGORA.minusMinutes(2)));

        assertThatThrownBy(() -> service.reenviarVerificacao(ADMIN, ALUNO))
                .isInstanceOf(ReenvioMuitoRecenteException.class)
                .satisfies(e -> assertThat(((ReenvioMuitoRecenteException) e).getSegundosParaTentar())
                        .isEqualTo(180));
        verify(verificacaoEmail, never()).emitirPara(any());
    }

    @Test
    void linkEnviadoHaExatamente5MinutosJaPodeReenviar() {
        when(tokens.ultimaEmissao(ALUNO, TipoTokenUsuario.VERIFICACAO_EMAIL))
                .thenReturn(Optional.of(AGORA.minusMinutes(5)));

        service.reenviarVerificacao(ADMIN, ALUNO);

        verify(verificacaoEmail).emitirPara(any());
    }
}
