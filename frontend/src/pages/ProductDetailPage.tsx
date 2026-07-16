import { ArrowLeft, Check, Heart, Info, PackageCheck, ShieldCheck, ShoppingBag, ShoppingCart, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { resolveSku, type SkuSelection } from "../features/catalog/skuSelection";
import { buildAddToCartAction, buildBuyNowAction, type PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { api } from "../shared/api/client";
import type { ProductDetail, RecommendationCandidate } from "../shared/api/types";
import { APP_PATHS } from "../app/navigation";

type ProductDetailPageProps = { onAction: (action: PendingCommerceAction) => void };

export function ProductDetailPage({ onAction }: ProductDetailPageProps) {
  const { spuId } = useParams();
  const [product, setProduct] = useState<ProductDetail>();
  const [skus, setSkus] = useState<RecommendationCandidate[]>([]);
  const [selection, setSelection] = useState<SkuSelection>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [isFavorite, setIsFavorite] = useState(false);
  const [favoriteBusy, setFavoriteBusy] = useState(false);

  useEffect(() => {
    const id = Number(spuId);
    if (!Number.isFinite(id)) { setError("商品编号无效"); setLoading(false); return; }
    setLoading(true);
    Promise.all([api.productDetail(id), api.recommendationCandidates({})])
      .then(([detail, candidates]) => { setProduct(detail); setSkus(candidates.filter((item) => item.spuId === id)); })
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : "商品详情加载失败"))
      .finally(() => setLoading(false));
    api.favorites()
      .then((favorites) => setIsFavorite(favorites.some((item) => item.spuId === id)))
      .catch(() => setIsFavorite(false));
  }, [spuId]);

  async function toggleFavorite() {
    const id = Number(spuId);
    if (!Number.isFinite(id) || favoriteBusy) return;
    setFavoriteBusy(true);
    try {
      if (isFavorite) await api.removeFavorite(id);
      else await api.addFavorite(id);
      setIsFavorite((current) => !current);
    } finally {
      setFavoriteBusy(false);
    }
  }

  const colors = useMemo(() => Array.from(new Set(skus.map((sku) => sku.color).filter(Boolean))) as string[], [skus]);
  const sizes = useMemo(() => Array.from(new Set(skus.map((sku) => sku.size).filter(Boolean))) as string[], [skus]);
  const selectedSku = resolveSku(skus, selection);
  const purchasable = Boolean(selectedSku && (selectedSku.availableStock === undefined || selectedSku.availableStock > 0) && selectedSku.stockStatus !== "out_of_stock");

  if (loading) return <main className="detail-loading" data-testid="product-detail-page">正在整理商品详情…</main>;
  if (error || !product) return <main className="detail-error" data-testid="product-detail-page"><Info size={24} /><h1>暂时无法查看这件商品</h1><p>{error}</p><Link to={APP_PATHS.products}>返回探索商品</Link></main>;

  return (
    <main className="product-detail-page" data-testid="product-detail-page">
      <div className="detail-breadcrumb"><Link to={APP_PATHS.products}><ArrowLeft size={16} />返回商品列表</Link><span>/</span><span>{product.categoryName}</span><span>/</span><strong>{product.name}</strong></div>
      <section className="product-decision">
        <div className="product-gallery">
          <div className="product-gallery__thumb">{product.mainImageUrl ? <img src={product.mainImageUrl} alt="" /> : <ShoppingBag size={24} />}</div>
          <div className="product-gallery__main">{product.mainImageUrl ? <img src={product.mainImageUrl} alt={product.name} /> : <div><ShoppingBag size={42} /><span>暂无商品图片</span></div>}<span className="gallery-tag">水木精选</span></div>
        </div>

        <div className="product-purchase">
          <p className="section-kicker">{product.categoryName} / SHUIMU SELECT</p>
          <h1>{product.name}</h1>
          <p className="product-description">{product.description || "为日常穿着挑选的舒适单品，具体材质与洗护信息以商品说明为准。"}</p>
          <div className="detail-price">¥{selectedSku?.salePrice ?? (product.minPrice === product.maxPrice ? product.minPrice : `${product.minPrice} – ${product.maxPrice}`)}</div>

          <fieldset className="sku-options"><legend><span>颜色</span><small>{selection.color || "请选择"}</small></legend><div>{colors.map((color) => <button aria-pressed={selection.color === color} className={selection.color === color ? "is-selected" : ""} key={color} type="button" onClick={() => setSelection((current) => ({ ...current, color }))}>{selection.color === color && <Check size={14} />}{color}</button>)}</div></fieldset>
          <fieldset className="sku-options"><legend><span>尺码</span><small>{selection.size || "请选择"}</small></legend><div>{sizes.map((size) => <button aria-pressed={selection.size === size} className={selection.size === size ? "is-selected" : ""} key={size} type="button" onClick={() => setSelection((current) => ({ ...current, size }))}>{size}</button>)}</div></fieldset>

          <div className={`stock-note${purchasable ? " is-ready" : ""}`}><PackageCheck size={18} />{selectedSku ? (purchasable ? `当前组合有货${selectedSku.availableStock === undefined ? "" : `，可售 ${selectedSku.availableStock} 件`}` : "当前组合暂时无货") : (skus.length ? "选择颜色和尺码后查看库存" : "当前商品暂不可购买")}</div>

          <div className="purchase-actions">
            <button type="button" aria-label={isFavorite ? "已收藏" : "收藏商品"} aria-pressed={isFavorite} className={`favorite-action${isFavorite ? " is-active" : ""}`} disabled={favoriteBusy} onClick={() => void toggleFavorite()}><Heart size={19} fill={isFavorite ? "currentColor" : "none"} /></button>
            <button type="button" disabled={!purchasable} onClick={() => selectedSku && onAction(buildAddToCartAction(selectedSku, 1))}><ShoppingCart size={18} />加入购物袋</button>
            <button type="button" className="buy-action" disabled={!purchasable} onClick={() => selectedSku && onAction(buildBuyNowAction(selectedSku, 1))}><ShoppingBag size={18} />立即购买</button>
          </div>

          <div className="ai-size-note"><span><Sparkles size={18} /></span><div><strong>AI 穿搭建议</strong><p>结合你的衣橱画像后，这里会给出风格与尺码建议。建议仅供决策参考，请以商品尺码参数为准。</p></div></div>
          <div className="purchase-guarantees"><span><ShieldCheck size={17} />商品事实来自商城后端</span><span><PackageCheck size={17} />库存以结算校验为准</span></div>
        </div>
      </section>

      <section className="detail-information"><nav><button className="is-active">商品详情</button><button>尺码参数</button><button>材质洗护</button><button>用户评价</button></nav><div><h2>关于这件单品</h2><p>{product.description || "详细商品说明待商家完善。"}</p>{product.materials?.length ? <p><strong>材质：</strong>{product.materials.join("、")}</p> : null}</div></section>
    </main>
  );
}
