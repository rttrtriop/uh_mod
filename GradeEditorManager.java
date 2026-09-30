package com.mod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GradeEditorManager {

    public static void showAddDialog(final Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int layoutId = activity.getResources().getIdentifier("dialog_grade_editor", "layout", activity.getPackageName());
                    if (layoutId == 0) return;

                    View view = LayoutInflater.from(activity).inflate(layoutId, null);
                    final AlertDialog dialog = new AlertDialog.Builder(activity)
                            .setView(view)
                            .create();

                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    }

                    int id5 = activity.getResources().getIdentifier("btn_grade_5", "id", activity.getPackageName());
                    int id4 = activity.getResources().getIdentifier("btn_grade_4", "id", activity.getPackageName());
                    int id3 = activity.getResources().getIdentifier("btn_grade_3", "id", activity.getPackageName());
                    int id2 = activity.getResources().getIdentifier("btn_grade_2", "id", activity.getPackageName());
                    int idCancel = activity.getResources().getIdentifier("btn_grade_cancel", "id", activity.getPackageName());

                    View.OnClickListener listener = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int grade = 5;
                            CharSequence text = ((TextView) v).getText();
                            if (text != null && text.length() > 0) {
                                try {
                                    grade = Integer.parseInt(text.toString().trim());
                                } catch (Exception ignored) {}
                            }
                            addGrade(activity, grade);
                            dialog.dismiss();
                        }
                    };

                    if (id5 != 0 && view.findViewById(id5) != null) view.findViewById(id5).setOnClickListener(listener);
                    if (id4 != 0 && view.findViewById(id4) != null) view.findViewById(id4).setOnClickListener(listener);
                    if (id3 != 0 && view.findViewById(id3) != null) view.findViewById(id3).setOnClickListener(listener);
                    if (id2 != 0 && view.findViewById(id2) != null) view.findViewById(id2).setOnClickListener(listener);

                    if (idCancel != 0 && view.findViewById(idCancel) != null) {
                        view.findViewById(idCancel).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                            }
                        });
                    }

                    dialog.show();
                } catch (Throwable t) {
                    // ignore
                }
            }
        });
    }

    public static void showDeleteDialog(final Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int layoutId = activity.getResources().getIdentifier("dialog_grade_delete", "layout", activity.getPackageName());
                    if (layoutId == 0) return;

                    View view = LayoutInflater.from(activity).inflate(layoutId, null);
                    final AlertDialog dialog = new AlertDialog.Builder(activity)
                            .setView(view)
                            .create();

                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    }

                    int idConfirm = activity.getResources().getIdentifier("btn_delete_confirm", "id", activity.getPackageName());
                    int idCancel = activity.getResources().getIdentifier("btn_delete_cancel", "id", activity.getPackageName());

                    if (idConfirm != 0 && view.findViewById(idConfirm) != null) {
                        view.findViewById(idConfirm).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                deleteLastGrade(activity);
                                dialog.dismiss();
                            }
                        });
                    }

                    if (idCancel != 0 && view.findViewById(idCancel) != null) {
                        view.findViewById(idCancel).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                            }
                        });
                    }

                    dialog.show();
                } catch (Throwable t) {
                    // ignore
                }
            }
        });
    }

    public static void addGrade(Activity activity, int gradeValue) {
        try {
            File gsFile = new File(activity.getFilesDir().getParent(), "app_flutter/GetStorage.gs");
            if (!gsFile.exists()) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(gsFile), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();

            JSONObject root = new JSONObject(sb.toString());
            String curDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

            // 1. Update all_marks_to_detail
            String targetSubName = "";
            if (root.has("all_marks_to_detail")) {
                JSONObject sub = root.getJSONObject("all_marks_to_detail");
                targetSubName = sub.optString("name", "");
                JSONArray qsMark = sub.optJSONArray("qs_mark");
                if (qsMark == null) {
                    qsMark = new JSONArray();
                    sub.put("qs_mark", qsMark);
                }

                JSONObject newMark = new JSONObject();
                newMark.put("date", curDate);
                newMark.put("mark_id", System.currentTimeMillis());
                newMark.put("question__subject", "Ответ на уроке");
                newMark.put("mark", gradeValue);
                qsMark.put(newMark);

                recalculateSubject(sub);
            }

            // 2. Update now_marks_data
            if (root.has("now_marks_data")) {
                JSONArray nowMarks = root.getJSONArray("now_marks_data");
                for (int p = 0; p < nowMarks.length(); p++) {
                    JSONObject period = nowMarks.getJSONObject(p);
                    if (!period.has("qs_sub")) continue;
                    JSONArray qsSub = period.getJSONArray("qs_sub");
                    for (int s = 0; s < qsSub.length(); s++) {
                        JSONObject sub = qsSub.getJSONObject(s);
                        if (targetSubName.equalsIgnoreCase(sub.optString("name", ""))) {
                            JSONArray qsMark = sub.optJSONArray("qs_mark");
                            if (qsMark == null) {
                                qsMark = new JSONArray();
                                sub.put("qs_mark", qsMark);
                            }
                            JSONObject newMark = new JSONObject();
                            newMark.put("date", curDate);
                            newMark.put("mark_id", System.currentTimeMillis());
                            newMark.put("question__subject", "Ответ на уроке");
                            newMark.put("mark", gradeValue);
                            qsMark.put(newMark);

                            recalculateSubject(sub);
                        }
                    }
                }
            }

            // 3. Recalculate avg_ball
            recalculateOverallAvg(root);

            // Write back
            FileOutputStream fos = new FileOutputStream(gsFile);
            fos.write(root.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();

            Toast.makeText(activity, "Оценка " + gradeValue + " добавлена! Переоткройте предмет для обновления", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(activity, "Ошибка: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void deleteLastGrade(Activity activity) {
        try {
            File gsFile = new File(activity.getFilesDir().getParent(), "app_flutter/GetStorage.gs");
            if (!gsFile.exists()) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(gsFile), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();

            JSONObject root = new JSONObject(sb.toString());
            String targetSubName = "";

            if (root.has("all_marks_to_detail")) {
                JSONObject sub = root.getJSONObject("all_marks_to_detail");
                targetSubName = sub.optString("name", "");
                JSONArray qsMark = sub.optJSONArray("qs_mark");
                if (qsMark != null && qsMark.length() > 0) {
                    JSONArray newList = new JSONArray();
                    for (int i = 0; i < qsMark.length() - 1; i++) {
                        newList.put(qsMark.get(i));
                    }
                    sub.put("qs_mark", newList);
                    recalculateSubject(sub);
                }
            }

            if (root.has("now_marks_data")) {
                JSONArray nowMarks = root.getJSONArray("now_marks_data");
                for (int p = 0; p < nowMarks.length(); p++) {
                    JSONObject period = nowMarks.getJSONObject(p);
                    if (!period.has("qs_sub")) continue;
                    JSONArray qsSub = period.getJSONArray("qs_sub");
                    for (int s = 0; s < qsSub.length(); s++) {
                        JSONObject sub = qsSub.getJSONObject(s);
                        if (targetSubName.equalsIgnoreCase(sub.optString("name", ""))) {
                            JSONArray qsMark = sub.optJSONArray("qs_mark");
                            if (qsMark != null && qsMark.length() > 0) {
                                JSONArray newList = new JSONArray();
                                for (int i = 0; i < qsMark.length() - 1; i++) {
                                    newList.put(qsMark.get(i));
                                }
                                sub.put("qs_mark", newList);
                                recalculateSubject(sub);
                            }
                        }
                    }
                }
            }

            recalculateOverallAvg(root);

            FileOutputStream fos = new FileOutputStream(gsFile);
            fos.write(root.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();

            Toast.makeText(activity, "Оценка удалена! Переоткройте предмет для обновления", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(activity, "Ошибка: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void recalculateSubject(JSONObject sub) {
        try {
            JSONArray qsMark = sub.optJSONArray("qs_mark");
            if (qsMark == null || qsMark.length() == 0) {
                sub.put("avg", 0);
                sub.put("need_five", 1);
                sub.put("need_four", 1);
                return;
            }

            int count = qsMark.length();
            int sum = 0;
            for (int i = 0; i < count; i++) {
                sum += qsMark.getJSONObject(i).optInt("mark", 0);
            }

            double avg = (double) sum / (double) count;
            avg = Math.round(avg * 100.0) / 100.0;
            sub.put("avg", avg);

            double diff5 = 4.50 * count - sum;
            int needFive = (diff5 > 0) ? (int) Math.ceil(diff5 / 0.5) : 0;
            sub.put("need_five", needFive);

            double diff4 = 3.50 * count - sum;
            int needFour = (diff4 > 0) ? (int) Math.ceil(diff4 / 0.5) : 0;
            sub.put("need_four", needFour);
        } catch (Throwable ignored) {}
    }

    private static void recalculateOverallAvg(JSONObject root) {
        try {
            if (!root.has("now_marks_data")) return;
            JSONArray nowMarks = root.getJSONArray("now_marks_data");
            double totalSum = 0;
            int totalCount = 0;

            for (int p = 0; p < nowMarks.length(); p++) {
                JSONObject period = nowMarks.getJSONObject(p);
                if (!period.has("qs_sub")) continue;
                JSONArray qsSub = period.getJSONArray("qs_sub");
                for (int s = 0; s < qsSub.length(); s++) {
                    JSONObject sub = qsSub.getJSONObject(s);
                    double avg = sub.optDouble("avg", 0.0);
                    if (avg > 0) {
                        totalSum += avg;
                        totalCount++;
                    }
                }
            }

            if (totalCount > 0) {
                double avgAll = totalSum / totalCount;
                avgAll = Math.round(avgAll * 10.0) / 10.0;
                root.put("avg_ball", avgAll);
            }
        } catch (Throwable ignored) {}
    }
}
