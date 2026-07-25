package com.termux.app.clipboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TermuxClipboardServer {

    private static final String TAG = "ClipboardServer";
    private static final int PORT = 18787;
    private static final AtomicBoolean sRunning = new AtomicBoolean(false);
    private static ServerSocket sServerSocket;
    private static ExecutorService sExecutor;

    public static synchronized void start(Context context) {
        if (sRunning.get()) return;

        sExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "clipboard-server");
            t.setDaemon(true);
            return t;
        });

        sExecutor.execute(() -> runServer(context));
    }

    private static void runServer(Context context) {
        try {
            sServerSocket = new ServerSocket(PORT, 5, java.net.InetAddress.getByName("127.0.0.1"));
            sRunning.set(true);
            Log.d(TAG, "Clipboard server listening on 127.0.0.1:" + PORT);

            while (!sServerSocket.isClosed()) {
                try {
                    Socket client = sServerSocket.accept();
                    handleClient(context, client);
                } catch (Exception e) {
                    if (!sServerSocket.isClosed()) {
                        Log.e(TAG, "Error accepting client", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start clipboard server", e);
            sRunning.set(false);
        }
    }

    private static void handleClient(Context context, Socket client) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter writer = new PrintWriter(client.getOutputStream(), true);

            String request = reader.readLine();
            if (request == null) {
                writer.println("ERROR:empty request");
                return;
            }

            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                writer.println("ERROR:clipboard service not available");
                return;
            }

            if (request.equals("GET")) {
                String text = "";
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    ClipData.Item item = clip.getItemAt(0);
                    CharSequence cs = item.getText();
                    if (cs != null) text = cs.toString();
                }
                writer.println(text);
            } else if (request.startsWith("SET:")) {
                String text = request.substring(4);
                ClipData clip = ClipData.newPlainText("termux", text != null ? text : "");
                clipboard.setPrimaryClip(clip);
                writer.println("OK");
            } else if (request.equals("PING")) {
                writer.println("PONG");
            } else {
                writer.println("ERROR:unknown command");
            }

            reader.close();
            writer.close();
            client.close();
        } catch (Exception e) {
            Log.e(TAG, "Error handling clipboard client", e);
        }
    }

    public static synchronized void stop() {
        sRunning.set(false);
        try {
            if (sServerSocket != null) sServerSocket.close();
        } catch (IOException ignored) {}
        if (sExecutor != null) sExecutor.shutdownNow();
    }
}
