package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.CredenciaisInvalidasException;
import eduar.studymindredacao.domain.exception.TokenInvalidoException;
import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedefinicaoSenhaServiceTest {
    private static final String SENHA_NOVA = "senhaNova123";

    private CenarioSenha c;
    private OffsetDateTime verificadaEm;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        c = CenarioSenha.ligado();
        verificadaEm = c.agora().minusDays(5);
        ana = c.salvar("Ana", "ana@exemplo.com", verificadaEm);
    }

    private Usuario recarregar(Usuario usuario) {
        return c.usuarios.buscarPorId(usuario.id()).orElseThrow();
    }

    /** Pede o link para a Ana e devolve o token em claro que foi no e-mail. */
    private String pedirLinkDaAna() {
        c.redefinicao.solicitar("ana@exemplo.com");
        return c.emails.ultimoTokenRedefinicao();
    }

    private void salvarTokenDeVerificacao(Usuario usuario, String valor) {
        c.tokens.salvar(TokenUsuario.novo(usuario.id(), TipoTokenUsuario.VERIFICACAO_EMAIL, c.gerador.hash(valor),
                c.agora(), Duration.ofHours(24)));
    }

    // ---------- solicitar ----------

    @Test
    void solicitarEnviaOLinkEGuardaSoOHashComValidadeDe30Minutos() {
        c.redefinicao.solicitar("ana@exemplo.com");

        var mensagem = c.emails.ultimaRedefinicao();
        assertThat(mensagem.destinatario()).isEqualTo("ana@exemplo.com");
        assertThat(mensagem.nome()).isEqualTo("Ana");
        assertThat(mensagem.link()).isEqualTo("https://kellyacademy.com.br/redefinir-senha#token=token-1");

        assertThat(c.tokens.todos()).hasSize(1);
        var salvo = c.tokens.todos().get(0);
        assertThat(salvo.tipo()).isEqualTo(TipoTokenUsuario.REDEFINICAO_SENHA);
        assertThat(salvo.usuarioId()).isEqualTo(ana.id());
        assertThat(salvo.tokenHash()).isEqualTo(c.gerador.hash("token-1")).isNotEqualTo("token-1");
        assertThat(salvo.expiraEm()).isEqualTo(c.agora().plusMinutes(30));
    }

    @Test
    void contaNaoVerificadaTambemRecebeOLink() {
        c.salvar("Beto", "beto@exemplo.com", null);

        c.redefinicao.solicitar("beto@exemplo.com");

        assertThat(c.emails.ultimaRedefinicao().destinatario()).isEqualTo("beto@exemplo.com");
    }

    @Test
    void ignoraMaiusculasEEspacosNoEmail() {
        c.redefinicao.solicitar("  ANA@Exemplo.COM ");

        assertThat(c.emails.redefinicoes).hasSize(1);
    }

    @Test
    void emailDesconhecidoNuloOuVazioNaoFazNadaENaoLancaExcecao() {
        assertThatCode(() -> {
            c.redefinicao.solicitar("ninguem@exemplo.com");
            c.redefinicao.solicitar(null);
            c.redefinicao.solicitar("   ");
        }).doesNotThrowAnyException();

        assertThat(c.emails.redefinicoes).isEmpty();
        assertThat(c.tokens.todos()).isEmpty();
    }

    @Test
    void comOEnvioDesligadoNaoFazNada() {
        var desligado = CenarioSenha.desligado();
        desligado.salvar("Ana", "ana@exemplo.com", null);

        desligado.redefinicao.solicitar("ana@exemplo.com");

        assertThat(desligado.emails.redefinicoes).isEmpty();
        assertThat(desligado.tokens.todos()).isEmpty();
    }

    @Test
    void pedirDeNovoInvalidaOLinkAnterior() {
        c.redefinicao.solicitar("ana@exemplo.com");
        c.redefinicao.solicitar("ana@exemplo.com");

        assertThatThrownBy(() -> c.redefinicao.redefinir("token-1", SENHA_NOVA)).isInstanceOf(TokenInvalidoException.class);
        assertThatCode(() -> c.redefinicao.redefinir("token-2", SENHA_NOVA)).doesNotThrowAnyException();
    }

    // ---------- redefinir ----------

    @Test
    void redefinirTrocaASenhaSobeAVersaoEAvisaODono() {
        String token = pedirLinkDaAna();

        c.redefinicao.redefinir(token, SENHA_NOVA);

        var depois = recarregar(ana);
        assertThat(depois.senhaHash()).isEqualTo("hash:" + SENHA_NOVA);
        assertThat(depois.versaoSessao()).isEqualTo(1);
        assertThat(depois.senhaAlteradaEm()).isEqualTo(c.agora());

        var aviso = c.emails.ultimoAviso();
        assertThat(aviso.destinatario()).isEqualTo("ana@exemplo.com");
        assertThat(aviso.nome()).isEqualTo("Ana");
        assertThat(aviso.alteradaEm()).isEqualTo(c.agora());
        assertThat(aviso.linkEsqueciSenha()).isEqualTo("https://kellyacademy.com.br/esqueci-senha");
    }

    @Test
    void redefinirConfirmaOEmailDeContaNaoVerificada() {
        var beto = c.salvar("Beto", "beto@exemplo.com", null);
        c.redefinicao.solicitar("beto@exemplo.com");

        c.redefinicao.redefinir(c.emails.ultimoTokenRedefinicao(), SENHA_NOVA);

        assertThat(recarregar(beto).emailVerificadoEm()).isEqualTo(c.agora());
    }

    @Test
    void redefinirMantemADataDeQuemJaTinhaConfirmado() {
        c.redefinicao.redefinir(pedirLinkDaAna(), SENHA_NOVA);

        assertThat(recarregar(ana).emailVerificadoEm()).isEqualTo(verificadaEm);
    }

    @Test
    void oLinkSoValeUmaVez() {
        String token = pedirLinkDaAna();
        c.redefinicao.redefinir(token, SENHA_NOVA);

        assertThatThrownBy(() -> c.redefinicao.redefinir(token, "outraSenha456")).isInstanceOf(TokenInvalidoException.class);
        assertThat(recarregar(ana).senhaHash()).isEqualTo("hash:" + SENHA_NOVA);
    }

    @Test
    void linkExpiraAos30MinutosEmPonto() {
        String token = pedirLinkDaAna();
        c.relogio.avancar(Duration.ofMinutes(30));

        assertThatThrownBy(() -> c.redefinicao.redefinir(token, SENHA_NOVA)).isInstanceOf(TokenInvalidoException.class);
        assertThat(recarregar(ana).senhaHash()).isEqualTo("hash:" + CenarioSenha.SENHA_ANTIGA);
        assertThat(recarregar(ana).versaoSessao()).isZero();
    }

    @Test
    void com29MinutosOLinkAindaVale() {
        String token = pedirLinkDaAna();
        c.relogio.avancar(Duration.ofMinutes(29));

        assertThatCode(() -> c.redefinicao.redefinir(token, SENHA_NOVA)).doesNotThrowAnyException();
    }

    @Test
    void tokenDeVerificacaoDeEmailNaoServeParaRedefinir() {
        salvarTokenDeVerificacao(ana, "token-verificacao");

        assertThatThrownBy(() -> c.redefinicao.redefinir("token-verificacao", SENHA_NOVA))
                .isInstanceOf(TokenInvalidoException.class);
        assertThat(recarregar(ana).versaoSessao()).isZero();
    }

    @Test
    void senhaFracaNaoQueimaOLink() {
        String token = pedirLinkDaAna();

        assertThatThrownBy(() -> c.redefinicao.redefinir(token, "curta"))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(TokenInvalidoException.class);
        assertThatCode(() -> c.redefinicao.redefinir(token, SENHA_NOVA))
                .as("o mesmo link funciona com uma senha válida")
                .doesNotThrowAnyException();
    }

    @Test
    void tokenInvalidoNaoRodaOBcrypt() {
        int antes = c.encoder.codificacoes;

        assertThatThrownBy(() -> c.redefinicao.redefinir("token-que-nao-existe", SENHA_NOVA))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(c.encoder.codificacoes).isEqualTo(antes);
    }

    @Test
    void tokenNuloVazioOuGrandeDemaisERecusado() {
        for (String token : new String[]{null, "", "   ", "a".repeat(129)}) {
            assertThatThrownBy(() -> c.redefinicao.redefinir(token, SENHA_NOVA))
                    .as(String.valueOf(token))
                    .isInstanceOf(TokenInvalidoException.class);
        }
    }

    @Test
    void aceitaOTokenComEspacosEmVolta() {
        String token = pedirLinkDaAna();

        assertThatCode(() -> c.redefinicao.redefinir("  " + token + " ", SENHA_NOVA)).doesNotThrowAnyException();
    }

    @Test
    void redefinirMataOLinkDeVerificacaoPendente() {
        var beto = c.salvar("Beto", "beto@exemplo.com", null);
        salvarTokenDeVerificacao(beto, "token-verificacao");
        c.redefinicao.solicitar("beto@exemplo.com");

        c.redefinicao.redefinir(c.emails.ultimoTokenRedefinicao(), SENHA_NOVA);

        assertThat(c.tokens.consumirValido(c.gerador.hash("token-verificacao"), TipoTokenUsuario.VERIFICACAO_EMAIL,
                c.agora())).isEmpty();
    }

    @Test
    void comOEnvioDesligadoUmTokenValidoAindaRedefineSemMandarAviso() {
        var desligado = CenarioSenha.desligado();
        var conta = desligado.salvar("Ana", "ana@exemplo.com", null);
        desligado.tokens.salvar(TokenUsuario.novo(conta.id(), TipoTokenUsuario.REDEFINICAO_SENHA,
                desligado.gerador.hash("token-x"), desligado.agora(), Duration.ofMinutes(30)));

        desligado.redefinicao.redefinir("token-x", SENHA_NOVA);

        assertThat(desligado.usuarios.buscarPorId(conta.id()).orElseThrow().versaoSessao()).isEqualTo(1);
        assertThat(desligado.emails.avisos).isEmpty();
    }

    // ---------- integração com o login e a sessão ----------

    private AutenticarUsuarioService login() {
        return new AutenticarUsuarioService(c.usuarios, c.encoder, c.jwt, CenarioVerificacao.desligada(c.usuarios).service);
    }

    @Test
    void depoisDeRedefinirEntraComASenhaNovaENaoComAAntiga() {
        c.redefinicao.redefinir(pedirLinkDaAna(), SENHA_NOVA);
        var login = login();

        assertThatCode(() -> login.autenticar("ana@exemplo.com", SENHA_NOVA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> login.autenticar("ana@exemplo.com", CenarioSenha.SENHA_ANTIGA))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void redefinirDerrubaAsSessoesAbertas() {
        String sessaoAntiga = login().autenticar("ana@exemplo.com", CenarioSenha.SENHA_ANTIGA).token();
        assertThat(c.validarSessao.validar(sessaoAntiga)).isPresent();

        c.redefinicao.redefinir(pedirLinkDaAna(), SENHA_NOVA);

        assertThat(c.validarSessao.validar(sessaoAntiga)).as("sessão de antes da troca").isEmpty();
        String sessaoNova = login().autenticar("ana@exemplo.com", SENHA_NOVA).token();
        assertThat(c.validarSessao.validar(sessaoNova)).as("login depois da troca").isPresent();
    }
}
