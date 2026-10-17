package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.TokenEmitido;

public interface GeradorTokenPort {
    /** Gera um token aleatório imprevisível, já com o hash que vai para o banco. */
    TokenEmitido emitir();

    /** Hash SHA-256 (64 caracteres hex minúsculos) do valor recebido no link, para buscar no banco. */
    String hash(String valorEmClaro);
}
