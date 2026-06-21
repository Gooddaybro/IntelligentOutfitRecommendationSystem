import { Check, X } from "lucide-react";
import { actionConfirmText, type PendingCommerceAction } from "./commerceActions";

type ConfirmActionDialogProps = {
  action: PendingCommerceAction | null;
  isBusy: boolean;
  onCancel: () => void;
  onConfirm: () => Promise<unknown>;
};

export function ConfirmActionDialog({ action, isBusy, onCancel, onConfirm }: ConfirmActionDialogProps) {
  if (!action) {
    return null;
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="confirm-dialog" data-testid="confirm-action-dialog" role="dialog" aria-modal="true" aria-label="确认交易动作">
        <div>
          <p className="eyebrow">需要你确认</p>
          <h2>{actionConfirmText(action)}</h2>
          <p className="muted">AI 只能推荐商品，最终加购和下单必须由你确认。</p>
        </div>
        <dl className="summary-list">
          <div>
            <dt>SKU</dt>
            <dd>{action.skuId}</dd>
          </div>
          <div>
            <dt>数量</dt>
            <dd>{action.quantity}</dd>
          </div>
          <div>
            <dt>后端价格</dt>
            <dd>￥{action.unitPrice}</dd>
          </div>
        </dl>
        <div className="dialog-actions">
          <button data-testid="confirm-action-cancel" onClick={onCancel} disabled={isBusy}>
            <X size={16} />
            取消
          </button>
          <button
            className="primary-button"
            data-testid="confirm-action-submit"
            onClick={() => void onConfirm()}
            disabled={isBusy}
          >
            <Check size={16} />
            确认
          </button>
        </div>
      </section>
    </div>
  );
}
