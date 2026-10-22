package eduar.studymindredacao.adapter.out.ia;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiConversaTest {

    @Test
    void aceitaConversaAlternadaQueComecaETerminaPeloUsuario() {
        assertThatCode(() -> new GeminiConversa("regras", List.of(
                GeminiTurno.doUsuario("p1"), GeminiTurno.doModelo("r1"), GeminiTurno.doUsuario("p2")
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejeitaSemTurnos() {
        assertThatThrownBy(() -> new GeminiConversa("regras", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaInstrucaoDeSistemaVazia() {
        assertThatThrownBy(() -> new GeminiConversa("  ", List.of(GeminiTurno.doUsuario("p1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaConversaQueComecaPeloModelo() {
        assertThatThrownBy(() -> new GeminiConversa("regras", List.of(
                GeminiTurno.doModelo("r1"), GeminiTurno.doUsuario("p1")
        ))).hasMessageContaining("começar pelo usuário");
    }

    @Test
    void rejeitaConversaQueTerminaPeloModelo() {
        assertThatThrownBy(() -> new GeminiConversa("regras", List.of(
                GeminiTurno.doUsuario("p1"), GeminiTurno.doModelo("r1")
        ))).hasMessageContaining("última mensagem");
    }

    @Test
    void rejeitaDoisTurnosSeguidosDoMesmoPapel() {
        assertThatThrownBy(() -> new GeminiConversa("regras", List.of(
                GeminiTurno.doUsuario("p1"), GeminiTurno.doUsuario("p2")
        ))).hasMessageContaining("alternar");
    }

    @Test
    void turnosNaoPodemSerAlteradosPorFora() {
        var turnos = new ArrayList<>(List.of(GeminiTurno.doUsuario("p1")));
        var conversa = new GeminiConversa("regras", turnos);

        turnos.add(GeminiTurno.doModelo("r1"));

        assertThat(conversa.turnos()).hasSize(1);
    }

    @Test
    void turnoComTextoVazioERejeitado() {
        assertThatThrownBy(() -> GeminiTurno.doUsuario(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
