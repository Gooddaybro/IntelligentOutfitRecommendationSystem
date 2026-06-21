import { useCallback, useEffect, useState } from "react";
import { api, clearAuthTokens, getAccessToken, setAuthTokens } from "../../shared/api/client";
import type { CurrentUserResponse } from "../../shared/api/types";

type UseAuthSessionOptions = {
  onAuthenticated: () => Promise<void>;
  onSessionCleared: () => void;
};

export function useAuthSession({ onAuthenticated, onSessionCleared }: UseAuthSessionOptions) {
  const [user, setUser] = useState<CurrentUserResponse | null>(null);
  const [error, setError] = useState("");
  const [isBusy, setIsBusy] = useState(false);

  const clearSession = useCallback(() => {
    clearAuthTokens();
    setUser(null);
    onSessionCleared();
  }, [onSessionCleared]);

  const loadUser = useCallback(async () => {
    if (!getAccessToken()) {
      return;
    }
    try {
      setUser(await api.me());
      await onAuthenticated();
    } catch {
      clearSession();
    }
  }, [clearSession, onAuthenticated]);

  useEffect(() => {
    void loadUser();
  }, [loadUser]);

  const login = useCallback(
    async (username: string, password: string) => {
      setError("");
      setIsBusy(true);
      try {
        setAuthTokens(await api.login(username, password));
        await loadUser();
      } catch (loginError) {
        setError(loginError instanceof Error ? loginError.message : "登录失败");
      } finally {
        setIsBusy(false);
      }
    },
    [loadUser]
  );

  const register = useCallback(
    async (username: string, password: string, email?: string) => {
      setError("");
      setIsBusy(true);
      try {
        await api.register(username, password, email);
        setAuthTokens(await api.login(username, password));
        await loadUser();
      } catch (registerError) {
        setError(registerError instanceof Error ? registerError.message : "注册失败");
      } finally {
        setIsBusy(false);
      }
    },
    [loadUser]
  );

  return {
    user,
    error,
    isBusy,
    login,
    register,
    clearSession
  };
}
