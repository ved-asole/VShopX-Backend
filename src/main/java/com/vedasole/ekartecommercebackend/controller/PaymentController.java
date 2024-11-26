package com.vedasole.ekartecommercebackend.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.vedasole.ekartecommercebackend.payload.ShoppingCartDto;
import com.vedasole.ekartecommercebackend.service.serviceInterface.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.GsonJsonParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@CrossOrigin(
        value = {
                "http://localhost:5173",
                "https://ekart.vedasole.cloud",
                "https://ekart-shopping.netlify.app",
                "https://develop--ekart-shopping.netlify.app"
        },
        allowCredentials = "true"
)
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private static final GsonJsonParser gsonJsonParser = new GsonJsonParser();
    @Value("${stripe.endpoint.secret}")
    private String endpointSecret;

    @PostMapping("/create-checkout-session")
    public ResponseEntity<String> createCheckoutSession(
        @Valid @RequestBody ShoppingCartDto shoppingCartDto
    ) {
        Session session = paymentService.createCheckoutSession(shoppingCartDto);
        return new ResponseEntity<>(
                session.getUrl(),
                HttpStatus.CREATED
        );
    }

    @PostMapping("/webhook/stripe")
    public void handleStripeEvents(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        log.info("Webhook received with sigHeader: {}", sigHeader);
        Event event;
        try {
            // Verify the signature
            Webhook.Signature.verifyHeader(payload, sigHeader, endpointSecret, 300L);
            log.debug("Webhook received and verified header: {}", sigHeader);

            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
//            Event event = StripeObject.deserializeStripeObject(payload, Event.class, null);
            log.info("Event received with id: {}, type: {}", event.getId(), event.getType());

            Map<String, Object> payloadMap = gsonJsonParser.parseMap(payload);

            if (event.getType().contains("checkout.session")) paymentService.handleCheckoutSessionEvents(payloadMap);
            if (event.getType().contains("payment_intent")) paymentService.handlePaymentIntentEvents(payloadMap);
            else paymentService.handleStripeEvents(payloadMap);

        } catch (SignatureVerificationException e) {
            // Invalid signature
            log.error("Webhook signature verification failed: {}", sigHeader, e);
        }
        Map<String, Object> payloadMap = gsonJsonParser.parseMap(payload);
        String type = payloadMap.get("type").toString();
        if (type.contains("checkout.session")) paymentService.handleCheckoutSessionEvents(payloadMap);
        if (type.contains("payment_intent")) paymentService.handlePaymentIntentEvents(payloadMap);
        else paymentService.handleStripeEvents(payloadMap);
    }

}