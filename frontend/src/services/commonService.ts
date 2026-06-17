/**
 * 通用服务 API
 */
import request from "@/utils/request";

/**
 * 环境信息
 */
export interface EnvInfo {
  profile: string;
  online: boolean;
  label: string;
}

/**
 * 获取当前环境信息
 */
export const getEnvInfo = async (): Promise<EnvInfo> => {
  const response = await request.get<EnvInfo>("/common/env");
  return response.data;
};
