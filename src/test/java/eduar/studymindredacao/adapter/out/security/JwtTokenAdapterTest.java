package eduar.studymindredacao.adapter.out.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.JWTCreator;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenAdapterTest {
    private static final String SECRET_TESTE = "segredo-de-teste-com-pelo-menos-32-caracteres";
    private static final String ISSUER = "studymind-redacao";

    private final Usuario usuario = new Usuario(
            UUID.randomUUID(), "Eduardo", "edu@exemplo.com", "hash", Role.ALUNO, null, null
    );

    @Test
    void geraEValidaTokenComDadosDoUsuario() {
        var adapter = new JwtTokenAdapter(SECRET_TESTE, ISSUER, Duration.ofMinutes(60), Clock.systemUTC());

        var gerado = adapter.gerar(usuario);
        var dados = adapter.validar(gerado.token());

        assertThat(dados).isPresent();
        assertThat(dados.get().usuarioId()).isEqualTo(usuario.id());
        assertThat(dados.get().email()).isEqualTo("edu@exemplo.com");
        assertThat(dados.get().role()).isEqualTo(Role.ALUNO);
        assertThat(gerado.expiraEm()).isAfter(Instant.now());
    }

    @Test
    void rejeitaTokenAssinadoComOutroSecret() {
        var emissor = new JwtTokenAdapter(SECRET_TESTE, ISSUER, Duration.ofMinutes(60), Clock.systemUTC());
        var outro = new JwtTokenAdapter(SECRET_TESTE + "-diferente", ISSUER, Duration.ofMinutes(60), Clock.systemUTC());

        assertThat(outro.validar(emissor.gerar(usuario).token())).isEmpty();
    }

    @Test
    void rejeitaTokenExpirado() {
        var duasHorasAtras = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        var adapter = new JwtTokenAdapter(SECRET_TESTE, ISSUER, Duration.ofMinutes(60), duasHorasAtras);

        assertThat(adapter.validar(adapter.gerar(usuario).token())).isEmpty();
    }

    @Test
    void rejeitaTokenMalformadoOuVazio() {
        var adapter = new JwtTokenAdapter(SECRET_TESTE, ISSUER, Duration.ofMinutes(60), Clock.systemUTC());

        assertThat(adapter.validar("isso-nao-e-um-jwt")).isEmpty();
        assertThat(adapter.validar("")).isEmpty();
        assertThat(adapter.validar(null)).isEmpty();
    }

    @Test
    void exigeSecretComTamanhoMinimo() {
        assertThatThrownBy(() -> new JwtTokenAdapter("curto", ISSUER, Duration.ofMinutes(60), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    // ---------- versão da sessão (claim "sv") ----------

    private JwtTokenAdapter adapter() {
        return new JwtTokenAdapter(SECRET_TESTE, ISSUER, Duration.ofMinutes(60), Clock.systemUTC());
    }

    /** Token assinado com o mesmo secret, montado à mão para testar o que chega de fora. */
    private JWTCreator.Builder tokenManual() {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(usuario.id().toString())
                .withClaim("email", usuario.email())
                .withClaim("role", "ALUNO")
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
    }

    @Test
    void oTokenCarregaAVersaoDaSessaoDoUsuario() {
        var naVersao3 = new Usuario(usuario.id(), "Eduardo", "edu@exemplo.com", "hash", Role.ALUNO, null, null, 3, null);
        var adapter = adapter();

        var dados = adapter.validar(adapter.gerar(naVersao3).token());

        assertThat(dados).isPresent();
        assertThat(dados.get().versaoSessao()).isEqualTo(3);
    }

    @Test
    void tokenSemAVersaoDaSessaoContaComoZero() {
        // tokens emitidos antes deste deploy não têm a claim e continuam valendo
        String antigo = tokenManual().sign(Algorithm.HMAC256(SECRET_TESTE));

        var dados = adapter().validar(antigo);

        assertThat(dados).isPresent();
        assertThat(dados.get().versaoSessao()).isZero();
    }

    @Test
    void recusaVersaoDaSessaoNegativa() {
        String token = tokenManual().withClaim(JwtTokenAdapter.CLAIM_VERSAO_SESSAO, -1)
                .sign(Algorithm.HMAC256(SECRET_TESTE));

        assertThat(adapter().validar(token)).isEmpty();
    }

    @Test
    void recusaVersaoDaSessaoQueNaoEInteiro() {
        String comoTexto = tokenManual().withClaim(JwtTokenAdapter.CLAIM_VERSAO_SESSAO, "1")
                .sign(Algorithm.HMAC256(SECRET_TESTE));
        String decimal = tokenManual().withClaim(JwtTokenAdapter.CLAIM_VERSAO_SESSAO, 1.5)
                .sign(Algorithm.HMAC256(SECRET_TESTE));

        assertThat(adapter().validar(comoTexto)).isEmpty();
        assertThat(adapter().validar(decimal)).isEmpty();
    }
}
