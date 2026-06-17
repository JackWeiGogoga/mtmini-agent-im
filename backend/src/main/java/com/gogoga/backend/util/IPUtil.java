package com.gogoga.backend.util;

import java.net.InetAddress;

import jakarta.servlet.http.HttpServletRequest;

public class IPUtil {

    /**
     * 从请求对象中获取ip地址.
     *
     * @param req
     * @return
     * @备注 此地有坑，注意isValidIP方法中，以192.168开头和以10.100开头的ip，都认为是非法ip，千万注意.
     */
    public static String getIPFormRequest(HttpServletRequest req) {
        String ip = req.getHeader("X-Real-IP");
        if (isValidIP(ip)) {
            ip = req.getHeader("X-Forwarded-For");
            if (isValidIP(ip)) {
                ip = req.getHeader("Proxy-Client-IP");
                if (isValidIP(ip)) {
                    ip = req.getHeader("WL-Proxy-Client-IP");
                    if (isValidIP(ip)) {
                        ip = req.getRemoteAddr();
                    }
                }
            }
        }
        return ip;
    }

    /**
     * 校验ip是否正确.
     *
     * @param ip ip字符串
     * @return
     */
    private static boolean isValidIP(String ip) {
        return ip == null || ip.length() == 0 || ip.startsWith("192.168") || ip.startsWith("10.100") || "unknown".equals(ip.toLowerCase());
    }

    /**
     * 获取请求的真实ip.
     *
     * @param req
     * @return
     */
    public static String getRealIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Real-IP");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Forwarded-For");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        return ip;
    }


    /**
     * 获取主机名：
     *
     * @return
     */
    public static String getLocalHostName() {
        String hostName;
        try {
            /** 返回本地主机。 */
            InetAddress addr = InetAddress.getLocalHost();
            /** 获取此 IP 地址的主机名。 */
            hostName = addr.getHostName();
        } catch (Exception ex) {
            hostName = "";
        }

        return hostName;
    }

    /**
     * 获取本机IP：
     *
     * @return
     */
    public static String getLocalIp() {
        String ip;
        try {
            /** 返回本地主机。 */
            InetAddress addr = InetAddress.getLocalHost();
            /** 获取此 IP 地址。 */
            ip = addr.getHostAddress();
        } catch (Exception ex) {
            ip = "";
        }

        return ip;
    }

}

