import type { ReactNode } from "react";

type AdminDataTableProps = {
  headers: string[];
  rows: Array<{ key: string | number; cells: ReactNode[] }>;
  emptyText: string;
};

export function AdminDataTable({ headers, rows, emptyText }: AdminDataTableProps) {
  if (!rows.length) return <div className="admin-table-empty">{emptyText}</div>;
  return <div className="admin-table-scroll"><table className="admin-table"><thead><tr>{headers.map((header) => <th key={header} scope="col">{header}</th>)}</tr></thead><tbody>{rows.map((row) => <tr key={row.key}>{row.cells.map((cell, index) => <td key={`${row.key}-${index}`}>{cell}</td>)}</tr>)}</tbody></table></div>;
}
