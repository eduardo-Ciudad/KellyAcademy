package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.service.AnalisadorTexto;

/**
 * O que vai para a correção por IA: tema, texto do aluno e a pré-análise determinística do texto
 * (repetições e estrutura), enviada à IA como dado exato.
 */
public record SolicitacaoAvaliacao(String tema, String textoRedacao, AnaliseTexto analise) {

    /** Calcula a pré-análise do texto automaticamente. */
    public SolicitacaoAvaliacao(String tema, String textoRedacao) {
        this(tema, textoRedacao, AnalisadorTexto.analisar(textoRedacao, tema));
    }

    public SolicitacaoAvaliacao{
        if (tema == null || tema.isBlank()) {
            throw new IllegalArgumentException("tema é obrigatorio");
        }
        if (textoRedacao == null || textoRedacao.isBlank()) {
            throw new IllegalArgumentException("textoRedacao é obrigatório");
        }
        tema = tema.strip();
        textoRedacao = textoRedacao.strip();
        analise = analise == null ? AnaliseTexto.VAZIA : analise;
    }
}
