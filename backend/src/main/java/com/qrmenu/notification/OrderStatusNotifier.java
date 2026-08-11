package com.qrmenu.notification;

/**
 * Section 3: "'Sipariş hazır' bildirimlerini kanal-agnostik soyutlayan arayüz (v1: SSE;
 * v1 sonrası: push/SMS)". Domain modules (ordering, kitchen) depend on this interface,
 * never on the concrete SSE implementation - swapping/adding a channel later only
 * means a new implementation, same as PaymentProviderPort for payment adapters.
 */
public interface OrderStatusNotifier {

    void notifyOrderStatusChanged(OrderStatusUpdate update);
}
