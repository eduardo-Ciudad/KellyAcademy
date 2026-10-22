package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.MensagemJulia;

import java.util.List;

/** O que o aluno vê ao abrir a Júlia numa redação: a conversa ativa (ou vazia) e quantas perguntas ainda cabem. */
public record ConversaJuliaDetalhada(List<MensagemJulia> mensagens, int restantesConversa, int restantesHoje) {

    public ConversaJuliaDetalhada {
        mensagens = mensagens == null ? List.of() : List.copyOf(mensagens);
    }
}
