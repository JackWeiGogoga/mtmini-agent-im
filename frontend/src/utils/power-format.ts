export function formatCompactNumber(value: number, maxDecimals = 2): string {
  const numeric = Number(value) || 0;
  return numeric.toFixed(maxDecimals).replace(/\.?0+$/, "");
}

export function formatPowerAmount(value?: number | null): string {
  const amount = Number(value) || 0;
  if (amount >= 100000000) {
    return `${formatCompactNumber(amount / 100000000, 2)}亿`;
  }
  if (amount >= 10000) {
    return `${formatCompactNumber(amount / 10000, 2)}万`;
  }
  return `${amount}`;
}

export function formatPowerWithUnit(value?: number | null): string {
  return `${formatPowerAmount(value)} 算力`;
}
