import { ArrowLeft, Save } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../../shared/api/client";
import type { AdminCategory, AdminProductInput } from "../../shared/api/adminTypes";

const emptyProduct: AdminProductInput = { spuCode: "", name: "", categoryId: 0, categoryName: "", minPrice: 0, maxPrice: 0, status: "DRAFT", mainImageUrl: "", description: "", styleTags: [] };

export function AdminProductFormPage() {
  const { spuId } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState<AdminProductInput>(emptyProduct);
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const editingId = Number(spuId);

  useEffect(() => {
    Promise.all([api.adminCategories(), Number.isFinite(editingId) ? api.adminProducts() : Promise.resolve([])])
      .then(([nextCategories, products]) => {
        setCategories(nextCategories.filter((item) => item.enabled));
        if (Number.isFinite(editingId)) {
          const current = products.find((item) => item.spuId === editingId);
          if (current) setProduct({ ...current });
          else setError("商品不存在");
        }
      })
      .catch((value) => setError(value instanceof Error ? value.message : "商品表单加载失败"));
  }, [editingId]);

  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => { if (dirty) event.preventDefault(); };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [dirty]);

  function update(patch: Partial<AdminProductInput>) { setProduct((current) => ({ ...current, ...patch })); setDirty(true); }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!product.name.trim() || !product.spuCode.trim() || !product.categoryId) { setError("请完整填写商品名称、编码和分类"); return; }
    setBusy(true);
    setError("");
    try {
      await api.adminSaveProduct({ ...product, spuId: Number.isFinite(editingId) ? editingId : undefined });
      setDirty(false);
      navigate("/admin/products");
    } catch (value) {
      setError(value instanceof Error ? value.message : "商品保存失败");
    } finally {
      setBusy(false);
    }
  }

  return <section className="admin-form-page">
    <header className="admin-page-heading"><div><p>PRODUCT EDITOR</p><h1>{Number.isFinite(editingId) ? "编辑商品" : "新增商品"}</h1><span>按分区维护商品事实，库存数量请在 SKU / 库存模块调整</span></div><Link to="/admin/products"><ArrowLeft size={17}/>返回列表</Link></header>
    <form onSubmit={submit}>
      {error && <p className="admin-inline-error">{error}</p>}
      <section><header><b>01</b><div><h2>基础信息</h2><p>用户识别商品所需的名称和唯一编码</p></div></header><div className="admin-form-grid"><label>商品名称<input value={product.name} onChange={(event) => update({ name: event.target.value })} required/></label><label>SPU 编码<input value={product.spuCode} onChange={(event) => update({ spuCode: event.target.value })} required/></label></div></section>
      <section><header><b>02</b><div><h2>分类与标签</h2><p>用于商城筛选和 AI 推荐候选</p></div></header><div className="admin-form-grid"><label>商品分类<select value={product.categoryId || ""} onChange={(event) => { const selected = categories.find((item) => item.id === Number(event.target.value)); update({ categoryId: selected?.id || 0, categoryName: selected?.name || "" }); }} required><option value="">请选择分类</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.level === 2 ? `└ ${item.name}` : item.name}</option>)}</select></label><label>风格标签<input value={product.styleTags?.join(",") || ""} onChange={(event) => update({ styleTags: event.target.value.split(/[,，]/).map((item) => item.trim()).filter(Boolean) })} placeholder="通勤, 简约"/></label></div></section>
      <section><header><b>03</b><div><h2>图片与价格</h2><p>图片地址和 SPU 展示价格范围</p></div></header><div className="admin-form-grid"><label className="admin-field-wide">主图地址<input value={product.mainImageUrl || ""} onChange={(event) => update({ mainImageUrl: event.target.value })} placeholder="/images/products/example.jpg"/></label><label>最低价格<input type="number" min="0" step="0.01" value={product.minPrice} onChange={(event) => update({ minPrice: Number(event.target.value) })}/></label><label>最高价格<input type="number" min="0" step="0.01" value={product.maxPrice} onChange={(event) => update({ maxPrice: Number(event.target.value) })}/></label></div></section>
      <section><header><b>04</b><div><h2>SKU 与库存</h2><p>属性组合、售价和可售库存由库存模块统一维护</p></div></header><div className="admin-form-handoff"><span>保存商品后前往 SKU / 库存管理维护颜色、尺码和库存。</span><Link to="/admin/inventory">进入库存管理</Link></div></section>
      <section><header><b>05</b><div><h2>详情与上架</h2><p>商品说明以及保存后的初始状态</p></div></header><div className="admin-form-grid"><label className="admin-field-wide">商品详情<textarea rows={5} value={product.description || ""} onChange={(event) => update({ description: event.target.value })}/></label><label>商品状态<select value={product.status} onChange={(event) => update({ status: event.target.value as AdminProductInput["status"] })}><option value="DRAFT">保存为草稿</option><option value="ON_SALE">立即上架</option><option value="OFF_SHELF">保持下架</option></select></label></div></section>
      <footer><span>{dirty ? "有未保存修改" : "当前内容已同步"}</span><button className="admin-primary-button" type="submit" disabled={busy}><Save size={17}/>{busy ? "正在保存…" : "保存商品"}</button></footer>
    </form>
  </section>;
}
