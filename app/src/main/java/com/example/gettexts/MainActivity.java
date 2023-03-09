package com.example.gettexts;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private final int BUTTON_PADDING = 10;
    DateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    genCalendar();
                } else {
                    throw new RuntimeException("Permission must be granted");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if ((ContextCompat.checkSelfPermission(
                this.getApplicationContext(), Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED)) {
            genCalendar();
        } else {
            requestPermissionLauncher.launch(
                    Manifest.permission.READ_SMS);
        }
    }

    private Calendar getDay(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, day, 0, 0, 0);
        return c;
    }

    private void genCalendar() {
        Map<Calendar, String[]> together = getDates().entrySet().stream().map(kv -> {
            Calendar c = Calendar.getInstance();
            c.setTime(kv.getKey());
            return new AbstractMap.SimpleEntry<>(c, kv.getValue());
        }).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        List<Calendar> togetherDates = new ArrayList<>(together.keySet());
        togetherDates.sort(Comparator.naturalOrder());

        Calendar currDate = togetherDates.get(0);
        TableRow currRow = new TableRow(this);
        currRow.addView(createButton(getDay(2022, 2, 13), null));
        currRow.addView(createButton(getDay(2022, 2, 14), null));
        currRow.addView(createButton(getDay(2022, 2, 15), null));

        while (currDate.compareTo(Calendar.getInstance()) <= 0) {
            if (currDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                ((TableLayout) findViewById(R.id.calendarTable)).addView(currRow);

                currRow = new TableRow(this);
                currRow.setLayoutParams(
                        new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                                TableLayout.LayoutParams.WRAP_CONTENT));
                // Set the weightSum of the row
                currRow.setWeightSum(1f);

            }

            String[] messages = together.get(currDate);

            currRow.addView(createButton(currDate, messages));
            currDate.add(Calendar.DATE, 1);
            currDate = (Calendar) currDate.clone();
        }
        ((TableLayout) findViewById(R.id.calendarTable)).addView(currRow);
    }

    private Button createButton(Calendar currDate, String[] messages) {
        TableRow.LayoutParams layoutParams =
                new TableRow.LayoutParams(0,
                        pixelsToSp(400), (float) (1.0 / 7.0));

        Button b = new Button(this);
        b.setLayoutParams(layoutParams);
        b.setPadding(BUTTON_PADDING, BUTTON_PADDING, BUTTON_PADDING, BUTTON_PADDING);
        b.setGravity(Gravity.CENTER);
        boolean isTogether = messages != null && (messages[0].contains(messages[1]) || messages[1].contains((messages[0])));
        if (messages == null) {
            b.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.gray_tile, getTheme()));
        } else if (isTogether) {
            b.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.green_tile, getTheme()));
        } else {
            b.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.yellow_tile, getTheme()));
        }

        Calendar finalCurrDate = (Calendar) currDate.clone();
        b.setOnClickListener(v ->
        {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle(formatter.format(finalCurrDate.getTime()));
            alertDialog.setMessage(messages == null ? "No wordle" : isTogether ? "Both: " + messages[0] : "Hailey: " + messages[0] + "\n\nJamin: " + messages[1]);
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    (dialog, which) -> dialog.dismiss());
            alertDialog.show();
        });

        return b;
    }

    public int pixelsToSp(float px) {
        float scaledDensity = this.getResources().getDisplayMetrics().scaledDensity;
        return (int) (px / scaledDensity);
    }

    // { date sent: [Hailey's wordle, Jamin's wordle] }
    private Map<Date, String[]> getDates() {
        String[] projection = new String[]{"address", "body", "date"};
        Cursor receivedCursor = getContentResolver().query(Uri.parse("content://sms/inbox"), projection, "address LIKE ? AND body LIKE ?", new String[]{"%2038196320%", "%Wordle%/6%"}, "date");
        Cursor sentCursor = getContentResolver().query(Uri.parse("content://sms/sent"), projection, "body LIKE ?", new String[]{"%Wordle%/6%"}, "date");

        Map<Date, String[]> together = new HashMap<>();
        try {
            Map<Date, String> receivedMsgs = msgsFromCursor(receivedCursor);
            Map<Date, String> sentMsgs = msgsFromCursor(sentCursor);

            for (Date date : receivedMsgs.keySet()) {
                String receivedMsg = receivedMsgs.get(date);
                String sentMsg = sentMsgs.get(date);
                if (receivedMsg != null && sentMsg != null) {
                    String receivedWordle = wordleChunk(receivedMsg);
                    String sentWordle = wordleChunk(sentMsg);

                    together.put(date, new String[]{receivedWordle, sentWordle});
                }
            }
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        return together;
    }

    private String wordleChunk(String s) {
        try {
            return s.substring(s.indexOf("Wordle"));
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println(s);
            throw new RuntimeException(s);
        }
    }

    private Map<Date, String> msgsFromCursor(Cursor cursor) throws ParseException {
        Map<Date, String> receivedMessages = new HashMap<>();
        if (cursor.moveToFirst()) { // must check the result to prevent exception
            do {
                int numberIdx = cursor.getColumnIndexOrThrow("address");
                int msgIdx = cursor.getColumnIndexOrThrow("body");
                int dateIdx = cursor.getColumnIndexOrThrow("date");
                Date receivedDateRaw = new Date(cursor.getLong(dateIdx));
                Date receivedDate = formatter.parse(formatter.format(receivedDateRaw));
                receivedMessages.put(receivedDate, cursor.getString(msgIdx));

            } while (cursor.moveToNext());
        }

        return receivedMessages;
    }
}