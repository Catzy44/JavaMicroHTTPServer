package me.catzy;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


public class HttpServer {

	private ServerSocket serverConnect = null;
	private List<HttpServerAction> actions = new ArrayList<>();
	private Thread t;
	private int port;
	
	public HttpServer(int port) {
		this.port = port;
	}
	
	private HttpServerAction byPath(String p) {
		for(HttpServerAction a : actions) {
			if(a.path.equals(p)) {
				return a;
			}
		}
		return null;
	}
	
	public void addAction(HttpServerAction a) {
		HttpServerAction x = byPath(a.path);
		if(x != null) {
			actions.remove(x);
		}
		actions.add(a);
	}
	
	private boolean debug = false;
	public void setDebug(boolean d) {
		debug = d;
	}
	
	public void start() {
		t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					//System.out.println("[WEBAPI] Starting web server...");
					serverConnect = new ServerSocket(port);
					//System.out.println("[WEBAPI] Done! Listening on port \"" + serverConnect.getLocalPort() + "\"");

					while (!Thread.interrupted()) {
						Socket socket = serverConnect.accept();

						new Thread(new Runnable() {
							@Override
							public void run() {
								BufferedReader in = null;
								PrintWriter pw = null;
								BufferedOutputStream bos = null;
								try {
									in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
									pw = new PrintWriter(socket.getOutputStream());// for headers
									bos = new BufferedOutputStream(socket.getOutputStream());// for data

									String input = in.readLine();
									StringTokenizer parse = new StringTokenizer(input);
									String method = parse.nextToken().toUpperCase(); // HTTP method
									String fileRequested = parse.nextToken().toLowerCase(); // file

									

									if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
										throwError(pw, bos, "501 Not Implemented");
									}

									byte[] fileData = null;
									String contentType = "application/json";
									String status = "200 OK";

									HttpServerAction ac = null;
									for (HttpServerAction a : actions) {
										if (!a.path.equals(fileRequested)) {
											continue;
										}
										ac = a;
									}
									if (ac == null) {
										status = "404 Not Found";
									} else {
										try {
											Object x = ac.run(method, socket.getRemoteSocketAddress().toString(), in);
											if (x instanceof JsonElement) {// json object
												JsonObject obj = new JsonObject();
												obj.addProperty("status", "ok");
												obj.add("value", (JsonElement) x);
												fileData = obj.toString().getBytes(Charset.forName("UTF-8"));

											} else if (x instanceof String) {// string (pack into json object)
												JsonObject obj = new JsonObject();
												obj.addProperty("status", "ok");
												obj.addProperty("value", (String) x);
												fileData = obj.toString().getBytes(Charset.forName("UTF-8"));

											} else if (x instanceof Integer) {// string (pack into json object)
												JsonObject obj = new JsonObject();
												obj.addProperty("status", "ok");
												obj.addProperty("value", (int) x);
												fileData = obj.toString().getBytes(Charset.forName("UTF-8"));

											} else if (x instanceof BufferedImage) {// bufferedimage - return bytes
												BufferedImage img = (BufferedImage) x;
												// ImageInputStream bigInputStream =
												// ImageIO.createImageInputStream(img);
												ByteArrayOutputStream baos = new ByteArrayOutputStream();
												ImageIO.write(img, "PNG", baos);
												contentType = "image/png";
												fileData = baos.toByteArray();
											} else {
												JsonObject obj = new JsonObject();
												obj.addProperty("status", "err");
												obj.addProperty("value", "server error");
												fileData = obj.toString().getBytes(Charset.forName("UTF-8"));
											}
										} catch (Exception e) {
											JsonObject obj = new JsonObject();
											obj.addProperty("status", "err");
											obj.addProperty("value", e.getMessage());
											fileData = obj.toString().getBytes(Charset.forName("UTF-8"));
											//e.printStackTrace();
											if(debug) {
												e.printStackTrace();
											}
										}

									}
									int fileLength = fileData == null ? 0 : fileData.length;

									pw.println("HTTP/1.1 "+status);
									pw.println("Server: Endi web server v1.0");
									pw.println("Date: " + new Date());
									pw.println("Content-type: " + contentType);// or text/html text/plain
									pw.println("Content-length: " + fileLength);
									pw.println();
									pw.flush();

									if (fileData != null) {
										bos.write(fileData, 0, fileLength);
										bos.flush();
									}
								} catch (Exception e) {// cleanup
									e.printStackTrace();
								} finally {
									try {in.close();} catch (Exception ex) {}
									try {pw.close();} catch (Exception ex) {}
									try {bos.close();} catch (Exception ex) {}
									try {socket.close();} catch (Exception ex) {}
									in = null;
									pw = null;
									bos = null;
								}
							}
						}).start();
					}
				} catch (IOException e) {
					// e.printStackTrace();
				}
			}
		});
		t.start();
	}

	public void shutdown() {
		t.interrupt();
		try {
			if(serverConnect != null) {
				serverConnect.close();
			}
		} catch (IOException e) {
		}
		serverConnect = null;
	}
	
	public static Gson gson = new Gson();

	public static JsonObject readJSONFromBRHTTP(BufferedReader in) throws IOException, ParseException {
		int contentLength = 0;
		String line;
		while (!(line = in.readLine()).equals("")) {
			if (line.startsWith("Content-Length:")) {
				contentLength = Integer.parseInt(line.split(": ")[1]);
			}
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < contentLength; i++) {
			sb.append((char) in.read());
		}
		
		synchronized (gson) {
			try {

				String s = sb.toString();
				return s == null || s.isEmpty() ? null : gson.fromJson(s, JsonObject.class);

			} catch (Exception e) {
				//EndiManager.log(ChatColor.RED+"HTTTPSV-JSON PARSING ERROR: "+e.getMessage());
				e.printStackTrace();
				return null;
			}
		}
	}

	public static Map<String, String> readFORMFromBRHTTP(BufferedReader in) throws NumberFormatException, IOException {
		int contentLength = 0;
		String line;
		while (!(line = in.readLine()).equals("")) {
			if (line.startsWith("Content-Length:")) {
				contentLength = Integer.parseInt(line.split(": ")[1]);
			}
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < contentLength; i++) {
			sb.append((char) in.read());
		}
		if(sb.toString().length() == 0) {
			return null;
		}
		String[] vals = sb.toString().split("&");
		Map<String, String> map = new HashMap<String, String>();
		for (String s : vals) {
			String g[] = s.split("=");
			map.put(g[0], g[1]);
		}
		return map;
	}

	private void throwError(PrintWriter out, OutputStream dataOut, String error) throws IOException {
		byte[] fileData = ("{\"status\":\"err\",\"value\",\"" + error + "\"}").getBytes(Charset.forName("UTF-8"));
		int fileLength = fileData.length;

		out.println("HTTP/1.1 " + error);
		out.println("Server: EndiMC statistics web server v1.0");
		out.println("Date: " + new Date());
		out.println("Content-type: " + "text/plain");
		out.println("Content-length: " + fileLength);
		out.println();
		out.flush();

		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
	}
}
