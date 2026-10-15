package com.ziur.darkflip;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    public static volatile boolean moduleActive;
    private SharedPreferences prefs;
    private LinearLayout root;
    private boolean shared;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        try {
            this.prefs = getSharedPreferences(C.PREFS, 1);
            this.shared = true;
        } catch (SecurityException unused) {
            this.prefs = getSharedPreferences(C.PREFS, 0);
            this.shared = false;
        }
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFitsSystemWindows(true);
        LinearLayout linearLayout = new LinearLayout(this);
        this.root = linearLayout;
        linearLayout.setOrientation(1);
        int iDp = dp(20);
        this.root.setPadding(iDp, iDp, iDp, dp(40));
        scrollView.addView(this.root, new ViewGroup.LayoutParams(-1, -2));
        setContentView(scrollView);
        setTitle("DarkFlip 反色深色");
        boolean z = moduleActive;
        text("DarkFlip  v" + C.VERSION, 22, true, 0);
        if (z && this.shared) {
            text("模块已激活。", 14, false, 4);
        } else if (z) {
            text("模块已激活，但设置文件无法共享给目标 App，下面的设置不会生效（会按默认值运行）。", 14, false, 4);
        } else {
            text("模块未激活：请在 LSPosed 里启用本模块，勾选要变暗的 App，然后把那个 App 从后台划掉重开。", 14, false, 4);
        }
        text("改完任何设置后，把目标 App 从后台划掉重新打开才会完全生效。", 13, false, 4);
        if ((getResources().getConfiguration().uiMode & 48) != 32 && this.prefs.getBoolean(C.K_FOLLOW, true)) {
            text("现在系统是浅色模式，而下面开着“跟随系统深色模式”，所以目标 App 此刻不会被反色。", 13, true, 8);
        }
        header("基本");
        toggle(C.K_ENABLED, true, "总开关", null);
        toggle(C.K_FOLLOW, true, "跟随系统深色模式", "系统是浅色模式时不反色。关掉后不管系统深浅都反色。系统切换深浅色后，App 里已经加载的图片可能颜色不对，重开 App 即可。");
        text("配色", 15, false, 14);
        RadioGroup radioGroup = new RadioGroup(this);
        RadioButton radioButton = new RadioButton(this);
        radioButton.setText("纯黑（白底变纯黑）");
        radioButton.setId(View.generateViewId());
        final RadioButton radioButton2 = new RadioButton(this);
        radioButton2.setText("深灰（白底变深灰，对比柔和一些）");
        radioButton2.setId(View.generateViewId());
        radioGroup.addView(radioButton);
        radioGroup.addView(radioButton2);
        radioGroup.check(this.prefs.getInt(C.K_STYLE, 0) == 1 ? radioButton2.getId() : radioButton.getId());
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup2, int i) {
                SettingsActivity.this.prefs.edit().putInt(C.K_STYLE, i == radioButton2.getId() ? 1 : 0).apply();
            }
        });
        this.root.addView(radioGroup);
        toggle(C.K_EXACT, true, "精确配色（Android 16 及以上）", "用着色器做反色：鲜艳的颜色不会发灰，图片还原也更准。App 闪退或画面花掉时先关掉它，会退回到颜色矩阵。");
        header("画面处理");
        toggle(C.K_KEEP_IMAGES, true, "保持图片原色", "照片、头像、彩色插图、二维码不跟着反色。关掉后所有图片都会被反色。");
        toggle(C.K_AUTO_SKIP, true, "自动跳过本来就是深色的页面", "看图、视频、扫码这类深色页面保持原样。打开页面的一瞬间可能会闪一下。Flutter 页面不做这个判断，一律反色。");
        toggle(C.K_FIX_BARS, true, "状态栏 / 导航栏图标颜色跟着翻转", null);
        toggle(C.K_NO_SHADOW, true, "去掉卡片阴影", "阴影反色后会变成卡片周围的一圈亮边。");
        toggle(C.K_FORCE_LIGHT, true, "让 App 无视系统深色模式", "App 先按原本的浅色画，再整体反色。关掉后，App 里自己会变深色的部分会被反成浅色。");
        toggle(C.K_FLUTTER_TEXTURE, true, "Flutter 页面改用纹理渲染", "Flutter 画面单独占一个图层时，改成纹理渲染后反色效果才和普通界面一致。本来就用纹理渲染的 App 不受影响。");
        header("不反色的页面");
        text("Activity 类名里含有这些关键词的页面保持原样（逗号分隔，不分大小写）。", 13, false, 0);
        edit(C.K_SKIP_KEYS, C.DEFAULT_SKIP_KEYS, false);
        header("系统级模式（不注入 App）");
        text("每行一个包名。名单里的 App 由系统在合成画面时反色，App 自己的进程完全不被改动，适合银行类 App。代价是灰色层次差一些、图片也会被反色，并且不跟随系统深浅色（一直反色）。\n需要把「系统框架」勾进本模块的作用域并重启手机；名单里的 App 不用再勾进作用域。", 13, false, 0);
        edit(C.K_SYS_PKGS, "", true);
        header("排查问题");
        toggle(C.K_DEBUG, true, "诊断日志", "写到 LSPosed 的模块日志里，前缀是 [DarkFlip]。");
        toggle(C.K_DEEP_SCAN, false, "深度诊断", "每个 App 版本只做一次：读取它的类名表，把和 Flutter / 图片加载有关的类名记进日志，用来适配图片处理。会多花几秒钟，只写本机日志。");
    }

    private int dp(int i) {
        return Math.round(TypedValue.applyDimension(1, i, getResources().getDisplayMetrics()));
    }

    private TextView text(String str, int i, boolean z, int i2) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(2, i);
        if (z) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        textView.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(i2);
        this.root.addView(textView, layoutParams);
        return textView;
    }

    private void header(String str) {
        text(str, 16, true, 26).setPadding(0, 0, 0, dp(4));
    }

    private void toggle(final String str, boolean z, String str2, String str3) {
        Switch r0 = new Switch(this);
        r0.setText(str2);
        r0.setTextSize(2, 15.0f);
        r0.setChecked(this.prefs.getBoolean(str, z));
        r0.setGravity(16);
        r0.setMinHeight(dp(48));
        r0.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean z2) {
                SettingsActivity.this.prefs.edit().putBoolean(str, z2).apply();
            }
        });
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(6);
        this.root.addView(r0, layoutParams);
        if (str3 != null) {
            TextView textViewText = text(str3, 13, false, 0);
            textViewText.setAlpha(0.7f);
            textViewText.setPadding(0, 0, dp(56), 0);
        }
    }

    private void edit(final String str, String str2, boolean z) {
        EditText editText = new EditText(this);
        editText.setText(this.prefs.getString(str, str2));
        editText.setTextSize(2, 14.0f);
        if (z) {
            editText.setInputType(655361);
            editText.setMinLines(3);
            editText.setGravity(8388659);
            editText.setHint("例如 cmb.pb");
        } else {
            editText.setInputType(524289);
        }
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                SettingsActivity.this.prefs.edit().putString(str, editable.toString()).apply();
            }
        });
        this.root.addView(editText, new LinearLayout.LayoutParams(-1, -2));
    }
}
