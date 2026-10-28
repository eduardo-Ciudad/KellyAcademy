package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.LimiteTemasPersonalizadosException;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cria o tema que o aluno digitou. Se ele já tem um tema com o mesmo título (sem diferenciar maiúsculas),
 * devolve o existente em vez de criar outro, e isso não gasta o limite do dia.
 * Liberado em todos os planos por enquanto: quem limita o gasto de IA é a cota de correção.
 */
@Service
public class CriarTemaPersonalizadoService {
    private final TemaRepositoryPort temaRepository;
    private final Clock clock;
    private final int limiteDiario;

    public CriarTemaPersonalizadoService(
            TemaRepositoryPort temaRepository,
            Clock clock,
            @Value("${app.limites.temas-personalizados-diarios}") int limiteDiario
    ) {
        this.temaRepository = temaRepository;
        this.clock = clock;
        this.limiteDiario = limiteDiario;
    }

    public Resultado criar(UUID usuarioId, String titulo) {
        // Valida e normaliza antes de qualquer consulta
        Tema novo = Tema.personalizado(titulo, usuarioId);

        var existente = temaRepository.buscarPersonalizadoPorTitulo(usuarioId, novo.titulo());
        if (existente.isPresent()) {
            return new Resultado(existente.get(), false);
        }

        // "Hoje" começa à meia-noite no fuso da aplicação (o Clock já vem nele)
        OffsetDateTime inicioDoDia = LocalDate.now(clock).atStartOfDay(clock.getZone()).toOffsetDateTime();
        if (temaRepository.contarPersonalizadosCriadosDesde(usuarioId, inicioDoDia) >= limiteDiario) {
            throw new LimiteTemasPersonalizadosException(limiteDiario);
        }

        try {
            return new Resultado(temaRepository.salvar(novo), true);
        } catch (DataIntegrityViolationException corrida) {
            // Duplo clique: outra requisição criou o mesmo título entre a busca e o salvar
            return temaRepository.buscarPersonalizadoPorTitulo(usuarioId, novo.titulo())
                    .map(tema -> new Resultado(tema, false))
                    .orElseThrow(() -> corrida);
        }
    }

    /** criado = false quando o tema já existia (o controller responde 200 em vez de 201). */
    public record Resultado(Tema tema, boolean criado) {
    }
}
