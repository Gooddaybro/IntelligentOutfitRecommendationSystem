import { CheckCircle2, CreditCard } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../shared/api/client";

export function PaymentResultPage() {
  const { orderNo = "" } = useParams();
  const [status, setStatus] = useState<"READY" | "SUCCESS">("READY");
  const [busy, setBusy] = useState(false);
  async function pay() { setBusy(true); try { await api.payMock(orderNo); setStatus("SUCCESS"); } finally { setBusy(false); } }
  return <main className="payment-result"><span>{status === "SUCCESS" ? <CheckCircle2 size={42}/> : <CreditCard size={42}/>}</span><p className="section-kicker">PAYMENT RESULT</p><h1>{status === "SUCCESS" ? "支付成功" : "订单已创建"}</h1><p>订单号：{orderNo}</p>{status === "READY" ? <button className="primary-button" disabled={busy} onClick={() => void pay()}>演示支付</button> : <div><Link to={`/app/orders/${orderNo}`}>查看订单详情</Link><Link to="/app/products">继续购物</Link></div>}</main>;
}
