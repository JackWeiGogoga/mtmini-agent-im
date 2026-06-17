import type { CSSProperties } from "react";

const COMPACT_OVERLAY_MARGIN = 24;
const MOBILE_OVERLAY_MARGIN = 16;

export function getResponsiveOverlayWidth(
  isCompact: boolean,
  desktopWidth: number,
  mobileWidth?: number,
): string | number {
  if (!isCompact) {
    return desktopWidth;
  }

  if (mobileWidth) {
    return `min(calc(100vw - ${MOBILE_OVERLAY_MARGIN}px), ${mobileWidth}px)`;
  }

  return `calc(100vw - ${COMPACT_OVERLAY_MARGIN}px)`;
}

export function getResponsiveDrawerWidth(
  isCompact: boolean,
  desktopWidth: number,
): string | number {
  if (!isCompact) {
    return desktopWidth;
  }

  return `calc(100vw - ${MOBILE_OVERLAY_MARGIN}px)`;
}

export function getResponsiveControlStyle(
  isCompact: boolean,
  desktopWidth: number | string,
): CSSProperties {
  return {
    width: isCompact ? "100%" : desktopWidth,
  };
}
