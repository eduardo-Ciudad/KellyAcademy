package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.EvidenciaCompetencia;
import eduar.studymindredacao.domain.model.ResultadoAvaliacaoIA;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte o texto gerado pelo Gemini numa correção ({@link ResultadoAvaliacaoIA}).
 * O envelope HTTP já foi lido pelo {@link GeminiEnvelopeParser}; aqui só se interpreta o JSON da avaliação.
 * Conteúdo fora do formato vira {@link GeminiException} repetível.
 */
class GeminiRespostaParser {
    private static final Pattern NUMERO_COMPETENCIA = Pattern.compile("(?i)^\\s*c?\\s*([1-5])\\s*$");

    private final JsonMapper json;

    GeminiRespostaParser(JsonMapper json) {
        this.json = json;
    }

    ResultadoAvaliacaoIA interpretar(GeminiResposta resposta) {
        JsonNode avaliacao = lerJson(resposta.textoGerado());

        try {
            return new ResultadoAvaliacaoIA(
                    avaliacao.path("anulada").asBoolean(false),
                    texto(avaliacao.path("motivo_anulacao")),
                    competencias(avaliacao),
                    listaDeTexto(avaliacao.path("pontos_fortes")),
                    listaDeTexto(avaliacao.path("pontos_desenvolvimento")),
                    texto(avaliacao.path("diagnostico")),
                    resposta.modelo(),
                    resposta.tokensEntrada(),
                    resposta.tokensSaida(),
                    json.writeValueAsString(avaliacao)
            );
        } catch (IllegalArgumentException e) {
            throw GeminiException.repetivel("avaliação gerada é inconsistente: " + e.getMessage(), e);
        }
    }

    private List<CompetenciaAvaliada> competencias(JsonNode avaliacao) {
        Map<Integer, List<String>> problemasDoFormatoV2 = problemasDoFormatoV2(avaliacao);

        List<CompetenciaAvaliada> competencias = new ArrayList<>();
        for (JsonNode item : avaliacao.path("avaliacao_por_competencia")) {
            Integer numero = numeroDaCompetencia(item.path("competencia"));
            if (numero == null) {
                throw new IllegalArgumentException("competência inválida: " + item.path("competencia"));
            }
            if (!item.path("nota").isNumber()) {
                throw new IllegalArgumentException("nota ausente ou não numérica na C" + numero);
            }
            List<String> problemas = new ArrayList<>(problemasDaCompetencia(item.path("problemas")));
            problemas.addAll(problemasDoFormatoV2.getOrDefault(numero, List.of()));
            competencias.add(new CompetenciaAvaliada(
                    numero,
                    item.path("nota").asInt(),
                    texto(item.path("nivel_referencia")),
                    texto(item.path("resumo")),
                    problemas,
                    evidenciaDaCompetencia(numero, item)
            ));
        }
        return competencias;
    }

    /**
     * Prompt v3: problemas dentro de cada competência, como {"trecho", "descricao"}.
     * Cada um vira "descricao — "trecho"" para caber na lista de texto que já é persistida.
     */
    private static List<String> problemasDaCompetencia(JsonNode problemas) {
        List<String> itens = new ArrayList<>();
        for (JsonNode problema : problemas) {
            if (problema.isString()) {
                String valor = texto(problema);
                if (valor != null) {
                    itens.add(valor);
                }
                continue;
            }
            String descricao = texto(problema.path("descricao"));
            String trecho = texto(problema.path("trecho"));
            if (descricao != null && trecho != null) {
                itens.add(descricao + " — \"" + trecho + "\"");
            } else if (descricao != null) {
                itens.add(descricao);
            } else if (trecho != null) {
                itens.add("\"" + trecho + "\"");
            }
        }
        return itens;
    }

    /**
     * Prompt v3 (#27): conta os tipos de problema e as evidências estruturadas da competência.
     * Campo ausente vira null ("não informado"), para a regra correspondente não ser aplicada.
     */
    private static EvidenciaCompetencia evidenciaDaCompetencia(int numero, JsonNode item) {
        JsonNode problemas = item.path("problemas");
        JsonNode evidencias = item.path("evidencias");
        boolean temProblemas = problemas.isArray();
        return switch (numero) {
            case 1 -> new EvidenciaCompetencia(
                    temProblemas ? contarTipo(problemas, "falha_estrutural") : null,
                    temProblemas ? contarTipo(problemas, "desvio") : null,
                    null, null, null, null, null, null);
            case 2 -> new EvidenciaCompetencia(null, null,
                    evidencias.path("repertorios").isArray() ? contarRepertoriosProdutivos(evidencias.path("repertorios")) : null,
                    null, null, null, null, null);
            case 3 -> new EvidenciaCompetencia(null, null, null,
                    temProblemas ? contarTipo(problemas, "lacuna") : null,
                    null, null, null, null);
            case 4 -> new EvidenciaCompetencia(null, null, null, null,
                    temProblemas ? contarTipo(problemas, "repeticao") : null,
                    temProblemas ? contarTipo(problemas, "inadequacao") : null,
                    evidencias.path("operadores_interparagrafos").isArray()
                            ? contarTextos(evidencias.path("operadores_interparagrafos")) : null,
                    null);
            case 5 -> new EvidenciaCompetencia(null, null, null, null, null, null, null,
                    evidencias.path("elementos").isObject() ? contarElementos(evidencias.path("elementos")) : null);
            default -> EvidenciaCompetencia.NAO_INFORMADA;
        };
    }

