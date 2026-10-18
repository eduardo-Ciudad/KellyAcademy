package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Troca de senha e versão da sessão contra o Postgres real (Flyway até a V17). Cada teste roda numa transação
 * desfeita no fim. Onde o teste precisa ver o que está de fato no banco (e não o que ficou em memória no
 * contexto do JPA), faz flush + clear antes de ler.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UsuarioRepositoryAdapter.class)
class UsuarioRepositoryAdapterIntegrationTest {
    // Postgres guarda microssegundos: sem truncar, a comparação com o Java falharia por nanos
    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private UsuarioRepositoryPort usuarios;
    @Autowired
    private EntityManager em;

    private UUID id;

    @BeforeEach
    void criarUsuario() {
        String email = "senha-" + UUID.randomUUID() + "@exemplo.com";
        id = usuarios.salvar(new Usuario(null, "Teste", email, "hash-original", Role.ALUNO, null, null)).id();
        recarregarDoBanco();
    }

    private void recarregarDoBanco() {
        em.flush();
        em.clear();
    }

    private Usuario doBanco() {
        recarregarDoBanco();
        return usuarios.buscarPorId(id).orElseThrow();
    }

    @Test
    void contaNovaComecaNaVersaoZeroSemTrocaDeSenha() {
        var usuario = doBanco();

        assertThat(usuario.versaoSessao()).isZero();
        assertThat(usuario.senhaAlteradaEm()).isNull();
    }

    @Test
    void alterarSenhaTrocaOHashSobeAVersaoEGuardaAData() {
        var alterado = usuarios.alterarSenha(id, "hash-novo", AGORA).orElseThrow();

        assertThat(alterado.senhaHash()).isEqualTo("hash-novo");
        assertThat(alterado.versaoSessao()).isEqualTo(1);
        assertThat(alterado.senhaAlteradaEm()).isAtSameInstantAs(AGORA);

        var noBanco = doBanco();
        assertThat(noBanco.senhaHash()).isEqualTo("hash-novo");
        assertThat(noBanco.versaoSessao()).isEqualTo(1);
    }

    @Test
    void duasTrocasSeguidasLevamAVersaoDois() {
        usuarios.alterarSenha(id, "hash-2", AGORA);
        var segunda = usuarios.alterarSenha(id, "hash-3", AGORA.plusMinutes(1)).orElseThrow();

        assertThat(segunda.versaoSessao()).isEqualTo(2);
        assertThat(segunda.senhaHash()).isEqualTo("hash-3");
        assertThat(segunda.senhaAlteradaEm()).isAtSameInstantAs(AGORA.plusMinutes(1));
    }

    @Test
    void alterarSenhaConfirmaOEmailQueAindaNaoEstavaVerificado() {
        assertThat(doBanco().emailVerificado()).isFalse();

        var alterado = usuarios.alterarSenha(id, "hash-novo", AGORA).orElseThrow();

        assertThat(alterado.emailVerificadoEm()).isAtSameInstantAs(AGORA);
    }

    @Test
    void alterarSenhaMantemADataDeVerificacaoQueJaExistia() {
        var verificadoEm = AGORA.minusDays(3);
        usuarios.salvar(doBanco().comEmailVerificado(verificadoEm));
        recarregarDoBanco();

        var alterado = usuarios.alterarSenha(id, "hash-novo", AGORA).orElseThrow();

        assertThat(alterado.emailVerificadoEm()).isAtSameInstantAs(verificadoEm);
    }

    @Test
    void alterarSenhaDeContaInexistenteDevolveVazioENaoMexeEmNada() {
        assertThat(usuarios.alterarSenha(UUID.randomUUID(), "hash-novo", AGORA)).isEmpty();

        var usuario = doBanco();
        assertThat(usuario.senhaHash()).isEqualTo("hash-original");
        assertThat(usuario.versaoSessao()).isZero();
    }

    @Test
    void buscarVersaoSessaoAcompanhaAsTrocas() {
        assertThat(usuarios.buscarVersaoSessao(id)).contains(0);

        usuarios.alterarSenha(id, "hash-novo", AGORA);

        assertThat(usuarios.buscarVersaoSessao(id)).contains(1);
    }

    @Test
    void buscarVersaoSessaoDeContaInexistenteDevolveVazio() {
        assertThat(usuarios.buscarVersaoSessao(UUID.randomUUID())).isEmpty();
        assertThat(usuarios.buscarVersaoSessao(null)).isEmpty();
    }

    @Test
    void salvarUmUsuarioCarregadoAntesDaTrocaNaoDesfazATroca() {
        var carregadoAntes = doBanco();
        usuarios.alterarSenha(id, "hash-novo", AGORA);
        recarregarDoBanco();

        // ex.: confirmar o e-mail com um Usuario lido antes da troca de senha
        usuarios.salvar(carregadoAntes.comEmailVerificado(AGORA.plusMinutes(5)));

        var noBanco = doBanco();
        assertThat(noBanco.senhaHash()).as("salvar não regrava a senha antiga").isEqualTo("hash-novo");
        assertThat(noBanco.versaoSessao()).as("salvar não volta a versão").isEqualTo(1);
        assertThat(noBanco.senhaAlteradaEm()).isAtSameInstantAs(AGORA);
        assertThat(noBanco.emailVerificadoEm()).as("o resto continua sendo salvo").isAtSameInstantAs(AGORA.plusMinutes(5));
    }

    @Test
    void oBancoRecusaVersaoDeSessaoNegativa() {
        // último passo do teste: depois do erro a transação do Postgres fica abortada
        assertThatThrownBy(() -> em.createNativeQuery("UPDATE usuarios SET versao_sessao = -1 WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }
}
