package com.winlator.tq;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.winlator.MainActivity;
import com.winlator.container.Container;
import com.winlator.core.AppUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

public class TQLauncherActivity extends AppCompatActivity {
    private TextView statusView;
    private EditText pathView;
    private Button launchButton;
    private Button backendButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int rootChecks = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestStoragePermissions();
        setContentView(buildUi());

        String detected = TQSetup.detectDataPath(this);
        pathView.setText(detected);
        TQSetup.setDataPath(this, detected);
        updateStatus();
        updateBackendLabel();

        RootFSInstaller.installIfNeeded(this);
        waitForRootFs();
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT > 28) return;
        String[] permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        boolean granted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        if (!granted) requestPermissions(permissions, 1);
    }

    private android.view.View buildUi() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("泰坦之旅 · 周年纪念版");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWidth());

        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setTextColor(Color.LTGRAY);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusView, matchWidth());

        TextView pathLabel = new TextView(this);
        pathLabel.setText("游戏数据目录（D: 盘映射）");
        pathLabel.setTextSize(13);
        pathLabel.setTextColor(Color.LTGRAY);
        root.addView(pathLabel, matchWidth());

        pathView = new EditText(this);
        pathView.setTextSize(13);
        pathView.setSingleLine(true);
        root.addView(pathView, matchWidth());

        Button savePath = new Button(this);
        savePath.setText("保存数据目录");
        savePath.setOnClickListener(v -> {
            TQSetup.setDataPath(this, pathView.getText().toString().trim());
            updateStatus();
            AppUtils.showToast(this, "数据目录已保存");
        });
        root.addView(savePath, matchWidth());

        launchButton = new Button(this);
        launchButton.setText("启动泰坦之旅");
        launchButton.setEnabled(false);
        launchButton.setOnClickListener(v -> startGame());
        root.addView(launchButton, matchWidth());

        backendButton = new Button(this);
        backendButton.setOnClickListener(v -> {
            boolean next = !TQSetup.useDxvk(this);
            TQSetup.setUseDxvk(this, next);
            updateBackendLabel();
            updateStatus();
            AppUtils.showToast(this, next ? "已切换到 DXVK 后备方案" : "已切换到 WineD3D 默认方案");
        });
        root.addView(backendButton, matchWidth());

        Button settings = new Button(this);
        settings.setText("打开 Winlator 高级设置");
        settings.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        root.addView(settings, matchWidth());

        Button log = new Button(this);
        log.setText("导出启动日志");
        log.setOnClickListener(v -> exportLog());
        root.addView(log, matchWidth());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void waitForRootFs() {
        if (RootFS.find(this).isValid()) {
            statusView.append("\n运行环境：已就绪");
            launchButton.setEnabled(true);
            return;
        }
        if (rootChecks++ >= 120) {
            statusView.append("\n运行环境：安装超时，请先打开 Winlator 完成首次解压");
            return;
        }
        handler.postDelayed(this::waitForRootFs, 500);
    }

    private void updateStatus() {
        String path = TQSetup.getDataPath(this);
        boolean ready = TQSetup.isDataReady(path);
        statusView.setText("游戏数据：" + (ready ? "已检测" : "未检测，请先导入 14 GB 数据") +
                "\n主程序：TQ.exe（32 位 x86 / DirectX 9）" +
                "\n设备：天玑 9400+ · Immortalis-G925 · 优先 WineD3D + Gladio" +
                "\n高频核心：" + TQSetup.detectFastCores());
    }

    private void updateBackendLabel() {
        backendButton.setText(TQSetup.useDxvk(this)
                ? "渲染后端：DXVK（点击切回 WineD3D）"
                : "渲染后端：WineD3D（点击切换 DXVK）");
    }

    private void startGame() {
        String path = pathView.getText().toString().trim();
        if (!TQSetup.isDataReady(path)) {
            AppUtils.showToast(this, "未在目录中检测到 TQ.exe 或 database.arz");
            return;
        }
        TQSetup.setDataPath(this, path);
        statusView.setText("正在准备容器，请稍候…");
        launchButton.setEnabled(false);

        TQSetup.prepareContainer(this, path, container -> {
            if (container == null) {
                statusView.setText("容器创建失败，请检查 Winlator 运行环境");
                launchButton.setEnabled(true);
                return;
            }
            TQSetup.launch(this, container, TQSetup.useDxvk(this));
            handler.postDelayed(() -> launchButton.setEnabled(true), 1500);
        });
    }

    private void exportLog() {
        File out = new File(getExternalFilesDir(null), "tq-launch.log");
        try (FileWriter writer = new FileWriter(out, false);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     Runtime.getRuntime().exec("logcat -d").getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("winlator") || line.contains("box64") || line.contains("wine")
                        || line.contains("TQ.exe") || line.contains("vulkan") || line.contains("Mali")) {
                    writer.write(line);
                    writer.write('\n');
                }
            }
            AppUtils.showToast(this, "日志已导出到 " + out.getAbsolutePath());
        }
        catch (Exception e) {
            AppUtils.showToast(this, "日志导出失败：" + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
    }
}
