package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.AcoesUsuarioAdminService;
import eduar.studymindredacao.application.usecase.ConsultarUsuariosAdminService;
import eduar.studymindredacao.application.usecase.ConsultarVisaoGeralAdminService;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.exception.AcaoAdminConflitoException;
import eduar.studymindredacao.domain.exception.AcessoAdminNegadoException;
import eduar.studymindredacao.domain.exception.ConcessaoProConflitoException;
import eduar.studymindredacao.domain.exception.ReenvioMuitoRecenteException;
import eduar.studymindredacao.domain.exception.UsuarioNaoEncontradoException;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.admin.FiltroUsuariosAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.AtividadeJulia;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.CompetenciaMaisFraca;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.RedacaoAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.UsoHoje;
import eduar.studymindredacao.domain.model.admin.UsuarioResumoAdmin;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /admin com a SecurityConfig real e os casos de uso simulados. */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, AdminControllerTest.Relogio.class})
class AdminControllerTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 27, 15, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID ALUNO = UUID.randomUUID();
    private static final String JWT_ADMIN = "jwt-admin";
    private static final String JWT_ALUNO = "jwt-aluno";

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock clock() {
            return Clock.fixed(AGORA.toInstant(), SAO_PAULO);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarVisaoGeralAdminService visaoGeral;
    @MockitoBean
    private ConsultarUsuariosAdminService consultarUsuarios;
    @MockitoBean
    private AcoesUsuarioAdminService acoes;
    @MockitoBean
    private ValidarSessaoService validarSessao;

    @BeforeEach
    void abrirSessoes() {
        when(validarSessao.validar(JWT_ADMIN))
                .thenReturn(Optional.of(new DadosToken(ADMIN, "admin@exemplo.com", Role.ADMIN, 0)));
        when(validarSessao.validar(JWT_ALUNO))
                .thenReturn(Optional.of(new DadosToken(ALUNO, "aluno@exemplo.com", Role.ALUNO, 0)));
    }

    private static String bearer(String jwt) {
        return "Bearer " + jwt;
    }

    private static UsuarioResumoAdmin resumo() {
        return new UsuarioResumoAdmin(ALUNO, "Ana", "ana@exemplo.com", Role.ALUNO, Plano.PRO, false, 3,
                612.67, AGORA.minusDays(1), AGORA.minusDays(10));
    }

    private static ConcessaoPro concessao() {
        return new ConcessaoPro(UUID.randomUUID(), ALUNO, ADMIN, "Testador do piloto", AGORA.minusDays(1),
                AGORA.plusDays(29), null, null, null);
    }

    // ---------- acesso ----------

    @Test
    void alunoRecebe403EmTodasAsRotasSemChegarNosCasosDeUso() throws Exception {
        String corpoConceder = "{\"dias\":30,\"motivo\":\"Testador do piloto\"}";
        List<MockHttpServletRequestBuilder> rotas = List.of(
                get("/admin/visao-geral"),
                get("/admin/usuarios"),
                get("/admin/usuarios/" + ALUNO),
                post("/admin/usuarios/" + ALUNO + "/concessao-pro")
                        .contentType(MediaType.APPLICATION_JSON).content(corpoConceder),
                post("/admin/usuarios/" + ALUNO + "/concessao-pro/revogar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Fim do piloto\"}"),
                post("/admin/usuarios/" + ALUNO + "/zerar-cota-hoje"),
                post("/admin/usuarios/" + ALUNO + "/reenviar-verificacao"));

        for (MockHttpServletRequestBuilder rota : rotas) {
            mvc.perform(rota.header(HttpHeaders.AUTHORIZATION, bearer(JWT_ALUNO)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(visaoGeral, consultarUsuarios, acoes);
    }

    @Test
    void semLoginResponde401() throws Exception {
        mvc.perform(get("/admin/usuarios")).andExpect(status().isUnauthorized());
        verifyNoInteractions(consultarUsuarios);
    }

    // ---------- visão geral ----------

    @Test
    void visaoGeralNoFormatoDoFrontComArredondamentosESemReceita() throws Exception {
        var visao = new VisaoGeralAdmin(7, AGORA, 12, 3, 5, 40, 612.5, 2,
                List.of(new VisaoGeralAdmin.PontoDiario(LocalDate.of(2026, 10, 27), 1, 4)),
                List.of(new VisaoGeralAdmin.UsoIA(RecursoIA.CORRECAO, 12, 30)),
                98_000, 1,
                List.of(new VisaoGeralAdmin.FaixaNota(600, 799, 9)),
                List.of(new VisaoGeralAdmin.MediaCompetencia(1, 146.67), new VisaoGeralAdmin.MediaCompetencia(2, null)),
                List.of(resumo()),
                new VisaoGeralAdmin.Alertas(2, 4, null));
        when(visaoGeral.consultar(7)).thenReturn(visao);

        mvc.perform(get("/admin/visao-geral?periodo=7").header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value(7))
                .andExpect(jsonPath("$.totalUsuarios").value(12))
                .andExpect(jsonPath("$.notaMediaNoPeriodo").value(613))
                .andExpect(jsonPath("$.serieDiaria[0].data").value("2026-10-27"))
                .andExpect(jsonPath("$.serieDiaria[0].correcoes").value(4))
                .andExpect(jsonPath("$.usoIaHoje[0].recurso").value("CORRECAO"))
                .andExpect(jsonPath("$.usoIaHoje[0].teto").value(30))
                .andExpect(jsonPath("$.distribuicaoNotas[0].quantidade").value(9))
                .andExpect(jsonPath("$.mediaPorCompetencia[0].media").value(146.7))
                .andExpect(jsonPath("$.mediaPorCompetencia[1].media").doesNotExist())
                .andExpect(jsonPath("$.ultimosCadastros[0].notaMedia").value(613))
                .andExpect(jsonPath("$.ultimosCadastros[0].plano").value("PRO"))
                .andExpect(jsonPath("$.alertas.contasSemVerificacao48h").value(4))
                .andExpect(jsonPath("$.receitaMensalCentavos").doesNotExist());
    }

    @Test
    void visaoGeralUsa30DiasPorPadrao() throws Exception {
        when(visaoGeral.consultar(30)).thenReturn(new VisaoGeralAdmin(30, AGORA, 0, 0, 0, 0, null, 0, List.of(),
                List.of(), 0, 0, List.of(), List.of(), List.of(), new VisaoGeralAdmin.Alertas(0, 0, null)));

        mvc.perform(get("/admin/visao-geral").header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value(30))
                .andExpect(jsonPath("$.notaMediaNoPeriodo").doesNotExist());

        verify(visaoGeral).consultar(30);
    }

    @Test
    void periodoInvalidoDa400() throws Exception {
        when(visaoGeral.consultar(anyInt())).thenThrow(new IllegalArgumentException("periodo deve ser 7, 30 ou 90"));

        mvc.perform(get("/admin/visao-geral?periodo=15").header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/visao-geral?periodo=abc").header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isBadRequest());
    }

    // ---------- listagem ----------

    @Test
    void listarMontaOFiltroComOsParametrosDoFront() throws Exception {
        when(consultarUsuarios.listar(any())).thenReturn(new Pagina<>(List.of(resumo()), 1, 10, 11));

        mvc.perform(get("/admin/usuarios?busca=ana&plano=PRO&email=PENDENTE&pagina=1&tamanho=10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(ALUNO.toString()))
                .andExpect(jsonPath("$.itens[0].emailVerificado").value(false))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.totalItens").value(11))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.temProxima").value(false));

        ArgumentCaptor<FiltroUsuariosAdmin> filtro = ArgumentCaptor.forClass(FiltroUsuariosAdmin.class);
        verify(consultarUsuarios).listar(filtro.capture());
        assertFiltro(filtro.getValue(), new FiltroUsuariosAdmin("ana", Plano.PRO, false, 1, 10));
    }

    @Test
    void listarSemParametrosUsaPadroes() throws Exception {
        when(consultarUsuarios.listar(any())).thenReturn(new Pagina<>(List.of(), 0, 20, 0));

        mvc.perform(get("/admin/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isOk());

        ArgumentCaptor<FiltroUsuariosAdmin> filtro = ArgumentCaptor.forClass(FiltroUsuariosAdmin.class);
        verify(consultarUsuarios).listar(filtro.capture());
        assertFiltro(filtro.getValue(), FiltroUsuariosAdmin.semFiltros(0));
    }

    @Test
    void emailVerificadoViraTrue() throws Exception {
        when(consultarUsuarios.listar(any())).thenReturn(new Pagina<>(List.of(), 0, 20, 0));

        mvc.perform(get("/admin/usuarios?email=VERIFICADO").header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isOk());

        ArgumentCaptor<FiltroUsuariosAdmin> filtro = ArgumentCaptor.forClass(FiltroUsuariosAdmin.class);
        verify(consultarUsuarios).listar(filtro.capture());
        org.assertj.core.api.Assertions.assertThat(filtro.getValue().emailVerificado()).isTrue();
    }

    @Test
    void parametrosInvalidosDa400SemConsultar() throws Exception {
        for (String query : List.of("plano=OURO", "email=TALVEZ", "tamanho=51", "tamanho=0", "pagina=-1",
                "busca=" + "a".repeat(101))) {
            mvc.perform(get("/admin/usuarios?" + query).header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(consultarUsuarios);
    }

    private static void assertFiltro(FiltroUsuariosAdmin atual, FiltroUsuariosAdmin esperado) {
        org.assertj.core.api.Assertions.assertThat(atual).isEqualTo(esperado);
    }

    // ---------- detalhe ----------

    @Test
    void detalheNoFormatoDoFrontComConcessao() throws Exception {
        var assinatura = Assinatura.nova(ALUNO, "sub_segredo", CicloAssinatura.ANUAL, StatusAssinatura.PAST_DUE,
                AGORA.plusMonths(11), true, AGORA.minusMonths(1));
        var aberta = concessao();
        var detalhe = new UsuarioDetalheAdmin(resumo(), 720, new CompetenciaMaisFraca(3, 93.333), assinatura,
                "cus_123", aberta, List.of(aberta),
                List.of(new UsoHoje(RecursoIA.CORRECAO, 4, 10)), new AtividadeJulia(2, 9),
                List.of(new RedacaoAdmin(UUID.randomUUID(), AGORA.minusDays(1), "Tema X", OrigemRedacao.MANUSCRITO,
                        StatusRedacao.AVALIADA, 720)));
        when(consultarUsuarios.detalhar(ALUNO)).thenReturn(detalhe);

        mvc.perform(get("/admin/usuarios/" + ALUNO).header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nome").value("Ana"))
                .andExpect(jsonPath("$.melhorNota").value(720))
                .andExpect(jsonPath("$.competenciaMaisFraca.competencia").value(3))
                .andExpect(jsonPath("$.competenciaMaisFraca.media").value(93.3))
                .andExpect(jsonPath("$.assinatura.status").value("PAST_DUE"))
                .andExpect(jsonPath("$.assinatura.ciclo").value("ANUAL"))
                .andExpect(jsonPath("$.assinatura.cancelarNoFimDoPeriodo").value(true))
                .andExpect(jsonPath("$.assinatura.stripeCustomerId").value("cus_123"))
                .andExpect(jsonPath("$.usoHoje[0].limite").value(10))
                .andExpect(jsonPath("$.julia.mensagens").value(9))
                .andExpect(jsonPath("$.redacoes[0].origem").value("MANUSCRITO"))
                .andExpect(jsonPath("$.redacoes[0].nota").value(720))
                .andExpect(jsonPath("$.concessaoPro.vigente").value(true))
                .andExpect(jsonPath("$.concessaoPro.motivo").value("Testador do piloto"))
                .andExpect(jsonPath("$.concessaoPro.concedidaPor").value(ADMIN.toString()))
                .andExpect(jsonPath("$.concessoes.length()").value(1))
                // a subscription do Stripe nunca sai, nem para o admin
                .andExpect(content().string(not(containsString("sub_segredo"))));
    }

    @Test
    void detalheDeAlunoInexistenteDa404() throws Exception {
        when(consultarUsuarios.detalhar(ALUNO)).thenThrow(new UsuarioNaoEncontradoException());

        mvc.perform(get("/admin/usuarios/" + ALUNO).header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void idMalformadoDa400() throws Exception {
        mvc.perform(get("/admin/usuarios/nao-e-uuid").header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(consultarUsuarios);
    }

    // ---------- conceder e revogar ----------

    @Test
    void concederResponde201EUsaOAdminDoTokenNaoDoCorpo() throws Exception {
        var concessao = concessao();
        when(acoes.concederPro(ADMIN, ALUNO, 30, "Testador do piloto")).thenReturn(concessao);
        String corpo = "{\"dias\":30,\"motivo\":\"Testador do piloto\",\"adminId\":\"" + UUID.randomUUID()
                + "\",\"concedidaPor\":\"" + UUID.randomUUID() + "\"}";

        mvc.perform(post("/admin/usuarios/" + ALUNO + "/concessao-pro")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(concessao.id().toString()))
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.concedidaPor").value(ADMIN.toString()));

        verify(acoes).concederPro(ADMIN, ALUNO, 30, "Testador do piloto");
    }

    @Test
    void corpoInvalidoNoConcederDa400SemChamarOCasoDeUso() throws Exception {
        for (String corpo : List.of(
                "{\"dias\":0,\"motivo\":\"Testador do piloto\"}",
                "{\"dias\":366,\"motivo\":\"Testador do piloto\"}",
                "{\"motivo\":\"Testador do piloto\"}",
                "{\"dias\":30,\"motivo\":\"   \"}",
                "{\"dias\":30,\"motivo\":\"abc\"}",
                "{\"dias\":30,\"motivo\":\"" + "a".repeat(301) + "\"}",
                "{\"dias\":\"trinta\",\"motivo\":\"Testador do piloto\"}",
                "nao e json")) {
            mvc.perform(post("/admin/usuarios/" + ALUNO + "/concessao-pro")
                            .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(acoes);
    }

    @Test
    void conflitoAoConcederDa409() throws Exception {
        when(acoes.concederPro(any(), any(), anyInt(), any()))
                .thenThrow(new ConcessaoProConflitoException("Este aluno já é Pro pela assinatura."));

        mvc.perform(post("/admin/usuarios/" + ALUNO + "/concessao-pro")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dias\":30,\"motivo\":\"Testador do piloto\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este aluno já é Pro pela assinatura."));
    }

    @Test
    void adminRebaixadoNoBancoDa403() throws Exception {
        when(acoes.concederPro(any(), any(), anyInt(), any())).thenThrow(new AcessoAdminNegadoException());

        mvc.perform(post("/admin/usuarios/" + ALUNO + "/concessao-pro")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dias\":30,\"motivo\":\"Testador do piloto\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void revogarResponde200ComAConcessaoRevogada() throws Exception {
        var revogada = concessao().revogar(ADMIN, "Fim do piloto", AGORA);
        when(acoes.revogarPro(ADMIN, ALUNO, "Fim do piloto")).thenReturn(revogada);

        mvc.perform(post("/admin/usuarios/" + ALUNO + "/concessao-pro/revogar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Fim do piloto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(false))
                .andExpect(jsonPath("$.revogadaPor").value(ADMIN.toString()))
                .andExpect(jsonPath("$.motivoRevogacao").value("Fim do piloto"));
    }

    @Test
    void revogarSemMotivoDa400() throws Exception {
        mvc.perform(post("/admin/usuarios/" + ALUNO + "/concessao-pro/revogar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(acoes);
    }

    // ---------- zerar cota e reenviar verificação ----------

    @Test
    void zerarCotaResponde204() throws Exception {
        mvc.perform(post("/admin/usuarios/" + ALUNO + "/zerar-cota-hoje")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isNoContent());

        verify(acoes).zerarCotaHoje(ADMIN, ALUNO);
    }

    @Test
    void reenviarResponde204() throws Exception {
        mvc.perform(post("/admin/usuarios/" + ALUNO + "/reenviar-verificacao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isNoContent());

        verify(acoes).reenviarVerificacao(ADMIN, ALUNO);
    }

    @Test
    void reenviarJaVerificadoDa409() throws Exception {
        doThrow(new AcaoAdminConflitoException("O e-mail deste aluno já foi verificado."))
                .when(acoes).reenviarVerificacao(ADMIN, ALUNO);

        mvc.perform(post("/admin/usuarios/" + ALUNO + "/reenviar-verificacao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isConflict());
    }

    @Test
    void reenviarRecenteDa429ComRetryAfter() throws Exception {
        doThrow(new ReenvioMuitoRecenteException(180)).when(acoes).reenviarVerificacao(ADMIN, ALUNO);

        mvc.perform(post("/admin/usuarios/" + ALUNO + "/reenviar-verificacao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "180"));
    }

    @Test
    void acaoEmAlunoInexistenteDa404() throws Exception {
        doThrow(new UsuarioNaoEncontradoException()).when(acoes).zerarCotaHoje(ADMIN, ALUNO);

        mvc.perform(post("/admin/usuarios/" + ALUNO + "/zerar-cota-hoje")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JWT_ADMIN)))
                .andExpect(status().isNotFound());
    }
}
