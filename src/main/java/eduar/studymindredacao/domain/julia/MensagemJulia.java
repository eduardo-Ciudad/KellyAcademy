package eduar.studymindredacao.domain.julia;

import java.time.Instant;
import java.util.Objects;

public record MensagemJulia(
        Long id,
        PapelMensagem papel,
        String conteudo,
        Integer tokensEntrada,
        Integer tokensSaida,
        Instant criadaEm
) {
    public MensagemJulia {
        Objects.requireNonNull(papel, "papel é obrigatório");
        Objects.requireNonNull(criadaEm, "criadaEm é obrigatório");
        if (conteudo == null || conteudo.isBlank()) {
            throw new IllegalArgumentException("o conteúdo da mensagem é obrigatório");
        }
        validarTokens(tokensEntrada);
        validarTokens(tokensSaida);
    }

    public static MensagemJulia doUsuario(String texto, int tamanhoMax, Instant agora) {
        if (texto == null || texto.isBlank()) {
            throw new PerguntaInvalidaException("A pergunta não pode ficar vazia.");
        }
        String pergunta = texto.strip();
        if (pergunta.length() > tamanhoMax) {
            throw new PerguntaInvalidaException(
                    "A pergunta pode ter no máximo " + tamanhoMax + " caracteres.");
        }
        return new MensagemJulia(null, PapelMensagem.USUARIO, pergunta, null, null, agora);
    }

    public static MensagemJulia daJulia(RespostaJulia resposta, Instant agora) {
        Objects.requireNonNull(resposta, "resposta é obrigatória");
        return new MensagemJulia(
                null,
                PapelMensagem.JULIA,
                resposta.texto(),
                resposta.tokensEntrada(),
                resposta.tokensSaida(),
                agora
        );
    }

    public boolean ehDoUsuario() {
        return papel == PapelMensagem.USUARIO;
    }

    private static void validarTokens(Integer tokens) {
        if (tokens != null && tokens < 0) {
            throw new IllegalArgumentException("tokens não podem ser negativos");
        }
    }
}