package eduar.studymindredacao.adapter.out.ia;

import java.util.List;

/**
 * Instrução de sistema + turnos de uma conversa.
 * A API exige começar e terminar pelo usuário, alternando com o modelo.
 */
record GeminiConversa(String instrucaoSistema, List<GeminiTurno> turnos) {

    GeminiConversa {
        if (instrucaoSistema == null || instrucaoSistema.isBlank()) {
            throw new IllegalArgumentException("instrução de sistema é obrigatória");
        }
        if (turnos == null || turnos.isEmpty()) {
            throw new IllegalArgumentException("a conversa precisa de ao menos um turno");
        }
        turnos = List.copyOf(turnos);
        if (turnos.get(0).papel() != GeminiTurno.Papel.USUARIO) {
            throw new IllegalArgumentException("a conversa deve começar pelo usuário");
        }
        if (turnos.get(turnos.size() - 1).papel() != GeminiTurno.Papel.USUARIO) {
            throw new IllegalArgumentException("a última mensagem deve ser do usuário");
        }
        for (int i = 1; i < turnos.size(); i++) {
            if (turnos.get(i).papel() == turnos.get(i - 1).papel()) {
                throw new IllegalArgumentException("os papéis da conversa devem se alternar");
            }
        }
    }
}
