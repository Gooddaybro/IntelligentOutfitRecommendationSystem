import { Palette, RefreshCcw, Ruler, Save, UserRound } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { api } from "../shared/api/client";
import type {
  UserBodyDataRequest,
  UserBodyDataResponse,
  UserPreferencesRequest,
  UserPreferencesResponse,
  UserProfileRequest,
  UserProfileResponse
} from "../shared/api/types";

type SavingSection = "profile" | "body" | "preferences";

type ProfileForm = {
  nickname: string;
  avatarUrl: string;
  gender: string;
  birthday: string;
};

type BodyForm = {
  heightCm: string;
  weightKg: string;
  gender: string;
  shoulderWidthCm: string;
  bustCm: string;
  waistCm: string;
  hipCm: string;
  preferredFit: string;
};

type PreferencesForm = {
  preferredStyles: string;
  preferredColors: string;
  dislikedColors: string;
  preferredCategories: string;
  budgetMin: string;
  budgetMax: string;
};

const emptyProfileForm: ProfileForm = {
  nickname: "",
  avatarUrl: "",
  gender: "",
  birthday: ""
};

const emptyBodyForm: BodyForm = {
  heightCm: "",
  weightKg: "",
  gender: "",
  shoulderWidthCm: "",
  bustCm: "",
  waistCm: "",
  hipCm: "",
  preferredFit: ""
};

const emptyPreferencesForm: PreferencesForm = {
  preferredStyles: "",
  preferredColors: "",
  dislikedColors: "",
  preferredCategories: "",
  budgetMin: "",
  budgetMax: ""
};

function textOrEmpty(value?: string | null) {
  return value ?? "";
}

function numberOrEmpty(value?: number | null) {
  return value === null || value === undefined ? "" : String(value);
}

function nullableText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function nullableNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error("请输入有效的非负数字");
  }
  return parsed;
}

function preferenceListText(values?: string[] | null) {
  return values?.join(", ") ?? "";
}

