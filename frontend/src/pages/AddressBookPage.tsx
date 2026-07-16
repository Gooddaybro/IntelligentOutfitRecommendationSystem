import { MapPin, Plus, Trash2 } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { api } from "../shared/api/client";
import type { Address } from "../shared/api/types";

const blank = { recipientName: "", phone: "", province: "", city: "", district: "", detail: "" };
export function AddressBookPage() {
  const [items, setItems] = useState<Address[]>([]); const [editing, setEditing] = useState(false); const [form, setForm] = useState(blank);
  useEffect(() => { api.addresses().then(setItems); }, []);
  async function submit(event: FormEvent) { event.preventDefault(); setItems(await api.saveAddress(form)); setForm(blank); setEditing(false); }
  return <div className="profile-panel"><header><span><MapPin/></span><div><h2>收货地址</h2><p>确认订单时可以直接选择。</p></div><button onClick={() => setEditing((value) => !value)}><Plus size={16}/>新增地址</button></header>{editing && <form className="address-form" onSubmit={(event) => void submit(event)}>{Object.entries(form).map(([key, value]) => <label key={key}>{({recipientName:"收货人",phone:"手机号",province:"省份",city:"城市",district:"区县",detail:"详细地址"} as Record<string,string>)[key]}<input required value={value} onChange={(event) => setForm((current) => ({ ...current, [key]: event.target.value }))}/></label>)}<button className="primary-button">保存地址</button></form>}<div className="address-list">{items.map((item) => <article key={item.id}><MapPin/><div><strong>{item.recipientName}　{item.phone}{item.isDefault && <em>默认</em>}</strong><p>{item.province}{item.city}{item.district}{item.detail}</p></div><button aria-label={`删除${item.recipientName}的地址`} onClick={() => void api.removeAddress(item.id).then(setItems)}><Trash2 size={16}/></button></article>)}</div></div>;
}

