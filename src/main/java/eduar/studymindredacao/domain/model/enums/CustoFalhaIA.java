package eduar.studymindredacao.domain.model.enums;

/**
 * Se uma chamada à IA que falhou foi cobrada pelo Gemini (6.5). Decide se as vagas voltam:
 * vaga devolvida por falha que custou é cota grátis, e uma falha provocada de propósito
 * (foto que não é redação, texto que quebra a saída) viraria uso ilimitado do Gemini.
 */
public enum CustoFalhaIA {
    /** Não chegou ao Gemini, ou ele recusou antes de processar (falha de conexão, 4xx, 5xx). */
    NAO_COBRADA,
    /** Pode ter sido cobrada: timeout de leitura ou conexão cortada no meio da resposta. */
    INCERTA,
    /** O Gemini respondeu (HTTP 200) em alguma tentativa: o custo aconteceu. */
    COBRADA
}
