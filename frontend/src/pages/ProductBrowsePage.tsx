import { Search } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { ChatPanel } from "../features/assistant/ChatPanel";
import { ProductCard } from "../features/catalog/ProductCard";
import type { PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { api } from "../shared/api/client";
import type { ProductSearchItem, RecommendationCandidate } from "../shared/api/types";

type ProductBrowsePageProps = {
  onAction: (action: PendingCommerceAction) => void;
};

export function ProductBrowsePage({ onAction }: ProductBrowsePageProps) {
  const [keyword, setKeyword] = useState("");
  const [products, setProducts] = useState<ProductSearchItem[]>([]);
  const [candidates, setCandidates] = useState<RecommendationCandidate[]>([]);
  const [error, setError] = useState("");

  async function load(keywordValue = keyword) {
    setError("");
    try {
      const [nextProducts, nextCandidates] = await Promise.all([
        api.searchProducts(keywordValue),
        api.recommendationCandidates({ style: keywordValue || undefined })
      ]);
      setProducts(nextProducts);
      setCandidates(nextCandidates);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "商品加载失败");
    }
  }

  useEffect(() => {
    void load("");
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    void load(keyword);
  }

  return (
    <main className="workbench browse-layout">
      <section className="catalog-panel">
        <form className="searchbar" onSubmit={submit}>
          <Search size={18} />
          <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索商品、风格或分类" />
          <button className="primary-button" type="submit">
            搜索
          </button>
        </form>
        {error && <p className="error-text">{error}</p>}
        <div className="section-heading">
          <div>
            <p className="eyebrow">传统浏览</p>
            <h2>商品列表</h2>
          </div>
          <span>{products.length} 个 SPU</span>
        </div>
        <div className="browse-summary-list">
          {products.map((product) => (
            <article key={product.spuId} className="browse-summary-row">
              <div className="product-image small">
                {product.mainImageUrl ? <img src={product.mainImageUrl} alt={product.name} /> : <span>暂无图片</span>}
              </div>
              <div>
                <h3>{product.name}</h3>
                <p>{product.categoryName} · {product.fitType || "常规版型"}</p>
              </div>
              <strong>￥{product.minPrice} - ￥{product.maxPrice}</strong>
            </article>
          ))}
        </div>
        <div className="section-heading">
          <div>
            <p className="eyebrow">可交易 SKU</p>
            <h2>候选商品卡片</h2>
          </div>
          <span>{candidates.length} 件</span>
        </div>
        <div className="product-grid">
          {candidates.map((candidate) => (
            <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} />
          ))}
        </div>
      </section>
      <aside className="assistant-side">
        <ChatPanel onRecommendations={setCandidates} />
      </aside>
    </main>
  );
}
