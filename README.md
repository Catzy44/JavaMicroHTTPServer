"# JavaMicroHTTPServer" 

##How to initialize the server:

HttpServer server = new HttpServer(80); //REPLACE 80 WITH YOUR CHOOSEN PORT!
//server.setDebug(true); //THIS PRINTS DEBUG INFORMATION INTO SYSTEM STDOUT (CONSOLE)
server.start(); //starts the server thread (ASYNC!!) (ITS NOT STOPING YOUR CODE!)


##How to make the server actually return something:
```
server.addAction(new HttpServerAction("/helloworld") {
	@Override
	public Object run(String method, String ip, BufferedReader in) throws Exception {
		return "Hello World!";
	}
});
```
this will make that the server should respond to:
```GET serverIpAddress:port/helloworld```

with a String:
Hello World!


##!IMPORTANT!

The server expects the return Object of HttpServerAction run method to be:
NULL, GSON JsonElement, Integer, String, BufferedImage

so u can for example return a bufferedImage directly from HttpServerAction
for example:
```
server.addAction(new HttpServerAction("/image") {
	@Override
	public Object run(String method, String ip, BufferedReader in) throws Exception {
	
		BufferedImage image = ImageIO.read(...)
	
		return image; //return BufferedImage object to the client through HTTP
	}
});
```
so client can just open in his browser: ```127.0.0.1:80/image``` and he will get that image (as preview or download, depends on the browser)