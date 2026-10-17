package eduar.studymindredacao.adapter.out.email;

/** E-mail pronto para envio. Texto e HTML levam o link com o token, por isso o toString não os mostra. */
record EmailMontado(String destinatario, String assunto, String texto, String html) {
    @Override
    public String toString() {
        return "EmailMontado[destinatario=" + EnderecosEmail.mascarar(destinatario) + ", assunto=" + assunto
                + ", texto=***, html=***]";
    }
}
