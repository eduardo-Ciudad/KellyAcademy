package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.admin.UsuarioResumoAdmin;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.PainelAdminQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * SQL nativo do painel admin. Só leitura; todos os valores entram por parâmetro (nada concatenado do usuário).
 * Os tipos que o driver devolve variam (Long/BigInteger, BigDecimal, Timestamp/OffsetDateTime/Instant), então tudo
 * passa pelos conversores no fim da classe.
 */
@Repository
@Transactional(readOnly = true)
public class PainelAdminQueryAdapter implements PainelAdminQueryPort {

    /**
     * "Pro agora" para o usuário {@code u}: a mesma regra do ConsultarPlanoService (assinatura que dá acesso e não
     * venceu, ou concessão aberta dentro do prazo). Precisa dos parâmetros :agora e :statusComAcesso.
     */
    static final String EXPRESSAO_PRO = """
            (EXISTS (SELECT 1 FROM assinaturas a
                      WHERE a.usuario_id = u.id
                        AND a.status IN (:statusComAcesso)
                        AND a.periodo_atual_fim > :agora)
             OR EXISTS (SELECT 1 FROM concessoes_pro c
                         WHERE c.usuario_id = u.id
                           AND c.revogada_em IS NULL
                           AND c.inicio <= :agora
                           AND c.fim > :agora))
            """;

    /** Colunas do UsuarioResumoAdmin, na ordem lida por {@link #paraResumo(Object[])}. Reaproveitado na listagem. */
    static final String SELECT_RESUMO = """
            SELECT u.id, u.nome, u.email, u.role,
                   """ + EXPRESSAO_PRO + """
                   AS pro,
                   u.email_verificado_em IS NOT NULL AS email_verificado,
                   (SELECT count(*) FROM redacoes r WHERE r.usuario_id = u.id) AS total_redacoes,
                   (SELECT avg(av.nota_total) FROM avaliacoes av
                      JOIN redacoes r ON r.id = av.redacao_id
                     WHERE r.usuario_id = u.id) AS nota_media,
                   (SELECT max(r.enviada_em) FROM redacoes r WHERE r.usuario_id = u.id) AS ultima_redacao_em,
                   u.criado_em
              FROM usuarios u
            """;

    @PersistenceContext
    private EntityManager em;

    @Override
    public long contarUsuarios() {
        return longo(em.createNativeQuery("SELECT count(*) FROM usuarios").getSingleResult());
    }

    @Override
    public long contarCadastros(OffsetDateTime de, OffsetDateTime ate) {
        return longo(intervalo(em.createNativeQuery(
                "SELECT count(*) FROM usuarios WHERE criado_em >= :de AND criado_em < :ate"), de, ate)
                .getSingleResult());
    }

    @Override
    public long contarAlunosAtivos(OffsetDateTime de, OffsetDateTime ate) {
        return longo(intervalo(em.createNativeQuery(
                "SELECT count(DISTINCT usuario_id) FROM redacoes WHERE enviada_em >= :de AND enviada_em < :ate"),
                de, ate).getSingleResult());
    }

    @Override
    public long contarCorrecoes(OffsetDateTime de, OffsetDateTime ate) {
        return longo(intervalo(em.createNativeQuery(
                "SELECT count(*) FROM avaliacoes WHERE avaliado_em >= :de AND avaliado_em < :ate"), de, ate)
                .getSingleResult());
    }

    @Override
    public Optional<Double> mediaNotas(OffsetDateTime de, OffsetDateTime ate) {
        return Optional.ofNullable(decimal(intervalo(em.createNativeQuery(
                "SELECT avg(nota_total) FROM avaliacoes WHERE avaliado_em >= :de AND avaliado_em < :ate"), de, ate)
                .getSingleResult()));
    }

    @Override
    public long contarAssinantesPro(OffsetDateTime agora, Set<StatusAssinatura> statusComAcesso) {
        Query query = em.createNativeQuery("SELECT count(*) FROM usuarios u WHERE " + EXPRESSAO_PRO);
        return longo(pro(query, agora, statusComAcesso).getSingleResult());
    }

    @Override
    public Map<LocalDate, Long> cadastrosPorDia(OffsetDateTime de, OffsetDateTime ate, ZoneId fuso) {
        return porDia("""
                SELECT CAST(criado_em AT TIME ZONE :fuso AS date) AS dia, count(*)
                  FROM usuarios
                 WHERE criado_em >= :de AND criado_em < :ate
                 GROUP BY dia
                """, de, ate, fuso);
    }

