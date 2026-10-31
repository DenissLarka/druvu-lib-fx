package com.druvu.lib.fx.updater.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An HTTP server of one thread for the download tests: a GET of a known path answers its bytes with a Content-Length,
 * any other path answers 404. Plain sockets, so the test needs no module the updater does not read.
 */
public final class TinyHttpServer implements AutoCloseable {

    private final ServerSocket socket;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Thread thread;

    public TinyHttpServer() {
        try {
            socket = new ServerSocket(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        thread = Thread.ofVirtual().start(this::serve);
    }

    public TinyHttpServer serving(String path, byte[] body) {
        files.put(path, body);
        return this;
    }

    public URI url(String path) {
        return URI.create("http://localhost:" + socket.getLocalPort() + path);
    }

    private void serve() {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                answer(client);
            } catch (IOException e) {
                if (socket.isClosed()) {
                    return; // close() ended the server; a client that went away just ends its own round
                }
            }
        }
    }

    private void answer(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        String requestLine = readLine(in);
        String header;
        do {
            header = readLine(in); // the headers are not needed, only their end
        } while (!header.isEmpty());
        String path = requestLine.split(" ")[1];
        byte[] body = files.get(path);
        OutputStream out = client.getOutputStream();
        if (body == null) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
        } else {
            out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.write(body);
        }
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (c != '\r') {
                line.append((char) c);
            }
        }
        return line.toString();
    }

    @Override
    public void close() throws IOException {
        socket.close();
        thread.interrupt();
    }
}
