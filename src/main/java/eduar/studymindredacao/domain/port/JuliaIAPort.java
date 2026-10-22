package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.julia.ContextoJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.RespostaJulia;

import java.util.List;

public interface JuliaIAPort {


    RespostaJulia responder(ContextoJulia contexto, List<MensagemJulia> historico, String pergunta);
}
