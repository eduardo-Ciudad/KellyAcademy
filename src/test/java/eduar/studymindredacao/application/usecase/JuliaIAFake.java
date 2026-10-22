package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.ContextoJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import eduar.studymindredacao.domain.port.JuliaIAPort;

import java.util.ArrayList;
import java.util.List;

/** Júlia de mentira: responde algo fixo (ou falha) e guarda o que recebeu em cada chamada. */
class JuliaIAFake implements JuliaIAPort {
    record Chamada(ContextoJulia contexto, List<MensagemJulia> historico, String pergunta) {
    }

    static final RespostaJulia RESPOSTA_PADRAO = new RespostaJulia("Na C3 você perdeu pontos porque...", 900, 120);

    private final List<Chamada> chamadas = new ArrayList<>();
    private RespostaJulia resposta = RESPOSTA_PADRAO;
    private RuntimeException falha;

    void responderCom(RespostaJulia resposta) {
        this.resposta = resposta;
    }

    void falharCom(RuntimeException falha) {
        this.falha = falha;
    }

    List<Chamada> chamadas() {
        return List.copyOf(chamadas);
    }

    @Override
    public RespostaJulia responder(ContextoJulia contexto, List<MensagemJulia> historico, String pergunta) {
        chamadas.add(new Chamada(contexto, List.copyOf(historico), pergunta));
        if (falha != null) {
            throw falha;
        }
        return resposta;
    }
}
