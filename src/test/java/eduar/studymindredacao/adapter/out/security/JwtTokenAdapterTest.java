package eduar.studymindredacao.adapter.out.security;

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
}
