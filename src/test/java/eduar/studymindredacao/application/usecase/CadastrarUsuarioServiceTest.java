package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.CadastroFechadoException;
import eduar.studymindredacao.domain.exception.EmailJaCadastradoException;
import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CadastrarUsuarioServiceTest {
    private UsuarioRepositoryEmMemoria repository;
    private CadastrarUsuarioService service;
    private CenarioVerificacao verificacao;

    private CadastrarUsuarioService novoService(PoliticaCadastro politica) {
        return new CadastrarUsuarioService(repository, new SenhaEncoderFake(), politica,
                verificacao.service, verificacao.relogio);
    }

    @BeforeEach
    void setUp() {
        repository = new UsuarioRepositoryEmMemoria();
        verificacao = CenarioVerificacao.desligada(repository);
        service = novoService(PoliticaCadastro.aberta());
    }

    @Test
    void cadastraAlunoComSenhaCodificadaEEmailNormalizado() {
        var usuario = service.cadastrar("  Eduardo  ", "  Edu@Exemplo.COM ", "senhaSegura123");

        assertThat(usuario.id()).isNotNull();
        assertThat(usuario.nome()).isEqualTo("Eduardo");
        assertThat(usuario.email()).isEqualTo("edu@exemplo.com");
        assertThat(usuario.senhaHash()).isEqualTo("hash:senhaSegura123");
        assertThat(usuario.role()).isEqualTo(Role.ALUNO);
    }

    @Test
    void rejeitaEmailJaCadastradoIgnorandoMaiusculas() {
        service.cadastrar("Eduardo", "edu@exemplo.com", "senhaSegura123");

        assertThatThrownBy(() -> service.cadastrar("Outro", "EDU@exemplo.com", "outraSenha123"))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    void rejeitaSenhaCurta() {
        assertThatThrownBy(() -> service.cadastrar("Eduardo", "edu@exemplo.com", "1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senha");
    }

    @Test
    void rejeitaSenhaAcimaDoLimiteDoBcrypt() {
        String senhaLonga = "a".repeat(73);

        assertThatThrownBy(() -> service.cadastrar("Eduardo", "edu@exemplo.com", senhaLonga))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaSenhaComAcentosAcimaDe72Bytes() {
        // 37 caracteres, mas 74 bytes: o BCrypt ignoraria o final em silêncio
        assertThatThrownBy(() -> service.cadastrar("Eduardo", "edu@exemplo.com", "á".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.existePorEmail("edu@exemplo.com")).isFalse();
    }

    @Test
    void cadastroFechadoRecusaEmailForaDaListaSemGravarNada() {
        var fechado = novoService(PoliticaCadastro.de(false, "amigo@exemplo.com"));

        assertThatThrownBy(() -> fechado.cadastrar("Estranho", "estranho@exemplo.com", "senhaSegura123"))
                .isInstanceOf(CadastroFechadoException.class);
        assertThat(repository.existePorEmail("estranho@exemplo.com")).isFalse();
    }

    @Test
    void cadastroFechadoAceitaEmailDaListaIgnorandoMaiusculasEEspacos() {
        var fechado = novoService(PoliticaCadastro.de(false, " Amigo@Exemplo.com , outra@exemplo.com"));

        var usuario = fechado.cadastrar("Amigo", "  AMIGO@exemplo.COM ", "senhaSegura123");

        assertThat(usuario.email()).isEqualTo("amigo@exemplo.com");
    }

    // ---------- verificação de e-mail ----------

    @Test
    void comVerificacaoDesligadaAContaNasceVerificadaSemEmail() {
        var usuario = service.cadastrar("Eduardo", "edu@exemplo.com", "senhaSegura123");

        assertThat(usuario.emailVerificado()).isTrue();
        assertThat(usuario.emailVerificadoEm()).isEqualTo(OffsetDateTime.now(verificacao.relogio));
        assertThat(verificacao.emails.enviadas).isEmpty();
        assertThat(verificacao.tokens.todos()).isEmpty();
    }

    @Test
    void comVerificacaoLigadaAContaNasceNaoVerificadaERecebeOLink() {
        verificacao = CenarioVerificacao.ligada(repository);
        var ligado = novoService(PoliticaCadastro.aberta());

        var usuario = ligado.cadastrar("Eduardo", "  Edu@Exemplo.COM ", "senhaSegura123");

        assertThat(usuario.emailVerificado()).isFalse();
        assertThat(repository.buscarPorId(usuario.id()).orElseThrow().emailVerificado()).isFalse();
        assertThat(verificacao.emails.enviadas).hasSize(1);
        assertThat(verificacao.emails.ultima().destinatario()).isEqualTo("edu@exemplo.com");
        assertThat(verificacao.tokens.todos()).hasSize(1);
        assertThat(verificacao.tokens.todos().get(0).usuarioId()).isEqualTo(usuario.id());
    }

    @Test
    void cadastroRecusadoNaoGeraTokenNemEmail() {
        verificacao = CenarioVerificacao.ligada(repository);
        var ligado = novoService(PoliticaCadastro.de(false, "amigo@exemplo.com"));
        ligado.cadastrar("Amigo", "amigo@exemplo.com", "senhaSegura123");
        verificacao.emails.enviadas.clear();
        int tokensAntes = verificacao.tokens.todos().size();

        assertThatThrownBy(() -> ligado.cadastrar("Estranho", "estranho@exemplo.com", "senhaSegura123"))
                .isInstanceOf(CadastroFechadoException.class);
        assertThatThrownBy(() -> ligado.cadastrar("Outro", "amigo@exemplo.com", "senhaSegura123"))
                .isInstanceOf(EmailJaCadastradoException.class);
        assertThatThrownBy(() -> ligado.cadastrar("Curta", "amigo2@exemplo.com", "123"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(verificacao.emails.enviadas).isEmpty();
        assertThat(verificacao.tokens.todos()).hasSize(tokensAntes);
    }
}
