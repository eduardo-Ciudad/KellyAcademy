package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.TokenInvalidoException;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificacaoEmailServiceTest {
    private UsuarioRepositoryEmMemoria usuarios;
    private CenarioVerificacao cenario;
    private VerificacaoEmailService service;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        usuarios = new UsuarioRepositoryEmMemoria();
        cenario = CenarioVerificacao.ligada(usuarios);
        service = cenario.service;
        ana = usuarios.salvar(new Usuario(null, "Ana", "ana@exemplo.com", "hash", Role.ALUNO, null, null));
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(cenario.relogio);
    }

    // ---------- emitirPara ----------

    @Test
    void emitirSalvaSoOHashEEnviaOLinkComOToken() {
        service.emitirPara(ana);

        assertThat(cenario.tokens.todos()).hasSize(1);
        var token = cenario.tokens.todos().get(0);
        assertThat(token.usuarioId()).isEqualTo(ana.id());
        assertThat(token.tipo()).isEqualTo(TipoTokenUsuario.VERIFICACAO_EMAIL);
        assertThat(token.tokenHash()).isEqualTo(cenario.gerador.hash("token-1")).isNotEqualTo("token-1");
        assertThat(token.criadoEm()).isEqualTo(agora());
        assertThat(token.expiraEm()).isEqualTo(agora().plus(CenarioVerificacao.VALIDADE));

        assertThat(cenario.emails.enviadas).hasSize(1);
        var email = cenario.emails.ultima();
        assertThat(email.destinatario()).isEqualTo("ana@exemplo.com");
        assertThat(email.nome()).isEqualTo("Ana");
        assertThat(email.link()).isEqualTo("https://kellyacademy.com.br/verificar-email#token=token-1");
    }

    @Test
    void emitirDeNovoInvalidaOLinkAnterior() {
        service.emitirPara(ana);
        String primeiro = cenario.emails.ultimoToken();
        service.emitirPara(ana);
        String segundo = cenario.emails.ultimoToken();

        assertThatThrownBy(() -> service.confirmar(primeiro)).isInstanceOf(TokenInvalidoException.class);
        assertThatCode(() -> service.confirmar(segundo)).doesNotThrowAnyException();
    }

    @Test
    void emitirNaoFazNadaComContaJaVerificada() {
        var verificada = usuarios.salvar(ana.comEmailVerificado(agora()));

        service.emitirPara(verificada);

        assertThat(cenario.tokens.todos()).isEmpty();
        assertThat(cenario.emails.enviadas).isEmpty();
    }

    @Test
    void emitirNaoFazNadaComVerificacaoDesligada() {
        var desligado = CenarioVerificacao.desligada(usuarios);

        desligado.service.emitirPara(ana);

        assertThat(desligado.tokens.todos()).isEmpty();
        assertThat(desligado.emails.enviadas).isEmpty();
    }

    @Test
    void emitirExigeUsuarioSalvo() {
        var semId = new Usuario(null, "Ana", "ana@exemplo.com", "hash", Role.ALUNO, null, null);

        assertThatThrownBy(() -> service.emitirPara(semId)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.emitirPara(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------- reenviar ----------

    @Test
    void reenviarMandaNovoLinkParaContaNaoVerificadaIgnorandoMaiusculas() {
        service.reenviar("  ANA@Exemplo.com ");

        assertThat(cenario.emails.enviadas).hasSize(1);
        assertThat(cenario.emails.ultima().destinatario()).isEqualTo("ana@exemplo.com");
    }

    @Test
    void reenviarParaEmailDesconhecidoNaoFazNadaENaoLanca() {
        assertThatCode(() -> service.reenviar("ninguem@exemplo.com")).doesNotThrowAnyException();

        assertThat(cenario.emails.enviadas).isEmpty();
        assertThat(cenario.tokens.todos()).isEmpty();
    }

    @Test
    void reenviarParaContaJaVerificadaNaoFazNada() {
        usuarios.salvar(ana.comEmailVerificado(agora()));

        service.reenviar("ana@exemplo.com");

        assertThat(cenario.emails.enviadas).isEmpty();
    }

    @Test
    void reenviarComEmailVazioOuNuloNaoFazNada() {
        assertThatCode(() -> {
            service.reenviar(null);
            service.reenviar("   ");
        }).doesNotThrowAnyException();

        assertThat(cenario.emails.enviadas).isEmpty();
    }

    @Test
    void reenviarComVerificacaoDesligadaNaoFazNada() {
        var desligado = CenarioVerificacao.desligada(usuarios);

        desligado.service.reenviar("ana@exemplo.com");

        assertThat(desligado.emails.enviadas).isEmpty();
    }

    // ---------- confirmar ----------

    @Test
    void confirmarMarcaAContaComoVerificadaNaHoraDoRelogio() {
        service.emitirPara(ana);
        cenario.relogio.avancar(Duration.ofMinutes(10));

        service.confirmar(cenario.emails.ultimoToken());

        var depois = usuarios.buscarPorId(ana.id()).orElseThrow();
        assertThat(depois.emailVerificado()).isTrue();
        assertThat(depois.emailVerificadoEm()).isEqualTo(agora());
        assertThat(cenario.tokens.todos().get(0).usadoEm()).isEqualTo(agora());
    }

    @Test
    void confirmarAceitaTokenComEspacosAoRedor() {
        service.emitirPara(ana);

        service.confirmar("  " + cenario.emails.ultimoToken() + "\n");

        assertThat(usuarios.buscarPorId(ana.id()).orElseThrow().emailVerificado()).isTrue();
    }

    @Test
    void linkSoPodeSerUsadoUmaVez() {
        service.emitirPara(ana);
        String token = cenario.emails.ultimoToken();
        service.confirmar(token);

        assertThatThrownBy(() -> service.confirmar(token)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void linkExpiradoNaoConfirma() {
        service.emitirPara(ana);
        cenario.relogio.avancar(CenarioVerificacao.VALIDADE);

        assertThatThrownBy(() -> service.confirmar(cenario.emails.ultimoToken()))
                .isInstanceOf(TokenInvalidoException.class);
        assertThat(usuarios.buscarPorId(ana.id()).orElseThrow().emailVerificado()).isFalse();
    }

    @Test
    void tokenDesconhecidoNaoConfirma() {
        service.emitirPara(ana);

        assertThatThrownBy(() -> service.confirmar("token-inventado")).isInstanceOf(TokenInvalidoException.class);
        assertThat(usuarios.buscarPorId(ana.id()).orElseThrow().emailVerificado()).isFalse();
    }

    @Test
    void tokenVazioNuloOuGrandeDemaisNaoConfirma() {
        for (String token : new String[]{null, "", "   ", "x".repeat(VerificacaoEmailService.TAMANHO_MAXIMO_TOKEN + 1)}) {
            assertThatThrownBy(() -> service.confirmar(token)).isInstanceOf(TokenInvalidoException.class);
        }
    }

    @Test
    void tokenDeContaQueNaoExisteMaisNaoConfirma() {
        service.emitirPara(ana);
        usuarios.excluirPorId(ana.id());

        assertThatThrownBy(() -> service.confirmar(cenario.emails.ultimoToken()))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void confirmarDeContaJaVerificadaMantemADataOriginal() {
        service.emitirPara(ana);
        String token = cenario.emails.ultimoToken();
        var verificadaAntes = usuarios.salvar(ana.comEmailVerificado(agora()));
        cenario.relogio.avancar(Duration.ofMinutes(5));

        service.confirmar(token);

        assertThat(usuarios.buscarPorId(ana.id()).orElseThrow().emailVerificadoEm())
                .isEqualTo(verificadaAntes.emailVerificadoEm());
    }
}
