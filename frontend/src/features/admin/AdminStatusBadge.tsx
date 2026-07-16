type AdminStatusBadgeProps = { status: string; label?: string };

const toneByStatus: Record<string, string> = {
  ON_SALE: "success",
  ACTIVE: "success",
  PAID: "success",
  SHIPPED: "info",
  DRAFT: "neutral",
  OFF_SHELF: "warning",
  DISABLED: "warning",
  CANCELLED: "danger",
  FAILED: "danger"
};

export function AdminStatusBadge({ status, label = status }: AdminStatusBadgeProps) {
  return <span className={`admin-status admin-status--${toneByStatus[status] || "neutral"}`}>{label}</span>;
}
