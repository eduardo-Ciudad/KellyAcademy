package eduar.studymindredacao.domain.model.admin;

import eduar.studymindredacao.domain.model.enums.RecursoIA;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** Números da tela "Visão geral" do painel admin para um período (7, 30 ou 90 dias contando hoje). */
public record VisaoGeralAdmin(
        int periodo,
        OffsetDateTime geradoEm,
        long totalUsuarios,
        long novosNoPeriodo,
        long ativosNoPeriodo,
        long correcoesNoPeriodo,
        Double notaMediaNoPeriodo,
        long assinantesPro,
        List<PontoDiario> serieDiaria,
        List<UsoIA> usoIaHoje,
        long tokensHoje,
        long usuariosNoLimiteHoje,
        List<FaixaNota> distribuicaoNotas,
        List<MediaCompetencia> mediaPorCompetencia,
        List<UsuarioResumoAdmin> ultimosCadastros,
        Alertas alertas
) {
    public VisaoGeralAdmin {
        Objects.requireNonNull(geradoEm, "geradoEm é obrigatório");
        Objects.requireNonNull(alertas, "alertas é obrigatório");
        serieDiaria = List.copyOf(serieDiaria);
        usoIaHoje = List.copyOf(usoIaHoje);
        distribuicaoNotas = List.copyOf(distribuicaoNotas);
        mediaPorCompetencia = List.copyOf(mediaPorCompetencia);
        ultimosCadastros = List.copyOf(ultimosCadastros);
    }

    public record PontoDiario(LocalDate data, long cadastros, long correcoes) {
    }

    public record UsoIA(RecursoIA recurso, int usados, int teto) {
    }

    /** Faixa fechada [min, max] de nota total. */
    public record FaixaNota(int min, int max, long quantidade) {
    }

    /** Média da competência (1 a 5) no período; null sem correções. */
    public record MediaCompetencia(int competencia, Double media) {
    }

    /**
     * @param correcoesComErro24h     redações com status ERRO enviadas nas últimas 24 horas
     * @param contasSemVerificacao48h contas com e-mail não confirmado criadas há mais de 48 horas
     * @param ultimoEventoStripeEm    último webhook do Stripe processado; null se nunca houve
     */
    public record Alertas(long correcoesComErro24h, long contasSemVerificacao48h,
                          OffsetDateTime ultimoEventoStripeEm) {
    }
}
