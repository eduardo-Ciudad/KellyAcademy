package eduar.studymindredacao.domain.exception;

import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;

/** A IA não entregou uma correção utilizável. Sem custo informado, assume INCERTA (o lado seguro). */
public class AvaliacaoIAException extends RuntimeException implements FalhaDeIA {
    private final CustoFalhaIA custo;

    public AvaliacaoIAException(String mensagem) {
        this(mensagem, null, CustoFalhaIA.INCERTA);
    }

    public AvaliacaoIAException(String mensagem, Throwable causa) {
        this(mensagem, causa, CustoFalhaIA.INCERTA);
    }

    public AvaliacaoIAException(String mensagem, Throwable causa, CustoFalhaIA custo) {
        super(mensagem, causa);
        this.custo = custo == null ? CustoFalhaIA.INCERTA : custo;
    }

    @Override
    public CustoFalhaIA custo() {
        return custo;
    }
}
