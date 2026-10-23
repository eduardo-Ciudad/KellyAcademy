package eduar.studymindredacao.domain.exception;

import eduar.studymindredacao.domain.model.enums.RecursoIA;

/**
 * Chamada à IA recusada pelo limite de concorrência (KELLY-SEC-002 e SEC-003), antes de gastar cota.
 * A mensagem é neutra: não diz quantas vagas o sistema tem nem quantas estão em uso,
 * para não ajudar quem tenta medir quantas requisições precisa disparar para lotar o sistema.
 */
public class IAOcupadaException extends RuntimeException {

    public enum Motivo {
        /** O aluno já tem uma chamada deste recurso em andamento. */
        ALUNO_EM_ANDAMENTO,
        /** Todas as vagas simultâneas do recurso estão em uso. */
        SISTEMA_OCUPADO
    }

    private final RecursoIA recurso;
    private final Motivo motivo;

    public IAOcupadaException(RecursoIA recurso, Motivo motivo) {
        super(mensagem(recurso, motivo));
        this.recurso = recurso;
        this.motivo = motivo;
    }

    public RecursoIA getRecurso() {
        return recurso;
    }

    public Motivo getMotivo() {
        return motivo;
    }

    private static String mensagem(RecursoIA recurso, Motivo motivo) {
        return switch (motivo) {
            case ALUNO_EM_ANDAMENTO -> switch (recurso) {
                case CORRECAO -> "Você já tem uma correção em andamento. Aguarde ela terminar.";
                case TRANSCRICAO -> "Você já tem uma transcrição em andamento. Aguarde ela terminar.";
                case JULIA -> "A Júlia ainda está respondendo sua pergunta anterior. Aguarde a resposta.";
            };
            case SISTEMA_OCUPADO -> switch (recurso) {
                case CORRECAO -> "A correção está ocupada agora. Tente novamente em alguns segundos.";
                case TRANSCRICAO -> "A transcrição está ocupada agora. Tente novamente em alguns segundos.";
                case JULIA -> "A Júlia está ocupada agora. Tente novamente em alguns segundos.";
            };
        };
    }
}
