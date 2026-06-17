package com.gogoga.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.gogoga.backend.util.IPUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 服务部署、健康检查相关接口
 *
 */
@Controller
public class ServerStatusController {

	private static final Logger logger = LoggerFactory.getLogger(ServerStatusController.class);
	// 默认为true，表示启动服务即注册成功
	private boolean serverAlive = true;

	/**
	 * return sever current status(always return true)
	 *
	 */
	@RequestMapping("/health/check")
	public void getServerCheck(HttpServletResponse resp) {
		writeRespBack(resp, HttpStatus.OK.value(), HttpStatus.OK.getReasonPhrase());
	}

	/**
	 * return sever current status （用于nginx探活）
	 *
	 */
	@RequestMapping("/health/status")
	public void getServerStatus(HttpServletResponse resp) {
		int httpCode = serverAlive ? HttpStatus.OK.value() : HttpStatus.FORBIDDEN.value();
		writeRespBack(resp, httpCode, serverAlive ? "alive" : "offline");
	}

	/**
	 * active the server.
	 *
	 */
	@RequestMapping("/health/active")
	public void active(HttpServletRequest req, HttpServletResponse resp) {
		if (serverAlive) {
			writeRespBack(resp, HttpStatus.OK.value(), HttpStatus.OK.getReasonPhrase());
			return;
		}
		String msg = "deny";
		if (isAllowIp(IPUtil.getIPFormRequest(req))) {
			this.serverAlive = true;
			msg = "ok";
		}
		writeRespBack(resp, HttpStatus.OK.value(), msg);
	}

	/**
	 * offline the server.
	 *
	 */
	@RequestMapping("/health/offline")
	public void offline(HttpServletRequest req, HttpServletResponse resp) {
		String msg = "deny";
		if (isAllowIp(IPUtil.getIPFormRequest(req))) {
			this.serverAlive = false;
			msg = "ok";
		}
		writeRespBack(resp, HttpStatus.OK.value(), msg);
	}

	@RequestMapping("/robots.txt")
	public void robotsTxt(HttpServletResponse response) {
		try {
			response.setStatus(HttpStatus.OK.value());
			response.setHeader("Pragma", "No-cache");
			response.setHeader("Cache-Control", "no-cache");
			response.setDateHeader("Expires", 0);
			response.setContentType("text/html; charset=UTF-8");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().println("User-agent: *");
			response.getWriter().println("Disallow: /");
			response.getWriter().flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeRespBack(HttpServletResponse reponse, int httpCode,
			String msg) {
		reponse.setStatus(httpCode);
		reponse.setCharacterEncoding("utf-8");
		reponse.setContentType("text/javascript; charset=utf-8");
		try {
			reponse.getWriter().print(msg);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	private boolean isAllowIp(String actualIp) {
		return "127.0.0.1".equals(actualIp);
	}
}
