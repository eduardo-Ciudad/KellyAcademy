package eduar.studymindredacao.adapter.out.security;

import eduar.studymindredacao.domain.model.TokenEmitido;
import eduar.studymindredacao.domain.port.GeradorTokenPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Token dos links enviados por e-mail: 32 bytes de {@link SecureRandom} (256 bits) em Base64URL sem "=",
 * seguro para ir na URL. No banco vai só o SHA-256: como o valor já é aleatório e longo, não precisa de BCrypt,
 * e o hash determinístico permite buscar o token por índice.
 */
@Component
public class SecureRandomTokenAdapter implements GeradorTokenPort {
    static final int BYTES_ALEATORIOS = 32;
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random;

    public SecureRandomTokenAdapter() {
        this(new SecureRandom());
    }

    SecureRandomTokenAdapter(SecureRandom random) {
        this.random = random;
    }

    @Override
    public TokenEmitido emitir() {
        byte[] bytes = new byte[BYTES_ALEATORIOS];
        random.nextBytes(bytes);
        String valor = BASE64_URL.encodeToString(bytes);
        return new TokenEmitido(valor, hash(valor));
    }

    @Override
    public String hash(String valorEmClaro) {
        Objects.requireNonNull(valorEmClaro, "valorEmClaro é obrigatório");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valorEmClaro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM é obrigada a ter SHA-256; se faltar, é problema do ambiente, não do token
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
