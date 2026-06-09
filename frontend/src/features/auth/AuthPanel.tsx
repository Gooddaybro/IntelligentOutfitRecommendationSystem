import { LockKeyhole, UserRound } from "lucide-react";
import { FormEvent, useState } from "react";

type AuthPanelProps = {
  error: string;
  isBusy: boolean;
  onLogin: (username: string, password: string) => Promise<void>;
  onRegister: (username: string, password: string, email?: string) => Promise<void>;
};

export function AuthPanel({ error, isBusy, onLogin, onRegister }: AuthPanelProps) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (mode === "login") {
      await onLogin(username, password);
    } else {
      await onRegister(username, password, email || undefined);
    }
  }

  return (
    <section className="auth-panel">
      <div>
        <p className="eyebrow">AI Clothing Shopping</p>
        <h1>登录后开始对话式购衣</h1>
      </div>
      <div className="segmented">
        <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} type="button">
          登录
        </button>
        <button className={mode === "register" ? "active" : ""} onClick={() => setMode("register")} type="button">
          注册
        </button>
      </div>
      <form onSubmit={submit} className="form-stack">
        <label>
          <span>用户名</span>
          <div className="input-with-icon">
            <UserRound size={16} />
            <input value={username} onChange={(event) => setUsername(event.target.value)} minLength={3} required />
          </div>
        </label>
        <label>
          <span>密码</span>
          <div className="input-with-icon">
            <LockKeyhole size={16} />
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              minLength={8}
              required
            />
          </div>
        </label>
        {mode === "register" && (
          <label>
            <span>邮箱</span>
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" />
          </label>
        )}
        {error && <p className="error-text">{error}</p>}
        <button className="primary-button" type="submit" disabled={isBusy}>
          {isBusy ? "处理中" : mode === "login" ? "登录" : "注册并登录"}
        </button>
      </form>
    </section>
  );
}
