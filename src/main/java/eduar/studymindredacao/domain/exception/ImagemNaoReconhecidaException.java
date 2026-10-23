package eduar.studymindredacao.domain.exception;

import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;

/**
 * O Gemini leu a foto e não encontrou uma redação. Sempre COBRADA: esta exceção só existe quando o Gemini
 * respondeu, e é exatamente a falha que alguém pode provocar de propósito com qualquer foto (6.5).
 */
public class ImagemNaoReconhecidaException extends RuntimeException implements FalhaDeIA {
    public static final String MENSAGEM =
            "Não encontramos uma redação manuscrita legível nesta foto. "
                    + "Tire outra com a folha inteira no quadro, boa luz e sem sombra.";

    public ImagemNaoReconhecidaException() {
        super(MENSAGEM);
    }

    @Override
    public CustoFalhaIA custo() {
        return CustoFalhaIA.COBRADA;
    }
}