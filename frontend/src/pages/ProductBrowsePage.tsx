import { Search, SlidersHorizontal, Sparkles } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ProductCard } from "../features/catalog/ProductCard";
import type { PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { api } from "../shared/api/client";
import type { RecommendationCandidate } from "../shared/api/types";

type ProductBrowsePageProps = { onAction: (action: PendingCommerceAction) => void };
const categories = ["全部", "上装", "外套", "裤装", "裙装", "鞋靴"];
const styles = ["全部", "自然", "通勤", "简约", "休闲", "复古"];
const scenes = ["全部", "日常", "通勤", "约会", "运动"];
const prices = [
  { value: "全部", label: "全部价格" },
  { value: "0-300", label: "¥300 以下" },
  { value: "300-600", label: "¥300–600" },
  { value: "600+", label: "¥600 以上" }
];
const sizes = ["全部", "S", "M", "L", "XL"];

export function ProductBrowsePage({ onAction }: ProductBrowsePageProps) {
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [activeCategory, setActiveCategory] = useState("全部");
  const [style, setStyle] = useState("全部");
  const [scene, setScene] = useState("全部");
  const [price, setPrice] = useState("全部");
  const [size, setSize] = useState("全部");
  const [sort, setSort] = useState("recommended");
  const [candidates, setCandidates] = useState<RecommendationCandidate[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  async function load(keywordValue = keyword, category = activeCategory) {
    setError("");
    setLoading(true);
    setAppliedKeyword(keywordValue.trim());
    const query = [keywordValue, category === "全部" ? "" : category].filter(Boolean).join(" ");
    try {
      const [, nextCandidates] = await Promise.all([
        api.searchProducts(query),
        api.recommendationCandidates({ category: category === "全部" ? undefined : category })
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
    const normalizedKeyword = appliedKeyword.toLocaleLowerCase();
    const filtered = candidates.filter((candidate) => {
      const searchableText = [candidate.name, candidate.categoryName, candidate.styleTags, candidate.attributeTags]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase();
      const tags = [candidate.styleTags, candidate.attributeTags].filter(Boolean).join(",");
      const keywordMatches = !normalizedKeyword || searchableText.includes(normalizedKeyword);
      const categoryMatches = activeCategory === "全部" || candidate.categoryName === activeCategory;
      const styleMatches = style === "全部" || candidate.styleTags?.includes(style);
      const sceneMatches = scene === "全部" || tags.includes(scene);
      const sizeMatches = size === "全部" || candidate.size === size;
      const priceMatches = price === "全部"
        || (price === "0-300" && candidate.salePrice < 300)
        || (price === "300-600" && candidate.salePrice >= 300 && candidate.salePrice <= 600)
        || (price === "600+" && candidate.salePrice > 600);
      return keywordMatches && categoryMatches && styleMatches && sceneMatches && sizeMatches && priceMatches;
    });
    if (sort === "price-asc") return filtered.sort((a, b) => a.salePrice - b.salePrice);
    if (sort === "price-desc") return filtered.sort((a, b) => b.salePrice - a.salePrice);
    return filtered;
  }, [activeCategory, appliedKeyword, candidates, price, scene, size, sort, style]);

  const suggestions = useMemo(() => Array.from(new Set(candidates.map((candidate) => candidate.name))), [candidates]);

  function submit(event: FormEvent) { event.preventDefault(); void load(keyword, activeCategory); }

  return (
    <main className="catalog-page" data-testid="browse-page">
      <header className="page-intro catalog-intro">
        <div><p className="section-kicker">DISCOVER / SHOP</p><h1>探索适合你的穿搭</h1><p>从品类、场景和风格出发，慢慢找到值得留下的单品。</p></div>
        <div className="catalog-ai-note"><Sparkles size={18} /><span><strong>拿不定主意？</strong>让 AI 根据你的衣橱画像缩小范围</span></div>
      </header>

      <form className="catalog-search" onSubmit={submit}>
        <Search size={19} />
        <input aria-label="搜索商品" list="product-search-suggestions" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索商品、风格或场景" />
        <datalist id="product-search-suggestions">{suggestions.map((suggestion) => <option key={suggestion} value={suggestion} />)}</datalist>
        <button type="submit">搜索</button>
      </form>

      <div className="category-tabs" aria-label="商品分类">
        {categories.map((category) => <button key={category} type="button" className={activeCategory === category ? "is-active" : ""} onClick={() => { setActiveCategory(category); void load(keyword, category); }}>{category}</button>)}
      </div>

      <section className="catalog-results">
        <div className="catalog-toolbar">
          <div className="catalog-filters">
            <SlidersHorizontal size={17} />
            <label><span>风格</span><select aria-label="风格筛选" value={style} onChange={(event) => setStyle(event.target.value)}>{styles.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
            <label><span>场景</span><select aria-label="场景筛选" value={scene} onChange={(event) => setScene(event.target.value)}>{scenes.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
            <label><span>价格</span><select aria-label="价格筛选" value={price} onChange={(event) => setPrice(event.target.value)}>{prices.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
            <label><span>尺码</span><select aria-label="尺码筛选" value={size} onChange={(event) => setSize(event.target.value)}>{sizes.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
          </div>
          <label>排序<select aria-label="商品排序" value={sort} onChange={(event) => setSort(event.target.value)}><option value="recommended">综合推荐</option><option value="price-asc">价格从低到高</option><option value="price-desc">价格从高到低</option></select></label>
        </div>
        <div className="catalog-count"><span>{loading ? "正在整理商品…" : `共 ${visibleCandidates.length} 件商品`}</span>{error && <button type="button" onClick={() => void load()}>加载失败，重新尝试</button>}</div>
        <div className="catalog-product-grid" data-testid="catalog-product-grid">
          {visibleCandidates.map((candidate) => <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} />)}
        </div>
        {!loading && !error && visibleCandidates.length === 0 && <div className="empty-note"><Search size={22} /><p>没有找到符合当前条件的商品。</p><button type="button" onClick={() => { setKeyword(""); setStyle("全部"); setScene("全部"); setPrice("全部"); setSize("全部"); setActiveCategory("全部"); void load("", "全部"); }}>清除筛选</button></div>}
      </section>
    </main>
  );
}
