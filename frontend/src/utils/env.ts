/**
 * 环境配置工具
 */

function normalizePrefix(value: string | undefined): string {
  if (!value) {
    return "";
  }

  const trimmed = value.trim();
  if (!trimmed || trimmed === "/") {
    return "";
  }

  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

/** API 请求前缀 */
export const API_PREFIX = normalizePrefix(import.meta.env.VITE_API_PREFIX);

/** 路由基础路径（从 Vite 配置的 base 获取） */
export const BASE_PATH = import.meta.env.BASE_URL || "/";

/**
 * 获取带前缀的完整路径
 * @param path 相对路径（以 / 开头）
 * @returns 完整路径
 */
export function getFullPath(path: string): string {
  const basePath = BASE_PATH.endsWith("/") ? BASE_PATH.slice(0, -1) : BASE_PATH;
  return `${basePath}${path}`;
}

/**
 * 检查当前路径是否以指定路径开头
 * @param pathname 当前路径
 * @param path 要检查的路径前缀
 * @returns 是否匹配
 */
export function startsWithPath(pathname: string, path: string): boolean {
  const basePath = BASE_PATH.endsWith("/") ? BASE_PATH.slice(0, -1) : BASE_PATH;
  return pathname.startsWith(`${basePath}${path}`) || pathname.startsWith(path);
}
