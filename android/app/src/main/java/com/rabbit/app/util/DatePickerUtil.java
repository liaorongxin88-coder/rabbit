package com.rabbit.app.util;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.EditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DatePickerUtil {
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    public static void attach(Context context, EditText et) {
        et.setFocusable(false);
        et.setOnClickListener(v -> show(context, et));
    }

    private static void show(Context context, EditText et) {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(context, (view, year, month, dayOfMonth) -> {
            Calendar x = Calendar.getInstance();
            x.set(Calendar.YEAR, year);
            x.set(Calendar.MONTH, month);
            x.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            et.setText(DF.format(x.getTime()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }
}
