package com.rabbit.app.ui;

import android.content.Intent;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Build;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.DatePickerUtil;
import com.rabbit.app.util.InputUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ReportsActivity extends AppCompatActivity {
    private EditText etFrom;
    private EditText etTo;
    private EditText etMaxRows;
    private Button btnClearDates;
    private Button btnRefresh;
    private TextView tvInfo;
    private TextView tvFeedSummary;
    private TextView tvBreedingSummary;
    private Spinner spAckCategory;
    private TextView tvAckSummary;
    private Button btnExportFeedCsv;
    private Button btnExportAckCsv;
    private ProgressBar pb;
    private ProgressBar pbExport;
    private TextView tvExportStatus;
    private Button btnCancelExport;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private ApiClient.DownloadHandle currentDownload;
    private StatePanel state;

    private final List<String> ackCategories = Arrays.asList("生产", "后备成熟");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        etFrom = findViewById(R.id.etReportsFrom);
        etTo = findViewById(R.id.etReportsTo);
        etMaxRows = findViewById(R.id.etReportsMaxRows);
        btnClearDates = findViewById(R.id.btnReportsClearDates);
        btnRefresh = findViewById(R.id.btnReportsRefresh);
        tvInfo = findViewById(R.id.tvReportsInfo);
        tvFeedSummary = findViewById(R.id.tvFeedSummary);
        tvBreedingSummary = findViewById(R.id.tvBreedingSummary);
        spAckCategory = findViewById(R.id.spAckCategory);
        tvAckSummary = findViewById(R.id.tvAckSummary);
        btnExportFeedCsv = findViewById(R.id.btnExportFeedCsv);
        btnExportAckCsv = findViewById(R.id.btnExportAckCsv);
        pb = findViewById(R.id.pbReportsLoading);
        pbExport = findViewById(R.id.pbReportsExport);
        tvExportStatus = findViewById(R.id.tvReportsExportStatus);
        btnCancelExport = findViewById(R.id.btnReportsCancelExport);

        api = new ApiClient();
        session = new SessionStore(this);
        state = new StatePanel(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("报表");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        DatePickerUtil.attach(this, etFrom);
        DatePickerUtil.attach(this, etTo);

        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, ackCategories);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAckCategory.setAdapter(ad);

        btnClearDates.setOnClickListener(v -> {
            etFrom.setText("");
            etTo.setText("");
        });
        btnRefresh.setOnClickListener(v -> loadSummaries());
        btnExportFeedCsv.setOnClickListener(v -> exportFeedCsv());
        btnExportAckCsv.setOnClickListener(v -> exportAckCsv());
        btnCancelExport.setOnClickListener(v -> cancelExport());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSummaries();
    }

    private void loadSummaries() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        Long fromMs = parseFromMs();
        Long toMs = parseToMs();
        String category = safeString(spAckCategory.getSelectedItem());

        tvInfo.setText("");
        state.hide();
        pb.setVisibility(View.VISIBLE);
        if (houseId <= 0) {
            pb.setVisibility(View.GONE);
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再刷新汇总", "刷新", this::loadSummaries);
            return;
        }
        new Thread(() -> {
            try {
                JsonObject feedResp = api.getJson("/api/reports/feed-summary" + qs(fromMs, toMs), token, houseId);
                JsonObject breedingResp = api.getJson("/api/reports/breeding-summary", token, houseId);
                JsonObject ackResp = api.getJson("/api/reports/event-ack-summary?category=" + enc(category) + qs2(fromMs, toMs), token, houseId);

                JsonObject feed = feedResp != null && feedResp.has("data") && feedResp.get("data").isJsonObject() ? feedResp.getAsJsonObject("data") : new JsonObject();
                JsonObject breeding = breedingResp != null && breedingResp.has("data") && breedingResp.get("data").isJsonObject() ? breedingResp.getAsJsonObject("data") : new JsonObject();
                JsonObject ack = ackResp != null && ackResp.has("data") && ackResp.get("data").isJsonObject() ? ackResp.getAsJsonObject("data") : new JsonObject();

                String feedLine = "记录数=" + safeNum(feed, "recordCount") + "  总量=" + safeNum(feed, "totalAmount");
                String breedingLine = "总窝数=" + safeNum(breeding, "totalLitters")
                        + "  总产仔=" + safeNum(breeding, "totalKits")
                        + "  总活仔=" + safeNum(breeding, "totalLiveKits")
                        + "  总断奶=" + safeNum(breeding, "totalWeaned")
                        + "  成功=" + safeNum(breeding, "successCount")
                        + "  失败=" + safeNum(breeding, "failCount");
                String ackLine = "确认=" + safeNum(ack, "ackCount")
                        + "  忽略=" + safeNum(ack, "ignoreCount")
                        + "  稍后=" + safeNum(ack, "snoozeCount")
                        + "  平均处理小时=" + safeNum(ack, "avgHandleHours");

                runOnUiThread(() -> {
                    tvFeedSummary.setText(feedLine);
                    tvBreedingSummary.setText(breedingLine);
                    tvAckSummary.setText(ackLine);
                    pb.setVisibility(View.GONE);
                    state.hide();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    state.showError(e.getMessage(), "重试", this::loadSummaries);
                });
            }
        }).start();
    }

    private void exportFeedCsv() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        Long fromMs = parseFromMs();
        Long toMs = parseToMs();
        Integer maxRows = parseMaxRows();
        String path = "/api/reports/feed-logs.csv" + qs(fromMs, toMs) + (maxRows == null ? "" : (qs(fromMs, toMs).isEmpty() ? "?" : "&") + "maxRows=" + maxRows);
        String name = fileName("feed_logs", rangeTag(), houseId);
        startExport(path, token, houseId, name, "分享投喂CSV");
    }

    private void exportAckCsv() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        Long fromMs = parseFromMs();
        Long toMs = parseToMs();
        String category = safeString(spAckCategory.getSelectedItem());

        String path = "/api/reports/event-ack-summary.csv?category=" + enc(category) + qs2(fromMs, toMs);
        String name = fileName("event_ack_" + slugCategory(category), rangeTag(), houseId);
        startExport(path, token, houseId, name, "分享提醒时效CSV");
    }

    private void startExport(String path, String token, long houseId, String fileName, String chooserTitle) {
        if (currentDownload != null) {
            tvInfo.setText("已有导出进行中");
            return;
        }
        File f;
        try {
            f = prepareCsvFile(fileName);
        } catch (Exception e) {
            tvInfo.setText(e.getMessage());
            return;
        }
        tvInfo.setText("");
        showExportUi(true);
        currentDownload = api.downloadToFileAsync(path, token, houseId, f, new ApiClient.DownloadCallback() {
            @Override
            public void onProgress(long bytesRead, long totalBytes) {
                runOnUiThread(() -> updateExportProgress(bytesRead, totalBytes));
            }

            @Override
            public void onSuccess(File file) {
                runOnUiThread(() -> {
                    showExportUi(false);
                    try {
                        Uri uri = saveToDownloads(file, fileName);
                        tvInfo.setText("已保存到下载：" + fileName);
                        shareUri(uri, "text/csv", chooserTitle);
                    } catch (Exception e) {
                        tvInfo.setText("已保存：" + file.getAbsolutePath() + "\n" + e.getMessage());
                        shareFile(file, "text/csv", chooserTitle);
                    }
                    currentDownload = null;
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showExportUi(false);
                    tvInfo.setText(e.getMessage());
                    currentDownload = null;
                });
            }

            @Override
            public void onCanceled() {
                runOnUiThread(() -> {
                    showExportUi(false);
                    tvInfo.setText("已取消导出");
                    currentDownload = null;
                });
            }
        });
    }

    private void cancelExport() {
        if (currentDownload != null) {
            currentDownload.cancel();
        }
    }

    private void showExportUi(boolean exporting) {
        btnExportFeedCsv.setEnabled(!exporting);
        btnExportAckCsv.setEnabled(!exporting);
        btnRefresh.setEnabled(!exporting);
        btnClearDates.setEnabled(!exporting);
        pbExport.setVisibility(exporting ? View.VISIBLE : View.GONE);
        tvExportStatus.setVisibility(exporting ? View.VISIBLE : View.GONE);
        btnCancelExport.setVisibility(exporting ? View.VISIBLE : View.GONE);
        if (!exporting) {
            pbExport.setIndeterminate(false);
            pbExport.setProgress(0);
            tvExportStatus.setText("");
        } else {
            pbExport.setIndeterminate(true);
            pbExport.setProgress(0);
            tvExportStatus.setText("开始下载...");
        }
    }

    private void updateExportProgress(long bytesRead, long totalBytes) {
        if (totalBytes > 0) {
            pbExport.setIndeterminate(false);
            int p = (int) Math.min(100L, bytesRead * 100L / totalBytes);
            pbExport.setProgress(p);
            tvExportStatus.setText("下载中 " + p + "%  " + bytesRead + "/" + totalBytes);
        } else {
            pbExport.setIndeterminate(true);
            tvExportStatus.setText("下载中 " + bytesRead);
        }
    }

    private File prepareCsvFile(String fileName) throws Exception {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }

    private Integer parseMaxRows() {
        String s = etMaxRows == null || etMaxRows.getText() == null ? "" : etMaxRows.getText().toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            if (v <= 0) {
                return null;
            }
            if (v > 500000) {
                v = 500000;
            }
            return v;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Uri saveToDownloads(File src, String displayName) throws Exception {
        if (Build.VERSION.SDK_INT < 29) {
            throw new RuntimeException("系统版本较低，无法写入系统下载目录");
        }
        ContentValues values = new ContentValues();
        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
        Uri collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri uri = getContentResolver().insert(collection, values);
        if (uri == null) {
            throw new RuntimeException("保存失败");
        }
        OutputStream os = null;
        FileInputStream is = null;
        try {
            os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                throw new RuntimeException("保存失败");
            }
            is = new FileInputStream(src);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) {
                os.write(buf, 0, n);
            }
            os.flush();
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
        }
        values.clear();
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
        getContentResolver().update(uri, values, null, null);
        try {
            src.delete();
        } catch (Exception ignored) {
        }
        return uri;
    }

    private void shareUri(Uri uri, String mime, String chooserTitle) {
        if (uri == null) {
            return;
        }
        try {
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType(mime);
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, chooserTitle));
        } catch (Exception e) {
            tvInfo.setText((e.getMessage() == null ? "" : e.getMessage()));
        }
    }

    private void shareFile(File f, String mime, String chooserTitle) {
        try {
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType(mime);
            it.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, chooserTitle));
        } catch (Exception e) {
            tvInfo.setText((e.getMessage() == null ? "" : e.getMessage()) + "\n已保存：" + (f == null ? "" : f.getAbsolutePath()));
        }
    }

    private Long parseFromMs() {
        Date d = InputUtil.parseDate(etFrom.getText() == null ? null : etFrom.getText().toString());
        return d == null ? null : startOfDayMs(d);
    }

    private Long parseToMs() {
        Date d = InputUtil.parseDate(etTo.getText() == null ? null : etTo.getText().toString());
        return d == null ? null : endOfDayMs(d);
    }

    private long startOfDayMs(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long endOfDayMs(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MILLISECOND, -1);
        return c.getTimeInMillis();
    }

    private String qs(Long fromMs, Long toMs) {
        StringBuilder sb = new StringBuilder();
        if (fromMs != null) {
            sb.append(sb.length() == 0 ? "?" : "&").append("from=").append(fromMs);
        }
        if (toMs != null) {
            sb.append(sb.length() == 0 ? "?" : "&").append("to=").append(toMs);
        }
        return sb.toString();
    }

    private String qs2(Long fromMs, Long toMs) {
        StringBuilder sb = new StringBuilder();
        if (fromMs != null) {
            sb.append("&from=").append(fromMs);
        }
        if (toMs != null) {
            sb.append("&to=").append(toMs);
        }
        return sb.toString();
    }

    private String fileName(String prefix, String rangeTag, long houseId) {
        long ts = System.currentTimeMillis();
        String r = (rangeTag == null || rangeTag.isEmpty()) ? "" : "_" + rangeTag;
        String h = houseId > 0 ? "_house" + houseId : "";
        return prefix + h + r + "_" + ts + ".csv";
    }

    private String rangeTag() {
        String from = etFrom.getText() == null ? "" : etFrom.getText().toString().trim();
        String to = etTo.getText() == null ? "" : etTo.getText().toString().trim();
        if (from.isEmpty() && to.isEmpty()) {
            return "";
        }
        if (from.isEmpty()) {
            from = "NA";
        }
        if (to.isEmpty()) {
            to = "NA";
        }
        String t = from + "-" + to;
        return t.replace("/", "-").replace(":", "-").replace(" ", "_");
    }

    private String slugCategory(String category) {
        if ("生产".equals(category)) {
            return "prod";
        }
        if ("后备成熟".equals(category)) {
            return "replacement";
        }
        return "unknown";
    }

    private String safeNum(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private String safeString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String enc(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
