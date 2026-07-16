import { Heart, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../shared/api/client";
import type { RecommendationCandidate } from "../shared/api/types";

export function FavoritesPage() { const [items, setItems] = useState<RecommendationCandidate[]>([]); useEffect(() => { api.favorites().then(setItems); }, []); return <div className="profile-panel"><header><span><Heart/></span><div><h2>我的收藏</h2><p>保存想继续比较的单品。</p></div></header><div className="favorite-list">{items.map((item) => <article key={item.spuId}>{item.mainImageUrl && <img src={item.mainImageUrl} alt=""/>}<div><small>{item.categoryName}</small><Link to={`/app/products/${item.spuId}`}>{item.name}</Link><strong>¥{item.salePrice}</strong></div><button aria-label={`取消收藏${item.name}`} onClick={() => void api.removeFavorite(item.spuId).then(setItems)}><Trash2 size={16}/></button></article>)}{!items.length && <div className="empty-note"><p>还没有收藏商品。</p><Link to="/app/products">去探索商品</Link></div>}</div></div>; }

