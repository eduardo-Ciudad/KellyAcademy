package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.julia.ContextoJulia;
import eduar.studymindredacao.domain.julia.JuliaIndisponivelException;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import eduar.studymindredacao.domain.port.JuliaIAPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class GeminiJuliaAdapter implements JuliaIAPort {
    static final String MENSAGEM_INDISPONIVEL = "A Júlia não conseguiu responder agora. Tente novamente em instantes.";

    private final PromptJuliaBuilder promptBuilder;
    private final GeminiClient client;
    private final String modelo;
    private final double temperatura;
    private final String raciocinio;
    private final int maxTokensSaida;

    public GeminiJuliaAdapter(
            PromptJuliaBuilder promptBuilder,
            @Qualifier("geminiClientJulia") GeminiClient client,
            @Value("${julia.gemini.modelo}") String modelo,
            @Value("${julia.gemini.temperatura}") double temperatura,
            @Value("${julia.gemini.raciocinio}") String raciocinio,
            @Value("${julia.gemini.max-tokens-saida}") int maxTokensSaida
    ) {
        this.promptBuilder = promptBuilder;
        this.client = client;
        this.modelo = modelo;
        this.temperatura = temperatura;
        this.raciocinio = raciocinio;
        this.maxTokensSaida = maxTokensSaida;
    }

    @Override
    public RespostaJulia responder(ContextoJulia contexto, List<MensagemJulia> historico, String pergunta) {
        Objects.requireNonNull(contexto, "contexto é obrigatório");

        List<GeminiTurno> turnos = new ArrayList<>();
        for (MensagemJulia mensagem : historico == null ? List.<MensagemJulia>of() : historico) {
            turnos.add(mensagem.ehDoUsuario()
                    ? GeminiTurno.doUsuario(mensagem.conteudo())
                    : GeminiTurno.doModelo(mensagem.conteudo()));
        }
        turnos.add(GeminiTurno.doUsuario(pergunta));
        var conversa = new GeminiConversa(promptBuilder.construir(contexto), turnos);

        try {
            return client.conversar(modelo, temperatura, raciocinio, maxTokensSaida, conversa,
                    resposta -> new RespostaJulia(
                            resposta.textoGerado(), resposta.tokensEntrada(), resposta.tokensSaida()));
        } catch (GeminiException e) {
            throw new JuliaIndisponivelException(MENSAGEM_INDISPONIVEL, e, e.custo());
        }
    }
}