    private static int contarTipo(JsonNode problemas, String tipo) {
        int total = 0;
        for (JsonNode problema : problemas) {
            if (tipo.equals(normalizarTipo(texto(problema.path("tipo"))))) {
                total++;
            }
        }
        return total;
    }

    private static int contarRepertoriosProdutivos(JsonNode repertorios) {
        int total = 0;
        for (JsonNode repertorio : repertorios) {
            if (repertorio.path("produtivo").asBoolean(false) && texto(repertorio.path("trecho")) != null) {
                total++;
            }
        }
        return total;
    }

    private static int contarTextos(JsonNode itens) {
        int total = 0;
        for (JsonNode item : itens) {
            if (textoValido(item)) {
                total++;
            }
        }
        return total;
    }

    /**
     * Agente, ação, meio, finalidade e detalhamento: conta os que vieram com trecho próprio.
     * Um trecho contido no trecho de outro elemento (ex.: "detalhamento" que é só um pedaço do agente)
     * não conta: cada parte do texto vale para um elemento só.
     */
    private static int contarElementos(JsonNode elementos) {
        String[][] chaves = {
                {"agente"}, {"acao", "ação"}, {"meio", "modo"}, {"finalidade", "efeito"}, {"detalhamento"}
        };
        List<String> trechos = new ArrayList<>();
        for (String[] alternativas : chaves) {
            for (String chave : alternativas) {
                if (textoValido(elementos.path(chave))) {
                    trechos.add(normalizarTrecho(texto(elementos.path(chave))));
                    break;
                }
            }
        }
        int total = 0;
        for (int i = 0; i < trechos.size(); i++) {
            if (!trechos.get(i).isEmpty() && !reaproveitado(trechos, i)) {
                total++;
            }
        }
        return total;
    }

    /** O trecho i está dentro de outro maior, ou repete um anterior idêntico. */
    private static boolean reaproveitado(List<String> trechos, int i) {
        String trecho = trechos.get(i);
        for (int j = 0; j < trechos.size(); j++) {
            String outro = trechos.get(j);
            if (j == i || !outro.contains(trecho)) {
                continue;
            }
            if (outro.length() > trecho.length() || j < i) {
                return true;
            }
        }
        return false;
    }

    /** Minúsculas, sem acento e só letras/números separados por um espaço, para comparar trechos. */
    private static String normalizarTrecho(String trecho) {
        String semAcento = Normalizer.normalize(trecho, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    /** Texto preenchido e que não seja a palavra "null" escrita pelo modelo. */
    private static boolean textoValido(JsonNode no) {
        String valor = texto(no);
        return valor != null && !valor.equalsIgnoreCase("null");
    }

    /** "Falha estrutural", "falha-estrutural" e "FALHA_ESTRUTURAL" viram "falha_estrutural"; "repetição" vira "repeticao". */
    private static String normalizarTipo(String tipo) {
        if (tipo == null) {
            return null;
        }
        String semAcento = Normalizer.normalize(tipo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    /** Prompt v2: lista global "problemas_identificados" com a competência em cada item. */
    private static Map<Integer, List<String>> problemasDoFormatoV2(JsonNode avaliacao) {
        Map<Integer, List<String>> problemasPorCompetencia = new HashMap<>();
        for (JsonNode problema : avaliacao.path("problemas_identificados")) {
            Integer numero = numeroDaCompetencia(problema.path("competencia"));
            String descricao = texto(problema.path("descricao"));
            if (numero != null && descricao != null) {
                problemasPorCompetencia.computeIfAbsent(numero, n -> new ArrayList<>()).add(descricao);
            }
        }
        return problemasPorCompetencia;
    }

    private static Integer numeroDaCompetencia(JsonNode no) {
        if (no.isInt()) {
            int numero = no.asInt();
            return numero >= 1 && numero <= 5 ? numero : null;
        }
        String valor = texto(no);
        if (valor == null) {
            return null;
        }
        Matcher matcher = NUMERO_COMPETENCIA.matcher(valor);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static List<String> listaDeTexto(JsonNode no) {
        List<String> itens = new ArrayList<>();
        for (JsonNode item : no) {
            String valor = texto(item);
            if (valor != null) {
                itens.add(valor);
            }
        }
        return itens;
    }

    /** Texto do nó, ou null se ausente, nulo ou em branco (evita a string "null" do NullNode). */
    private static String texto(JsonNode no) {
        if (no == null || no.isMissingNode() || no.isNull()) {
            return null;
        }
        String valor = no.asString("").strip();
        return valor.isEmpty() ? null : valor;
    }

    private JsonNode lerJson(String conteudo) {
        try {
            return json.readTree(conteudo);
        } catch (JacksonException e) {
            throw GeminiException.repetivel("avaliação gerada não é JSON válido", e);
        }
    }
}