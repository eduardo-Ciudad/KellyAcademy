package eduar.studymindredacao.domain.julia;

public class LimiteJuliaExcedidoException extends RuntimeException {

    private final TipoLimiteJulia tipo;

    public LimiteJuliaExcedidoException(TipoLimiteJulia tipo) {
        super(mensagemPara(tipo));
        this.tipo = tipo;
    }

    public TipoLimiteJulia getTipo() {
        return tipo;
    }

    private static String mensagemPara(TipoLimiteJulia tipo) {
        return switch (tipo) {
            case CONVERSA -> "Você chegou ao limite de perguntas desta conversa.";
            case DIARIO_USUARIO -> "Você chegou ao limite de perguntas à Júlia hoje.";
            case GLOBAL -> "A Júlia atingiu o limite de atendimentos de hoje. Tente novamente amanhã.";
        };
    }
}