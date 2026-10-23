package eduar.studymindredacao.domain.exception;

import eduar.studymindredacao.domain.exception.IAOcupadaException.Motivo;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IAOcupadaExceptionTest {

    @Test
    void todaCombinacaoTemMensagemPropriaSemRevelarNumeros() {
        for (RecursoIA recurso : RecursoIA.values()) {
            for (Motivo motivo : Motivo.values()) {
                String mensagem = new IAOcupadaException(recurso, motivo).getMessage();

                // Sem números: não revela quantas vagas existem nem quantas estão em uso
                assertThat(mensagem).as(recurso + "/" + motivo).isNotBlank().doesNotContainPattern("\\d");
            }
        }
    }

    @Test
    void guardaRecursoEMotivoParaOHandlerEscolherAResposta() {
        var e = new IAOcupadaException(RecursoIA.JULIA, Motivo.SISTEMA_OCUPADO);

        assertThat(e.getRecurso()).isEqualTo(RecursoIA.JULIA);
        assertThat(e.getMotivo()).isEqualTo(Motivo.SISTEMA_OCUPADO);
        assertThat(e.getMessage()).contains("Júlia");
    }
}
