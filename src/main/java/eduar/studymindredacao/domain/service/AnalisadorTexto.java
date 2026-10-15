package eduar.studymindredacao.domain.service;

import eduar.studymindredacao.domain.model.AnaliseTexto;
import eduar.studymindredacao.domain.model.AnaliseTexto.Repeticao;
import eduar.studymindredacao.domain.model.AnaliseTexto.TipoRepeticao;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pré-análise determinística do texto do aluno (contagem exata, sem IA).
 * <p>
 * Encontra repetições que a IA costuma deixar passar. Não decide se a repetição é um problema:
 * isso fica com a IA, que distingue vocabulário necessário do tema de repetição inadequada.
 * <p>
 * As formas devolvidas contêm só letras, hífen e espaço: vão para o prompt fora da tag do texto
 * do aluno e não podem carregar pontuação nem tags.
 */
public final class AnalisadorTexto {
    static final int MIN_OCORRENCIAS_PALAVRA = 3;
    static final int MIN_LETRAS_PALAVRA = 5;
    static final int MIN_LETRAS_MESMO_PARAGRAFO = 7;
    static final int MIN_LETRAS_EXPRESSAO = 3;
    static final int MAX_POR_TIPO = 5;
    static final int MAX_REPETICOES = MAX_POR_TIPO * TipoRepeticao.values().length;
    static final int PALAVRAS_DA_ABERTURA = 3;
    static final int MAX_TAMANHO_FORMA = 30;

    private static final Pattern PALAVRA = Pattern.compile("\\p{L}+(?:-\\p{L}+)*");
    /** Pontuação que separa trechos: uma expressão repetida não pode atravessá-la. */
    private static final Pattern PONTUACAO = Pattern.compile("[.,;:!?()\\[\\]\"“”«»—–]");

    /** Palavras gramaticais (sem acento) que nunca contam como repetição. */
    private static final Set<String> GRAMATICAIS = Set.of(
            "a", "o", "as", "os", "um", "uma", "uns", "umas", "ao", "aos",
            "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "num", "numa",
            "por", "pelo", "pela", "pelos", "pelas", "para", "com", "sem", "sob", "sobre",
            "entre", "ate", "apos", "ante", "contra", "desde", "perante",
            "e", "ou", "mas", "que", "se", "como", "quando", "porque", "pois", "porem",
            "tambem", "ja", "nao", "mais", "menos", "muito", "muitos", "muita", "muitas",
            "ser", "sao", "foi", "foram", "era", "eram", "sera", "seja", "sejam",
            "estar", "esta", "estao", "estava", "ter", "tem", "tinha", "ha", "haver",
            "este", "estes", "estas", "isto", "esse", "essa", "esses", "essas", "isso",
            "aquele", "aquela", "aqueles", "aquelas", "aquilo",
            "seu", "sua", "seus", "suas", "ele", "ela", "eles", "elas", "lhe", "lhes",
            "qual", "quais", "cujo", "cuja", "onde", "mesmo", "mesma", "assim",
            "desse", "dessa", "desses", "dessas", "deste", "desta", "nesse", "nessa", "neste", "nesta",
            "todo", "toda", "todos", "todas", "outro", "outra", "outros", "outras", "cada", "ainda", "apenas"
    );

    private AnalisadorTexto() {
    }

    /**
     * Uma palavra do texto: a forma original (minúsculas), a forma sem acento usada nas comparações
     * e o trecho do parágrafo (entre pontuações) em que aparece.
     */
    private record Termo(String forma, String normal, int trecho) {
    }

    /** Acumula as ocorrências de um radical ou expressão. */
    private static final class Contagem {
        private final Set<String> formas = new LinkedHashSet<>();
        private final Set<Integer> paragrafos = new TreeSet<>();
        private int ocorrencias;

        void somar(String forma, int paragrafo) {
            formas.add(forma);
            paragrafos.add(paragrafo);
            ocorrencias++;
        }
    }

