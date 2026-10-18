package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração com o Postgres real (Flyway aplica até a V17). Mesmo slice de JPA do teste do Repertório:
 * só precisa das variáveis do banco. Cada teste roda numa transação desfeita no fim, então nada fica no banco.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TokenUsuarioRepositoryAdapter.class, UsuarioRepositoryAdapter.class})
class TokenUsuarioRepositoryAdapterIntegrationTest {
    private static final TipoTokenUsuario TIPO = TipoTokenUsuario.VERIFICACAO_EMAIL;
    // Postgres guarda microssegundos: sem truncar, a comparação com o Java falharia por nanos
    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private TokenUsuarioRepositoryPort tokens;
    @Autowired
    private UsuarioRepositoryPort usuarios;
    @Autowired
    private TokenUsuarioJpaRepository jpa;

    private UUID usuarioId;

    @BeforeEach
    void criarUsuario() {
        String email = "token-" + UUID.randomUUID() + "@exemplo.com";
        usuarioId = usuarios.salvar(new Usuario(null, "Teste", email, "hash", Role.ALUNO, null, null)).id();
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private TokenUsuario salvarToken(String hash, OffsetDateTime criadoEm, Duration validade) {
        return tokens.salvar(TokenUsuario.novo(usuarioId, TIPO, hash, criadoEm, validade));
    }

    @Test
    void salvarGeraIdEGuardaOsCampos() {
        String hash = hash();

        var salvo = salvarToken(hash, AGORA, Duration.ofHours(24));

        assertThat(salvo.id()).isNotNull();
        var linha = jpa.findById(salvo.id()).orElseThrow();
        assertThat(linha.getTokenHash()).isEqualTo(hash);
        assertThat(linha.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(linha.getTipo()).isEqualTo(TIPO);
        assertThat(linha.getExpiraEm()).isAtSameInstantAs(AGORA.plusHours(24));
        assertThat(linha.getUsadoEm()).isNull();
    }

    @Test
    void consumirTokenValidoDevolveODonoEMarcaComoUsado() {
        String hash = hash();
        var salvo = salvarToken(hash, AGORA, Duration.ofHours(24));

        var dono = tokens.consumirValido(hash, TIPO, AGORA.plusMinutes(1));

        assertThat(dono).contains(usuarioId);
        assertThat(jpa.findById(salvo.id()).orElseThrow().getUsadoEm()).isAtSameInstantAs(AGORA.plusMinutes(1));
    }

    @Test
    void tokenSoPodeSerConsumidoUmaVez() {
        String hash = hash();
        salvarToken(hash, AGORA, Duration.ofHours(24));

        assertThat(tokens.consumirValido(hash, TIPO, AGORA.plusMinutes(1))).isPresent();
        assertThat(tokens.consumirValido(hash, TIPO, AGORA.plusMinutes(2))).isEmpty();
    }

    @Test
    void tokenExpiradoNaoEConsumidoNemMarcado() {
        String hash = hash();
        var salvo = salvarToken(hash, AGORA, Duration.ofHours(1));

        assertThat(tokens.consumirValido(hash, TIPO, AGORA.plusHours(1))).as("no instante da expiração").isEmpty();
        assertThat(tokens.consumirValido(hash, TIPO, AGORA.plusHours(2))).isEmpty();
        assertThat(jpa.findById(salvo.id()).orElseThrow().getUsadoEm()).isNull();
    }

    @Test
    void hashInexistenteOuVazioNaoConsome() {
        salvarToken(hash(), AGORA, Duration.ofHours(24));

        assertThat(tokens.consumirValido(hash(), TIPO, AGORA)).isEmpty();
        assertThat(tokens.consumirValido("", TIPO, AGORA)).isEmpty();
        assertThat(tokens.consumirValido(null, TIPO, AGORA)).isEmpty();
    }

    @Test
    void invalidarAtivosInutilizaOsTokensPendentesDoUsuario() {
        String primeiro = hash();
        String segundo = hash();
        salvarToken(primeiro, AGORA, Duration.ofHours(24));
        salvarToken(segundo, AGORA, Duration.ofHours(24));

        int invalidados = tokens.invalidarAtivos(usuarioId, TIPO, AGORA.plusMinutes(1));

        assertThat(invalidados).isEqualTo(2);
        assertThat(tokens.consumirValido(primeiro, TIPO, AGORA.plusMinutes(2))).isEmpty();
        assertThat(tokens.consumirValido(segundo, TIPO, AGORA.plusMinutes(2))).isEmpty();
    }

    @Test
    void invalidarAtivosNaoMexeEmTokenDeOutroUsuario() {
        String doOutro = hash();
        salvarToken(doOutro, AGORA, Duration.ofHours(24));
        var outro = usuarios.salvar(new Usuario(null, "Outro", "outro-" + UUID.randomUUID() + "@exemplo.com",
                "hash", Role.ALUNO, null, null));

        assertThat(tokens.invalidarAtivos(outro.id(), TIPO, AGORA.plusMinutes(1))).isZero();
        assertThat(tokens.consumirValido(doOutro, TIPO, AGORA.plusMinutes(2))).contains(usuarioId);
    }

    @Test
    void excluirAntigosApagaExpiradosEUsadosAntesDoCorteEMantemOsAtivos() {
        var expirado = salvarToken(hash(), AGORA.minusDays(10), Duration.ofHours(24));
        String usadoHash = hash();
        var usado = salvarToken(usadoHash, AGORA.minusDays(9), Duration.ofDays(30));
        tokens.consumirValido(usadoHash, TIPO, AGORA.minusDays(9).plusMinutes(1));
        var ativo = salvarToken(hash(), AGORA, Duration.ofHours(24));

        int apagados = tokens.excluirAntigos(AGORA.minusDays(7));

        assertThat(apagados).isGreaterThanOrEqualTo(2);
        assertThat(jpa.findById(expirado.id())).isEmpty();
        assertThat(jpa.findById(usado.id())).isEmpty();
        assertThat(jpa.findById(ativo.id())).isPresent();
    }

    @Test
    void usuarioGuardaEDevolveADataDeVerificacao() {
        var usuario = usuarios.buscarPorId(usuarioId).orElseThrow();
        assertThat(usuario.emailVerificado()).isFalse();

        usuarios.salvar(usuario.comEmailVerificado(AGORA));

        var devolta = usuarios.buscarPorId(usuarioId).orElseThrow();
        assertThat(devolta.emailVerificado()).isTrue();
        assertThat(devolta.emailVerificadoEm()).isAtSameInstantAs(AGORA);
    }

    @Test
    void tokenDeRedefinicaoDeSenhaESalvoEConsumido() {
        String hash = hash();
        tokens.salvar(TokenUsuario.novo(usuarioId, TipoTokenUsuario.REDEFINICAO_SENHA, hash, AGORA, Duration.ofMinutes(30)));

        assertThat(tokens.consumirValido(hash, TipoTokenUsuario.REDEFINICAO_SENHA, AGORA.plusMinutes(1)))
                .contains(usuarioId);
    }

    @Test
    void tokenDeUmTipoNaoServeParaOOutro() {
        String redefinicao = hash();
        String verificacao = hash();
        tokens.salvar(TokenUsuario.novo(usuarioId, TipoTokenUsuario.REDEFINICAO_SENHA, redefinicao, AGORA, Duration.ofMinutes(30)));
        salvarToken(verificacao, AGORA, Duration.ofHours(24));

        assertThat(tokens.consumirValido(redefinicao, TipoTokenUsuario.VERIFICACAO_EMAIL, AGORA.plusMinutes(1))).isEmpty();
        assertThat(tokens.consumirValido(verificacao, TipoTokenUsuario.REDEFINICAO_SENHA, AGORA.plusMinutes(1))).isEmpty();
        assertThat(tokens.consumirValido(redefinicao, TipoTokenUsuario.REDEFINICAO_SENHA, AGORA.plusMinutes(2)))
                .as("a tentativa com o tipo errado não queimou o token").contains(usuarioId);
    }

    @Test
    void invalidarAtivosDeUmTipoNaoMexeNoOutroTipo() {
        String verificacao = hash();
        salvarToken(verificacao, AGORA, Duration.ofHours(24));
        tokens.salvar(TokenUsuario.novo(usuarioId, TipoTokenUsuario.REDEFINICAO_SENHA, hash(), AGORA, Duration.ofMinutes(30)));

        int invalidados = tokens.invalidarAtivos(usuarioId, TipoTokenUsuario.REDEFINICAO_SENHA, AGORA.plusMinutes(1));

        assertThat(invalidados).isEqualTo(1);
        assertThat(tokens.consumirValido(verificacao, TIPO, AGORA.plusMinutes(2))).contains(usuarioId);
    }
}
