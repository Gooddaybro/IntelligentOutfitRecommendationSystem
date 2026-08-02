import { useCallback, useMemo, useReducer, useRef } from "react";
import type { Dispatch, MutableRefObject, SetStateAction } from "react";
import {
  initialChatFilters,
  initialChatMessages
} from "./ChatPanel";
import type { ChatFilters, ChatMessage, ChatPanelState, RecommendationResultMeta } from "./ChatPanel";
import type { RecommendationCandidate } from "../../shared/api/types";

export type AssistantShoppingState = {
  messages: ChatMessage[];
  draft: string;
  filters: ChatFilters;
  threadId?: string;
  isStreaming: boolean;
  error: string;
  recommendations: RecommendationCandidate[];
  recommendationMeta?: RecommendationResultMeta;
  recommendationsLoaded: boolean;
  recommendationsLoading: boolean;
};

type AssistantShoppingAction =
  | { type: "setMessages"; value: SetStateAction<ChatMessage[]> }
  | { type: "setDraft"; value: SetStateAction<string> }
  | { type: "setFilters"; value: SetStateAction<ChatFilters> }
  | { type: "setThreadId"; value: SetStateAction<string | undefined> }
  | { type: "setIsStreaming"; value: SetStateAction<boolean> }
  | { type: "setError"; value: SetStateAction<string> }
  | { type: "setRecommendations"; value: SetStateAction<RecommendationCandidate[]> }
  | { type: "setRecommendationMeta"; value: SetStateAction<RecommendationResultMeta | undefined> }
  | { type: "setRecommendationsLoaded"; value: SetStateAction<boolean> }
  | { type: "setRecommendationsLoading"; value: SetStateAction<boolean> }
  | { type: "reset" };

export const initialAssistantShoppingState: AssistantShoppingState = {
  messages: initialChatMessages,
  draft: "",
  filters: initialChatFilters,
  threadId: undefined,
  isStreaming: false,
  error: "",
  recommendations: [],
  recommendationMeta: undefined,
  recommendationsLoaded: false,
  recommendationsLoading: false
};

function resolveStateAction<T>(value: SetStateAction<T>, current: T): T {
  return typeof value === "function" ? (value as (previous: T) => T)(current) : value;
}

export function assistantShoppingReducer(
  state: AssistantShoppingState,
  action: AssistantShoppingAction
): AssistantShoppingState {
  switch (action.type) {
    case "setMessages":
      return { ...state, messages: resolveStateAction(action.value, state.messages) };
    case "setDraft":
      return { ...state, draft: resolveStateAction(action.value, state.draft) };
    case "setFilters":
      return { ...state, filters: resolveStateAction(action.value, state.filters) };
    case "setThreadId":
      return { ...state, threadId: resolveStateAction(action.value, state.threadId) };
    case "setIsStreaming":
      return { ...state, isStreaming: resolveStateAction(action.value, state.isStreaming) };
    case "setError":
      return { ...state, error: resolveStateAction(action.value, state.error) };
    case "setRecommendations":
      return { ...state, recommendations: resolveStateAction(action.value, state.recommendations) };
    case "setRecommendationMeta":
      return { ...state, recommendationMeta: resolveStateAction(action.value, state.recommendationMeta) };
    case "setRecommendationsLoaded":
      return { ...state, recommendationsLoaded: resolveStateAction(action.value, state.recommendationsLoaded) };
    case "setRecommendationsLoading":
      return { ...state, recommendationsLoading: resolveStateAction(action.value, state.recommendationsLoading) };
    case "reset":
      return initialAssistantShoppingState;
  }
}

function dispatchSetter<T>(
  dispatch: Dispatch<AssistantShoppingAction>,
  type: AssistantShoppingAction["type"]
): Dispatch<SetStateAction<T>> {
  return (value) => dispatch({ type, value } as AssistantShoppingAction);
}

export function useAssistantShoppingState() {
  const [state, dispatch] = useReducer(assistantShoppingReducer, initialAssistantShoppingState);
  const abortRef = useRef<AbortController | null>(null);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    dispatch({ type: "reset" });
  }, []);

  const chatState = useMemo<ChatPanelState>(
    () => ({
      messages: state.messages,
      setMessages: dispatchSetter<ChatMessage[]>(dispatch, "setMessages"),
      draft: state.draft,
      setDraft: dispatchSetter<string>(dispatch, "setDraft"),
      filters: state.filters,
      setFilters: dispatchSetter<ChatFilters>(dispatch, "setFilters"),
      threadId: state.threadId,
      setThreadId: dispatchSetter<string | undefined>(dispatch, "setThreadId"),
      isStreaming: state.isStreaming,
      setIsStreaming: dispatchSetter<boolean>(dispatch, "setIsStreaming"),
      error: state.error,
      setError: dispatchSetter<string>(dispatch, "setError"),
      abortRef: abortRef as MutableRefObject<AbortController | null>
    }),
    [state.draft, state.error, state.filters, state.isStreaming, state.messages, state.threadId]
  );

  return {
    chatState,
    recommendations: state.recommendations,
    setRecommendations: dispatchSetter<RecommendationCandidate[]>(dispatch, "setRecommendations"),
    recommendationMeta: state.recommendationMeta,
    setRecommendationMeta: dispatchSetter<RecommendationResultMeta | undefined>(dispatch, "setRecommendationMeta"),
    recommendationsLoaded: state.recommendationsLoaded,
    setRecommendationsLoaded: dispatchSetter<boolean>(dispatch, "setRecommendationsLoaded"),
    recommendationsLoading: state.recommendationsLoading,
    setRecommendationsLoading: dispatchSetter<boolean>(dispatch, "setRecommendationsLoading"),
    reset
  };
}
