package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.BuscarRepertorioService;
import eduar.studymindredacao.application.usecase.ListagemRepertorios;
import eduar.studymindredacao.application.usecase.ListarRepertoriosService;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.model.ConteudoDidatico;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.Fonte;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.ProblemaSocial;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.model.enums.FuncaoArgumentativa;
import eduar.studymindredacao.domain.model.enums.PapelRepertorio;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusVerificacao;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import eduar.studymindredacao.domain.model.enums.TipoFonte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /repertorios e /repertorios/{codigo} com a SecurityConfig real e o plano do aluno. */
@WebMvcTest(RepertorioController.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, RepertorioControllerPlanoTest.Relogio.class})
class RepertorioControllerPlanoTest {

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListarRepertoriosService listarRepertorios;
    @MockitoBean
    private BuscarRepertorioService buscarRepertorio;
    @MockitoBean
    private ValidarSessaoService validarSessao;

    private UUID usuarioId;
    private String jwt;

    private static Repertorio repertorio(String codigo) {
        return new Repertorio(
                null, codigo, TipoEntidade.PERSON, PapelRepertorio.REPERTOIRE, "Nome", null, null, null, null,
                new ConteudoDidatico("Ideia", "Lembre", "Como usar", "Exemplo", "Erro"),
                null, null, List.of(FuncaoArgumentativa.EXPLAIN_CAUSE), List.of(), List.of(),
                List.of(new ProblemaSocial(null, "saude-mental", "Saúde mental", "saude")),
                List.of(new Fonte(TipoFonte.GENERAL, "Fonte", "https://exemplo.org")),
                StatusVerificacao.VERIFIED, null, true
        );
    }

    @BeforeEach
    void abrirSessao() {
        usuarioId = UUID.randomUUID();
        jwt = "jwt-" + usuarioId;
        when(validarSessao.validar(jwt))
                .thenReturn(Optional.of(new DadosToken(usuarioId, "ana@exemplo.com", Role.ALUNO, 0)));
    }

    @Test
    void semLoginResponde401SemListar() throws Exception {
        mvc.perform(get("/repertorios"))
                .andExpect(status().isUnauthorized());

        verify(listarRepertorios, never()).listar(any(), any());
    }

    @Test
    void alunoGratisRecebeAListaLimitada() throws Exception {
        var pagina = new Pagina<>(List.of(repertorio("REP-030")), 0, 20, 1);
        when(listarRepertorios.listar(eq(usuarioId), any())).thenReturn(new ListagemRepertorios(pagina, true));

        mvc.perform(get("/repertorios").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].codigo").value("REP-030"))
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.limitadoAoPlano").value(true));
    }

    @Test
    void alunoProRecebeAListaSemLimite() throws Exception {
        var pagina = new Pagina<>(List.of(repertorio("REP-031")), 0, 20, 1);
        when(listarRepertorios.listar(eq(usuarioId), any())).thenReturn(new ListagemRepertorios(pagina, false));

        mvc.perform(get("/repertorios").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitadoAoPlano").value(false));
    }

    @Test
    void detalheNaoLiberadoNoGratisResponde403ComCodigoERecurso() throws Exception {
        when(buscarRepertorio.buscar(usuarioId, "REP-031"))
                .thenThrow(new RecursoExclusivoProException(RecursoPro.REPERTORIO_COMPLETO));

        mvc.perform(get("/repertorios/REP-031").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLANO_PRO_NECESSARIO"))
                .andExpect(jsonPath("$.recurso").value("REPERTORIO_COMPLETO"));
    }

    @Test
    void detalheLiberadoResponde200() throws Exception {
        when(buscarRepertorio.buscar(usuarioId, "REP-030")).thenReturn(repertorio("REP-030"));

        mvc.perform(get("/repertorios/REP-030").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("REP-030"));
    }
}
