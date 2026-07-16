import { Save, UserRound } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { api } from "../shared/api/client";

export function ProfileAccountPage() {
  const [nickname, setNickname] = useState(""); const [gender, setGender] = useState(""); const [saved, setSaved] = useState(false);
  useEffect(() => { api.profile().then((value) => { setNickname(value.nickname || ""); setGender(value.gender || ""); }); }, []);
  async function submit(event: FormEvent) { event.preventDefault(); await api.updateProfile({ nickname, gender: gender || null }); setSaved(true); }
  return <div className="profile-panel"><header><span><UserRound/></span><div><h2>个人资料</h2><p>用于账户展示和基础身份信息。</p></div></header><form className="profile-simple-form" onSubmit={(event) => void submit(event)}><label>昵称<input value={nickname} onChange={(event) => setNickname(event.target.value)}/></label><label>性别<select value={gender} onChange={(event) => setGender(event.target.value)}><option value="">未设置</option><option value="male">男</option><option value="female">女</option></select></label><button className="primary-button"><Save size={16}/>保存资料</button>{saved && <p className="success-text">资料已保存</p>}</form></div>;
}

