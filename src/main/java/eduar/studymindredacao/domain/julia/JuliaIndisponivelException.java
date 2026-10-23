package eduar.studymindredacao.domain.julia;

import eduar.studymindredacao.domain.exception.FalhaDeIA;
import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;

/** A Júlia não conseguiu responder. Sem custo informado, assume INCERTA (o lado seguro). */
public class JuliaIndisponivelException extends RuntimeException implements FalhaDeIA {
    private final CustoFalhaIA custo;

    public JuliaIndisponivelException(String mensagem) {
        this(mensagem, null, CustoFalhaIA.INCERTA);
    }

    public JuliaIndisponivelException(String mensagem, Throwable causa) {
        this(mensagem, causa, CustoFalhaIA.INCERTA);
    }

    public JuliaIndisponivelException(String mensagem, Throwable causa, CustoFalhaIA custo) {
        super(mensagem, causa);
        this.custo = custo == null ? CustoFalhaIA.INCERTA : custo;
    }

    @Override
    public CustoFalhaIA custo() {
        return custo;
    }
}
