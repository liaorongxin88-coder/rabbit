package com.rabbit.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.NfcUtil;

public class NfcRouterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        NfcRoute route = parseRoute(intent);
        SessionStore session = new SessionStore(this);
        if (route.houseId > 0) {
            session.setHouseId(route.houseId);
        }

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            Intent it = new Intent(this, LoginActivity.class);
            if (route.houseId > 0) {
                it.putExtra("nfcHouseId", route.houseId);
            }
            if (route.targetType != null) {
                it.putExtra("nfcTargetType", route.targetType);
            }
            if (route.cageId > 0) {
                it.putExtra("nfcCageId", route.cageId);
            }
            if (route.cageNumber != null) {
                it.putExtra("nfcCageNumber", route.cageNumber);
            }
            if (route.rabbitId > 0) {
                it.putExtra("nfcRabbitId", route.rabbitId);
            }
            if (route.recordId > 0) {
                it.putExtra("nfcRecordId", route.recordId);
            }
            if (route.tagUid != null) {
                it.putExtra("nfcTagUid", route.tagUid);
            }
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            finish();
            return;
        }

        Intent it = new Intent(this, MainActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (route.houseId > 0) {
            it.putExtra("nfcHouseId", route.houseId);
        }
        if (route.targetType != null) {
            it.putExtra("nfcTargetType", route.targetType);
        }
        if (route.cageId > 0) {
            it.putExtra("nfcCageId", route.cageId);
        }
        if (route.cageNumber != null) {
            it.putExtra("nfcCageNumber", route.cageNumber);
        }
        if (route.rabbitId > 0) {
            it.putExtra("nfcRabbitId", route.rabbitId);
        }
        if (route.recordId > 0) {
            it.putExtra("nfcRecordId", route.recordId);
        }
        if (route.tagUid != null) {
            it.putExtra("nfcTagUid", route.tagUid);
        }
        startActivity(it);
        finish();
    }

    private NfcRoute parseRoute(Intent intent) {
        NfcRoute route = new NfcRoute();
        route.tagUid = NfcUtil.getTagUid(intent);

        if (intent != null) {
            String action = intent.getAction();
            if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) || NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
                String s = NfcUtil.readNdefTextOrUri(intent);
                if (s != null && !s.isEmpty()) {
                    Uri uri = null;
                    try {
                        uri = Uri.parse(s);
                    } catch (Exception ignored) {
                    }
                    if (uri != null && uri.getScheme() != null) {
                        String scheme = uri.getScheme();
                        if ("rabbitapp".equalsIgnoreCase(scheme)) {
                            String host = uri.getHost();
                            route.houseId = parseLong(uri.getQueryParameter("houseId"));
                            if (host != null && host.equalsIgnoreCase("cage")) {
                                route.targetType = "CAGE";
                                route.cageId = parseLong(uri.getQueryParameter("cageId"));
                                route.cageNumber = uri.getQueryParameter("cageNumber");
                                return route;
                            } else if (host != null && host.equalsIgnoreCase("feed")) {
                                route.targetType = "FEED";
                                return route;
                            } else if (host != null && host.equalsIgnoreCase("sale")) {
                                route.targetType = "SALE";
                                return route;
                            } else if (host != null && host.equalsIgnoreCase("treatment")) {
                                route.targetType = "TREATMENT";
                                route.rabbitId = parseLong(uri.getQueryParameter("rabbitId"));
                                route.recordId = parseLong(uri.getQueryParameter("treatmentId"));
                                return route;
                            }
                        }
                        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                            route.houseId = parseLong(uri.getQueryParameter("houseId"));
                            route.cageId = parseLong(uri.getQueryParameter("cageId"));
                            route.cageNumber = uri.getQueryParameter("cageNumber");
                        }
                    }
                }
            }
        }
        return route;
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static class NfcRoute {
        long houseId;
        String targetType;
        long cageId;
        String cageNumber;
        long rabbitId;
        long recordId;
        String tagUid;
    }
}
