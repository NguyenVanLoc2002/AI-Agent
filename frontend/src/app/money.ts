// Formats an exact decimal amount string from the backend without floating point ("1250000" →
// "1.250.000 ₫"). VND has scale 0 (ADR-014); another currency keeps its decimals and shows its code.

export function formatMoney(amount: string, currency: string): string {
  const [whole = '0', fraction] = amount.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const number = fraction ? `${grouped},${fraction}` : grouped;
  return currency === 'VND' ? `${number} ₫` : `${number} ${currency}`;
}
