import { MapPin, Plus, Trash2 } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { api } from "../shared/api/client";
import type { Address, AddressInput } from "../shared/api/types";

const blank: AddressInput = { recipientName: "", phone: "", province: "", city: "", district: "", detail: "" };
const labels: Record<keyof AddressInput, string> = { recipientName: "收货人", phone: "手机号", province: "省份", city: "城市", district: "区县", detail: "详细地址" };

export function AddressBookPage() {
  const [items, setItems] = useState<Address[]>([]);
  const [form, setForm] = useState<AddressInput>(blank);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    void api.addresses()
      .then(setItems)
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "地址加载失败"))
      .finally(() => setLoading(false));
  }, []);

  function openCreate() {
    setError("");
    setEditingId(null);
    setForm(blank);
    setFormOpen(true);
  }

  function openEdit(address: Address) {
    setError("");
    setEditingId(address.id);
    setForm({ recipientName: address.recipientName, phone: address.phone, province: address.province, city: address.city, district: address.district, detail: address.detail });
    setFormOpen(true);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      setItems(editingId === null ? await api.createAddress(form) : await api.updateAddress(editingId, form));
      setForm(blank);
      setFormOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存地址失败");
    } finally {
      setSubmitting(false);
    }
  }

  async function mutate(operation: () => Promise<Address[]>) {
    setError("");
    try {
      setItems(await operation());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "地址操作失败");
    }
  }

  return <div className="profile-panel">
    <header><span><MapPin /></span><div><h2>收货地址</h2><p>确认订单时可以直接选择。</p></div><button onClick={openCreate}><Plus size={16} />新增地址</button></header>
    {loading && <p role="status">地址加载中...</p>}
    {error && <p role="alert">{error}</p>}
    {formOpen && <form className="address-form" onSubmit={(event) => void submit(event)}>
      {Object.entries(form).map(([key, value]) => <label key={key}>{labels[key as keyof AddressInput]}<input required value={value} onChange={(event) => setForm((current) => ({ ...current, [key]: event.target.value }))} /></label>)}
      <button className="primary-button" disabled={submitting}>保存地址</button>
    </form>}
    <div className="address-list">{items.map((item) => <article key={item.id}>
      <MapPin /><div><strong>{item.recipientName}　{item.phone}{item.isDefault && <em>默认</em>}</strong><p>{item.province}{item.city}{item.district}{item.detail}</p></div>
      {!item.isDefault && <button aria-label={`设为默认地址${item.recipientName}`} onClick={() => void mutate(() => api.setDefaultAddress(item.id))}>设为默认</button>}
      <button aria-label={`编辑${item.recipientName}的地址`} onClick={() => openEdit(item)}>编辑</button>
      <button aria-label={`删除${item.recipientName}的地址`} onClick={() => void mutate(() => api.deleteAddress(item.id))}><Trash2 size={16} /></button>
    </article>)}</div>
  </div>;
}
