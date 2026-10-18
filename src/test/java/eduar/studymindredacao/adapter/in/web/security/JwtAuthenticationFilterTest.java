package eduar.studymindredacao.adapter.in.web.security;

import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    private static final UUID ID = UUID.randomUUID();

    private final ValidarSessaoService validarSessao = mock(ValidarSessaoService.class);
    private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(validarSessao);
    private final MockHttpServletResponse resposta = new MockHttpServletResponse();
    private final MockFilterChain cadeia = new MockFilterChain();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest comHeader(String authorization) {
        var requisicao = new MockHttpServletRequest("GET", "/usuarios/me");
        if (authorization != null) {
            requisicao.addHeader("Authorization", authorization);
        }
        return requisicao;
    }

    @Test
    void tokenValidoAutenticaComOUsuarioEARole() throws Exception {
        when(validarSessao.validar("jwt-bom")).thenReturn(Optional.of(new DadosToken(ID, "ana@exemplo.com", Role.ALUNO, 2)));

        filtro.doFilter(comHeader("Bearer jwt-bom"), resposta, cadeia);

        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        assertThat(autenticacao).isNotNull();
        assertThat(autenticacao.getPrincipal()).isEqualTo(new UsuarioAutenticado(ID, "ana@exemplo.com", Role.ALUNO));
        assertThat(autenticacao.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ALUNO");
        assertThat(cadeia.getRequest()).as("a cadeia continua").isNotNull();
    }

    @Test
    void sessaoDerrubadaSegueSemAutenticacao() throws Exception {
        when(validarSessao.validar("jwt-antigo")).thenReturn(Optional.empty());

        filtro.doFilter(comHeader("Bearer jwt-antigo"), resposta, cadeia);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadeia.getRequest()).as("o entry point é que responde 401").isNotNull();
    }

    @Test
    void semHeaderNemChamaAValidacao() throws Exception {
        filtro.doFilter(comHeader(null), resposta, cadeia);

        verify(validarSessao, never()).validar(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadeia.getRequest()).isNotNull();
    }

    @Test
    void headerQueNaoEBearerNemChamaAValidacao() throws Exception {
        filtro.doFilter(comHeader("Basic dXN1YXJpbzpzZW5oYQ=="), resposta, cadeia);

        verify(validarSessao, never()).validar(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadeia.getRequest()).isNotNull();
    }

    @Test
    void removeEspacosEmVoltaDoToken() throws Exception {
        when(validarSessao.validar("jwt-bom")).thenReturn(Optional.of(new DadosToken(ID, "ana@exemplo.com", Role.ALUNO)));

        filtro.doFilter(comHeader("Bearer   jwt-bom  "), resposta, cadeia);

        verify(validarSessao).validar("jwt-bom");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
