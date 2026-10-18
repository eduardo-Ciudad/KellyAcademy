package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.AutenticarUsuarioService;
import eduar.studymindredacao.application.usecase.CadastrarUsuarioService;
import eduar.studymindredacao.application.usecase.RedefinicaoSenhaService;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.application.usecase.VerificacaoEmailService;
import eduar.studymindredacao.domain.exception.EmailNaoVerificadoException;
import eduar.studymindredacao.domain.exception.TokenInvalidoException;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Camada web das rotas de autenticação com a SecurityConfig real (rotas públicas, 401 no resto) e o
 * LimitadorDeTentativas real. Os casos de uso são mocks: as regras deles têm testes próprios.
 * O limitador é o mesmo bean em todos os testes da classe, por isso cada teste usa IP e e-mail próprios.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, AuthControllerTest.Relogio.class})
class AuthControllerTest {

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
    private CadastrarUsuarioService cadastrarUsuario;
    @MockitoBean
    private AutenticarUsuarioService autenticarUsuario;
    @MockitoBean
    private VerificacaoEmailService verificacaoEmail;
    @MockitoBean
    private RedefinicaoSenhaService redefinicaoSenha;
    @MockitoBean
    private ValidarSessaoService validarSessao;

    private static RequestPostProcessor ip(String endereco) {
        return requisicao -> {
            requisicao.setRemoteAddr(endereco);
            return requisicao;
        };
    }

    private static MockHttpServletRequestBuilder json(String rota, String corpo, String ip) {
        return post(rota).contentType(MediaType.APPLICATION_JSON).content(corpo).with(ip(ip));
    }

    // ---------- POST /auth/verificar-email ----------

    @Test
    void verificarEmailEhPublicoEResponde204() throws Exception {
        mvc.perform(json("/auth/verificar-email", "{\"token\":\"AbC_123\"}", "10.0.1.1"))
                .andExpect(status().isNoContent());

        verify(verificacaoEmail).confirmar("AbC_123");
    }

    @Test
    void tokenInvalidoResponde400ComCodigo() throws Exception {
        doThrow(new TokenInvalidoException()).when(verificacaoEmail).confirmar("vencido");

        mvc.perform(json("/auth/verificar-email", "{\"token\":\"vencido\"}", "10.0.1.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"))
                .andExpect(jsonPath("$.detail").value("Link inválido ou expirado. Peça um novo link."));
    }

    @Test
    void verificarSemTokenOuComTokenGrandeDemaisResponde400SemChamarOCasoDeUso() throws Exception {
        mvc.perform(json("/auth/verificar-email", "{}", "10.0.1.3")).andExpect(status().isBadRequest());
        mvc.perform(json("/auth/verificar-email", "{\"token\":\"" + "x".repeat(129) + "\"}", "10.0.1.3"))
                .andExpect(status().isBadRequest());

        verify(verificacaoEmail, never()).confirmar(anyString());
    }

    @Test
    void verificarPorGetNaoEhRotaPublica() throws Exception {
        // só o POST é liberado: GET cai na regra geral e pede autenticação (link pré-aberto não queima token)
        mvc.perform(get("/auth/verificar-email").param("token", "AbC_123").with(ip("10.0.1.4")))
                .andExpect(status().isUnauthorized());

        verify(verificacaoEmail, never()).confirmar(anyString());
    }