    @Override
    public Map<LocalDate, Long> correcoesPorDia(OffsetDateTime de, OffsetDateTime ate, ZoneId fuso) {
        return porDia("""
                SELECT CAST(avaliado_em AT TIME ZONE :fuso AS date) AS dia, count(*)
                  FROM avaliacoes
                 WHERE avaliado_em >= :de AND avaliado_em < :ate
                 GROUP BY dia
                """, de, ate, fuso);
    }

    @Override
    public Map<RecursoIA, Integer> usoGlobalDoDia(LocalDate dia) {
        Objects.requireNonNull(dia, "dia é obrigatório");
        List<?> linhas = em.createNativeQuery(
                        "SELECT recurso, quantidade FROM uso_ia_global_diario WHERE data = :dia")
                .setParameter("dia", dia)
                .getResultList();
        var uso = new EnumMap<RecursoIA, Integer>(RecursoIA.class);
        for (Object linha : linhas) {
            Object[] colunas = (Object[]) linha;
            uso.put(RecursoIA.valueOf((String) colunas[0]), (int) longo(colunas[1]));
        }
        return uso;
    }

    @Override
    public long tokensDoDia(LocalDate dia, OffsetDateTime inicioDoDia, OffsetDateTime fimDoDia) {
        Objects.requireNonNull(dia, "dia é obrigatório");
        Object total = em.createNativeQuery("""
                        SELECT (SELECT coalesce(sum(tokens_entrada + tokens_saida), 0)
                                  FROM uso_ia_diario WHERE data = :dia)
                             + (SELECT coalesce(sum(coalesce(tokens_entrada, 0) + coalesce(tokens_saida, 0)), 0)
                                  FROM mensagens_julia WHERE criada_em >= :de AND criada_em < :ate)
                        """)
                .setParameter("dia", dia)
                .setParameter("de", requerer(inicioDoDia, "inicioDoDia"))
                .setParameter("ate", requerer(fimDoDia, "fimDoDia"))
                .getSingleResult();
        return longo(total);
    }

    @Override
    public long contarNoLimiteDeCorrecoes(LocalDate dia, int limiteGratis, int limitePro, OffsetDateTime agora,
                                          Set<StatusAssinatura> statusComAcesso) {
        Objects.requireNonNull(dia, "dia é obrigatório");
        Query query = em.createNativeQuery("""
                SELECT count(*)
                  FROM uso_ia_diario d
                  JOIN usuarios u ON u.id = d.usuario_id
                 WHERE d.data = :dia
                   AND d.qtd_correcoes >= CASE WHEN """ + EXPRESSAO_PRO + """
                                               THEN :limitePro ELSE :limiteGratis END
                """);
        pro(query, agora, statusComAcesso)
                .setParameter("dia", dia)
                .setParameter("limitePro", limitePro)
                .setParameter("limiteGratis", limiteGratis);
        return longo(query.getSingleResult());
    }

    @Override
    public Map<Integer, Long> contagemPorNota(OffsetDateTime de, OffsetDateTime ate) {
        List<?> linhas = intervalo(em.createNativeQuery("""
                SELECT nota_total, count(*)
                  FROM avaliacoes
                 WHERE avaliado_em >= :de AND avaliado_em < :ate
                 GROUP BY nota_total
                """), de, ate).getResultList();
        var contagem = new TreeMap<Integer, Long>();
        for (Object linha : linhas) {
            Object[] colunas = (Object[]) linha;
            contagem.put((int) longo(colunas[0]), longo(colunas[1]));
        }
        return contagem;
    }

    @Override
    public Map<Integer, Double> mediasPorCompetencia(OffsetDateTime de, OffsetDateTime ate) {
        Object[] colunas = (Object[]) intervalo(em.createNativeQuery("""
                SELECT avg(nota_c1), avg(nota_c2), avg(nota_c3), avg(nota_c4), avg(nota_c5)
                  FROM avaliacoes
                 WHERE avaliado_em >= :de AND avaliado_em < :ate
                """), de, ate).getSingleResult();
        var medias = new LinkedHashMap<Integer, Double>();
        for (int i = 0; i < colunas.length; i++) {
            Double media = decimal(colunas[i]);
            if (media != null) {
                medias.put(i + 1, media);
            }
        }
        return medias;
    }

    @Override
    public List<UsuarioResumoAdmin> ultimosCadastros(int limite, OffsetDateTime agora,
                                                     Set<StatusAssinatura> statusComAcesso) {
        if (limite < 1) {
            throw new IllegalArgumentException("limite deve ser pelo menos 1");
        }
        Query query = em.createNativeQuery(SELECT_RESUMO + " ORDER BY u.criado_em DESC, u.id LIMIT :limite");
        List<?> linhas = pro(query, agora, statusComAcesso)
                .setParameter("limite", limite)
                .getResultList();
        return linhas.stream().map(linha -> paraResumo((Object[]) linha)).toList();
    }

