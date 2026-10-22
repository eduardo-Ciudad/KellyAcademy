package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.StatusConversa;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EncerrarConversasJuliaServiceTest {
    private final ConversaJuliaRepositoryEmMemoria conversas = new ConversaJuliaRepositoryEmMemoria();
    private final RelogioAjustavel relogio = RelogioAjustavel.em("2026-10-22T15:00:00Z");
    private final EncerrarConversasJuliaService service = new EncerrarConversasJuliaService(conversas, relogio);

    private final UUID alunoId = UUID.randomUUID();

    @Test
    void encerraTodasAsConversasAtivasDoAlunoNoInstanteDoRelogio() {
        conversas.salvar(ConversaJulia.iniciar(alunoId, UUID.randomUUID(), relogio.instant()));
        conversas.salvar(ConversaJulia.iniciar(alunoId, UUID.randomUUID(), relogio.instant()));
        relogio.avancar(Duration.ofMinutes(30));

        int encerradas = service.encerrar(alunoId);

        assertThat(encerradas).isEqualTo(2);
        assertThat(conversas.todas()).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(StatusConversa.ENCERRADA);
            assertThat(c.getEncerradaEm()).isEqualTo(relogio.instant());
        });
    }

    @Test
    void naoMexeNasConversasDeOutroAluno() {
        var outroAluno = UUID.randomUUID();
        conversas.salvar(ConversaJulia.iniciar(outroAluno, UUID.randomUUID(), relogio.instant()));

        int encerradas = service.encerrar(alunoId);

        assertThat(encerradas).isZero();
        assertThat(conversas.todas().get(0).getStatus()).isEqualTo(StatusConversa.ATIVA);
    }

    @Test
    void semConversaAtivaNaoFalha() {
        assertThat(service.encerrar(alunoId)).isZero();
    }
}
