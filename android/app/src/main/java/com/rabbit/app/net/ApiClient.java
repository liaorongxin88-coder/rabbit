package com.rabbit.app.net;

import com.google.gson.JsonObject;
import com.rabbit.app.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ApiClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client;

    public ApiClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public JsonObject postJson(String path, String token, Long houseId, JsonObject body) throws IOException {
        RequestBody rb = RequestBody.create(JSON, body == null ? "{}" : body.toString());
        Request.Builder b = new Request.Builder()
                .url(Config.getBaseUrl() + path)
                .post(rb)
                .addHeader("Content-Type", "application/json");
        if (token != null) {
            b.addHeader("Authorization", "Bearer " + token);
        }
        if (houseId != null && houseId > 0) {
            b.addHeader("X-House-Id", String.valueOf(houseId));
        }
        Response resp = client.newCall(b.build()).execute();
        String s = resp.body() != null ? resp.body().string() : "";
        if (!resp.isSuccessful()) {
            throw new IOException("http " + resp.code() + " " + s);
        }
        return parseApiResponse(s);
    }

    public JsonObject putJson(String path, String token, Long houseId, JsonObject body) throws IOException {
        RequestBody rb = RequestBody.create(JSON, body == null ? "{}" : body.toString());
        Request.Builder b = new Request.Builder()
                .url(Config.getBaseUrl() + path)
                .put(rb)
                .addHeader("Content-Type", "application/json");
        if (token != null) {
            b.addHeader("Authorization", "Bearer " + token);
        }
        if (houseId != null && houseId > 0) {
            b.addHeader("X-House-Id", String.valueOf(houseId));
        }
        Response resp = client.newCall(b.build()).execute();
        String s = resp.body() != null ? resp.body().string() : "";
        if (!resp.isSuccessful()) {
            throw new IOException("http " + resp.code() + " " + s);
        }
        return parseApiResponse(s);
    }

    public JsonObject deleteJson(String path, String token, Long houseId) throws IOException {
        Request.Builder b = new Request.Builder()
                .url(Config.getBaseUrl() + path)
                .delete();
        if (token != null) {
            b.addHeader("Authorization", "Bearer " + token);
        }
        if (houseId != null && houseId > 0) {
            b.addHeader("X-House-Id", String.valueOf(houseId));
        }
        Response resp = client.newCall(b.build()).execute();
        String s = resp.body() != null ? resp.body().string() : "";
        if (!resp.isSuccessful()) {
            throw new IOException("http " + resp.code() + " " + s);
        }
        return parseApiResponse(s);
    }

    public JsonObject getJson(String path, String token, Long houseId) throws IOException {
        Request.Builder b = new Request.Builder()
                .url(Config.getBaseUrl() + path)
                .get();
        if (token != null) {
            b.addHeader("Authorization", "Bearer " + token);
        }
        if (houseId != null && houseId > 0) {
            b.addHeader("X-House-Id", String.valueOf(houseId));
        }
        Response resp = client.newCall(b.build()).execute();
        String s = resp.body() != null ? resp.body().string() : "";
        if (!resp.isSuccessful()) {
            throw new IOException("http " + resp.code() + " " + s);
        }
        return parseApiResponse(s);
    }

    public byte[] getBytes(String path, String token, Long houseId) throws IOException {
        Request.Builder b = new Request.Builder()
                .url(Config.getBaseUrl() + path)
                .get();
        if (token != null) {
            b.addHeader("Authorization", "Bearer " + token);
        }
        if (houseId != null && houseId > 0) {
            b.addHeader("X-House-Id", String.valueOf(houseId));
        }
        Response resp = client.newCall(b.build()).execute();
        byte[] bytes = resp.body() != null ? resp.body().bytes() : new byte[0];
        if (!resp.isSuccessful()) {
            throw new IOException("http " + resp.code() + " " + new String(bytes, StandardCharsets.UTF_8));
        }
        return bytes;
    }

    public interface ProgressListener {
        void onProgress(long bytesRead, long totalBytes);
    }

    public static class DownloadHandle {
        private final Call call;

        public DownloadHandle(Call call) {
            this.call = call;
        }

        public void cancel() {
            if (call != null) {
                call.cancel();
            }
        }

        public boolean isCanceled() {
            return call != null && call.isCanceled();
        }
    }

    public interface DownloadCallback {
        void onProgress(long bytesRead, long totalBytes);

        void onSuccess(File file);

        void onError(Exception e);

        void onCanceled();
    }

    public DownloadHandle downloadToFileAsync(String path, String token, Long houseId, File target, DownloadCallback cb) {
        Request.Builder b = new Request.Builder()
                .url(Config.getBaseUrl() + path)
                .get();
        if (token != null) {
            b.addHeader("Authorization", "Bearer " + token);
        }
        if (houseId != null && houseId > 0) {
            b.addHeader("X-House-Id", String.valueOf(houseId));
        }
        Call call = client.newCall(b.build());
        DownloadHandle handle = new DownloadHandle(call);

        new Thread(() -> {
            Response resp = null;
            ResponseBody body = null;
            InputStream is = null;
            FileOutputStream os = null;
            boolean ok = false;
            try {
                resp = call.execute();
                body = resp.body();
                if (!resp.isSuccessful()) {
                    byte[] err = body != null ? body.bytes() : new byte[0];
                    throw new IOException("http " + resp.code() + " " + new String(err, StandardCharsets.UTF_8));
                }
                if (body == null) {
                    throw new IOException("empty body");
                }
                long total = body.contentLength();
                is = body.byteStream();
                os = new FileOutputStream(target);
                byte[] buf = new byte[8192];
                long read = 0;
                int n;
                while ((n = is.read(buf)) >= 0) {
                    if (handle.isCanceled()) {
                        throw new IOException("Canceled");
                    }
                    os.write(buf, 0, n);
                    read += n;
                    if (cb != null) {
                        cb.onProgress(read, total);
                    }
                }
                os.flush();
                ok = true;
                if (cb != null) {
                    cb.onSuccess(target);
                }
            } catch (Exception e) {
                if (handle.isCanceled()) {
                    if (cb != null) {
                        cb.onCanceled();
                    }
                } else {
                    if (cb != null) {
                        cb.onError(e);
                    }
                }
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (os != null) {
                        os.close();
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (body != null) {
                        body.close();
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (resp != null) {
                        resp.close();
                    }
                } catch (Exception ignored) {
                }
                if (!ok && target != null) {
                    try {
                        target.delete();
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
        return handle;
    }

    private JsonObject parseApiResponse(String s) throws IOException {
        JsonObject obj = Json.gson.fromJson(s, JsonObject.class);
        if (obj != null && obj.has("code")) {
            int code = obj.get("code").getAsInt();
            if (code != 0) {
                String msg = obj.has("message") ? obj.get("message").getAsString() : "error";
                throw new IOException(msg);
            }
        }
        return obj;
    }
}
