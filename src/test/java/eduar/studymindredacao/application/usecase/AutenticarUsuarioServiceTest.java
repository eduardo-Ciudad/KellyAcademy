package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.CredenciaisInvalidasException;
import eduar.studymindredacao.domain.exception.EmailNaoVerificadoException;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.TokenGerado;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.port.TokenPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutenticarUsuarioServiceTest {
    private UsuarioRepositoryEmMemoria repository;
    private EncoderQueConta encoder;
    private AutenticarUsuarioService service;

    /** Conta as conferências de senha e guarda contra qual hash cada uma rodou (SEC-12). */
    static class EncoderQueConta extends SenhaEncoderFake {
        final List<String> hashesConferidos = new ArrayList<>();
        int codificacoes;

        @Override
        public String codificar(String senhaPura) {
            codificacoes++;
            return super.codificar(senhaPura);
        }

        @Override
        public boolean confere(String senhaPura, String senhaHash) {
            hashesConferidos.add(senhaHash);
            return super.confere(senhaPura, senhaHash);
        }
    }

    private final TokenPort tokenFake = new TokenPort() {
        @Override
        public TokenGerado gerar(Usuario usuario) {
            return new TokenGerado("token-de-" + usuario.email(), Instant.now().plusSeconds(3600));
        }

        @Override
        public Optional<DadosToken> validar(String token) {
            return Optional.empty();
        }
    };

    private AutenticarUsuarioService servico(CenarioVerificacao verificacao) {
        return new AutenticarUsuarioService(repository, encoder, tokenFake, verificacao.service);
    }

    private Usuario salvar(String email, boolean verificado) {
        var relogio = RelogioAjustavel.em("2026-10-17T00:00:00Z");
        return repository.salvar(new Usuario(null, "Aluno", email, "hash:senhaSegura123", Role.ALUNO, null,
                verificado ? java.time.OffsetDateTime.now(relogio) : null));
    }

    @BeforeEach
    void setUp() {
        repository = new UsuarioRepositoryEmMemoria();
        encoder = new EncoderQueConta();
        var verificacao = CenarioVerificacao.desligada(repository);
        new CadastrarUsuarioService(repository, encoder, PoliticaCadastro.aberta(), verificacao.service, verificacao.relogio)
                .cadastrar("Eduardo", "edu@exemplo.com", "senhaSegura123");
        service = servico(verificacao);
        encoder.hashesConferidos.clear();
    }

    @Test
    void geraTokenComCredenciaisCorretas() {
        var token = service.autenticar("EDU@exemplo.com", "senhaSegura123");

        assertThat(token.token()).isEqualTo("token-de-edu@exemplo.com");
    }

    @Test
    void rejeitaSenhaErrada() {
        assertThatThrownBy(() -> service.autenticar("edu@exemplo.com", "senhaErrada"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void rejeitaEmailInexistenteComAMesmaExcecao() {
        assertThatThrownBy(() -> service.autenticar("ninguem@exemplo.com", "senhaSegura123"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void rejeitaCamposVazios() {
        assertThatThrownBy(() -> service.autenticar("", ""))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    // ---------- SEC-12: mesmo custo exista a conta ou não ----------

    @Test
    void emailInexistenteAindaConfereASenhaContraUmHashFalso() {
        assertThatThrownBy(() -> service.autenticar("ninguem@exemplo.com", "qualquerSenha1"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(encoder.hashesConferidos).hasSize(1);
        assertThat(encoder.hashesConferidos.get(0)).isNotNull().isNotEqualTo("hash:senhaSegura123");
    }

    @Test
    void contaExistenteConfereContraOHashDela() {
        service.autenticar("edu@exemplo.com", "senhaSegura123");

        assertThat(encoder.hashesConferidos).containsExactly("hash:senhaSegura123");
    }

    @Test
    void senhaIgualAoTextoDoHashFalsoNaoEntra() {
        // nem adivinhando a senha usada no hash falso dá para entrar com e-mail inexistente
        String hashFalso;
        assertThatThrownBy(() -> service.autenticar("ninguem@exemplo.com", "x")).isInstanceOf(CredenciaisInvalidasException.class);
        hashFalso = encoder.hashesConferidos.get(0);
        String senhaDoHashFalso = hashFalso.substring("hash:".length());

        assertThatThrownBy(() -> service.autenticar("ninguem@exemplo.com", senhaDoHashFalso))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void hashFalsoEhGeradoUmaVezSoNaCriacao() {
        int antes = encoder.codificacoes;
        var outro = servico(CenarioVerificacao.desligada(repository));
        int depoisDeCriar = encoder.codificacoes;

        assertThatThrownBy(() -> outro.autenticar("ninguem@exemplo.com", "x")).isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> outro.autenticar("ninguem2@exemplo.com", "y")).isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(depoisDeCriar - antes).isEqualTo(1);
        assertThat(encoder.codificacoes).isEqualTo(depoisDeCriar);
    }

    // ---------- verificação de e-mail ----------

    @Test
    void verificacaoLigadaBloqueiaContaNaoVerificadaComSenhaCorreta() {
        salvar("nova@exemplo.com", false);
        var ligado = servico(CenarioVerificacao.ligada(repository));

        assertThatThrownBy(() -> ligado.autenticar("nova@exemplo.com", "senhaSegura123"))
                .isInstanceOf(EmailNaoVerificadoException.class);
    }

    @Test
    void contaNaoVerificadaComSenhaErradaContinuaCredenciaisInvalidas() {
        salvar("nova@exemplo.com", false);
        var ligado = servico(CenarioVerificacao.ligada(repository));

        // sem a senha certa ninguém descobre que a conta existe e falta confirmar
        assertThatThrownBy(() -> ligado.autenticar("nova@exemplo.com", "senhaErrada1"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void verificacaoLigadaDeixaEntrarContaVerificada() {
        salvar("ok@exemplo.com", true);
        var ligado = servico(CenarioVerificacao.ligada(repository));

        assertThat(ligado.autenticar("ok@exemplo.com", "senhaSegura123").token()).isEqualTo("token-de-ok@exemplo.com");
    }

    @Test
    void verificacaoDesligadaNaoBloqueiaContaAntigaSemVerificacao() {
        salvar("antiga@exemplo.com", false);

        assertThat(service.autenticar("antiga@exemplo.com", "senhaSegura123").token())
                .isEqualTo("token-de-antiga@exemplo.com");
    }
}
