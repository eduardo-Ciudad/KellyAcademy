package eduar.studymindredacao.domain.exception;

import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;

/**
 * Falha de uma chamada à IA que sabe dizer se o Gemini cobrou (6.5).
 * Interface, não classe base, para não mexer na hierarquia das exceções (a da Júlia fica em outro pacote).
 */
public interface FalhaDeIA {
    CustoFalhaIA custo();
}
