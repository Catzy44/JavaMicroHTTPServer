package me.kotsu.microHTTPServer;

import java.io.BufferedReader;

public abstract class HttpServerAction {
	public String path;

	public HttpServerAction(String path) {
		this.path = path;
	}

	public abstract Object run(String method, String ip, BufferedReader in) throws Exception;
}