    @Test
    void verificarTemLimiteDeTentativasPorIp() throws Exception {
        for (int i = 0; i < AuthController.VERIFICAR_POR_IP_POR_MINUTO; i++) {
            mvc.perform(json("/auth/verificar-email", "{\"token\":\"t" + i + "\"}", "10.0.1.5"))
                    .andExpect(status().isNoContent());
        }

        mvc.perform(json("/auth/verificar-email", "{\"token\":\"mais-um\"}", "10.0.1.5"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        verify(verificacaoEmail, never()).confirmar("mais-um");
    }

    // ---------- POST /auth/reenviar-verificacao ----------

    @Test
    void reenviarEhPublicoEResponde202ComMensagemNeutra() throws Exception {
        mvc.perform(json("/auth/reenviar-verificacao", "{\"email\":\"ana@exemplo.com\"}", "10.0.2.1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(AuthController.MENSAGEM_REENVIO));

        verify(verificacaoEmail).reenviar("ana@exemplo.com");
    }

    @Test
    void reenviarComEmailInvalidoResponde400SemChamarOCasoDeUso() throws Exception {
        mvc.perform(json("/auth/reenviar-verificacao", "{\"email\":\"nao-e-email\"}", "10.0.2.2"))
                .andExpect(status().isBadRequest());
        mvc.perform(json("/auth/reenviar-verificacao", "{}", "10.0.2.2"))
                .andExpect(status().isBadRequest());

        verify(verificacaoEmail, never()).reenviar(anyString());
    }

    @Test
    void reenviarTemLimitePorEmailMesmoComIpsDiferentes() throws Exception {
        for (int i = 0; i < AuthController.REENVIO_POR_EMAIL_POR_HORA; i++) {
            mvc.perform(json("/auth/reenviar-verificacao", "{\"email\":\"limite@exemplo.com\"}", "10.0.3." + i))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(json("/auth/reenviar-verificacao", "{\"email\":\"LIMITE@exemplo.com\"}", "10.0.3.99"))
                .andExpect(status().isTooManyRequests());
        verify(verificacaoEmail, times(AuthController.REENVIO_POR_EMAIL_POR_HORA)).reenviar(anyString());
    }

    @Test
    void reenviarTemLimitePorIp() throws Exception {
        for (int i = 0; i < AuthController.REENVIO_POR_IP_POR_HORA; i++) {
            mvc.perform(json("/auth/reenviar-verificacao", "{\"email\":\"ip" + i + "@exemplo.com\"}", "10.0.4.1"))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(json("/auth/reenviar-verificacao", "{\"email\":\"outro@exemplo.com\"}", "10.0.4.1"))
                .andExpect(status().isTooManyRequests());
    }

    // ---------- login e cadastro ----------

    @Test
    void loginDeContaNaoVerificadaResponde403ComCodigo() throws Exception {
        when(autenticarUsuario.autenticar("nova@exemplo.com", "senhaSegura123"))
                .thenThrow(new EmailNaoVerificadoException());

        mvc.perform(json("/auth/login", "{\"email\":\"nova@exemplo.com\",\"senha\":\"senhaSegura123\"}", "10.0.5.1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EMAIL_NAO_VERIFICADO"));
    }

    @Test
    void cadastroInformaQueOEmailAindaNaoFoiVerificado() throws Exception {
        var usuario = new Usuario(UUID.randomUUID(), "Ana", "ana@exemplo.com", "hash", Role.ALUNO,
                OffsetDateTime.parse("2026-10-16T21:00:00-03:00"), null);
        when(cadastrarUsuario.cadastrar("Ana", "ana@exemplo.com", "senhaSegura123")).thenReturn(usuario);

        mvc.perform(json("/auth/cadastro", "{\"nome\":\"Ana\",\"email\":\"ana@exemplo.com\",\"senha\":\"senhaSegura123\"}", "10.0.6.1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificado").value(false))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    // ---------- POST /auth/esqueci-senha ----------

    @Test
    void esqueciSenhaEhPublicoEResponde202ComMensagemNeutra() throws Exception {
        mvc.perform(json("/auth/esqueci-senha", "{\"email\":\"ana@exemplo.com\"}", "10.0.7.1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(AuthController.MENSAGEM_ESQUECI_SENHA));

        verify(redefinicaoSenha).solicitar("ana@exemplo.com");
    }

    @Test
    void esqueciSenhaRespondeIgualParaQualquerEmail() throws Exception {
        String existe = mvc.perform(json("/auth/esqueci-senha", "{\"email\":\"ana@exemplo.com\"}", "10.0.7.2"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String naoExiste = mvc.perform(json("/auth/esqueci-senha", "{\"email\":\"ninguem@exemplo.com\"}", "10.0.7.2"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(naoExiste).isEqualTo(existe);
    }

    @Test
    void esqueciSenhaComEmailInvalidoResponde400SemChamarOCasoDeUso() throws Exception {
        mvc.perform(json("/auth/esqueci-senha", "{\"email\":\"nao-e-email\"}", "10.0.7.3"))
                .andExpect(status().isBadRequest());
        mvc.perform(json("/auth/esqueci-senha", "{}", "10.0.7.3"))
                .andExpect(status().isBadRequest());

        verify(redefinicaoSenha, never()).solicitar(anyString());
    }

    @Test
    void esqueciSenhaTemLimitePorEmailMesmoComIpsDiferentes() throws Exception {
        for (int i = 0; i < AuthController.ESQUECI_SENHA_POR_EMAIL_POR_HORA; i++) {
            mvc.perform(json("/auth/esqueci-senha", "{\"email\":\"esqueci@exemplo.com\"}", "10.0.8." + i))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(json("/auth/esqueci-senha", "{\"email\":\"ESQUECI@exemplo.com\"}", "10.0.8.99"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        verify(redefinicaoSenha, times(AuthController.ESQUECI_SENHA_POR_EMAIL_POR_HORA)).solicitar(anyString());
    }

    @Test
    void esqueciSenhaTemLimitePorIp() throws Exception {
        for (int i = 0; i < AuthController.ESQUECI_SENHA_POR_IP_POR_HORA; i++) {
            mvc.perform(json("/auth/esqueci-senha", "{\"email\":\"ip" + i + "@exemplo.com\"}", "10.0.9.1"))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(json("/auth/esqueci-senha", "{\"email\":\"outro@exemplo.com\"}", "10.0.9.1"))
                .andExpect(status().isTooManyRequests());
    }

    // ---------- POST /auth/redefinir-senha ----------

    @Test
    void redefinirSenhaEhPublicoEResponde204() throws Exception {
        mvc.perform(json("/auth/redefinir-senha", "{\"token\":\"AbC_123\",\"novaSenha\":\"senhaNova123\"}", "10.0.10.1"))
                .andExpect(status().isNoContent());

        verify(redefinicaoSenha).redefinir("AbC_123", "senhaNova123");
    }

    @Test
    void redefinirComTokenInvalidoResponde400ComCodigo() throws Exception {
        doThrow(new TokenInvalidoException()).when(redefinicaoSenha).redefinir("vencido", "senhaNova123");

        mvc.perform(json("/auth/redefinir-senha", "{\"token\":\"vencido\",\"novaSenha\":\"senhaNova123\"}", "10.0.10.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test
    void redefinirComSenhaFracaResponde400ComAMensagemDaRegra() throws Exception {
        doThrow(new IllegalArgumentException("senha deve ter pelo menos 8 caracteres"))
                .when(redefinicaoSenha).redefinir("AbC_123", "curta");

        mvc.perform(json("/auth/redefinir-senha", "{\"token\":\"AbC_123\",\"novaSenha\":\"curta\"}", "10.0.10.3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("senha deve ter pelo menos 8 caracteres"))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    void redefinirSemTokenOuSemSenhaResponde400SemChamarOCasoDeUso() throws Exception {
        mvc.perform(json("/auth/redefinir-senha", "{\"novaSenha\":\"senhaNova123\"}", "10.0.10.4"))
                .andExpect(status().isBadRequest());
        mvc.perform(json("/auth/redefinir-senha", "{\"token\":\"AbC_123\"}", "10.0.10.4"))
                .andExpect(status().isBadRequest());
        mvc.perform(json("/auth/redefinir-senha", "{\"token\":\"" + "x".repeat(129) + "\",\"novaSenha\":\"senhaNova123\"}", "10.0.10.4"))
                .andExpect(status().isBadRequest());

        verify(redefinicaoSenha, never()).redefinir(anyString(), anyString());
    }

    @Test
    void redefinirTemLimiteDeTentativasPorIp() throws Exception {
        for (int i = 0; i < AuthController.REDEFINIR_POR_IP_POR_MINUTO; i++) {
            mvc.perform(json("/auth/redefinir-senha", "{\"token\":\"t" + i + "\",\"novaSenha\":\"senhaNova123\"}", "10.0.11.1"))
                    .andExpect(status().isNoContent());
        }

        mvc.perform(json("/auth/redefinir-senha", "{\"token\":\"mais-um\",\"novaSenha\":\"senhaNova123\"}", "10.0.11.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        verify(redefinicaoSenha, never()).redefinir(org.mockito.ArgumentMatchers.eq("mais-um"), anyString());
    }
}
