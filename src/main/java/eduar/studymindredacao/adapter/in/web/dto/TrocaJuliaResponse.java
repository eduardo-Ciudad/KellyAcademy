package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.application.usecase.TrocaJulia;

public record TrocaJuliaResponse(MensagemJuliaResponse pergunta, MensagemJuliaResponse resposta,
                                 int restantesConversa, int restantesHoje) {
    public static TrocaJuliaResponse de(TrocaJulia troca) {
        return new TrocaJuliaResponse(
                MensagemJuliaResponse.de(troca.pergunta()),
                MensagemJuliaResponse.de(troca.resposta()),
                troca.restantesConversa(),
                troca.restantesHoje()
        );
    }
}
