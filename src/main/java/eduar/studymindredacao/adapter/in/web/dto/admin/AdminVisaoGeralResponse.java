package eduar.studymindredacao.adapter.in.web.dto.admin;

import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin;
import eduar.studymindredacao.domain.model.enums.RecursoIA;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formato do AdminVisaoGeral do front. Sem receita por enquanto (decisão de out/2026): o front esconde esse card.
 */
public record AdminVisaoGeralResponse(
        int periodo,
        OffsetDateTime geradoEm,
        long totalUsuarios,
        long novosNoPeriodo,
        long ativosNoPeriodo,
        long correcoesNoPeriodo,
        Integer notaMediaNoPeriodo,
        long assinantesPro,
        List<PontoDiario> serieDiaria,
        List<UsoIA> usoIaHoje,
        long tokensHoje,
        long usuariosNoLimiteHoje,
        List<FaixaNota> distribuicaoNotas,
        List<MediaCompetencia> mediaPorCompetencia,
        List<AdminUsuarioResumoResponse> ultimosCadastros,
        Alertas alertas
) {
    public record PontoDiario(LocalDate data, long cadastros, long correcoes) {
    }

    public record UsoIA(RecursoIA recurso, int usados, int teto) {
    }

    public record FaixaNota(int min, int max, long quantidade) {
    }

    public record MediaCompetencia(int competencia, Double media) {
    }

    public record Alertas(long correcoesComErro24h, long contasSemVerificacao48h, OffsetDateTime ultimoEventoStripeEm) {
    }

    public static AdminVisaoGeralResponse de(VisaoGeralAdmin v) {
        return new AdminVisaoGeralResponse(
                v.periodo(),
                v.geradoEm(),
                v.totalUsuarios(),
                v.novosNoPeriodo(),
                v.ativosNoPeriodo(),
                v.correcoesNoPeriodo(),
                Arredondamento.inteiro(v.notaMediaNoPeriodo()),
                v.assinantesPro(),
                v.serieDiaria().stream().map(p -> new PontoDiario(p.data(), p.cadastros(), p.correcoes())).toList(),
                v.usoIaHoje().stream().map(u -> new UsoIA(u.recurso(), u.usados(), u.teto())).toList(),
                v.tokensHoje(),
                v.usuariosNoLimiteHoje(),
                v.distribuicaoNotas().stream().map(f -> new FaixaNota(f.min(), f.max(), f.quantidade())).toList(),
                v.mediaPorCompetencia().stream()
                        .map(m -> new MediaCompetencia(m.competencia(), Arredondamento.umaCasa(m.media())))
                        .toList(),
                v.ultimosCadastros().stream().map(AdminUsuarioResumoResponse::de).toList(),
                new Alertas(v.alertas().correcoesComErro24h(), v.alertas().contasSemVerificacao48h(),
                        v.alertas().ultimoEventoStripeEm())
        );
    }
}
