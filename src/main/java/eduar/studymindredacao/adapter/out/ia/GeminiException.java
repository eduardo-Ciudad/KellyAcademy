package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;

/**
 * Falha na chamada ao Gemini: se vale repetir e se a chamada foi cobrada (6.5).
 * O custo final é preenchido pelo {@link GeminiClient} olhando TODAS as tentativas ({@link #comCusto});
 * até lá vale INCERTA, o lado seguro.
 */
class GeminiException extends RuntimeException {
    private final boolean podeRepetir;
    private final CustoFalhaIA custo;

    private GeminiException(String mensagem, boolean podeRepetir, Throwable causa, CustoFalhaIA custo) {
        super(mensagem, causa);
        this.podeRepetir = podeRepetir;
        this.custo = custo;
    }

    static GeminiException repetivel(String mensagem) {
        return new GeminiException(mensagem, true, null, CustoFalhaIA.INCERTA);
    }

    static GeminiException repetivel(String mensagem, Throwable causa) {
        return new GeminiException(mensagem, true, causa, CustoFalhaIA.INCERTA);
    }

    static GeminiException definitiva(String mensagem, Throwable causa) {
        return new GeminiException(mensagem, false, causa, CustoFalhaIA.INCERTA);
    }

    /** Cópia com o custo final, mesma mensagem, causa e rastro. */
    GeminiException comCusto(CustoFalhaIA custoFinal) {
        var copia = new GeminiException(getMessage(), podeRepetir, getCause(), custoFinal);
        copia.setStackTrace(getStackTrace());
        return copia;
    }

    boolean podeRepetir() {
        return podeRepetir;
    }

    CustoFalhaIA custo() {
        return custo;
    }
}
