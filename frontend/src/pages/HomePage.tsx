import { ArrowRight, Bot, Heart, PackageSearch, ReceiptText, ShoppingBag, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import type { RecommendationCandidate } from "../shared/api/types";
import { APP_PATHS } from "../app/navigation";

type HomePageProps = {
  username: string;
  cartCount: number;
  recommendations: RecommendationCandidate[];
};

const categories = ["上装", "外套", "裤装", "裙装", "鞋靴"];

export function HomePage({ username, cartCount, recommendations }: HomePageProps) {
  const featuredProducts = Array.from(new Map(recommendations.map((item) => [item.spuId, item])).values()).slice(0, 3);
  return (
    <main className="home-page" data-testid="home-page">
      <header className="page-intro">
        <div><p className="section-kicker">TODAY / STYLE NOTE</p><h1>我的穿搭空间</h1><p>{username}，今天想穿成什么样？</p></div>
        <Link className="text-link" to={APP_PATHS.products}>探索全部商品 <ArrowRight size={17} /></Link>
      </header>

      <section className="home-hero">
        <div>
          <span className="home-hero__icon"><Sparkles size={20} /></span>
          <p>AI 造型灵感</p>
          <h2>从一个场景开始，找到真正适合你的搭配</h2>
          <span>告诉我场合、预算和风格，其余交给水木。</span>
        </div>
        <Link className="hero-action" to={APP_PATHS.ai}><Bot size={18} />和 AI 一起挑选 <ArrowRight size={17} /></Link>
      </section>

      <section className="home-section">
        <div className="section-title"><div><p className="section-kicker">QUICK EXPLORE</p><h2>从分类开始探索</h2></div></div>
        <div className="category-rail">
          {categories.map((category, index) => (
            <Link key={category} to={`${APP_PATHS.products}?category=${encodeURIComponent(category)}`}>
              <span>0{index + 1}</span><strong>{category}</strong><ArrowRight size={17} />
            </Link>
          ))}
        </div>
      </section>

      <div className="home-dashboard">
        <section className="home-section inspiration-panel">
          <div className="section-title"><div><p className="section-kicker">CURATED FOR YOU</p><h2>今日灵感</h2></div><Link className="text-link" to={APP_PATHS.ai}>让 AI 重新推荐</Link></div>
          {featuredProducts.length ? (
            <div className="inspiration-grid">
              {featuredProducts.map((product) => (
                <Link key={`${product.spuId}-${product.skuId}`} to={APP_PATHS.productDetail(product.spuId)} className="inspiration-card" aria-label={`查看${product.name}详情`}>
                  <div>{product.mainImageUrl ? <img src={product.mainImageUrl} alt="" /> : <PackageSearch size={30} />}</div>
                  <p>{product.categoryName}</p><strong>{product.name}</strong><span>¥{product.salePrice}</span>
                </Link>
              ))}
            </div>
          ) : <div className="empty-note"><Sparkles size={22} /><p>完成一次 AI 对话后，适合你的商品会出现在这里。</p><Link to={APP_PATHS.ai}>开始描述需求</Link></div>}
        </section>

        <aside className="wardrobe-signal">
          <div className="section-title"><div><p className="section-kicker">YOUR SIGNALS</p><h2>衣橱信号</h2></div></div>
          <Link to={APP_PATHS.cart}><ShoppingBag size={19} /><span><strong>购物袋 {cartCount} 件待决定</strong><small>继续比较尺码与搭配</small></span><ArrowRight size={16} /></Link>
          <Link to={APP_PATHS.orders}><ReceiptText size={19} /><span><strong>查看订单进度</strong><small>支付、履约与售后</small></span><ArrowRight size={16} /></Link>
          <Link to={APP_PATHS.profile}><Heart size={19} /><span><strong>完善衣橱画像</strong><small>让推荐更贴近你的日常</small></span><ArrowRight size={16} /></Link>
        </aside>
      </div>
    </main>
  );
}