    public static AnaliseTexto analisar(String texto, String tema) {
        if (texto == null || texto.isBlank()) {
            return AnaliseTexto.VAZIA;
        }
        List<List<Termo>> paragrafos = paragrafos(texto);
        Set<String> radicaisDoTema = radicaisDoTema(tema);

        int palavras = 0;
        List<String> aberturas = new ArrayList<>();
        for (List<Termo> paragrafo : paragrafos) {
            palavras += paragrafo.size();
            aberturas.add(limpar(String.join(" ", paragrafo.subList(0, Math.min(PALAVRAS_DA_ABERTURA, paragrafo.size()))
                    .stream().map(Termo::forma).toList())));
        }

        List<Repeticao> repeticoes = new ArrayList<>();
        Set<String> radicaisJaListados = new HashSet<>();
        repeticoesDePalavra(paragrafos, radicaisDoTema, repeticoes, radicaisJaListados);
        repeticoesDeExpressao(paragrafos, radicaisDoTema, repeticoes);
        repeticoesNoMesmoParagrafo(paragrafos, radicaisDoTema, repeticoes, radicaisJaListados);

        // Até 5 por tipo: senão as palavras soltas (mais frequentes) escondem expressões e repetições locais
        List<Repeticao> principais = new ArrayList<>();
        for (TipoRepeticao tipo : TipoRepeticao.values()) {
            repeticoes.stream()
                    .filter(r -> r.tipo() == tipo)
                    .sorted(Comparator.comparingInt(Repeticao::ocorrencias).reversed())
                    .limit(MAX_POR_TIPO)
                    .forEach(principais::add);
        }
        principais.sort(Comparator.comparingInt(Repeticao::ocorrencias).reversed()
                .thenComparing(Repeticao::tipo));
        return new AnaliseTexto(paragrafos.size(), palavras, aberturas, principais);
    }

    private static void repeticoesDePalavra(List<List<Termo>> paragrafos, Set<String> radicaisDoTema,
                                            List<Repeticao> saida, Set<String> listados) {
        Map<String, Contagem> porRadical = new LinkedHashMap<>();
        for (int p = 0; p < paragrafos.size(); p++) {
            for (Termo termo : paragrafos.get(p)) {
                if (termo.normal().length() >= MIN_LETRAS_PALAVRA && conta(termo, radicaisDoTema)) {
                    porRadical.computeIfAbsent(radical(termo.normal()), r -> new Contagem()).somar(termo.forma(), p + 1);
                }
            }
        }
        porRadical.forEach((radical, contagem) -> {
            if (contagem.ocorrencias >= MIN_OCORRENCIAS_PALAVRA) {
                saida.add(repeticao(contagem, TipoRepeticao.PALAVRA));
                listados.add(radical);
            }
        });
    }

    private static void repeticoesDeExpressao(List<List<Termo>> paragrafos, Set<String> radicaisDoTema,
                                              List<Repeticao> saida) {
        Map<String, Contagem> porExpressao = new LinkedHashMap<>();
        for (int p = 0; p < paragrafos.size(); p++) {
            List<Termo> termos = paragrafos.get(p);
            for (int i = 0; i + 1 < termos.size(); i++) {
                Termo primeiro = termos.get(i);
                Termo segundo = termos.get(i + 1);
                boolean doTema = radicaisDoTema.contains(radical(primeiro.normal()))
                        && radicaisDoTema.contains(radical(segundo.normal()));
                boolean mesmoTrecho = primeiro.trecho() == segundo.trecho();
                if (mesmoTrecho && ehExpressao(primeiro) && ehExpressao(segundo) && !doTema) {
                    porExpressao.computeIfAbsent(primeiro.normal() + " " + segundo.normal(), e -> new Contagem())
                            .somar(primeiro.forma() + " " + segundo.forma(), p + 1);
                }
            }
        }
        // "apoio pedagógico" e "pedagógico adequado" nos mesmos parágrafos são a mesma repetição: fica a primeira
        List<String> chavesMantidas = new ArrayList<>();
        List<Contagem> mantidas = new ArrayList<>();
        porExpressao.forEach((chave, contagem) -> {
            if (contagem.paragrafos.size() < 2) {
                return;
            }
            for (int i = 0; i < mantidas.size(); i++) {
                if (mantidas.get(i).paragrafos.equals(contagem.paragrafos) && compartilhamPalavra(chavesMantidas.get(i), chave)) {
                    return;
                }
            }
            chavesMantidas.add(chave);
            mantidas.add(contagem);
        });
        mantidas.forEach(c -> saida.add(repeticao(c, TipoRepeticao.EXPRESSAO)));
    }

