package eduar.studymindredacao.adapter.out.email;

/** Utilidades de e-mail para log: nunca registrar o endereço inteiro do aluno. */
final class EnderecosEmail {
    private EnderecosEmail() {
    }

    /** "ana@gmail.com" vira "***@gmail.com"; sem "@" (ou nulo) vira "***". */
    static String mascarar(String email) {
        if (email == null) {
            return "***";
        }
        int arroba = email.lastIndexOf('@');
        if (arroba < 0 || arroba == email.length() - 1) {
            return "***";
        }
        return "***" + email.substring(arroba).replaceAll("[\\p{Cntrl}\\s]", "");
    }
}
