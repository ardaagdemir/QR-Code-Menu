package com.qrmenu.payment;

/** Result of PaymentProviderPort.createPaymentIntent - the id the provider will later reference in its webhook. */
public record ProviderPaymentIntent(String providerPaymentIntentId) {
}
