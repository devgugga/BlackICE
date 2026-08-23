/*
 * DO NOT EDIT.
 *
 * Gerado por .problem-catalog a partir de docs/contracts/problems/catalog.json.
 * Altere o catálogo e execute `pnpm generate` em .problem-catalog/.
 */

package dev.blackice.shared.api.problem.generated;

import java.net.URI;

/**
 * Tipos de problema publicados no catálogo oficial.
 *
 * <p>O nome da constante é o {@code code} do catálogo. Os textos são públicos,
 * estáveis e nunca derivam de uma exceção.
 */
public enum ProblemType {

    API_ARCHIVE_UNAVAILABLE(
            "urn:uuid:cbe2c734-1873-570b-a498-a27a96ebadd4",
            ProblemScope.API,
            503,
            "Archive unavailable",
            "The imaging archive is temporarily unavailable.",
            RetryPolicy.MANUAL),
    API_DICOM_VALIDATION_FAILED(
            "urn:uuid:d365af5c-a52e-549e-a322-cde01e2cba1e",
            ProblemScope.API,
            422,
            "DICOM validation failed",
            "None of the uploaded files passed validation.",
            RetryPolicy.NEVER),
    API_SEARCH_INVALID(
            "urn:uuid:47212223-d0b8-54f1-a288-d9781204bb9e",
            ProblemScope.API,
            400,
            "Invalid search",
            "Review the supplied search filters.",
            RetryPolicy.NEVER),
    /** Depreciado no catálogo: use API_SEARCH_INVALID. */
    @Deprecated
    API_SEARCH_MALFORMED(
            "urn:uuid:a28f9550-6865-5a9c-94bf-98824b1c6775",
            ProblemScope.API,
            400,
            "Malformed search",
            "Review the supplied search filters.",
            RetryPolicy.NEVER),
    CLIENT_NETWORK_UNAVAILABLE(
            "urn:uuid:850ffcf4-95bb-5902-90df-d06f1b9aeb2c",
            ProblemScope.CLIENT,
            null,
            null,
            null,
            RetryPolicy.MANUAL);

    /** Resposta HTTP observável ({@code API}) ou falha local do cliente ({@code CLIENT}). */
    public enum ProblemScope {
        API,
        CLIENT
    }

    /** {@code AUTOMATIC} é reservado e não existe nesta versão. */
    public enum RetryPolicy {
        NEVER,
        MANUAL
    }

    private final URI type;
    private final ProblemScope scope;
    private final Integer httpStatus;
    private final String title;
    private final String detail;
    private final RetryPolicy retryPolicy;

    ProblemType(
            String type,
            ProblemScope scope,
            Integer httpStatus,
            String title,
            String detail,
            RetryPolicy retryPolicy) {
        this.type = URI.create(type);
        this.scope = scope;
        this.httpStatus = httpStatus;
        this.title = title;
        this.detail = detail;
        this.retryPolicy = retryPolicy;
    }

    /** URN {@code urn:uuid} estável do tipo. */
    public URI type() {
        return type;
    }

    /** Código legível, idêntico ao nome da constante. */
    public String code() {
        return name();
    }

    public ProblemScope scope() {
        return scope;
    }

    /** {@code null} para tipos {@code CLIENT}, que não são respostas HTTP. */
    public Integer httpStatus() {
        return httpStatus;
    }

    /** {@code null} para tipos {@code CLIENT}. */
    public String title() {
        return title;
    }

    /** {@code null} para tipos {@code CLIENT}. */
    public String detail() {
        return detail;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }
}
