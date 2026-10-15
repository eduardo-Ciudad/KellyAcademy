package eduar.studymindredacao.domain.model;

/**
 * Contagens que a IA devolve junto com cada competência (prompt v3, issue #27) para o backend
 * conferir a nota pelas regras objetivas da grade de correção.
 * <p>
 * Cada competência só preenche os campos que lhe dizem respeito; {@code null} significa
 * "não informado" (formato antigo ou campo ausente) e a regra correspondente não se aplica.
 *
 * @param falhasEstruturais        C1: problemas do tipo falha_estrutural
 * @param desvios                  C1: problemas do tipo desvio
 * @param repertoriosProdutivos    C2: repertórios marcados como produtivos e com trecho
 * @param lacunas                  C3: problemas do tipo lacuna
 * @param repeticoes               C4: problemas do tipo repeticao
 * @param inadequacoes             C4: problemas do tipo inadequacao
 * @param operadoresInterparagrafos C4: operadores argumentativos entre parágrafos, com trecho
 * @param elementosProposta        C5: elementos da proposta com trecho (0 a 5)
 */
public record EvidenciaCompetencia(
        Integer falhasEstruturais,
        Integer desvios,
        Integer repertoriosProdutivos,
        Integer lacunas,
        Integer repeticoes,
        Integer inadequacoes,
        Integer operadoresInterparagrafos,
        Integer elementosProposta) {

    public static final EvidenciaCompetencia NAO_INFORMADA =
            new EvidenciaCompetencia(null, null, null, null, null, null, null, null);

    public EvidenciaCompetencia {
        naoNegativo(falhasEstruturais, "falhasEstruturais");
        naoNegativo(desvios, "desvios");
        naoNegativo(repertoriosProdutivos, "repertoriosProdutivos");
        naoNegativo(lacunas, "lacunas");
        naoNegativo(repeticoes, "repeticoes");
        naoNegativo(inadequacoes, "inadequacoes");
        naoNegativo(operadoresInterparagrafos, "operadoresInterparagrafos");
        naoNegativo(elementosProposta, "elementosProposta");
        if (elementosProposta != null && elementosProposta > 5) {
            throw new IllegalArgumentException("elementosProposta deve estar entre 0 e 5");
        }
    }

    private static void naoNegativo(Integer valor, String campo) {
        if (valor != null && valor < 0) {
            throw new IllegalArgumentException(campo + " não pode ser negativo");
        }
    }
}
