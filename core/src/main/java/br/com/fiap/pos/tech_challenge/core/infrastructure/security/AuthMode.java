package br.com.fiap.pos.tech_challenge.core.infrastructure.security;

/**
 * Modo de autenticação ({@code security.auth.mode}). O padrão operacional é {@link #BOTH}
 * (funcionário + cliente convivem). {@link #INTERNAL} e {@link #LAMBDA} ficam para regressão/teste.
 *
 * @author johncgo
 * @since 2026-10-30
 */
public enum AuthMode {

    /**
     * Só o JWT de funcionário emitido pelo próprio core ({@code POST /api/signin}).
     */
    INTERNAL,

    /**
     * Só o JWT de cliente emitido pela Lambda de autenticação por CPF. O {@code /api/signin} fica bloqueado
     * e o token de funcionário é recusado.
     */
    LAMBDA,

    /**
     * Funcionário ({@code /api/signin}) e cliente (Lambda / CPF) convivem: cada um nas suas rotas.
     */
    BOTH
}
