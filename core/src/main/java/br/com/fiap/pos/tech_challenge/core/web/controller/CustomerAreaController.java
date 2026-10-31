package br.com.fiap.pos.tech_challenge.core.web.controller;

import br.com.fiap.pos.tech_challenge.core.application.CustomerService;
import br.com.fiap.pos.tech_challenge.core.application.dto.CustomerResponse;
import br.com.fiap.pos.tech_challenge.core.application.dto.ServiceOrderResponse;
import br.com.fiap.pos.tech_challenge.core.application.serviceorder.ServiceOrderQueryService;
import br.com.fiap.pos.tech_challenge.core.domain.enums.ServiceOrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Área do cliente autenticado pelo CPF (JWT da Lambda; modo {@code BOTH} ou {@code LAMBDA}).
 * O cliente só enxerga os próprios dados: o UUID vem do {@code sub} do token, nunca da requisição.
 *
 * @author johncgo
 * @since 2026-10-30
 */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@Tag(name = "Customer area")
public class CustomerAreaController {

    private final CustomerService customerService;

    private final ServiceOrderQueryService queryService;

    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the authenticated customer", operationId = "get-customer-me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(customerService.findByUuid(customerUuid(jwt)));
    }

    @GetMapping(path = "/service-orders", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the authenticated customer's service orders", operationId = "list-customer-service-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<ServiceOrderResponse>> serviceOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ServiceOrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(queryService.listServiceOrders(status, customerUuid(jwt), null, null, pageable));
    }

    private static UUID customerUuid(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
