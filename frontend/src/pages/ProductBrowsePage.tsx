import { Search, SlidersHorizontal, Sparkles } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ProductCard } from "../features/catalog/ProductCard";
import type { PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { api } from "../shared/api/client";
import type { RecommendationCandidate } from "../shared/api/types";

type ProductBrowsePageProps = { onAction: (action: PendingCommerceAction) => void };
const categories = ["全部", "上装", "外套", "裤装", "裙装", "鞋靴"];

export function ProductBrowsePage({ onAction }: ProductBrowsePageProps) {
  const [keyword, setKeyword] = useState("");
  const [activeCategory, setActiveCategory] = useState("全部");
  const [sort, setSort] = useState("recommended");
  const [candidates, setCandidates] = useState<RecommendationCandidate[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  async function load(keywordValue = keyword, category = activeCategory) {
    setError("");
    setLoading(true);
    const query = [keywordValue, category === "全部" ? "" : category].filter(Boolean).join(" ");
    try {
      const [, nextCandidates] = await Promise.all([
        api.searchProducts(query),
        api.recommendationCandidates({ category: category === "全部" ? undefined : category, style: keywordValue || undefined })
      ]);
      setCandidates(Array.from(new Map(nextCandidates.map((item) => [item.spuId, item])).values()));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "商品加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load("", "全部"); }, []);

  const visibleCandidates = useMemo(() => {
    if (sort === "price-asc") return [...candidates].sort((a, b) => a.salePrice - b.salePrice);
    if (sort === "price-desc") return [...candidates].sort((a, b) => b.salePrice - a.salePrice);
    return candidates;
  }, [candidates, sort]);

  function submit(event: FormEvent) { event.preventDefault(); void load(keyword, activeCategory); }

  return (
    <main className="catalog-page" data-testid="browse-page">
      <header className="page-intro catalog-intro">
        <div><p className="section-kicker">DISCOVER / SHOP</p><h1>探索适合你的穿搭</h1><p>从品类、场景和风格出发，慢慢找到值得留下的单品。</p></div>
        <div className="catalog-ai-note"><Sparkles size={18} /><span><strong>拿不定主意？</strong>让 AI 根据你的衣橱画像缩小范围</span></div>
      </header>

      <form className="catalog-search" onSubmit={submit}>
        <Search size={19} />
        <input aria-label="搜索商品" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索商品、风格或场景" />
        <button type="submit">搜索</button>
      </form>

      <div className="category-tabs" aria-label="商品分类">
        {categories.map((category) => <button key={category} type="button" className={activeCategory === category ? "is-active" : ""} onClick={() => { setActiveCategory(category); void load(keyword, category); }}>{category}</button>)}
      </div>

      <section className="catalog-results">
        <div className="catalog-toolbar">
          <div><SlidersHorizontal size={17} /><button type="button">风格</button><button type="button">场景</button><button type="button">价格</button><button type="button">尺码</button></div>
          <label>排序<select aria-label="商品排序" value={sort} onChange={(event) => setSort(event.target.value)}><option value="recommended">综合推荐</option><option value="price-asc">价格从低到高</option><option value="price-desc">价格从高到低</option></select></label>
        </div>
        <div className="catalog-count"><span>{loading ? "正在整理商品…" : `共 ${visibleCandidates.length} 件商品`}</span>{error && <button type="button" onClick={() => void load()}>加载失败，重新尝试</button>}</div>
        <div className="catalog-product-grid" data-testid="catalog-product-grid">
          {visibleCandidates.map((candidate) => <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} />)}
        </div>
        {!loading && !error && visibleCandidates.length === 0 && <div className="empty-note"><Search size={22} /><p>没有找到符合当前条件的商品。</p><button type="button" onClick={() => { setKeyword(""); setActiveCategory("全部"); void load("", "全部"); }}>清除筛选</button></div>}
      </section>
    </main>
  );
}
