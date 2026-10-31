package br.com.fiap.pos.tech_challenge.core.infrastructure.security;

/**
 * Feature toggle da autenticação ({@code security.auth.mode}). Os modos são exclusivos.
 *
 * @author johncgo
 * @since 2026-10-30
 */
public enum AuthMode {

    /**
     * Comportamento original: só o JWT de funcionário emitido pelo próprio core ({@code POST /api/signin}).
     */
    INTERNAL,

    /**
     * Só o JWT de cliente emitido pela Lambda de autenticação por CPF. O {@code /api/signin} fica bloqueado
     * e o token de funcionário é recusado: as rotas de ADMIN / ATTENDANT / MECHANIC ficam inacessíveis.
     */
    LAMBDA
}
