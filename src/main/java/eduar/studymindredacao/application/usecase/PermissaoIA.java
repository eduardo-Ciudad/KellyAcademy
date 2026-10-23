package eduar.studymindredacao.application.usecase;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vaga de chamada simultânea à IA, obtida no {@link LimitadorConcorrenciaIA}. Use com try-with-resources,
 * para a vaga voltar em qualquer saída (retorno, exceção da IA, timeout, erro ao salvar).
 *
 * <p>{@link #close()} é idempotente: devolver duas vezes daria ao semáforo uma vaga que nunca foi tirada,
 * e o limite de concorrência cresceria para sempre, sem nenhum contador no banco mostrar.
 */
public final class PermissaoIA implements AutoCloseable {
    private final Runnable devolver;
    private final AtomicBoolean fechada = new AtomicBoolean(false);

    PermissaoIA(Runnable devolver) {
        this.devolver = devolver;
    }

    @Override
    public void close() {
        if (fechada.compareAndSet(false, true)) {
            devolver.run();
        }
    }
}
