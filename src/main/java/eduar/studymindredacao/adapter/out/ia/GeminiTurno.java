package eduar.studymindredacao.adapter.out.ia;

import java.util.Objects;

/** Uma mensagem de uma conversa com o Gemini. */
record GeminiTurno(Papel papel, String texto) {

    enum Papel {
        USUARIO("user"),
        MODELO("model");

        private final String valorApi;

        Papel(String valorApi) {
            this.valorApi = valorApi;
        }

        String valorApi() {
            return valorApi;
        }
    }

    GeminiTurno {
        Objects.requireNonNull(papel, "papel é obrigatório");
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("texto do turno é obrigatório");
        }
    }

    static GeminiTurno doUsuario(String texto) {
        return new GeminiTurno(Papel.USUARIO, texto);
    }

    static GeminiTurno doModelo(String texto) {
        return new GeminiTurno(Papel.MODELO, texto);
    }
}
