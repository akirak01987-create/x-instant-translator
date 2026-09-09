package jp.crescendo.xtranslator.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.NotificationEntity;
import jp.crescendo.xtranslator.data.Prefs;
import jp.crescendo.xtranslator.service.GeminiAnalyzer;
import jp.crescendo.xtranslator.util.InsetsUtil;

/** 通知履歴をGeminiに渡して分析させる専用画面。対象期間と質問(自由入力・音声入力対応)を
 * 選んで実行し、結果をこの画面に表示する。 */
public class AiAnalysisActivity extends AppCompatActivity {
    private static final int REQUEST_VOICE_INPUT = 701;

    private RadioGroup radioDuration;
    private EditText editQuestion;
    private EditText editTemplate;
    private Button btnRunAnalysis;
    private ProgressBar progressAnalysis;
    private TextView textResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_analysis);
        InsetsUtil.applySystemBarPadding(findViewById(R.id.ai_analysis_root), true, true);

        radioDuration = findViewById(R.id.radio_duration);
        editQuestion = findViewById(R.id.edit_question);
        editTemplate = findViewById(R.id.edit_template);
        btnRunAnalysis = findViewById(R.id.btn_run_analysis);
        progressAnalysis = findViewById(R.id.progress_analysis);
        textResult = findViewById(R.id.text_result);

        radioDuration.check(R.id.radio_duration_10);
        editTemplate.setText(Prefs.getAiTemplateQuestion(this));

        ImageButton btnVoiceInput = findViewById(R.id.btn_voice_input);
        btnVoiceInput.setOnClickListener(v -> startVoiceInput());

        findViewById(R.id.btn_save_template).setOnClickListener(v -> saveTemplate());
        findViewById(R.id.btn_use_template).setOnClickListener(v ->
                editQuestion.setText(editTemplate.getText().toString()));

        btnRunAnalysis.setOnClickListener(v -> runAnalysis());
    }

    /** テンプレート欄の内容を保存する。次回この画面を開いたときも同じ内容が復元される。 */
    private void saveTemplate() {
        String text = editTemplate.getText().toString();
        Prefs.setAiTemplateQuestion(this, text);
        editTemplate.setText(Prefs.getAiTemplateQuestion(this));
        Toast.makeText(this, "テンプレートを保存しました", Toast.LENGTH_SHORT).show();
    }

    /** 端末の音声入力(Googleアプリ等)を呼び出し、認識結果を質問欄へ入れる。 */
    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPAN);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "質問を話してください");

        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "この端末では音声入力を利用できません", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivityForResult(intent, REQUEST_VOICE_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "音声入力を起動できませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VOICE_INPUT || resultCode != Activity.RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String recognized = results.get(0);
        String current = editQuestion.getText().toString();
        editQuestion.setText(current.trim().isEmpty() ? recognized : current + "\n" + recognized);
        editQuestion.setSelection(editQuestion.getText().length());
    }

    private long selectedDurationMinutes() {
        int checkedId = radioDuration.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_duration_10) return 10;
        if (checkedId == R.id.radio_duration_30) return 30;
        if (checkedId == R.id.radio_duration_60) return 60;
        if (checkedId == R.id.radio_duration_180) return 180;
        return -1; // 全履歴
    }

    private void runAnalysis() {
        String question = editQuestion.getText().toString();
        long minutes = selectedDurationMinutes();
        long cutoffMillis = minutes < 0 ? 0 : System.currentTimeMillis() - minutes * 60_000L;

        setLoading(true);
        android.content.Context appContext = getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(this);
        AppExecutors.background(() -> {
            List<NotificationEntity> items = db.notificationDao().getSince(cutoffMillis);
            List<String> posts = new ArrayList<>();
            for (NotificationEntity item : items) {
                String body = (item.wasTranslated && item.translatedText != null && !item.translatedText.isEmpty())
                        ? item.translatedText : item.originalText;
                String author = (item.author == null || item.author.isEmpty()) ? "" : item.author + ": ";
                posts.add(author + body);
            }

            try {
                String result = GeminiAnalyzer.analyze(appContext, posts, question);
                AppExecutors.main(() -> {
                    setLoading(false);
                    textResult.setText(result.isEmpty() ? "(結果が空でした)" : result);
                });
            } catch (GeminiAnalyzer.GeminiException e) {
                String message = e.getMessage();
                AppExecutors.main(() -> {
                    setLoading(false);
                    textResult.setText(message);
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        progressAnalysis.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRunAnalysis.setEnabled(!loading);
        if (loading) textResult.setText("分析中…");
    }
}
