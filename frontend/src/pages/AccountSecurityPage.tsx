import { LockKeyhole, ShieldCheck } from "lucide-react";
export function AccountSecurityPage() { return <div className="profile-panel"><header><span><LockKeyhole/></span><div><h2>账户安全</h2><p>管理密码和登录安全。</p></div></header><div className="security-card"><ShieldCheck/><div><strong>登录密码</strong><p>真实密码修改将在 Java 安全接口接入后开放。</p></div><button disabled>修改密码</button></div><div className="security-card"><ShieldCheck/><div><strong>登录设备</strong><p>当前演示模式不记录真实设备信息。</p></div></div></div>; }

