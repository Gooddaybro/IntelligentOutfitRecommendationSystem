# Payment Callback And After-Sale Boundary Design

## Summary

Stage 6 makes the transaction boundary more realistic without requiring real payment provider credentials. `MOCK` remains the deterministic local channel. `ALIPAY` and `WECHAT` become provider-shaped channels that create `PENDING` payment attempts and rely on signed callbacks to confirm success through the same Java transaction path used by mock payment. After-sale requests become explicit API and database state, but no real refund money movement is introduced in this stage.

## Goals

- Keep Java as the source of truth for order, payment, inventory, and after-sale state.
- Let non-mock payment channels create pending payment records.
- Verify callback signatures before changing payment, order, or inventory state.
- Reuse the existing payment success path so duplicate callbacks cannot double-confirm stock.
- Add a current-user after-sale request API for paid orders.
- Avoid MQ, real provider SDKs, refund settlement, shipping, and address book work in this stage.

## Payment Design

`PaymentStrategy` remains the channel boundary. `MockPaymentStrategy` still returns `SUCCESS`. New provider-shaped strategies for `ALIPAY` and `WECHAT` return `PENDING`, leaving the order `UNPAID` until a trusted callback arrives.

Callback trust is handled by a small verifier that:

- Reads the raw body exactly as received.
- Verifies `X-Payment-Signature` using HMAC-SHA256 and a per-channel local secret.
- Parses a minimal callback payload: `paymentNo`, `orderNo`, `amount`, `status`, `providerTradeNo`, `transactionId`, and optional `paidAt`.
- Refuses to mutate state when signature, channel, payment number, order number, amount, or success status does not match Java-owned records.

`PaymentService` remains the only component that moves transaction state. A valid success callback locks the payment and order, confirms locked stock, marks the payment `SUCCESS`, marks the order `PAID`, and records payment-success behavior events. If the payment is already successful, the callback is recorded as handled but stock and order state are not touched again.

## After-Sale Design

After-sale requests are user-owned records linked to a paid order. Users can:

- Create an after-sale request for their own `PAID` order.
- List their requests.
- Cancel their own `REQUESTED` request.

The status model is explicit:

- `REQUESTED`: user submitted request.
- `CANCELLED`: user cancelled before operations handling.
- `APPROVED`: reserved for later operations/refund handling.
- `REJECTED`: reserved for later operations handling.

This stage does not expose admin operation endpoints. The schema includes handler fields so a later admin/refund stage can extend the model without changing the user-facing request contract.

## Error Handling

- Unsupported payment channels are rejected before order or inventory changes.
- Invalid callbacks return a generic received response but persist an audit log with `signatureValid=false` or `handled=false`.
- Callback responses do not reveal order ownership, amount mismatch details, or whether a state change occurred.
- After-sale creation rejects unpaid, cancelled, closed, or unknown orders.
- A user cannot read or cancel another user's after-sale request.

## Testing

- Unit tests cover provider pending payment creation and signed callback success/idempotency.
- Controller tests cover public callback behavior and authenticated after-sale APIs.
- Mapper tests cover callback audit and after-sale persistence.
- Migration tests remain covered by the normal Spring/Flyway test bootstrap.
