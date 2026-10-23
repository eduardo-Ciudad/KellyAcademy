package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.UsoIADiario;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Uso de IA de UM aluno por dia. O teto do sistema fica no {@link UsoIAGlobalRepositoryPort}.
 * Não há "soma do dia" aqui de propósito (KELLY-SEC-002): somar e depois reservar, em duas operações,
 * deixava requisições simultâneas passarem do teto. Reservas passam pelo ReservadorCotaIA.
 */
public interface UsoIADiarioRepositoryPort {
    UsoIADiario salvar(UsoIADiario usoIADiario);

    Optional<UsoIADiario> buscarPorId(UUID id);

    Optional<UsoIADiario> buscarPorUsuarioIdEData(UUID usuarioId, LocalDate data);

    void excluirPorId(UUID id);

    boolean reservarCorrecao(UUID usuarioId, LocalDate data, int limite);

    void liberarCorrecao(UUID usuarioId, LocalDate data);

    boolean reservarTranscricao(UUID usuarioId, LocalDate data, int limite);

    void liberarTranscricao(UUID usuarioId, LocalDate data);

    void registrarTokens(UUID usuarioId, LocalDate data, int tokensEntrada, int tokensSaida);

    boolean reservarMensagemJulia(UUID usuarioId, LocalDate data, int limite);

    void liberarMensagemJulia(UUID usuarioId, LocalDate data);

    /** Perguntas à Júlia feitas pelo aluno no dia (0 se ainda não há linha), para mostrar quantas restam. */
    int contarMensagensJulia(UUID usuarioId, LocalDate data);
}