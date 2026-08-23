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

    API_ACCESS_DENIED(
            "urn:uuid:8c7666e3-0c02-5a7b-8a7e-d511314c4ccc",
            ProblemScope.API,
            403,
            "Access denied",
            "You do not have permission to access this resource.",
            RetryPolicy.NEVER),
    API_ARCHIVE_RESPONSE_INVALID(
            "urn:uuid:8a220e49-3e80-5e59-83e5-43483c4a6dd8",
            ProblemScope.API,
            502,
            "Invalid Archive response",
            "The imaging archive returned an unexpected response.",
            RetryPolicy.MANUAL),
    API_ARCHIVE_UNAVAILABLE(
            "urn:uuid:8dd49378-697d-5e0e-aa0f-5ab72a5e98a6",
            ProblemScope.API,
            503,
            "Archive unavailable",
            "The imaging archive is temporarily unavailable.",
            RetryPolicy.MANUAL),
    API_AUTHENTICATION_REQUIRED(
            "urn:uuid:a665ee9e-36bf-599f-814a-e5d5da82f3cb",
            ProblemScope.API,
            401,
            "Authentication required",
            "Authentication is required to access this resource.",
            RetryPolicy.NEVER),
    API_CSRF_INVALID(
            "urn:uuid:dac0c0fc-38bf-5d19-95f4-45568abb2380",
            ProblemScope.API,
            403,
            "Request verification failed",
            "The request could not be verified.",
            RetryPolicy.MANUAL),
    API_DICOM_VALIDATION_FAILED(
            "urn:uuid:0908ac46-fe0e-5516-b4f8-e4f25882ed8e",
            ProblemScope.API,
            422,
            "DICOM validation failed",
            "None of the uploaded files passed validation.",
            RetryPolicy.NEVER),
    API_INTERNAL_ERROR(
            "urn:uuid:4685c43c-aff3-5a91-a688-1f05cb9bfe78",
            ProblemScope.API,
            500,
            "Internal server error",
            "An unexpected error occurred.",
            RetryPolicy.MANUAL),
    API_MEDIA_TYPE_UNSUPPORTED(
            "urn:uuid:c9210550-428c-5b67-9d83-d5af3770134f",
            ProblemScope.API,
            415,
            "Unsupported media type",
            "The request media type is not supported.",
            RetryPolicy.NEVER),
    API_METHOD_NOT_ALLOWED(
            "urn:uuid:ad905e04-1351-5fab-a371-1d5c50382c6a",
            ProblemScope.API,
            405,
            "Method not allowed",
            "The requested method is not allowed for this resource.",
            RetryPolicy.NEVER),
    API_PAYLOAD_TOO_LARGE(
            "urn:uuid:db695bb8-95d6-56b3-b938-f76b77e2d09b",
            ProblemScope.API,
            413,
            "Payload too large",
            "The request exceeds the permitted size.",
            RetryPolicy.NEVER),
    API_REPRESENTATION_NOT_ACCEPTABLE(
            "urn:uuid:9f6fda99-1849-58a1-85fe-deb9a5897740",
            ProblemScope.API,
            406,
            "Representation not acceptable",
            "The requested response format is not supported.",
            RetryPolicy.NEVER),
    API_REQUEST_INVALID(
            "urn:uuid:4ab4a9cc-9774-5c32-b0fa-83594c7bf6e1",
            ProblemScope.API,
            400,
            "Invalid request",
            "The request is invalid or malformed.",
            RetryPolicy.NEVER),
    API_RESOURCE_NOT_FOUND(
            "urn:uuid:25c41e16-7fd0-51f7-9731-1a9c0c0e8dd4",
            ProblemScope.API,
            404,
            "Resource not found",
            "The requested resource was not found.",
            RetryPolicy.NEVER),
    API_SEARCH_INVALID(
            "urn:uuid:5fdeb44a-6add-5d54-a7f4-5f15f7cdc830",
            ProblemScope.API,
            400,
            "Invalid search",
            "Review the supplied search filters.",
            RetryPolicy.NEVER),
    API_SEARCH_TOO_BROAD(
            "urn:uuid:3059d5b4-bb73-52e9-b8ed-a73539b98460",
            ProblemScope.API,
            413,
            "Search too broad",
            "Refine the search filters and try again.",
            RetryPolicy.NEVER),
    API_UPLOAD_EMPTY(
            "urn:uuid:74e415dc-1966-5124-be1f-8732a25fc777",
            ProblemScope.API,
            400,
            "Empty upload",
            "Select at least one file to upload.",
            RetryPolicy.NEVER),
    CLIENT_CSRF_COOKIE_MISSING(
            "urn:uuid:7c5107ac-deab-5dc7-b9b9-f87018515842",
            ProblemScope.CLIENT,
            null,
            null,
            null,
            RetryPolicy.MANUAL),
    CLIENT_NETWORK_UNAVAILABLE(
            "urn:uuid:958f8ed3-f5ea-50a5-af1c-6828e25df077",
            ProblemScope.CLIENT,
            null,
            null,
            null,
            RetryPolicy.MANUAL),
    CLIENT_REQUEST_TIMEOUT(
            "urn:uuid:928412fe-24d7-5395-901f-4b0231d4dc8f",
            ProblemScope.CLIENT,
            null,
            null,
            null,
            RetryPolicy.MANUAL),
    CLIENT_RESPONSE_INVALID(
            "urn:uuid:2c52e3d7-f437-5ff7-a3b0-55b896da2ae5",
            ProblemScope.CLIENT,
            null,
            null,
            null,
            RetryPolicy.MANUAL),
    CLIENT_UNEXPECTED_ERROR(
            "urn:uuid:063da3c0-f056-5e8f-b20d-cb494baf8652",
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