    @Override
    public long contarCorrecoesComErroDesde(OffsetDateTime desde) {
        return longo(em.createNativeQuery(
                        "SELECT count(*) FROM redacoes WHERE status = 'ERRO' AND enviada_em >= :desde")
                .setParameter("desde", requerer(desde, "desde"))
                .getSingleResult());
    }

    @Override
    public long contarNaoVerificadasCriadasAntesDe(OffsetDateTime limite) {
        return longo(em.createNativeQuery(
                        "SELECT count(*) FROM usuarios WHERE email_verificado_em IS NULL AND criado_em < :limite")
                .setParameter("limite", requerer(limite, "limite"))
                .getSingleResult());
    }

    @Override
    public Optional<OffsetDateTime> ultimoEventoStripe() {
        return Optional.ofNullable(dataHora(
                em.createNativeQuery("SELECT max(processado_em) FROM eventos_stripe").getSingleResult()));
    }

    // ---------- apoio ----------

    private Map<LocalDate, Long> porDia(String sql, OffsetDateTime de, OffsetDateTime ate, ZoneId fuso) {
        Objects.requireNonNull(fuso, "fuso é obrigatório");
        List<?> linhas = intervalo(em.createNativeQuery(sql), de, ate)
                .setParameter("fuso", fuso.getId())
                .getResultList();
        var mapa = new TreeMap<LocalDate, Long>();
        for (Object linha : linhas) {
            Object[] colunas = (Object[]) linha;
            mapa.put(data(colunas[0]), longo(colunas[1]));
        }
        return mapa;
    }

    private static Query intervalo(Query query, OffsetDateTime de, OffsetDateTime ate) {
        requerer(de, "de");
        requerer(ate, "ate");
        if (!ate.isAfter(de)) {
            throw new IllegalArgumentException("ate deve ser depois de de");
        }
        return query.setParameter("de", de).setParameter("ate", ate);
    }

    /** Parâmetros da {@link #EXPRESSAO_PRO}. Lista vazia de status quebraria o IN (), então é recusada. */
    static Query pro(Query query, OffsetDateTime agora, Set<StatusAssinatura> statusComAcesso) {
        requerer(agora, "agora");
        if (statusComAcesso == null || statusComAcesso.isEmpty()) {
            throw new IllegalArgumentException("statusComAcesso não pode ser vazio");
        }
        return query.setParameter("agora", agora)
                .setParameter("statusComAcesso", statusComAcesso.stream().map(Enum::name).toList());
    }

    static UsuarioResumoAdmin paraResumo(Object[] c) {
        return new UsuarioResumoAdmin(
                (UUID) c[0],
                (String) c[1],
                (String) c[2],
                Role.valueOf((String) c[3]),
                Boolean.TRUE.equals(c[4]) ? Plano.PRO : Plano.GRATIS,
                Boolean.TRUE.equals(c[5]),
                longo(c[6]),
                decimal(c[7]),
                dataHora(c[8]),
                dataHora(c[9])
        );
    }

    private static <T> T requerer(T valor, String campo) {
        return Objects.requireNonNull(valor, campo + " é obrigatório");
    }

    static long longo(Object valor) {
        return valor == null ? 0L : ((Number) valor).longValue();
    }

    /** Média com 2 casas (o front só mostra inteiro ou 1 casa). */
    static Double decimal(Object valor) {
        if (valor == null) {
            return null;
        }
        BigDecimal numero = valor instanceof BigDecimal b ? b : new BigDecimal(valor.toString());
        return numero.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    static LocalDate data(Object valor) {
        if (valor instanceof LocalDate d) {
            return d;
        }
        if (valor instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        throw new IllegalStateException("tipo de data inesperado: " + (valor == null ? null : valor.getClass()));
    }

    static OffsetDateTime dataHora(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof OffsetDateTime o) {
            return o;
        }
        if (valor instanceof java.time.ZonedDateTime z) {
            return z.toOffsetDateTime();
        }
        if (valor instanceof Instant i) {
            return i.atOffset(ZoneOffset.UTC);
        }
        if (valor instanceof Timestamp t) {
            return t.toInstant().atOffset(ZoneOffset.UTC);
        }
        throw new IllegalStateException("tipo de data/hora inesperado: " + valor.getClass());
    }
}