    private static void repeticoesNoMesmoParagrafo(List<List<Termo>> paragrafos, Set<String> radicaisDoTema,
                                                   List<Repeticao> saida, Set<String> listados) {
        for (int p = 0; p < paragrafos.size(); p++) {
            Map<String, Contagem> porRadical = new LinkedHashMap<>();
            for (Termo termo : paragrafos.get(p)) {
                if (termo.normal().length() >= MIN_LETRAS_MESMO_PARAGRAFO && conta(termo, radicaisDoTema)) {
                    porRadical.computeIfAbsent(radical(termo.normal()), r -> new Contagem()).somar(termo.forma(), p + 1);
                }
            }
            porRadical.forEach((radical, contagem) -> {
                if (contagem.ocorrencias >= 2 && !listados.contains(radical)) {
                    saida.add(repeticao(contagem, TipoRepeticao.MESMO_PARAGRAFO));
                }
            });
        }
    }

    private static Repeticao repeticao(Contagem contagem, TipoRepeticao tipo) {
        List<String> formas = contagem.formas.stream().map(AnalisadorTexto::limpar).distinct().toList();
        return new Repeticao(formas, contagem.ocorrencias, new ArrayList<>(contagem.paragrafos), tipo);
    }

    private static boolean compartilhamPalavra(String expressao, String outra) {
        Set<String> palavras = new HashSet<>(List.of(expressao.split(" ")));
        for (String palavra : outra.split(" ")) {
            if (palavras.contains(palavra)) {
                return true;
            }
        }
        return false;
    }

    private static boolean conta(Termo termo, Set<String> radicaisDoTema) {
        return !GRAMATICAIS.contains(termo.normal()) && !radicaisDoTema.contains(radical(termo.normal()));
    }

    private static boolean ehExpressao(Termo termo) {
        return termo.normal().length() >= MIN_LETRAS_EXPRESSAO && !GRAMATICAIS.contains(termo.normal());
    }

    private static List<List<Termo>> paragrafos(String texto) {
        List<List<Termo>> paragrafos = new ArrayList<>();
        for (String linha : texto.split("\\R+")) {
            List<Termo> termos = termos(linha);
            if (!termos.isEmpty()) {
                paragrafos.add(termos);
            }
        }
        return paragrafos;
    }

    private static List<Termo> termos(String paragrafo) {
        List<Termo> termos = new ArrayList<>();
        String[] trechos = PONTUACAO.split(paragrafo);
        for (int t = 0; t < trechos.length; t++) {
            Matcher matcher = PALAVRA.matcher(trechos[t]);
            while (matcher.find()) {
                String forma = matcher.group().toLowerCase(Locale.ROOT);
                termos.add(new Termo(forma, semAcento(forma), t));
            }
        }
        return termos;
    }

    private static Set<String> radicaisDoTema(String tema) {
        Set<String> radicais = new HashSet<>();
        if (tema != null) {
            for (Termo termo : termos(tema)) {
                if (!GRAMATICAIS.contains(termo.normal())) {
                    radicais.add(radical(termo.normal()));
                }
            }
        }
        return radicais;
    }

    /**
     * Radical simplificado para agrupar flexões: "adequado", "adequada" e "adequadas" viram "adequad";
     * "professores" e "professor" viram "professor". Não é um lematizador; basta para contar repetição.
     */
    static String radical(String normal) {
        String r = normal;
        if (r.length() > 5 && r.endsWith("es") && "rsz".indexOf(r.charAt(r.length() - 3)) >= 0) {
            r = r.substring(0, r.length() - 2);
        } else if (r.length() > 4 && r.endsWith("s")) {
            r = r.substring(0, r.length() - 1);
        }
        if (r.length() > 4 && (r.endsWith("a") || r.endsWith("o"))) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Só letras, hífen e espaço, no máximo 30 caracteres: seguro para ir ao prompt fora da tag do texto. */
    private static String limpar(String forma) {
        String limpo = forma.replaceAll("[^\\p{L}\\- ]", "").strip();
        return limpo.length() > MAX_TAMANHO_FORMA ? limpo.substring(0, MAX_TAMANHO_FORMA) : limpo;
    }
}
