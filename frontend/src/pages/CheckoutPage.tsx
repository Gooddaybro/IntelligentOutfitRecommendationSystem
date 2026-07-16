import { MapPin, PackageCheck, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { parseCheckoutSkuIds } from "../features/checkout/checkoutSelection";
import { api } from "../shared/api/client";
import type { Address, CheckoutPreview, OrderResponse } from "../shared/api/types";

export function CheckoutPage({ onOrderCreated }: { onOrderCreated: (order: OrderResponse) => void }) {
  const [params] = useSearchParams();
  const skuIds = parseCheckoutSkuIds(params.get("skuIds"));
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [addressId, setAddressId] = useState<number>();
  const [preview, setPreview] = useState<CheckoutPreview>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => { api.addresses().then((items) => { setAddresses(items); setAddressId(items.find((item) => item.isDefault)?.id || items[0]?.id); }); }, []);
  useEffect(() => { if (skuIds.length) api.checkoutPreview(skuIds, addressId).then(setPreview).catch((value) => setError(value instanceof Error ? value.message : "结算信息加载失败")); }, [params.toString(), addressId]);

  async function submit() {
    if (!addressId || !preview || preview.invalidReasons.length) return;
    setBusy(true); setError("");
    try { onOrderCreated(await api.createOrder(skuIds, addressId)); } catch (value) { setError(value instanceof Error ? value.message : "订单提交失败"); } finally { setBusy(false); }
  }

  return <main className="checkout-page">
    <header className="page-intro"><div><p className="section-kicker">CHECKOUT</p><h1>确认订单</h1><p>提交前再次核对收货信息和商品明细。</p></div></header>
    <div className="checkout-grid"><div>
      <section className="checkout-card"><h2><MapPin size={20}/>收货地址</h2>{addresses.map((address) => <label key={address.id} className={`address-choice${addressId === address.id ? " is-active" : ""}`}><input type="radio" checked={addressId === address.id} onChange={() => setAddressId(address.id)}/><span><strong>{address.recipientName}　{address.phone}</strong><small>{address.province}{address.city}{address.district}{address.detail}</small></span></label>)}{!addresses.length && <p>请先在个人中心添加收货地址。</p>}</section>
      <section className="checkout-card"><h2><PackageCheck size={20}/>商品清单</h2>{preview?.items.map((item) => <article className="checkout-item" key={item.skuId}><span>{item.name}<small>{item.color} · {item.size} × {item.quantity}</small></span><strong>¥{(item.salePrice * item.quantity).toFixed(2)}</strong></article>)}</section>
    </div><aside className="checkout-summary"><h2>金额明细</h2><p><span>商品金额</span><strong>¥{(preview?.merchandiseAmount || 0).toFixed(2)}</strong></p><p><span>运费</span><strong>¥{(preview?.shippingAmount || 0).toFixed(2)}</strong></p><div><span>应付金额</span><strong>¥{(preview?.payableAmount || 0).toFixed(2)}</strong></div>{preview?.invalidReasons.map((reason) => <p className="error-text" key={reason}>{reason}</p>)}{error && <p className="error-text">{error}</p>}<button className="primary-button" disabled={!addressId || !preview || Boolean(preview.invalidReasons.length) || busy} onClick={() => void submit()}>提交订单</button><small><ShieldCheck size={14}/>金额与库存将在服务端重新校验</small></aside></div>
  </main>;
}
