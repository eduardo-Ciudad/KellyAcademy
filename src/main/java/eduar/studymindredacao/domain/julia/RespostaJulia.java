package eduar.studymindredacao.domain.julia;

public record RespostaJulia(String texto, int tokensEntrada, int tokensSaida) {

    public RespostaJulia {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("o texto da resposta é obrigatório");
        }
        if (tokensEntrada < 0 || tokensSaida < 0) {
            throw new IllegalArgumentException("tokens não podem ser negativos");
        }
        texto = texto.strip();
    }
}
