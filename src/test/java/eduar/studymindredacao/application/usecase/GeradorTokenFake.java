package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.TokenEmitido;
import eduar.studymindredacao.domain.port.GeradorTokenPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Tokens previsíveis ("token-1", "token-2"...) com SHA-256 de verdade, para o teste saber qual link saiu. */
class GeradorTokenFake implements GeradorTokenPort {
    private int contador;

    @Override
    public TokenEmitido emitir() {
        String valor = "token-" + (++contador);
        return new TokenEmitido(valor, hash(valor));
    }

    @Override
    public String hash(String valorEmClaro) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valorEmClaro.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
