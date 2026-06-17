import axios, { type AxiosResponse } from "axios";
import { message } from "@/components/AppMessageContext";
import { API_PREFIX, getFullPath, startsWithPath } from "@/utils/env";

// 创建 axios 实例
const request = axios.create({
  baseURL: `${API_PREFIX}/api`,
  timeout: 30000,
  withCredentials: true, // 支持跨域携带 Cookie
  headers: {
    "Content-Type": "application/json",
  },
});

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response;
    if (data.code === 200) {
      return data;
    }
    // 业务错误
    message.error(data.msg || "请求失败");
    return Promise.reject(new Error(data.msg || "请求失败"));
  },
  (error) => {
    // 网络错误
    if (error.response) {
      const { status, data } = error.response;
      switch (status) {
        case 401:
          // 未登录或 Token 过期
          // 只有在非登录相关页面时才跳转，避免无限循环
          if (
            !startsWithPath(window.location.pathname, "/login") &&
            !startsWithPath(window.location.pathname, "/register") &&
            !startsWithPath(window.location.pathname, "/oauth")
          ) {
            message.warning(data.msg || "请先登录");
            window.location.href = getFullPath("/login");
          }
          break;
        case 403:
          // 无权限
          message.error(data.msg || "您没有权限执行此操作");
          break;
        case 409:
          message.error(data.msg || "数据已被修改，请刷新后重试");
          break;
        case 404:
          message.error(data.msg || "资源不存在");
          break;
        case 400:
          message.error(data.msg || "参数错误");
          break;
        default:
          message.error(data.msg || "服务器错误");
      }
    } else {
      message.error("网络错误，请检查网络连接");
    }
    return Promise.reject(error);
  },
);

export default request;
