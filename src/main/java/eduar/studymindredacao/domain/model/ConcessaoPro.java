package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.exception.ConcessaoProConflitoException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Pro concedido à mão por um admin, sem passar pelo Stripe. Nunca é apagado: revogar só marca quem, quando e por
 * quê, e a tabela serve de trilha de auditoria.
 * <ul>
 *   <li>{@code fim} é sempre obrigatório: não existe Pro eterno por concessão;</li>
 *   <li>{@code revogadaEm}, {@code revogadaPor} e {@code motivoRevogacao} andam juntos (todos nulos ou todos
 *       preenchidos), igual à constraint ck_concessoes_pro_revogacao da V25.</li>
 * </ul>
 */
public record ConcessaoPro(
        UUID id,
        UUID usuarioId,
        UUID concedidaPor,
        String motivo,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        OffsetDateTime revogadaEm,
        UUID revogadaPor,
        String motivoRevogacao
) {
    public static final int DIAS_MINIMO = 1;
    public static final int DIAS_MAXIMO = 365;
    public static final int MOTIVO_MINIMO = 5;
    public static final int MOTIVO_MAXIMO = 300;
    /** Motivo gravado quando uma concessão vencida e nunca revogada é fechada para abrir a próxima. */
    static final String MOTIVO_EXPIRADA = "Expirada";

    public ConcessaoPro {
        Validacoes.requererNaoNulo(usuarioId, "usuarioId");
        Validacoes.requererNaoNulo(concedidaPor, "concedidaPor");
        motivo = validarMotivo(motivo, "motivo");
        Validacoes.requererNaoNulo(inicio, "inicio");
        Validacoes.requererNaoNulo(fim, "fim");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser depois de inicio");
        }

        boolean algumaRevogacao = revogadaEm != null || revogadaPor != null || motivoRevogacao != null;
        boolean revogacaoCompleta = revogadaEm != null && revogadaPor != null && motivoRevogacao != null;
        if (algumaRevogacao && !revogacaoCompleta) {
            throw new IllegalArgumentException("revogadaEm, revogadaPor e motivoRevogacao devem vir juntos");
        }
        if (revogacaoCompleta) {
            motivoRevogacao = validarMotivo(motivoRevogacao, "motivoRevogacao");
            if (revogadaEm.isBefore(inicio)) {
                throw new IllegalArgumentException("revogadaEm não pode ser antes de inicio");
            }
        }
    }

    /**
     * Nova concessão a partir de agora. Vale até o fim do dia (hoje + dias) no fuso informado: 30 dias concedidos
     * hoje valem até 23h59 do dia hoje+30, então o resto de hoje não come um dos dias.
     */
    public static ConcessaoPro conceder(
            UUID usuarioId,
            UUID concedidaPor,
            String motivo,
            int dias,
            OffsetDateTime agora,
            ZoneId fuso
    ) {
        Validacoes.requererNaoNulo(agora, "agora");
        Validacoes.requererNaoNulo(fuso, "fuso");
        if (dias < DIAS_MINIMO || dias > DIAS_MAXIMO) {
            throw new IllegalArgumentException("dias deve estar entre " + DIAS_MINIMO + " e " + DIAS_MAXIMO);
        }
        LocalDate hoje = agora.atZoneSameInstant(fuso).toLocalDate();
        OffsetDateTime fim = hoje.plusDays(dias + 1L).atStartOfDay(fuso).toOffsetDateTime();
        return new ConcessaoPro(null, usuarioId, concedidaPor, motivo, agora, fim, null, null, null);
    }

    public boolean revogada() {
        return revogadaEm != null;
    }

    /** Dá acesso Pro agora: não revogada, já começou e ainda não terminou (o instante do fim já não vale). */
    public boolean vigente(OffsetDateTime agora) {
        Validacoes.requererNaoNulo(agora, "agora");
        return !revogada() && !inicio.isAfter(agora) && fim.isAfter(agora);
    }

    /** Não revogada, mas o prazo já passou: ainda ocupa a vaga de "concessão aberta" no índice único. */
    public boolean vencida(OffsetDateTime agora) {
        Validacoes.requererNaoNulo(agora, "agora");
        return !revogada() && !fim.isAfter(agora);
    }

    /** Revogação feita por um admin. Revogar de novo é conflito, não é ignorado em silêncio. */
    public ConcessaoPro revogar(UUID adminId, String motivo, OffsetDateTime quando) {
        Validacoes.requererNaoNulo(adminId, "adminId");
        Validacoes.requererNaoNulo(quando, "quando");
        if (revogada()) {
            throw new ConcessaoProConflitoException("Esta concessão de Pro já foi revogada.");
        }
        return new ConcessaoPro(id, usuarioId, concedidaPor, this.motivo, inicio, fim, quando, adminId, motivo);
    }

    /**
     * Fecha uma concessão que venceu sem ser revogada, para liberar a vaga da próxima. Quem fecha é o admin que
     * está concedendo a nova (a V25 exige revogada_por), com o motivo "Expirada".
     */
    public ConcessaoPro fecharVencida(UUID adminId, OffsetDateTime quando) {
        Validacoes.requererNaoNulo(quando, "quando");
        if (!vencida(quando)) {
            throw new ConcessaoProConflitoException("Só uma concessão vencida e não revogada pode ser fechada.");
        }
        return revogar(adminId, MOTIVO_EXPIRADA, quando);
    }

    private static String validarMotivo(String valor, String campo) {
        Validacoes.validarTextoObrigatorio(valor, campo, null);
        String limpo = valor.trim();
        if (limpo.length() < MOTIVO_MINIMO || limpo.length() > MOTIVO_MAXIMO) {
            throw new IllegalArgumentException(
                    campo + " deve ter entre " + MOTIVO_MINIMO + " e " + MOTIVO_MAXIMO + " caracteres");
        }
        return limpo;
    }

    /** Sem os motivos: são texto livre e não precisam ir para o log. */
    @Override
    public String toString() {
        return "ConcessaoPro[id=" + id + ", usuarioId=" + usuarioId + ", concedidaPor=" + concedidaPor
                + ", inicio=" + inicio + ", fim=" + fim + ", revogadaEm=" + revogadaEm
                + ", revogadaPor=" + revogadaPor + "]";
    }
}
