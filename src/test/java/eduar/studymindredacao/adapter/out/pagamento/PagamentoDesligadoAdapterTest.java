package eduar.studymindredacao.adapter.out.pagamento;

import eduar.studymindredacao.domain.exception.PagamentoIndisponivelException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoDesligadoAdapterTest {
    private final PagamentoDesligadoAdapter adapter = new PagamentoDesligadoAdapter();

    @Test
    void todoUsoRespondeIndisponivel() {
        assertThatThrownBy(() -> adapter.criarCliente(UUID.randomUUID(), "Ana", "a@x.com"))
                .isInstanceOf(PagamentoIndisponivelException.class);
        assertThatThrownBy(() -> adapter.criarSessaoPortal("cus_abc"))
                .isInstanceOf(PagamentoIndisponivelException.class);
        assertThatThrownBy(() -> adapter.validarEvento("{}", "t=1"))
                .isInstanceOf(PagamentoIndisponivelException.class);
        assertThatThrownBy(() -> adapter.buscarAssinatura("sub_1"))
                .isInstanceOf(PagamentoIndisponivelException.class);
    }
}