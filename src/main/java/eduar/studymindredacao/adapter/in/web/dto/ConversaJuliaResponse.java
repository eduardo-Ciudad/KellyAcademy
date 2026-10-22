package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.application.usecase.ConversaJuliaDetalhada;

import java.util.List;

public record ConversaJuliaResponse(List<MensagemJuliaResponse> mensagens, int restantesConversa, int restantesHoje) {
    public static ConversaJuliaResponse de(ConversaJuliaDetalhada conversa) {
        return new ConversaJuliaResponse(
                conversa.mensagens().stream().map(MensagemJuliaResponse::de).toList(),
                conversa.restantesConversa(),
                conversa.restantesHoje()
        );
    }
}
