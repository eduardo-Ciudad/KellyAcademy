package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.CredenciaisInvalidasException;
import eduar.studymindredacao.domain.exception.SenhaAtualIncorretaException;
import eduar.studymindredacao.domain.exception.TokenInvalidoException;
import eduar.studymindredacao.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlterarSenhaServiceTest {
    private static final String ATUAL = CenarioSenha.SENHA_ANTIGA;
    private static final String NOVA = "senhaNova123";

    private CenarioSenha c;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        c = CenarioSenha.ligado();
        ana = c.salvar("Ana", "ana@exemplo.com", c.agora().minusDays(5));
    }

    private Usuario recarregar() {
        return c.usuarios.buscarPorId(ana.id()).orElseThrow();
    }

    private void nadaMudou() {
        var usuario = recarregar();
        assertThat(usuario.senhaHash()).isEqualTo("hash:" + ATUAL);
        assertThat(usuario.versaoSessao()).isZero();
        assertThat(c.emails.avisos).isEmpty();
    }

    @Test
    void trocaASenhaSobeAVersaoAvisaEDevolveUmTokenDaVersaoNova() {
        var token = c.alterar.alterar(ana.id(), ATUAL, NOVA);

        var depois = recarregar();
        assertThat(depois.senhaHash()).isEqualTo("hash:" + NOVA);
        assertThat(depois.versaoSessao()).isEqualTo(1);
        assertThat(depois.senhaAlteradaEm()).isEqualTo(c.agora());
        assertThat(c.emails.ultimoAviso().alteradaEm()).isEqualTo(c.agora());

        var dados = c.validarSessao.validar(token.token());
        assertThat(dados).isPresent();
        assertThat(dados.get().versaoSessao()).isEqualTo(1);
    }

    @Test
    void aSessaoAntigaCaiEADevolvidaContinuaValendo() {
        String antiga = c.jwt.gerar(ana).token();

        var nova = c.alterar.alterar(ana.id(), ATUAL, NOVA);

        assertThat(c.validarSessao.validar(antiga)).as("outros aparelhos").isEmpty();
        assertThat(c.validarSessao.validar(nova.token())).as("aparelho que trocou").isPresent();
    }

    @Test
    void senhaAtualErradaNaoMudaNada() {
        assertThatThrownBy(() -> c.alterar.alterar(ana.id(), "errada12345", NOVA))
                .isInstanceOf(SenhaAtualIncorretaException.class);

        nadaMudou();
    }

    @Test
    void senhaAtualVaziaOuNulaEIncorreta() {
        assertThatThrownBy(() -> c.alterar.alterar(ana.id(), "", NOVA)).isInstanceOf(SenhaAtualIncorretaException.class);
        assertThatThrownBy(() -> c.alterar.alterar(ana.id(), null, NOVA)).isInstanceOf(SenhaAtualIncorretaException.class);

        nadaMudou();
    }

    @Test
    void conferirASenhaAtualVemAntesDeValidarANova() {
        // senha atual errada + nova fraca: a resposta é sempre a da senha atual
        assertThatThrownBy(() -> c.alterar.alterar(ana.id(), "errada12345", "curta"))
                .isInstanceOf(SenhaAtualIncorretaException.class);
    }

    @Test
    void novaSenhaFracaERecusada() {
        assertThatThrownBy(() -> c.alterar.alterar(ana.id(), ATUAL, "curta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senha");

        nadaMudou();
    }

    @Test
    void novaSenhaIgualAAtualERecusada() {
        assertThatThrownBy(() -> c.alterar.alterar(ana.id(), ATUAL, ATUAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AlterarSenhaService.MENSAGEM_SENHA_IGUAL);

        nadaMudou();
    }

    @Test
    void trocarASenhaInvalidaUmLinkDeRedefinicaoPendente() {
        c.redefinicao.solicitar("ana@exemplo.com");
        String linkPendente = c.emails.ultimoTokenRedefinicao();

        c.alterar.alterar(ana.id(), ATUAL, NOVA);

        assertThatThrownBy(() -> c.redefinicao.redefinir(linkPendente, "outraSenha456"))
                .isInstanceOf(TokenInvalidoException.class);
        assertThat(recarregar().senhaHash()).isEqualTo("hash:" + NOVA);
    }

    @Test
    void contaInexistenteERecusada() {
        assertThatThrownBy(() -> c.alterar.alterar(UUID.randomUUID(), ATUAL, NOVA))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void comOEnvioDesligadoTrocaSemMandarAviso() {
        var desligado = CenarioSenha.desligado();
        var conta = desligado.salvar("Ana", "ana@exemplo.com", null);

        desligado.alterar.alterar(conta.id(), ATUAL, NOVA);

        assertThat(desligado.usuarios.buscarPorId(conta.id()).orElseThrow().senhaHash()).isEqualTo("hash:" + NOVA);
        assertThat(desligado.emails.avisos).isEmpty();
    }
}
