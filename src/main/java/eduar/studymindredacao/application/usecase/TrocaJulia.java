package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.MensagemJulia;

import java.util.Objects;

/** Resultado de uma pergunta à Júlia: as duas mensagens já salvas (com id) e quantas perguntas ainda cabem. */
public record TrocaJulia(MensagemJulia pergunta, MensagemJulia resposta, int restantesConversa, int restantesHoje) {

    public TrocaJulia {
        Objects.requireNonNull(pergunta, "pergunta é obrigatória");
        Objects.requireNonNull(resposta, "resposta é obrigatória");
    }
}
