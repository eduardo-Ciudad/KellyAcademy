package eduar.studymindredacao.adapter.out.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.TokenGerado;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.port.TokenPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class JwtTokenAdapter implements TokenPort {
    static final int TAMANHO_MINIMO_SECRET = 32;
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    /** Versão da sessão do usuário quando o token foi emitido. Ver ValidarSessaoService. */
    static final String CLAIM_VERSAO_SESSAO = "sv";
    private static final Pattern VERSAO_VALIDA = Pattern.compile("^[0-9]{1,9}$");

    private final Algorithm algoritmo;
    private final JWTVerifier verificador;
    private final String issuer;
    private final Duration expiracao;
    private final Clock clock;

    @Autowired
    public JwtTokenAdapter(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.expiracao-minutos}") long expiracaoMinutos
    ) {
        this(secret, issuer, Duration.ofMinutes(expiracaoMinutos), Clock.systemUTC());
    }

    JwtTokenAdapter(String secret, String issuer, Duration expiracao, Clock clock) {
        if (secret == null || secret.length() < TAMANHO_MINIMO_SECRET) {
            throw new IllegalStateException(
                    "security.jwt.secret (JWT_SECRET) deve ter pelo menos " + TAMANHO_MINIMO_SECRET + " caracteres"
            );
        }
        this.algoritmo = Algorithm.HMAC256(secret);
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.expiracao = Objects.requireNonNull(expiracao, "expiracao");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.verificador = JWT.require(algoritmo).withIssuer(issuer).build();
    }

    @Override
    public TokenGerado gerar(Usuario usuario) {
        Objects.requireNonNull(usuario.id(), "usuario precisa estar persistido para gerar token");
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(expiracao);
        String token = JWT.create()
                .withIssuer(issuer)
                .withSubject(usuario.id().toString())
                .withClaim(CLAIM_EMAIL, usuario.email())
                .withClaim(CLAIM_ROLE, usuario.role().name())
                .withClaim(CLAIM_VERSAO_SESSAO, usuario.versaoSessao())
                .withIssuedAt(agora)
                .withExpiresAt(expiraEm)
                .sign(algoritmo);
        return new TokenGerado(token, expiraEm);
    }

    @Override
    public Optional<DadosToken> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            DecodedJWT jwt = verificador.verify(token);
            Optional<Integer> versaoSessao = lerVersaoSessao(jwt.getClaim(CLAIM_VERSAO_SESSAO));
            if (versaoSessao.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new DadosToken(
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaim(CLAIM_EMAIL).asString(),
                    Role.valueOf(jwt.getClaim(CLAIM_ROLE).asString()),
                    versaoSessao.get()
            ));
        } catch (JWTVerificationException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    /**
     * Sem a claim: token emitido antes da versão de sessão existir, conta como 0 (ninguém é deslogado no deploy).
     * Presente, só vale inteiro maior ou igual a zero; qualquer outra coisa recusa o token.
     */
    private static Optional<Integer> lerVersaoSessao(Claim claim) {
        if (claim == null || claim.isMissing()) {
            return Optional.of(0);
        }
        // Confere o JSON bruto: asInt() aceitaria 1.5 truncando para 1. Só dígitos (até 9, cabe em int).
        String bruto = claim.toString();
        return bruto != null && VERSAO_VALIDA.matcher(bruto).matches()
                ? Optional.of(Integer.parseInt(bruto))
                : Optional.empty();
    }
}