function parsePreferenceList(value: string) {
  const seen = new Set<string>();
  // 多值偏好在后端按数组保存，前端保留紧凑输入体验，并在提交前去重、去空值。
  return value
    .split(/[,，;；\n]/)
    .map((item) => item.trim())
    .filter((item) => {
      if (!item || seen.has(item)) {
        return false;
      }
      seen.add(item);
      return true;
    })
    .slice(0, 20);
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function profileToForm(profile: UserProfileResponse): ProfileForm {
  return {
    nickname: textOrEmpty(profile.nickname),
    avatarUrl: textOrEmpty(profile.avatarUrl),
    gender: textOrEmpty(profile.gender),
    birthday: textOrEmpty(profile.birthday)
  };
}

function bodyToForm(bodyData: UserBodyDataResponse): BodyForm {
  return {
    heightCm: numberOrEmpty(bodyData.heightCm),
    weightKg: numberOrEmpty(bodyData.weightKg),
    gender: textOrEmpty(bodyData.gender),
    shoulderWidthCm: numberOrEmpty(bodyData.shoulderWidthCm),
    bustCm: numberOrEmpty(bodyData.bustCm),
    waistCm: numberOrEmpty(bodyData.waistCm),
    hipCm: numberOrEmpty(bodyData.hipCm),
    preferredFit: textOrEmpty(bodyData.preferredFit)
  };
}

function preferencesToForm(preferences: UserPreferencesResponse): PreferencesForm {
  return {
    preferredStyles: preferenceListText(preferences.preferredStyles),
    preferredColors: preferenceListText(preferences.preferredColors),
    dislikedColors: preferenceListText(preferences.dislikedColors),
    preferredCategories: preferenceListText(preferences.preferredCategories),
    budgetMin: numberOrEmpty(preferences.budgetMin),
    budgetMax: numberOrEmpty(preferences.budgetMax)
  };
}

export function ProfilePreferencesPage() {
  const [profileForm, setProfileForm] = useState<ProfileForm>(emptyProfileForm);
  const [bodyForm, setBodyForm] = useState<BodyForm>(emptyBodyForm);
  const [preferencesForm, setPreferencesForm] = useState<PreferencesForm>(emptyPreferencesForm);
  const [isLoading, setIsLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [savingSection, setSavingSection] = useState<SavingSection | null>(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  async function loadProfileData() {
    setIsLoading(true);
    setNotice("");
    setError("");
    try {
      const [profile, bodyData, preferences] = await Promise.all([
        api.profile(),
        api.bodyData(),
        api.preferences()
      ]);
      setProfileForm(profileToForm(profile));
      setBodyForm(bodyToForm(bodyData));
      setPreferencesForm(preferencesToForm(preferences));
      setHasLoaded(true);
    } catch (loadError) {
      setHasLoaded(false);
      setError(errorMessage(loadError, "资料加载失败"));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadProfileData();
  }, []);

  function updateProfileField(field: keyof ProfileForm, value: string) {
    setProfileForm((current) => ({ ...current, [field]: value }));
  }

  function updateBodyField(field: keyof BodyForm, value: string) {
    setBodyForm((current) => ({ ...current, [field]: value }));
  }

  function updatePreferencesField(field: keyof PreferencesForm, value: string) {
    setPreferencesForm((current) => ({ ...current, [field]: value }));
  }

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    setSavingSection("profile");
    setNotice("");
    setError("");
    try {
      const request: UserProfileRequest = {
        nickname: nullableText(profileForm.nickname),
        avatarUrl: nullableText(profileForm.avatarUrl),
        gender: nullableText(profileForm.gender),
        birthday: nullableText(profileForm.birthday)
      };
      setProfileForm(profileToForm(await api.updateProfile(request)));
      setNotice("基础资料已保存");
    } catch (saveError) {
      setError(errorMessage(saveError, "基础资料保存失败"));
    } finally {
      setSavingSection(null);
    }
  }

  async function saveBodyData(event: FormEvent) {
    event.preventDefault();
    setNotice("");
    setError("");
    try {
      const request: UserBodyDataRequest = {
        heightCm: nullableNumber(bodyForm.heightCm),
        weightKg: nullableNumber(bodyForm.weightKg),
        gender: nullableText(bodyForm.gender),
        shoulderWidthCm: nullableNumber(bodyForm.shoulderWidthCm),
        bustCm: nullableNumber(bodyForm.bustCm),
        waistCm: nullableNumber(bodyForm.waistCm),
        hipCm: nullableNumber(bodyForm.hipCm),
        preferredFit: nullableText(bodyForm.preferredFit)
      };
      setSavingSection("body");
      setBodyForm(bodyToForm(await api.updateBodyMeasurements({
        heightCm: request.heightCm,
        weightKg: request.weightKg
      })));
      setNotice("身体数据已保存");
    } catch (saveError) {
      setError(errorMessage(saveError, "身体数据保存失败"));
    } finally {
      setSavingSection(null);
    }
  }

  async function savePreferences(event: FormEvent) {
    event.preventDefault();
    setNotice("");
    setError("");
    try {
      const budgetMin = nullableNumber(preferencesForm.budgetMin);
      const budgetMax = nullableNumber(preferencesForm.budgetMax);
      if (budgetMin !== null && budgetMax !== null && budgetMin > budgetMax) {
        setError("预算下限不能大于预算上限");
        return;
      }

      const request: UserPreferencesRequest = {
        preferredStyles: parsePreferenceList(preferencesForm.preferredStyles),
        preferredColors: parsePreferenceList(preferencesForm.preferredColors),
        dislikedColors: parsePreferenceList(preferencesForm.dislikedColors),
        preferredCategories: parsePreferenceList(preferencesForm.preferredCategories),
        budgetMin,
        budgetMax
      };
      setSavingSection("preferences");
      setPreferencesForm(preferencesToForm(await api.updatePreferences(request)));
      setNotice("穿衣偏好已保存");
    } catch (saveError) {
      setError(errorMessage(saveError, "穿衣偏好保存失败"));
    } finally {
      setSavingSection(null);
    }
  }

  return (
    <main className="profile-layout profile-wardrobe-page" data-testid="profile-page">
      <div className="profile-page-heading">
        <div>
          <p className="eyebrow">我的偏好</p>
          <h1>用户画像与尺码偏好</h1>
        </div>
        <button type="button" onClick={() => void loadProfileData()} disabled={isLoading} title="刷新资料">
          <RefreshCcw size={16} />
          刷新
        </button>
      </div>

      {notice && (
        <p className="status-line profile-feedback" role="status">
          {notice}
        </p>
      )}
      {error && (
        <p className="error-text profile-feedback" role="alert">
          {error}
        </p>
      )}

      {isLoading && <section className="profile-empty-state">资料加载中</section>}

      {!isLoading && !hasLoaded && (
        <section className="profile-empty-state">
          <span>资料暂时无法加载</span>
          <button className="primary-button" type="button" onClick={() => void loadProfileData()}>
            <RefreshCcw size={16} />
            重试
          </button>
        </section>
      )}

      {!isLoading && hasLoaded && (
        <div className="profile-grid">
          <form className="profile-section" onSubmit={saveProfile}>
            <div className="section-heading">
              <div>
                <p className="eyebrow">Profile</p>
                <h2>
                  <UserRound size={18} />
                  基础资料
                </h2>
              </div>
              <button
                className="primary-button"
                data-testid="profile-save"
                type="submit"
                disabled={savingSection === "profile"}
              >
                <Save size={16} />
                {savingSection === "profile" ? "保存中" : "保存"}
              </button>
            </div>
            <div className="profile-form-grid">
              <label>
                <span>昵称</span>
                <input
                  data-testid="profile-nickname"
                  value={profileForm.nickname}
                  onChange={(event) => updateProfileField("nickname", event.target.value)}
                  maxLength={64}
                />
              </label>
              <label>
                <span>性别</span>
                <select
                  data-testid="profile-gender"
                  data-native-dark-control="true"
                  value={profileForm.gender}
                  onChange={(event) => updateProfileField("gender", event.target.value)}
                >
                  <option value="">未设置</option>
                  <option value="male">男</option>
                  <option value="female">女</option>
                </select>
              </label>
              <label>
                <span>生日</span>
                <input
                  data-testid="profile-birthday"
                  data-native-dark-control="true"
                  type="date"
                  value={profileForm.birthday}
                  onChange={(event) => updateProfileField("birthday", event.target.value)}
                />
              </label>
              <label>
                <span>头像 URL</span>
                <input
                  data-testid="profile-avatar-url"
                  value={profileForm.avatarUrl}
                  onChange={(event) => updateProfileField("avatarUrl", event.target.value)}
                  maxLength={512}
                />
              </label>
            </div>
          </form>

          <form className="profile-section" onSubmit={saveBodyData}>
            <div className="section-heading">
              <div>
                <p className="eyebrow">Sizing</p>
                <h2>
                  <Ruler size={18} />
                  身体数据
                </h2>
              </div>
              <button
                className="primary-button"
                data-testid="body-save"
                type="submit"
                disabled={savingSection === "body"}
              >
                <Save size={16} />
                {savingSection === "body" ? "保存中" : "保存"}
              </button>
            </div>
            <div className="profile-form-grid">
              <label>
                <span>身高 cm</span>
                <input
                  data-testid="body-height-cm"
                  type="number"
                  min="0"
                  step="0.01"
                  value={bodyForm.heightCm}
                  onChange={(event) => updateBodyField("heightCm", event.target.value)}
                />
              </label>
              <label>
                <span>体重 kg</span>
                <input
                  data-testid="body-weight-kg"
                  type="number"
                  min="0"
                  step="0.01"
                  value={bodyForm.weightKg}
                  onChange={(event) => updateBodyField("weightKg", event.target.value)}
                />
              </label>
              <label>
                <span>性别</span>
                <select
                  data-testid="body-gender"
                  value={bodyForm.gender}
                  onChange={(event) => updateBodyField("gender", event.target.value)}
                >
                  <option value="">未设置</option>
                  <option value="male">男</option>
                  <option value="female">女</option>
                </select>
              </label>
              <label>
                <span>版型偏好</span>
                <select
                  data-testid="body-preferred-fit"
                  value={bodyForm.preferredFit}
                  onChange={(event) => updateBodyField("preferredFit", event.target.value)}
                >
                  <option value="">未设置</option>
                  <option value="slim">修身</option>
                  <option value="regular">标准</option>
                  <option value="loose">宽松</option>
                  <option value="oversized">Oversize</option>
                </select>
              </label>
              <label>
                <span>肩宽 cm</span>
                <input
                  data-testid="body-shoulder-width-cm"
                  type="number"
                  min="0"
                  step="0.01"
                  value={bodyForm.shoulderWidthCm}
                  onChange={(event) => updateBodyField("shoulderWidthCm", event.target.value)}
                />
              </label>
              <label>
                <span>胸围 cm</span>
                <input
                  data-testid="body-bust-cm"
                  type="number"
                  min="0"
                  step="0.01"
                  value={bodyForm.bustCm}
                  onChange={(event) => updateBodyField("bustCm", event.target.value)}
                />
              </label>
              <label>
                <span>腰围 cm</span>
                <input
                  data-testid="body-waist-cm"
                  type="number"
                  min="0"
                  step="0.01"
                  value={bodyForm.waistCm}
                  onChange={(event) => updateBodyField("waistCm", event.target.value)}
                />
              </label>
              <label>
                <span>臀围 cm</span>
                <input
                  data-testid="body-hip-cm"
                  type="number"
                  min="0"
                  step="0.01"
                  value={bodyForm.hipCm}
                  onChange={(event) => updateBodyField("hipCm", event.target.value)}
                />
              </label>
            </div>
          </form>

          <form className="profile-section wide" onSubmit={savePreferences}>
            <div className="section-heading">
              <div>
                <p className="eyebrow">Preference</p>
                <h2>
                  <Palette size={18} />
                  穿衣偏好
                </h2>
              </div>
              <button
                className="primary-button"
                data-testid="preferences-save"
                type="submit"
                disabled={savingSection === "preferences"}
              >
                <Save size={16} />
                {savingSection === "preferences" ? "保存中" : "保存"}
              </button>
            </div>
            <div className="profile-list-fields">
              <label>
                <span>偏好风格</span>
                <textarea
                  data-testid="preferences-styles"
                  value={preferencesForm.preferredStyles}
                  onChange={(event) => updatePreferencesField("preferredStyles", event.target.value)}
                  maxLength={1280}
                />
              </label>
              <label>
                <span>偏好颜色</span>
                <textarea
                  data-testid="preferences-colors"
                  value={preferencesForm.preferredColors}
                  onChange={(event) => updatePreferencesField("preferredColors", event.target.value)}
                  maxLength={1280}
                />
              </label>
              <label>
                <span>不喜欢的颜色</span>
                <textarea
                  data-testid="preferences-disliked-colors"
                  value={preferencesForm.dislikedColors}
                  onChange={(event) => updatePreferencesField("dislikedColors", event.target.value)}
                  maxLength={1280}
                />
              </label>
              <label>
                <span>偏好品类</span>
                <textarea
                  data-testid="preferences-categories"
                  value={preferencesForm.preferredCategories}
                  onChange={(event) => updatePreferencesField("preferredCategories", event.target.value)}
                  maxLength={1280}
                />
              </label>
              <label>
                <span>预算下限</span>
                <input
                  data-testid="preferences-budget-min"
                  type="number"
                  min="0"
                  step="0.01"
                  value={preferencesForm.budgetMin}
                  onChange={(event) => updatePreferencesField("budgetMin", event.target.value)}
                />
              </label>
              <label>
                <span>预算上限</span>
                <input
                  data-testid="preferences-budget-max"
                  type="number"
                  min="0"
                  step="0.01"
                  value={preferencesForm.budgetMax}
                  onChange={(event) => updatePreferencesField("budgetMax", event.target.value)}
                />
              </label>
            </div>
          </form>
        </div>
      )}
    </main>
  );
}
