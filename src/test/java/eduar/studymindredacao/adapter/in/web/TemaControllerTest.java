package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.CriarTemaPersonalizadoService;
import eduar.studymindredacao.application.usecase.ListarTemasPossiveisService;
import eduar.studymindredacao.application.usecase.ListarTemasService;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.exception.LimiteTemasPersonalizadosException;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints de /temas com a SecurityConfig real e os services mockados. */
@WebMvcTest(TemaController.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, TemaControllerTest.Relogio.class})
class TemaControllerTest {
    private static final String TITULO = "Os desafios da mobilidade urbana no Brasil";

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("America/Sao_Paulo"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListarTemasService listarTemas;
    @MockitoBean
    private ListarTemasPossiveisService listarTemasPossiveis;
    @MockitoBean
    private CriarTemaPersonalizadoService criarTemaPersonalizado;
    @MockitoBean
    private ValidarSessaoService validarSessao;

    private UUID usuarioId;
    private String jwt;

    @BeforeEach
    void abrirSessao() {
        usuarioId = UUID.randomUUID();
        jwt = "jwt-" + usuarioId;
        when(validarSessao.validar(jwt))
                .thenReturn(Optional.of(new DadosToken(usuarioId, "ana@exemplo.com", Role.ALUNO, 0)));
    }

    private Tema temaDoAluno() {
        return new Tema(UUID.randomUUID(), TITULO, null, OrigemTema.PERSONALIZADO, null, true, null, usuarioId);
    }

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
    }

    private MockHttpServletRequestBuilder criar(String corpo) {
        return autenticado(post("/temas/personalizados"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    // ---------- POST /temas/personalizados ----------

    @Test
    void criarSemLoginResponde401() throws Exception {
        mvc.perform(post("/temas/personalizados")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"" + TITULO + "\"}"))
                .andExpect(status().isUnauthorized());

        verify(criarTemaPersonalizado, never()).criar(any(), anyString());
    }

    @Test
    void temaNovoResponde201ComOTema() throws Exception {
        var tema = temaDoAluno();
        when(criarTemaPersonalizado.criar(usuarioId, TITULO))
                .thenReturn(new CriarTemaPersonalizadoService.Resultado(tema, true));

        mvc.perform(criar("{\"titulo\":\"" + TITULO + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(tema.id().toString()))
                .andExpect(jsonPath("$.titulo").value(TITULO))
                .andExpect(jsonPath("$.origem").value("PERSONALIZADO"));
    }

    @Test
    void temaQueJaExistiaResponde200ComOExistente() throws Exception {
        var tema = temaDoAluno();
        when(criarTemaPersonalizado.criar(usuarioId, TITULO))
                .thenReturn(new CriarTemaPersonalizadoService.Resultado(tema, false));

        mvc.perform(criar("{\"titulo\":\"" + TITULO + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tema.id().toString()));
    }

    @Test
    void respostaNaoExpoeODonoDoTema() throws Exception {
        when(criarTemaPersonalizado.criar(usuarioId, TITULO))
                .thenReturn(new CriarTemaPersonalizadoService.Resultado(temaDoAluno(), true));

        mvc.perform(criar("{\"titulo\":\"" + TITULO + "\"}"))
                .andExpect(jsonPath("$.criadoPorId").doesNotExist());
    }

    @Test
    void tituloEmBrancoResponde400SemChamarOServico() throws Exception {
        mvc.perform(criar("{\"titulo\":\"   \"}")).andExpect(status().isBadRequest());

        verify(criarTemaPersonalizado, never()).criar(any(), anyString());
    }

    @Test
    void tituloAcimaDe500CaracteresResponde400SemChamarOServico() throws Exception {
        mvc.perform(criar("{\"titulo\":\"" + "a".repeat(501) + "\"}")).andExpect(status().isBadRequest());

        verify(criarTemaPersonalizado, never()).criar(any(), anyString());
    }

    @Test
    void regraDoDominioRecusadaResponde400ComAMensagem() throws Exception {
        when(criarTemaPersonalizado.criar(usuarioId, "curto demais"))
                .thenThrow(new IllegalArgumentException("titulo deve ter no mínimo 10 caracteres"));

        mvc.perform(criar("{\"titulo\":\"curto demais\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("titulo deve ter no mínimo 10 caracteres"));
    }

    @Test
    void limiteDoDiaResponde429ComCodigoProprioERetryAfter() throws Exception {
        when(criarTemaPersonalizado.criar(usuarioId, TITULO)).thenThrow(new LimiteTemasPersonalizadosException(10));

        mvc.perform(criar("{\"titulo\":\"" + TITULO + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.codigo").value("LIMITE_TEMAS_PERSONALIZADOS"))
                .andExpect(jsonPath("$.limite").value(10));
    }

    // ---------- GET /temas ----------

    @Test
    void listarDevolveOsPersonalizadosComAOrigem() throws Exception {
        var meu = temaDoAluno();
        var oficial = new Tema(UUID.randomUUID(), "Oficial", null, OrigemTema.ENEM_OFICIAL, (short) 2024, true, null);
        when(listarTemas.listarDisponiveis(usuarioId)).thenReturn(List.of(meu, oficial));

        mvc.perform(autenticado(get("/temas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(meu.id().toString()))
                .andExpect(jsonPath("$[0].origem").value("PERSONALIZADO"))
                .andExpect(jsonPath("$[1].origem").value("ENEM_OFICIAL"));
    }
}
