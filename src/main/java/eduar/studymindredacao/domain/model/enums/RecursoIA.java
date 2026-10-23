package eduar.studymindredacao.domain.model.enums;

public enum RecursoIA {
    CORRECAO("correções"),
    TRANSCRICAO("transcrições"),
    JULIA("perguntas à Júlia");

    private final String rotuloPlural;

    RecursoIA(String rotuloPlural) {
        this.rotuloPlural = rotuloPlural;
    }

    public String rotuloPlural() {
        return rotuloPlural;
    }
}