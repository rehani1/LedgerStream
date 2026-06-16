package com.ledgerstream.orders;

import java.util.List;
import java.util.UUID;

import com.ledgerstream.auth.AuthenticatedUser;
import com.ledgerstream.orders.dto.CreateOrderRequest;
import com.ledgerstream.orders.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> createOrder(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody CreateOrderRequest request
	) {
		CreateOrderResult result = orderService.createOrder(authenticatedUser, idempotencyKey, request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
	}

	@GetMapping
	public List<OrderResponse> listOrders(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		return orderService.listOrders(authenticatedUser);
	}

	@GetMapping("/{id}")
	public OrderResponse getOrder(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable UUID id
	) {
		return orderService.getOrder(authenticatedUser, id);
	}

	@PostMapping("/{id}/cancel")
	public OrderResponse cancelOrder(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable UUID id
	) {
		return orderService.cancelOrder(authenticatedUser, id);
	}
}
