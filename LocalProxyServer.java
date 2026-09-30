package com.mod;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LocalProxyServer {
    private static final int PORT = 8765;
    private static final String TARGET_HOST = "https://api3d.ruobr.ru";
    private static boolean sStarted = false;
    private static Context sContext;

    public static synchronized void start(Context context) {
        if (sStarted) return;
        sContext = context != null ? context.getApplicationContext() : null;
        sStarted = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"));
                    while (true) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            handleClientAsync(clientSocket);
                        } catch (Throwable t) {
                            // ignore socket errors
                        }
                    }
                } catch (Throwable t) {
                    sStarted = false;
                }
            }
        }).start();
    }

    private static void handleClientAsync(final Socket clientSocket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    handleClient(clientSocket);
                } catch (Throwable t) {
                    // ignore
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Throwable ignored) {}
                }
            }
        }).start();
    }

    private static void handleClient(Socket clientSocket) throws Exception {
        InputStream in = clientSocket.getInputStream();
        OutputStream out = clientSocket.getOutputStream();

        // Read request line and headers
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) return;

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return;
        String method = parts[0];
        String path = parts[1];

        Map<String, String> headers = new HashMap<String, String>();
        String line;
        int contentLength = 0;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String k = line.substring(0, colon).trim();
                String v = line.substring(colon + 1).trim();
                headers.put(k, v);
                if (k.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(v);
                    } catch (Exception ignored) {}
                }
            }
        }

        byte[] requestBody = null;
        if (contentLength > 0) {
            requestBody = new byte[contentLength];
            int read = 0;
            while (read < contentLength) {
                int r = in.read(requestBody, read, contentLength - read);
                if (r < 0) break;
                read += r;
            }
        }

        // Forward to target
        URL targetUrl = new URL(TARGET_HOST + path);
        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setRequestMethod(method);
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("Host") && !entry.getKey().equalsIgnoreCase("Connection")) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        conn.setRequestProperty("Host", "api3d.ruobr.ru");

        if (requestBody != null && requestBody.length > 0) {
            conn.setDoOutput(true);
            OutputStream connOut = conn.getOutputStream();
            connOut.write(requestBody);
            connOut.flush();
            connOut.close();
        }

        int responseCode = conn.getResponseCode();
        InputStream responseStream = (responseCode >= 200 && responseCode < 400) 
                ? conn.getInputStream() 
                : conn.getErrorStream();

        ByteArrayOutputStream respBuf = new ByteArrayOutputStream();
        if (responseStream != null) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = responseStream.read(buf)) != -1) {
                respBuf.write(buf, 0, n);
            }
            responseStream.close();
        }

        byte[] respBytes = respBuf.toByteArray();

        // Check if this is performance response and grade editor is enabled
        if (path.contains("/ba/new_mark/performance/") && responseCode == 200) {
            respBytes = modifyPerformanceJson(respBytes);
        }

        // Send response back to Flutter
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, "ISO-8859-1"));
        pw.print("HTTP/1.1 " + responseCode + " " + conn.getResponseMessage() + "\r\n");
        Map<String, List<String>> respHeaders = conn.getHeaderFields();
        for (Map.Entry<String, List<String>> h : respHeaders.entrySet()) {
            if (h.getKey() != null && !h.getKey().equalsIgnoreCase("Transfer-Encoding") 
                    && !h.getKey().equalsIgnoreCase("Content-Length")) {
                for (String val : h.getValue()) {
                    pw.print(h.getKey() + ": " + val + "\r\n");
                }
            }
        }
        pw.print("Content-Length: " + respBytes.length + "\r\n");
        pw.print("Connection: close\r\n\r\n");
        pw.flush();

        out.write(respBytes);
        out.flush();
    }

    private static byte[] modifyPerformanceJson(byte[] originalBytes) {
        try {
            if (sContext == null) return originalBytes;
            SharedPreferences prefs = sContext.getSharedPreferences("mod_prefs", Context.MODE_PRIVATE);
            if (!prefs.getBoolean("grade_editor_enabled", true)) {
                return originalBytes;
            }

            File overrideFile = new File(sContext.getFilesDir(), "mod_marks_override.json");
            if (!overrideFile.exists()) {
                return originalBytes;
            }

            // Read override config
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(overrideFile), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();

            JSONObject overrideJson = new JSONObject(sb.toString());
            // Format of overrideJson:
            // {
            //   "added": [ {"name": "Алгебра", "mark": 5, "date": "2026-09-30", "id": 99990001} ],
            //   "deleted_ids": [ 1375355996 ]
            // }

            String origStr = new String(originalBytes, "UTF-8");
            JSONObject root = new JSONObject(origStr);
            if (!root.has("data")) return originalBytes;

            JSONObject dataObj = root.getJSONObject("data");
            if (!dataObj.has("now_year")) return originalBytes;

            JSONArray nowYear = dataObj.getJSONArray("now_year");
            JSONArray addedMarks = overrideJson.optJSONArray("added");
            JSONArray deletedIds = overrideJson.optJSONArray("deleted_ids");

            Set<Long> deletedSet = new HashSet<Long>();
            if (deletedIds != null) {
                for (int i = 0; i < deletedIds.length(); i++) {
                    deletedSet.add(deletedIds.getLong(i));
                }
            }

            double totalSumAll = 0.0;
            int totalCountAll = 0;

            for (int p = 0; p < nowYear.length(); p++) {
                JSONObject period = nowYear.getJSONObject(p);
                if (!period.has("qs_sub")) continue;
                JSONArray qsSub = period.getJSONArray("qs_sub");

                for (int s = 0; s < qsSub.length(); s++) {
                    JSONObject sub = qsSub.getJSONObject(s);
                    String subName = sub.optString("name", "");
                    JSONArray qsMark = sub.optJSONArray("qs_mark");
                    if (qsMark == null) {
                        qsMark = new JSONArray();
                        sub.put("qs_mark", qsMark);
                    }

                    // 1. Remove deleted marks
                    JSONArray newMarkList = new JSONArray();
                    for (int m = 0; m < qsMark.length(); m++) {
                        JSONObject markObj = qsMark.getJSONObject(m);
                        long mid = markObj.optLong("mark_id", 0);
                        if (!deletedSet.contains(mid)) {
                            newMarkList.put(markObj);
                        }
                    }

                    // 2. Add new marks for this subject
                    if (addedMarks != null) {
                        for (int a = 0; a < addedMarks.length(); a++) {
                            JSONObject addObj = addedMarks.getJSONObject(a);
                            if (subName.equalsIgnoreCase(addObj.optString("name", ""))) {
                                JSONObject createdMark = new JSONObject();
                                createdMark.put("mark_id", addObj.optLong("id", System.currentTimeMillis() + a));
                                createdMark.put("date", addObj.optString("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())));
                                createdMark.put("mark", addObj.optInt("mark", 5));
                                createdMark.put("question__subject", "Ответ на уроке");
                                newMarkList.put(createdMark);
                            }
                        }
                    }

                    sub.put("qs_mark", newMarkList);

                    // 3. Recalculate avg, need_five, need_four
                    int markCount = newMarkList.length();
                    if (markCount > 0) {
                        int markSum = 0;
                        for (int m = 0; m < markCount; m++) {
                            markSum += newMarkList.getJSONObject(m).optInt("mark", 0);
                        }
                        double avg = (double) markSum / (double) markCount;
                        avg = Math.round(avg * 100.0) / 100.0;
                        sub.put("avg", avg);

                        // Need five: (markSum + 5*x) / (markCount + x) >= 4.50 => 0.5*x >= 4.5*markCount - markSum
                        double diff5 = 4.50 * markCount - markSum;
                        int needFive = (diff5 > 0) ? (int) Math.ceil(diff5 / 0.5) : 0;
                        sub.put("need_five", needFive);

                        // Need four: (markSum + 4*x) / (markCount + x) >= 3.50 => 0.5*x >= 3.5*markCount - markSum
                        double diff4 = 3.50 * markCount - markSum;
                        int needFour = (diff4 > 0) ? (int) Math.ceil(diff4 / 0.5) : 0;
                        sub.put("need_four", needFour);

                        totalSumAll += avg;
                        totalCountAll++;
                    }
                }
            }

            if (totalCountAll > 0) {
                double avgAll = totalSumAll / totalCountAll;
                avgAll = Math.round(avgAll * 10.0) / 10.0;
                dataObj.put("avg_all_sub", avgAll);
            }

            return root.toString().getBytes("UTF-8");
        } catch (Throwable t) {
            return originalBytes;
        }
    }
}
