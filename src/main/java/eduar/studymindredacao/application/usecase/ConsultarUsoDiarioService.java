package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.UsoIADiario;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lê os mesmos contadores que o EnviarRedacaoService e o TranscreverRedacaoService usam para reservar vagas,
 * e o mesmo limite do plano, então o saldo mostrado ao aluno nunca diverge do que dispara o 429.
 * Correções e transcrições em andamento já contam como usadas (a vaga foi reservada).
 */
@Service
public class ConsultarUsoDiarioService {
    private final UsoIADiarioRepositoryPort usoRepository;
    private final ConsultarPlanoService consultarPlano;
    private final Clock clock;

    public ConsultarUsoDiarioService(
            UsoIADiarioRepositoryPort usoRepository,
            ConsultarPlanoService consultarPlano,
            Clock clock
    ) {
        this.usoRepository = usoRepository;
        this.consultarPlano = consultarPlano;
        this.clock = clock;
    }

    public UsoDiario consultar(UUID usuarioId) {
        return saldo(usuarioId, UsoIADiario::qtdCorrecoes, RecursoIA.CORRECAO);
    }

    /** Saldo de transcrições de foto: cota própria, separada das correções (#16). */
    public UsoDiario consultarTranscricoes(UUID usuarioId) {
        return saldo(usuarioId, UsoIADiario::qtdTranscricoes, RecursoIA.TRANSCRICAO);
    }

    private UsoDiario saldo(UUID usuarioId, Function<UsoIADiario, Integer> contador, RecursoIA recurso) {
        int limite = consultarPlano.doUsuario(usuarioId).limiteDiario(recurso);
        LocalDate hoje = LocalDate.now(clock);
        int usadas = usoRepository.buscarPorUsuarioIdEData(usuarioId, hoje)
                .map(contador)
                .orElse(0);
        int restantes = Math.max(limite - usadas, 0);
        var renovaEm = hoje.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
        return new UsoDiario(usadas, limite, restantes, renovaEm);
    }
}
