package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.admin.FiltroUsuariosAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.AtividadeJulia;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.RedacaoAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioResumoAdmin;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.PainelAdminQueryPort;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contra o Postgres local. Os dados de teste ficam numa janela em 2091, onde não há dado real, então as contagens
 * por intervalo são exatas; as contagens globais (sem intervalo) são conferidas pela diferença antes/depois.
 * O @DataJpaTest desfaz tudo no fim de cada teste.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PainelAdminQueryAdapter.class)
class PainelAdminQueryAdapterIntegrationTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final ZoneOffset BR = ZoneOffset.ofHours(-3);
    private static final LocalDate DIA = LocalDate.of(2091, 3, 10);
    /** Janela [01/03/2091 00h, 15/03/2091 00h) em São Paulo. */
    private static final OffsetDateTime DE = OffsetDateTime.of(2091, 3, 1, 0, 0, 0, 0, BR);
    private static final OffsetDateTime ATE = OffsetDateTime.of(2091, 3, 15, 0, 0, 0, 0, BR);
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2091, 3, 10, 15, 0, 0, 0, BR);
    private static final Set<StatusAssinatura> COM_ACESSO = Arrays.stream(StatusAssinatura.values())
            .filter(StatusAssinatura::concedeAcesso)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(StatusAssinatura.class)));

    @Autowired
    private PainelAdminQueryPort painel;
    @Autowired
    private EntityManager em;

    private UUID temaId;

    @BeforeEach
    void buscarTema() {
        temaId = (UUID) em.createNativeQuery("SELECT id FROM temas ORDER BY id LIMIT 1").getSingleResult();
    }

    // ---------- inserts de apoio (SQL direto: as datas precisam ficar na janela de 2091) ----------

    private UUID usuario(OffsetDateTime criadoEm, boolean verificado) {
        return usuario("Teste Admin", criadoEm, verificado);
    }

    private UUID usuario(String nome, OffsetDateTime criadoEm, boolean verificado) {
        UUID id = UUID.randomUUID();
        // Sem parâmetro nulo: o Postgres não consegue inferir o tipo de um NULL vindo do Hibernate
        String verificadoEm = verificado ? ":criadoEm" : "NULL";
        em.createNativeQuery("""
                        INSERT INTO usuarios (id, nome, email, senha_hash, role, criado_em, email_verificado_em)
                        VALUES (:id, :nome, :email, 'hash', 'ALUNO', :criadoEm, %s)
                        """.formatted(verificadoEm))
                .setParameter("id", id)
                .setParameter("nome", nome)
                .setParameter("email", "painel-" + id + "@exemplo.com")
                .setParameter("criadoEm", criadoEm)
                .executeUpdate();
        return id;
    }

    private UUID redacao(UUID usuarioId, OffsetDateTime enviadaEm, String status) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                        INSERT INTO redacoes (id, usuario_id, tipo, texto, status, enviada_em, tema_id)
                        VALUES (:id, :usuario, 'PRATICA', 'texto de teste', :status, :enviadaEm, :tema)
                        """)
                .setParameter("id", id)
                .setParameter("usuario", usuarioId)
                .setParameter("status", status)
                .setParameter("enviadaEm", enviadaEm)
                .setParameter("tema", temaId)
                .executeUpdate();
        return id;
    }

    /** Redação AVALIADA com avaliação; notas das 5 competências em níveis válidos. */
    private void avaliada(UUID usuarioId, OffsetDateTime quando, int c1, int c2, int c3, int c4, int c5) {
        UUID redacaoId = redacao(usuarioId, quando, "AVALIADA");
        em.createNativeQuery("""
                        INSERT INTO avaliacoes (redacao_id, nota_c1, nota_c2, nota_c3, nota_c4, nota_c5,
                                                nota_total, avaliado_em)
                        VALUES (:redacao, :c1, :c2, :c3, :c4, :c5, :total, :quando)
                        """)
                .setParameter("redacao", redacaoId)
                .setParameter("c1", c1).setParameter("c2", c2).setParameter("c3", c3)
                .setParameter("c4", c4).setParameter("c5", c5)
                .setParameter("total", c1 + c2 + c3 + c4 + c5)
                .setParameter("quando", quando)
                .executeUpdate();
    }

    private void assinatura(UUID usuarioId, String status, OffsetDateTime fim) {
        em.createNativeQuery("""
                        INSERT INTO assinaturas (usuario_id, stripe_subscription_id, ciclo, status, periodo_atual_fim)
                        VALUES (:usuario, :sub, 'MENSAL', :status, :fim)
                        """)
                .setParameter("usuario", usuarioId)
                .setParameter("sub", "sub_" + UUID.randomUUID().toString().replace("-", ""))
                .setParameter("status", status)
                .setParameter("fim", fim)
                .executeUpdate();
    }

    private void concessao(UUID usuarioId, OffsetDateTime inicio, OffsetDateTime fim, boolean revogada) {
        String revogacao = revogada
                ? "CAST(:inicio AS timestamptz) + INTERVAL '1 hour', :usuario, 'Fim do teste'"
                : "NULL, NULL, NULL";
        em.createNativeQuery("""
                        INSERT INTO concessoes_pro (usuario_id, concedida_por, motivo, inicio, fim,
                                                    revogada_em, revogada_por, motivo_revogacao)
                        VALUES (:usuario, :usuario, 'Testador do piloto', :inicio, :fim, %s)
                        """.formatted(revogacao))
                .setParameter("usuario", usuarioId)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .executeUpdate();
    }

    private void usoDoDia(UUID usuarioId, int correcoes, int tokensEntrada, int tokensSaida) {
        em.createNativeQuery("""
                        INSERT INTO uso_ia_diario (usuario_id, data, qtd_correcoes, tokens_entrada, tokens_saida)
                        VALUES (:usuario, :dia, :correcoes, :entrada, :saida)
                        """)
                .setParameter("usuario", usuarioId)
                .setParameter("dia", DIA)
                .setParameter("correcoes", correcoes)
                .setParameter("entrada", tokensEntrada)
                .setParameter("saida", tokensSaida)
                .executeUpdate();
    }

    // ---------- cadastros e atividade ----------

    @Test
    void contaCadastrosEAtivosSoDentroDoIntervalo() {
        UUID ana = usuario(DE.plusDays(1).plusHours(10), true);   // 02/03
        UUID bia = usuario(DE.plusDays(3).plusHours(23), true);   // 04/03 23h (ainda 04/03 em SP)
        UUID fora = usuario(ATE.plusDays(1), true);
        redacao(ana, DE.plusDays(2), "ENVIADA");
        redacao(ana, DE.plusDays(4), "ENVIADA");
        redacao(bia, DE.plusDays(5), "ERRO");
        redacao(fora, ATE.plusDays(2), "ENVIADA");

        assertThat(painel.contarCadastros(DE, ATE)).isEqualTo(2);
        assertThat(painel.contarAlunosAtivos(DE, ATE)).isEqualTo(2);
        assertThat(painel.cadastrosPorDia(DE, ATE, SAO_PAULO)).containsExactlyInAnyOrderEntriesOf(Map.of(
                LocalDate.of(2091, 3, 2), 1L,
                LocalDate.of(2091, 3, 4), 1L));
    }

    @Test
    void contarUsuariosSobeComCadaCadastro() {
        long antes = painel.contarUsuarios();

        usuario(AGORA, true);
        usuario(AGORA, false);

        assertThat(painel.contarUsuarios()).isEqualTo(antes + 2);
    }

    @Test
    void intervaloInvertidoEhRecusado() {
        assertThatThrownBy(() -> painel.contarCadastros(ATE, DE)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- correções e notas ----------

    @Test
    void correcoesMediaFaixasECompetenciasDoIntervalo() {
        UUID ana = usuario(DE, true);
        avaliada(ana, DE.plusDays(1), 200, 160, 120, 80, 40);   // 600
        avaliada(ana, DE.plusDays(1).plusHours(2), 200, 200, 200, 200, 200);   // 1000
        avaliada(ana, DE.plusDays(6), 40, 40, 40, 40, 40);   // 200
        avaliada(ana, ATE.plusDays(1), 0, 0, 0, 0, 0);   // fora

        assertThat(painel.contarCorrecoes(DE, ATE)).isEqualTo(3);
        assertThat(painel.mediaNotas(DE, ATE)).contains(600.0);
        assertThat(painel.contagemPorNota(DE, ATE)).containsExactlyInAnyOrderEntriesOf(Map.of(
                200, 1L, 600, 1L, 1000, 1L));
        assertThat(painel.correcoesPorDia(DE, ATE, SAO_PAULO)).containsExactlyInAnyOrderEntriesOf(Map.of(
                LocalDate.of(2091, 3, 2), 2L,
                LocalDate.of(2091, 3, 7), 1L));
        // C1: (200+200+40)/3 = 146,67; C5: (40+200+40)/3 = 93,33
        var medias = painel.mediasPorCompetencia(DE, ATE);
        assertThat(medias).containsEntry(1, 146.67).containsEntry(5, 93.33).hasSize(5);
    }

    @Test
    void semAvaliacoesNaoHaMediaNemCompetencias() {
        assertThat(painel.mediaNotas(DE, ATE)).isEmpty();
        assertThat(painel.mediasPorCompetencia(DE, ATE)).isEmpty();
        assertThat(painel.contagemPorNota(DE, ATE)).isEmpty();
    }

    // ---------- Pro ----------

    @Test
    void assinantesProContaAssinaturaValidaEConcessaoVigenteSemDuplicar() {
        UUID porAssinatura = usuario(DE, true);
        assinatura(porAssinatura, "ACTIVE", AGORA.plusDays(20));
        UUID porConcessao = usuario(DE, true);
        concessao(porConcessao, AGORA.minusDays(1), AGORA.plusDays(30), false);
        UUID comAsDuas = usuario(DE, true);
        assinatura(comAsDuas, "TRIALING", AGORA.plusDays(5));
        concessao(comAsDuas, AGORA.minusDays(1), AGORA.plusDays(30), false);

        UUID atrasado = usuario(DE, true);
        assinatura(atrasado, "PAST_DUE", AGORA.plusDays(20));
        UUID vencido = usuario(DE, true);
        assinatura(vencido, "ACTIVE", AGORA.minusSeconds(1));
        UUID revogado = usuario(DE, true);
        concessao(revogado, AGORA.minusDays(2), AGORA.plusDays(30), true);
        UUID concessaoVencida = usuario(DE, true);
        concessao(concessaoVencida, AGORA.minusDays(10), AGORA.minusDays(1), false);
        UUID concessaoFutura = usuario(DE, true);
        concessao(concessaoFutura, AGORA.plusHours(1), AGORA.plusDays(30), false);

        assertThat(painel.contarAssinantesPro(AGORA, COM_ACESSO)).isEqualTo(3);
    }

    @Test
    void statusComAcessoVazioEhRecusado() {
        assertThatThrownBy(() -> painel.contarAssinantesPro(AGORA, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noLimiteComparaComOLimiteDoPlanoDeCadaAluno() {
        UUID gratisNoLimite = usuario(DE, true);
        usoDoDia(gratisNoLimite, 3, 0, 0);
        UUID gratisAbaixo = usuario(DE, true);
        usoDoDia(gratisAbaixo, 2, 0, 0);
        UUID proAbaixo = usuario(DE, true);
        concessao(proAbaixo, AGORA.minusDays(1), AGORA.plusDays(30), false);
        usoDoDia(proAbaixo, 3, 0, 0);
        UUID proNoLimite = usuario(DE, true);
        assinatura(proNoLimite, "ACTIVE", AGORA.plusDays(20));
        usoDoDia(proNoLimite, 10, 0, 0);

        assertThat(painel.contarNoLimiteDeCorrecoes(DIA, 3, 10, AGORA, COM_ACESSO)).isEqualTo(2);
    }

    // ---------- uso de IA do dia ----------

    @Test
    void usoGlobalDoDiaPorRecurso() {
        em.createNativeQuery("""
                        INSERT INTO uso_ia_global_diario (data, recurso, quantidade)
                        VALUES (:dia, 'CORRECAO', 7), (:dia, 'TRANSCRICAO', 2)
                        """)
                .setParameter("dia", DIA)
                .executeUpdate();

        assertThat(painel.usoGlobalDoDia(DIA)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RecursoIA.CORRECAO, 7, RecursoIA.TRANSCRICAO, 2));
        assertThat(painel.usoGlobalDoDia(DIA.plusDays(1))).isEmpty();
    }

    @Test
    void tokensDoDiaSomaCorrecaoTranscricaoEJulia() {
        UUID ana = usuario(DE, true);
        usoDoDia(ana, 1, 1_000, 200);
        UUID redacao = redacao(ana, AGORA.minusHours(2), "AVALIADA");
        UUID conversa = UUID.randomUUID();
        em.createNativeQuery("""
                        INSERT INTO conversas_julia (id, usuario_id, redacao_id, status, criada_em, ultima_atividade_em)
                        VALUES (:id, :usuario, :redacao, 'ATIVA', :quando, :quando)
                        """)
                .setParameter("id", conversa)
                .setParameter("usuario", ana)
                .setParameter("redacao", redacao)
                .setParameter("quando", AGORA.minusHours(1))
                .executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO mensagens_julia (conversa_id, ordem, papel, conteudo, tokens_entrada, tokens_saida,
                                                     criada_em)
                        VALUES (:conversa, 0, 'USUARIO', 'pergunta', NULL, NULL, :quando),
                               (:conversa, 1, 'JULIA', 'resposta', 300, 50, :quando)
                        """)
                .setParameter("conversa", conversa)
                .setParameter("quando", AGORA.minusHours(1))
                .executeUpdate();

        var inicioDoDia = DIA.atStartOfDay(SAO_PAULO).toOffsetDateTime();
        assertThat(painel.tokensDoDia(DIA, inicioDoDia, inicioDoDia.plusDays(1))).isEqualTo(1_550);
    }

    // ---------- últimos cadastros ----------

    @Test
    void ultimosCadastrosVemDoMaisNovoComPlanoERedacoes() {
        UUID antigo = usuario(AGORA.minusDays(3), true);
        UUID novo = usuario(AGORA.minusDays(1), false);
        concessao(novo, AGORA.minusHours(5), AGORA.plusDays(30), false);
        avaliada(novo, AGORA.minusHours(4), 200, 160, 120, 80, 40);   // 600
        avaliada(novo, AGORA.minusHours(2), 120, 120, 120, 120, 120);   // 600
        redacao(novo, AGORA.minusHours(1), "ERRO");

        var lista = painel.ultimosCadastros(2, AGORA, COM_ACESSO);

        assertThat(lista).extracting(UsuarioResumoAdmin::id).containsExactly(novo, antigo);
        var resumo = lista.get(0);
        assertThat(resumo.role()).isEqualTo(Role.ALUNO);
        assertThat(resumo.plano()).isEqualTo(Plano.PRO);
        assertThat(resumo.emailVerificado()).isFalse();
        assertThat(resumo.totalRedacoes()).isEqualTo(3);
        assertThat(resumo.notaMedia()).isEqualTo(600.0);
        assertThat(resumo.ultimaRedacaoEm()).isAtSameInstantAs(AGORA.minusHours(1));
        assertThat(resumo.criadoEm()).isAtSameInstantAs(AGORA.minusDays(1));

        var semRedacao = lista.get(1);
        assertThat(semRedacao.plano()).isEqualTo(Plano.GRATIS);
        assertThat(semRedacao.totalRedacoes()).isZero();
        assertThat(semRedacao.notaMedia()).isNull();
        assertThat(semRedacao.ultimaRedacaoEm()).isNull();
    }

    // ---------- alertas ----------

    @Test
    void alertasDeErroVerificacaoEStripe() {
        long naoVerificadasAntes = painel.contarNaoVerificadasCriadasAntesDe(AGORA.minusHours(48));
        UUID ana = usuario(AGORA.minusDays(5), false);   // não verificada há mais de 48h
        usuario(AGORA.minusHours(10), false);   // não verificada, mas recente
        redacao(ana, AGORA.minusHours(3), "ERRO");
        redacao(ana, AGORA.minusHours(30), "ERRO");   // fora das 24h
        em.createNativeQuery("INSERT INTO eventos_stripe (id, tipo, processado_em) VALUES (:id, 'teste', :quando)")
                .setParameter("id", "evt_" + UUID.randomUUID())
                .setParameter("quando", AGORA.minusMinutes(5))
                .executeUpdate();

        assertThat(painel.contarCorrecoesComErroDesde(AGORA.minusHours(24))).isEqualTo(1);
        assertThat(painel.contarNaoVerificadasCriadasAntesDe(AGORA.minusHours(48)))
                .isEqualTo(naoVerificadasAntes + 1);
        assertThat(painel.ultimoEventoStripe()).hasValueSatisfying(
                quando -> assertThat(quando).isAtSameInstantAs(AGORA.minusMinutes(5)));
    }

    // ---------- listagem de usuários ----------

    /** Marca única por teste: a busca por ela isola os usuários criados aqui dos dados reais do banco. */
    private static String marca() {
        return "qzx" + UUID.randomUUID().toString().substring(0, 8);
    }

    private FiltroUsuariosAdmin busca(String texto, Plano plano, Boolean verificado, int pagina, int tamanho) {
        return new FiltroUsuariosAdmin(texto, plano, verificado, pagina, tamanho);
    }

    @Test
    void buscaIgnoraAcentoEMaiusculaEPegaNomeOuEmail() {
        String marca = marca();
        UUID conceicao = usuario("Maria Conceição " + marca, AGORA, true);
        usuario("Outra Pessoa " + marca, AGORA.minusHours(1), true);

        var porNome = painel.listarUsuarios(busca("MARIA CONCEICAO " + marca, null, null, 0, 20), AGORA, COM_ACESSO);
        var porEmail = painel.listarUsuarios(busca("painel-" + conceicao, null, null, 0, 20), AGORA, COM_ACESSO);

        assertThat(porNome.itens()).extracting(UsuarioResumoAdmin::id).containsExactly(conceicao);
        assertThat(porNome.totalItens()).isEqualTo(1);
        assertThat(porEmail.itens()).extracting(UsuarioResumoAdmin::id).containsExactly(conceicao);
    }

    @Test
    void porcentagemEUnderlineNaBuscaSaoLiterais() {
        String marca = marca();
        UUID comPorcento = usuario(marca + " 100% fim", AGORA, true);
        usuario(marca + " 100x fim", AGORA, true);
        UUID comUnderline = usuario(marca + " a_b", AGORA, true);
        usuario(marca + " acb", AGORA, true);

        assertThat(painel.listarUsuarios(busca(marca + " 100%", null, null, 0, 20), AGORA, COM_ACESSO).itens())
                .extracting(UsuarioResumoAdmin::id).containsExactly(comPorcento);
        assertThat(painel.listarUsuarios(busca(marca + " a_b", null, null, 0, 20), AGORA, COM_ACESSO).itens())
                .extracting(UsuarioResumoAdmin::id).containsExactly(comUnderline);
    }

    @Test
    void filtraPorPlanoComAMesmaRegraDoPro() {
        String marca = marca();
        UUID porAssinatura = usuario("A " + marca, AGORA.minusHours(1), true);
        assinatura(porAssinatura, "ACTIVE", AGORA.plusDays(10));
        UUID porConcessao = usuario("B " + marca, AGORA.minusHours(2), true);
        concessao(porConcessao, AGORA.minusDays(1), AGORA.plusDays(10), false);
        UUID atrasado = usuario("C " + marca, AGORA.minusHours(3), true);
        assinatura(atrasado, "PAST_DUE", AGORA.plusDays(10));
        UUID gratis = usuario("D " + marca, AGORA.minusHours(4), true);

        var pro = painel.listarUsuarios(busca(marca, Plano.PRO, null, 0, 20), AGORA, COM_ACESSO);
        var gratisPagina = painel.listarUsuarios(busca(marca, Plano.GRATIS, null, 0, 20), AGORA, COM_ACESSO);

        assertThat(pro.itens()).extracting(UsuarioResumoAdmin::id).containsExactly(porAssinatura, porConcessao);
        assertThat(pro.itens()).allMatch(u -> u.plano() == Plano.PRO);
        assertThat(pro.totalItens()).isEqualTo(2);
        assertThat(gratisPagina.itens()).extracting(UsuarioResumoAdmin::id).containsExactly(atrasado, gratis);
        assertThat(gratisPagina.totalItens()).isEqualTo(2);
    }

    @Test
    void filtraPorEmailVerificadoOuPendente() {
        String marca = marca();
        UUID verificado = usuario("A " + marca, AGORA.minusHours(1), true);
        UUID pendente = usuario("B " + marca, AGORA.minusHours(2), false);

        assertThat(painel.listarUsuarios(busca(marca, null, true, 0, 20), AGORA, COM_ACESSO).itens())
                .extracting(UsuarioResumoAdmin::id).containsExactly(verificado);
        assertThat(painel.listarUsuarios(busca(marca, null, false, 0, 20), AGORA, COM_ACESSO).itens())
                .extracting(UsuarioResumoAdmin::id).containsExactly(pendente);
    }

    @Test
    void paginaDoMaisNovoParaOMaisAntigoComTotal() {
        String marca = marca();
        UUID terceiro = usuario("A " + marca, AGORA.minusHours(3), true);
        UUID primeiro = usuario("B " + marca, AGORA.minusHours(1), true);
        UUID segundo = usuario("C " + marca, AGORA.minusHours(2), true);

        var pagina0 = painel.listarUsuarios(busca(marca, null, null, 0, 2), AGORA, COM_ACESSO);
        var pagina1 = painel.listarUsuarios(busca(marca, null, null, 1, 2), AGORA, COM_ACESSO);
        var pagina5 = painel.listarUsuarios(busca(marca, null, null, 5, 2), AGORA, COM_ACESSO);

        assertThat(pagina0.itens()).extracting(UsuarioResumoAdmin::id).containsExactly(primeiro, segundo);
        assertThat(pagina0.totalItens()).isEqualTo(3);
        assertThat(pagina0.temProxima()).isTrue();
        assertThat(pagina1.itens()).extracting(UsuarioResumoAdmin::id).containsExactly(terceiro);
        assertThat(pagina1.temProxima()).isFalse();
        assertThat(pagina5.itens()).isEmpty();
        assertThat(pagina5.totalItens()).isEqualTo(3);
    }

    @Test
    void semFiltrosListaTodosComOTotalDoBanco() {
        long total = painel.contarUsuarios();

        var pagina = painel.listarUsuarios(FiltroUsuariosAdmin.semFiltros(0), AGORA, COM_ACESSO);

        assertThat(pagina.totalItens()).isEqualTo(total);
        assertThat(pagina.itens()).hasSizeLessThanOrEqualTo(FiltroUsuariosAdmin.TAMANHO_PADRAO);
    }

    // ---------- detalhe do usuário ----------

    @Test
    void buscarResumoDeIdInexistenteVemVazio() {
        assertThat(painel.buscarResumo(UUID.randomUUID(), AGORA, COM_ACESSO)).isEmpty();
    }

    @Test
    void buscarResumoTrazOAluno() {
        UUID ana = usuario("Ana Resumo", AGORA.minusDays(2), true);

        var resumo = painel.buscarResumo(ana, AGORA, COM_ACESSO).orElseThrow();

        assertThat(resumo.nome()).isEqualTo("Ana Resumo");
        assertThat(resumo.plano()).isEqualTo(Plano.GRATIS);
        assertThat(resumo.emailVerificado()).isTrue();
    }

    @Test
    void notasDoAlunoMelhorEMediasPorCompetencia() {
        UUID ana = usuario(DE, true);
        UUID outro = usuario(DE, true);
        avaliada(ana, AGORA.minusDays(2), 200, 160, 120, 80, 40);   // 600
        avaliada(ana, AGORA.minusDays(1), 200, 200, 120, 120, 40);   // 680
        avaliada(outro, AGORA.minusDays(1), 200, 200, 200, 200, 200);   // de outro aluno: não entra

        assertThat(painel.melhorNota(ana)).contains(680);
        assertThat(painel.mediasPorCompetenciaDoUsuario(ana)).containsExactlyInAnyOrderEntriesOf(Map.of(
                1, 200.0, 2, 180.0, 3, 120.0, 4, 100.0, 5, 40.0));
    }

    @Test
    void alunoSemCorrecaoNaoTemNotas() {
        UUID ana = usuario(DE, true);

        assertThat(painel.melhorNota(ana)).isEmpty();
        assertThat(painel.mediasPorCompetenciaDoUsuario(ana)).isEmpty();
    }

    @Test
    void usoDoAlunoNoDia() {
        UUID ana = usuario(DE, true);
        em.createNativeQuery("""
                        INSERT INTO uso_ia_diario (usuario_id, data, qtd_correcoes, qtd_transcricoes, qtd_mensagens_julia)
                        VALUES (:usuario, :dia, 2, 1, 5)
                        """)
                .setParameter("usuario", ana)
                .setParameter("dia", DIA)
                .executeUpdate();

        assertThat(painel.usoDoUsuarioNoDia(ana, DIA)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RecursoIA.CORRECAO, 2, RecursoIA.TRANSCRICAO, 1, RecursoIA.JULIA, 5));
        assertThat(painel.usoDoUsuarioNoDia(ana, DIA.plusDays(1))).isEmpty();
    }

    @Test
    void atividadeJuliaContaConversasEPerguntasDoAluno() {
        UUID ana = usuario(DE, true);
        UUID redacao = redacao(ana, AGORA.minusHours(2), "AVALIADA");
        UUID conversa = UUID.randomUUID();
        em.createNativeQuery("""
                        INSERT INTO conversas_julia (id, usuario_id, redacao_id, status, criada_em, ultima_atividade_em)
                        VALUES (:id, :usuario, :redacao, 'ATIVA', :quando, :quando)
                        """)
                .setParameter("id", conversa)
                .setParameter("usuario", ana)
                .setParameter("redacao", redacao)
                .setParameter("quando", AGORA.minusHours(1))
                .executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO mensagens_julia (conversa_id, ordem, papel, conteudo, criada_em)
                        VALUES (:conversa, 0, 'USUARIO', 'p1', :quando), (:conversa, 1, 'JULIA', 'r1', :quando),
                               (:conversa, 2, 'USUARIO', 'p2', :quando), (:conversa, 3, 'JULIA', 'r2', :quando)
                        """)
                .setParameter("conversa", conversa)
                .setParameter("quando", AGORA.minusHours(1))
                .executeUpdate();

        assertThat(painel.atividadeJulia(ana)).isEqualTo(new AtividadeJulia(1, 2));
        assertThat(painel.atividadeJulia(usuario(DE, true))).isEqualTo(new AtividadeJulia(0, 0));
    }

    @Test
    void ultimasRedacoesComTemaNotaEOrdem() {
        UUID ana = usuario(DE, true);
        avaliada(ana, AGORA.minusDays(3), 200, 160, 120, 80, 40);   // 600, mais antiga
        UUID comErro = redacao(ana, AGORA.minusDays(1), "ERRO");
        avaliada(ana, AGORA.minusDays(2), 120, 120, 120, 120, 120);   // 600
        String tituloDoTema = (String) em.createNativeQuery("SELECT titulo FROM temas WHERE id = :id")
                .setParameter("id", temaId).getSingleResult();

        List<RedacaoAdmin> redacoes = painel.ultimasRedacoes(ana, 2);

        assertThat(redacoes).hasSize(2);
        var maisRecente = redacoes.get(0);
        assertThat(maisRecente.id()).isEqualTo(comErro);
        assertThat(maisRecente.status()).isEqualTo(StatusRedacao.ERRO);
        assertThat(maisRecente.nota()).isNull();
        assertThat(maisRecente.origem()).isEqualTo(OrigemRedacao.DIGITADO);
        assertThat(maisRecente.tema()).isEqualTo(tituloDoTema);
        assertThat(maisRecente.enviadaEm()).isAtSameInstantAs(AGORA.minusDays(1));
        assertThat(redacoes.get(1).nota()).isEqualTo(600);
        assertThat(redacoes.get(1).status()).isEqualTo(StatusRedacao.AVALIADA);
    }
}
