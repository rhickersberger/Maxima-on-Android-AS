/*
    Copyright 2012, 2013, 2014, 2015, 2016, 2017 Yasuaki Honda (yasuaki.honda@gmail.com)
    This file is part of MaximaOnAndroid.

    MaximaOnAndroid is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    MaximaOnAndroid is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with MaximaOnAndroid.  If not, see <http://www.gnu.org/licenses/>.
 */

package jp.yhonda;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.MultiAutoCompleteTextView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaximaOnAndroidActivity extends AppCompatActivity implements
        TextView.OnEditorActionListener, OnTouchListener {
    boolean inited = false; /* expSize initialize is done or not */
    String[] mcmdArray = null; /* manual example input will be stored. */
    int mcmdArrayIndex = 0;
    Activity thisActivity = this;
    String maximaURL = null;

    String manEn = "file:///android_asset/maxima-doc/en/maxima.html";
    String manURL;
    boolean manLangChanged = true;
    boolean allExampleFinished = false;
    Semaphore sem = new Semaphore(1);
    MultiAutoCompleteTextView editText;
    Button enterB;
    WebView webview;
    ScrollView scview;
    CommandExec maximaProccess;
    File internalDir;
    String dataDir;
    MaximaVersion mvers = new MaximaVersion(5, 41, 0);

    private static final int READ_REQUEST_CODE = 42;
    File temporaryScriptFile = null;
    final double scriptFileMaxSize = 1E7;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d("MoA", "onCreate()");
        super.onCreate(savedInstanceState);

        maximaURL = "file:///android_asset/maxima_svg.html";

        PreferenceManager.setDefaultValues(this, R.xml.preference, false);
        SharedPreferences settings = PreferenceManager
                .getDefaultSharedPreferences(this);
        manURL = settings.getString("manURL", manEn);

        setContentView(R.layout.main);
        internalDir = this.getFilesDir();
        dataDir = "/data/"; // TODO: Remove hardcoded data dir.
        Log.v("Internal dir is ", internalDir.getPath());
        Log.v("Data dir is ", dataDir);

        webview = (WebView) findViewById(R.id.webView1);
        webview.getSettings().setJavaScriptEnabled(true);
        WebView.setWebContentsDebuggingEnabled(true);
        webview.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                Log.v("MoA", "onPageFinished");
                SharedPreferences settings = PreferenceManager
                        .getDefaultSharedPreferences(thisActivity);
                float sc = settings.getFloat("maxima main scale", 1.5f);
                Log.v("MoA", "sc=" + sc);
                view.setInitialScale((int) (100 * sc));
            }

        });
        float sc = settings.getFloat("maxima main scale", 1.5f);
        Log.v("MoA", "onCreate sc=" + sc);
        webview.setInitialScale((int) (100 * sc));

        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setDisplayZoomControls(false);

        webview.setWebChromeClient(new WebChromeClient() {
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d("MyApplication",
                        cm.message() + " -- From line " + cm.lineNumber()
                                + " of " + cm.sourceId());
                return true;
            }
        });
        scview = (ScrollView) findViewById(R.id.scrollView1);

        webview.addJavascriptInterface(this, "MOA");

        editText = (MultiAutoCompleteTextView) findViewById(R.id.editText1);
        editText.setTextSize((float) Integer.parseInt(settings.getString("fontSize1", "20")));
        boolean bflag = settings.getBoolean("auto_completion_check_box_pref", true);
        if (bflag) {
            editText.setThreshold(2);
        } else {
            editText.setThreshold(100);
        }

        editText.setOnEditorActionListener(this);

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, getResources().getStringArray(R.array.MaximaCompletionList));
        editText.setTokenizer(new MaximaTokenizer());
        editText.setAdapter(adapter);
        webview.setOnTouchListener(this);

        enterB = (Button) findViewById(R.id.enterB);
        enterB.setOnClickListener(v -> {
            editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_ENTER));
            editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_ENTER));
        });

        MaximaVersion prevVers = new MaximaVersion();
        prevVers.loadVersFromSharedPrefs(this);
        long verNo = prevVers.versionInteger();
        long thisVerNo = mvers.versionInteger();

        if ((thisVerNo > verNo)
                || !maximaBinaryExists()
                || !((new File(internalDir + "/additions")).exists())
                || !((new File(internalDir + "/init.lisp")).exists())
                || !(new File(internalDir + "/maxima-" + mvers.versionString()))
                .exists()) {
            Intent intent = new Intent(this, MOAInstallerActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra("version", mvers.versionString());
            this.startActivityForResult(intent, 0);
        } else {
            // startMaxima();
            new Thread(this::startMaxima).start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v("MoA", "onActivityResult()");
        // super.onActivityResult(requestCode, resultCode, data);
        String sender;
        if (data != null) {
            sender = data.getStringExtra("sender");
        } else {
            sender = "anon";
        }
        Log.v("MoA", "sender = " + sender);
        if (sender == null) {
            if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
                //User has selected a script file to load.
                Uri uri;
                uri = data.getData();
                //Copy file contents to input area:
                copyScriptFileToInputArea(uri);
            }
        } else if (sender.equals("manualActivity")) {
            if (resultCode == RESULT_OK) {
                String mcmd = data.getStringExtra("maxima command");
                if (mcmd != null) {
                    copyExample(mcmd);
                }
            }
        } else if (sender.equals("FBActivity")) {
            if (resultCode == RESULT_OK) {
                String mcmd = data.getStringExtra("maxima command");
                if (mcmd != null) {
                    editText.setText(mcmd);
                    editText.setSelection(mcmd.length());
                    editText.requestFocus();
                }
            }
        } else if (sender.equals("MOAInstallerActivity")) {
            if (resultCode == RESULT_OK) {
                /* everything is installed properly. */
                mvers.saveVersToSharedPrefs(this);
                // startMaxima();

                new Thread(this::startMaxima).start();

            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.installer_title)
                        .setMessage(R.string.install_failure)
                        .setPositiveButton(R.string.OK, (dialog, which) -> finish()).show();
            }
        }
    }

    @Override
    protected void onResume() {
        Log.d("MoA", "onResume");
        super.onResume();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        AppGlobals globals = AppGlobals.getSingleton();
        String flag;

        flag = globals.get("auto_completion_check_box_pref");
        if (flag != null && flag.equals("true")) {
            boolean bflag = sharedPrefs.getBoolean("auto_completion_check_box_pref", true);
            if (bflag) {
                editText.setThreshold(2);
            } else {
                editText.setThreshold(100);
            }
        }

        flag = globals.get("manURL");
        if (flag != null && flag.equals("true")) {
            manLangChanged = true;
            manURL = sharedPrefs.getString("manURL", manEn);
        } else {
            manLangChanged = false;
        }

        flag = globals.get("fontSize1");
        if (flag != null && flag.equals("true")) {
            editText.setTextSize((float) Integer.parseInt(sharedPrefs.getString("fontSize1", "20")));
        }

        flag = globals.get("fontSize2");
        if (flag != null && flag.equals("true")) {
            webview.loadUrl("javascript:window.ChangeExpSize(" + sharedPrefs.getString("fontSize2", "20") + ")");
        }

        List<String> list = Arrays.asList("auto_completion_check_box_pref", "manURL", "fontSize1", "fontSize2");
        for (String key : list) {
            globals.set(key, "false");
        }
    }

    private void startMaxima() {
        Log.d("MoA", "startMaxima()");
        try {
            sem.acquire();
        } catch (InterruptedException e1) {
            Log.d("MoA", "exception1");
            e1.printStackTrace();
        }

        CpuArchitecture.initCpuArchitecture();

		/*
		webview.loadUrl(maximaURL);
		*/
        runOnUiThread(() -> webview.loadUrl(maximaURL));
        if (!(new File(internalDir + "/maxima-" + mvers.versionString()))
                .exists()) {
            this.finish();
        }
        List<String> list = new ArrayList<>();
        list.add(internalDir + "/" + CpuArchitecture.getMaximaFile());
        list.add("--init-lisp=" + internalDir + "/init.lisp");

        maximaProccess = new CommandExec();
        try {
            maximaProccess.execCommand(list);
        } catch (IOException e) {
            Log.d("MoA", "exception2");
            e.printStackTrace();
            exitMOA();
        }
        maximaProccess.clearStringBuilder();
        sem.release();
        Log.v("MoA", "sem released.");
    }

    private void copyExample(String mcmd) {
        Log.d("MoA", "copyExample()");
        String[] mcmd2 = mcmd.split("\n\\(%");
        mcmd2[0] = mcmd2[0].replaceFirst("^\\(%", "");
        for (int i = 0; i < mcmd2.length; i++) {
            mcmd2[i] = mcmd2[i].replaceAll("\n", " ");
        }

        int j = 0;
        for (String s : mcmd2) {
            if (s.startsWith("i")) {
                j++;
            }
        }
        mcmdArray = new String[j];
        j = 0;
        for (String s : mcmd2) {
            if (s.startsWith("i")) {
                int a = s.indexOf(" ");
                mcmdArray[j] = s.substring(a + 1);
                a = mcmdArray[j].lastIndexOf(";");
                if (a > 0) {
                    mcmdArray[j] = mcmdArray[j].substring(0, a + 1); /*
                     * to
                     * include ;
                     */
                } else {
                    a = mcmdArray[j].lastIndexOf("$");
                    if (a > 0) {
                        mcmdArray[j] = mcmdArray[j].substring(0, a + 1); /*
                         * to
                         * include
                         * $
                         */
                    }
                }
                j++;
            }
        }
        mcmdArrayIndex = 0;
        copyExampleInputToInputArea();
    }

    private void copyExampleInputToInputArea() {
        if (mcmdArray == null)
            return;
        Log.d("MoA", "copyExampleInputToInputArea()");
        editText.setText(mcmdArray[mcmdArrayIndex]);
        editText.setSelection(mcmdArray[mcmdArrayIndex].length());
        editText.requestFocus();
        mcmdArrayIndex++;
        if (mcmdArrayIndex == mcmdArray.length) {
            allExampleFinished = true;
            mcmdArrayIndex = 0;
        }
    }

    @JavascriptInterface
    public void scrollToEnd() {
        Handler handler = new Handler();
        Runnable task = () -> {
            Runnable viewtask = () -> {
                scview.fullScroll(ScrollView.FOCUS_DOWN);
                Log.v("MoA", "scroll!");
            };
            scview.post(viewtask);
        };
        handler.postDelayed(task, 1000);
    }

    public boolean onEditorAction(TextView testview, int id, KeyEvent keyEvent) {
        try {
            Log.v("MoA", "onEditorAction");
            sem.acquire();
        } catch (InterruptedException e1) {
            Log.d("MoA", "exception3");
            e1.printStackTrace();
            exitMOA();
        }
        sem.release();
        if (!inited) {
            SharedPreferences settings = PreferenceManager
                    .getDefaultSharedPreferences(thisActivity);
            final String newsize = settings.getString("fontSize2", "");
            if (!newsize.equals("")) {
                runOnUiThread(() -> webview.loadUrl("javascript:window.ChangeExpSize(" + newsize + ")"));
                // webview.loadUrl("javascript:window.ChangeExpSize("+newsize+")");
            }
            inited = true;
        }

        Log.v("MoA", "sem released");
        String cmdstr = editText.getText().toString();
        if ((keyEvent == null) || (keyEvent.getAction() == KeyEvent.ACTION_UP)) {
            try {
                // cmdstr = editText.getText().toString();
                if (cmdstr.equals("")) {
                    return true;
                }
                if (cmdstr.equals("reload;")) {
                    webview.loadUrl(maximaURL);
                    return true;
                }
                if (cmdstr.equals("sc;")) {
                    this.scrollToEnd();
                    return true;
                }
                if (cmdstr.equals("quit();"))
                    exitMOA();
                if (cmdstr.equals("aboutme;")) {
                    showHTML("file:///android_asset/docs/aboutMOA.html", true);
                    return true;
                }
                if (cmdstr.equals("man;")) {
                    showHTML("file://" + internalDir
                            + "/additions/en/maxima.html", true);
                    return true;
                }
                if (cmdstr.equals("manj;")) {
                    showHTML("file://" + internalDir
                            + "/additions/ja/maxima.html", true);
                    return true;
                }

                removeTmpFiles();
                cmdstr = maxima_syntax_check(cmdstr);
                maximaProccess.maximaCmd(cmdstr + "\n");
            } catch (IOException e) {
                Log.d("MoA", "exception4");
                e.printStackTrace();
                exitMOA();
            } catch (Exception e) {
                Log.d("MoA", "exception5");
                e.printStackTrace();
                exitMOA();
            }

            final String cmdstr2 = cmdstr;
            runOnUiThread(() -> webview.loadUrl("javascript:window.UpdateInput('"
                    + escapeChars(cmdstr2) + "<br>" + "')"));
            String resString = maximaProccess.getProcessResult();
            maximaProccess.clearStringBuilder();
            while (isStartQepcadString(resString)) {
                List<String> list = new ArrayList<>();
                list.add(dataDir + "data/jp.yhonda/files/additions/qepcad/qepcad.sh");
                CommandExec qepcadcom = new CommandExec();
                try {
                    qepcadcom.execCommand(list);
                } catch (Exception e) {
                    Log.d("MoA", "exception7");
                }
                try {
                    maximaProccess.maximaCmd("qepcad finished\n");
                } catch (IOException e) {
                    Log.d("MoA", "exception8");
                    e.printStackTrace();
                }
                resString = maximaProccess.getProcessResult();
                maximaProccess.clearStringBuilder();
            }

            if (isGraphFile()) {
                List<String> list = new ArrayList<>();
                if (CpuArchitecture.getCpuArchitecture().equals(CpuArchitecture.X86)) {
                    // list.add(internalDir + "/additions/gnuplot/bin/gnuplot.x86");
                    list.add(dataDir + "data/jp.yhonda/files/additions/gnuplot/bin/gnuplot.x86");
                } else {
                    list.add(internalDir + "/additions/gnuplot/bin/gnuplot");
                }
                list.add(dataDir + "data/jp.yhonda/files/maxout" + maximaProccess.getPID() + ".gnuplot");
                CommandExec gnuplotcom = new CommandExec();
                try {
                    gnuplotcom.execCommand(list);
                } catch (Exception e) {
                    Log.d("MoA", "exception6");
                }
                if ((new File(dataDir + "data/jp.yhonda/files/maxout.html")).exists()) {
                    showGraph();
                }
            }

            displayMaximaCmdResults(resString);

        }

        return true;
    }

    private boolean isStartQepcadString(String res) {
        int i = res.indexOf("start qepcad");
        if (i < 0) {
            return false;
        } else if (i == 0) {
            return true;
        } else {
            // i>0
            displayMaximaCmdResults(res.substring(0, i));
            return true;
        }

    }

    private String maxima_syntax_check(String cmd) {
        /*
         * Search the last char which is not white spaces. If the last char is
         * semi-colon or dollar, that is OK. Otherwise, semi-colon is added at
         * the end.
         */
        int i = cmd.length() - 1;
        assert (i >= 0);
        char c = ';';
        while (i >= 0) {
            c = cmd.charAt(i);
            if (c == ' ' || c == '\t') {
                i--;
            } else {
                break;
            }
        }

        if (c == ';' || c == '$') {
            return (cmd.substring(0, i + 1));
        } else {
            return (cmd.substring(0, i + 1) + ';');
        }
    }

    private String escapeChars(String cmd) {
        return substitute(cmd, "'", "\\'");
    }

    private void displayMaximaCmdResults(String resString) {
        Log.v("MoA cmd", resString);
        String[] resArray = resString.split("\\$\\$\\$\\$\\$\\$");
        for (int i = 0; i < resArray.length; i++) {
            if (i % 2 == 0) {
                /* normal text, as we are outside of $$$$$$...$$$$$$ */
                if (resArray[i].equals(""))
                    continue;
                final String htmlStr = substitute(resArray[i], "\n", "<br>");
                runOnUiThread(() -> webview.loadUrl("javascript:window.UpdateText('" + htmlStr + "')"));
                // webview.loadUrl("javascript:window.UpdateText('" + htmlStr + "')");
            } else {
                /* tex commands, as we are inside of $$$$$$...$$$$$$ */
                String texStr = substCRinMBOX(resArray[i]);
                texStr = substitute(texStr, "\n", " \\\\\\\\ ");
                texStr = substituteMBOXVERB(texStr);
                final String urlstr = "javascript:window.UpdateMath('" + texStr + "')";
                runOnUiThread(() -> webview.loadUrl(urlstr));
                // webview.loadUrl(urlstr);
            }
        }
        if (allExampleFinished) {
            Toast.makeText(this, "All examples are executed.", Toast.LENGTH_LONG).show();
            allExampleFinished = false;
        }
        //Delete temporary script file:
        if (temporaryScriptFile != null) {
            if (!temporaryScriptFile.delete()) {
                Log.e("MoA", "failed to delete temporary script file");
            }
        }
    }

    private String substCRinMBOX(String str) {
        StringBuilder resValueBuilder = new StringBuilder();
        String tmpValue = str;
        int p;
        while ((p = tmpValue.indexOf("mbox{")) != -1) {
            resValueBuilder.append(tmpValue.substring(0, p));
            resValueBuilder.append("mbox{");
            int p2 = tmpValue.indexOf("}", p + 5);
            assert (p2 > 0);
            String tmp2Value = tmpValue.substring(p + 5, p2);
            resValueBuilder.append(substitute(tmp2Value, "\n", "}\\\\\\\\ \\\\mbox{"));
            tmpValue = tmpValue.substring(p2);
        }
        resValueBuilder.append(tmpValue);
        return resValueBuilder.toString();
    }

    static private String substituteMBOXVERB(String texStr) {
        Pattern pat = Pattern.compile("\\\\\\\\mbox\\{\\\\\\\\verb\\|(.)\\|\\}"); // this escape isn't redundant
        Matcher m = pat.matcher(texStr);
        StringBuffer sb = new StringBuffer();
        if (m.find()) {
            m.appendReplacement(sb, "\\\\\\\\text{$1");
            while (m.find()) {
                m.appendReplacement(sb, "$1");
            }
            m.appendTail(sb);
            return sb.toString() + "}";
        }
        return (texStr);
    }

    static private String substitute(String input, String pattern,
                                     String replacement) {
        int index = input.indexOf(pattern);

        if (index == -1) {
            return input;
        }

        StringBuilder sb = new StringBuilder();

        sb.append(input.substring(0, index)).append(replacement);

        if (index + pattern.length() < input.length()) {
            String rest = input.substring(index + pattern.length());
            sb.append(substitute(rest, pattern, replacement));
        }
        return sb.toString();
    }

    private void showHTML(String url, boolean hwaccel) {
        Intent intent = new Intent(this, HTMLActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("url", url);
        intent.putExtra("hwaccel", hwaccel);
        this.startActivity(intent);
    }

    private void showPreference() {
        Intent intent = new Intent(this, MoAPreferenceActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        this.startActivity(intent);
    }

    private void showManual() {
        Intent intent = new Intent(this, ManualActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("url", manURL);
        intent.putExtra("manLangChanged", manLangChanged);
        manLangChanged = false;
        this.startActivityForResult(intent, 0);
    }

    private void showGraph() {
        if ((new File(dataDir + "data/jp.yhonda/files/maxout.html")).exists()) {
            showHTML("file:///data/data/jp.yhonda/files/maxout.html", false);
        } else {
            Toast.makeText(this, getString(R.string.toast_no_graph), Toast.LENGTH_LONG).show();
        }
    }

    private void removeTmpFileIfExists(String path) {
        File a = new File(path);
        if (a.exists()) {
            if (!a.delete()) Log.e("MoA", "Failed to delete temporary file: " + path);
        }
    }

    private void removeTmpFiles() {
        removeTmpFileIfExists(dataDir + "data/jp.yhonda/files/maxout" + maximaProccess.getPID() + ".gnuplot");
        removeTmpFileIfExists(dataDir + "data/jp.yhonda/files/maxout.html");
        removeTmpFileIfExists(dataDir + "data/jp.yhonda/files/qepcad_input.txt");
    }

    private Boolean isGraphFile() {
        File a = new File(dataDir + "data/jp.yhonda/files/maxout" + maximaProccess.getPID() + ".gnuplot");
        return (a.exists());
    }

    private void exitMOA() {
        try {
            maximaProccess.maximaCmd("quit();\n");
            finish();
        } catch (IOException e) {
            Log.d("MoA", "exception7");
            e.printStackTrace();
        } catch (Exception e) {
            Log.d("MoA", "exception8");
            e.printStackTrace();
        }
        finish();
    }

    private boolean maximaBinaryExists() {
        CpuArchitecture.initCpuArchitecture();
        String res = CpuArchitecture.getMaximaFile();
        if (res.startsWith("not")) {
            return false;
        }
        return ((new File(internalDir.getAbsolutePath() + "/" + res)).exists());

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                Toast.makeText(this, getString(R.string.quit_hint), Toast.LENGTH_LONG)
                        .show();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if ((view == webview) && (event.getAction() == MotionEvent.ACTION_UP)) {
            Log.v("MoA", "onTouch on webview");
            @SuppressWarnings("deprecation")
            float sc = webview.getScale();
            Log.v("MoA", "sc=" + sc);
            SharedPreferences settings = PreferenceManager
                    .getDefaultSharedPreferences(this);
            Editor editor = settings.edit();
            editor.putFloat("maxima main scale", sc);
            editor.apply();
            // setMCIAfontSize((int)(sc*12));
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retval = false;
        switch (item.getItemId()) {
            case R.id.about:
                showHTML("file:///android_asset/About_MoA/index.html", true);
                retval = true;
                break;
            case R.id.preference:
                showPreference();
                retval = true;
                break;
            case R.id.graph:
                showGraph();
                retval = true;
                break;
            case R.id.quit:
                exitMOA();
                retval = true;
                break;
            case R.id.nextexample:
                copyExampleInputToInputArea();
                break;
            case R.id.man:
                showManual();
                retval = true;
                break;
            case R.id.save:
                sessionMenu("ssave();");
                retval = true;
                break;
            case R.id.restore:
                sessionMenu("srestore();");
                retval = true;
                break;
            case R.id.playback:
                sessionMenu("playback();");
                retval = true;
                break;
            case R.id.loadscript:
                selectScriptFile();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return retval;
    }

    private void sessionMenu(String cmd) {
        editText.setText(cmd);
        editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER));
        editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ENTER));
    }

    private void selectScriptFile() {
        //Show file path input box instead:
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(MaximaOnAndroidActivity.this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View layout = inflater.inflate(R.layout.scriptpathalert, null);
        builder.setView(layout);
        final AutoCompleteTextView textView = (AutoCompleteTextView) layout.findViewById(R.id.scriptPathInput);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            dialog.dismiss();
            if (!textView.getText().toString().matches("")) {
                copyScriptFileToInputArea(Uri.fromFile(new File(textView.getText().toString())));
            }
        });
        android.support.v7.app.AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void copyScriptFileToInputArea(Uri fileUri) {
        try {
            File temporaryDirectory = getApplicationContext().getCacheDir();
            FileInputStream stream = (FileInputStream) (getApplicationContext().getContentResolver().openInputStream(fileUri));
            FileChannel sourceChannel = stream.getChannel();

            //Abort if file is too large:
            if (sourceChannel.size() > scriptFileMaxSize) {
                Toast.makeText(getApplicationContext(), R.string.script_file_too_large, Toast.LENGTH_LONG).show();
                return;
            }

            File temporaryFile = File.createTempFile("userscript", ".mac", temporaryDirectory);
            FileChannel destinationChannel = new FileOutputStream(temporaryFile).getChannel();
            destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            String command = "batch(\"" + temporaryFile.getAbsolutePath() + "\");";
            this.editText.setText(command);

            //Build maxima load command and write it to the editText:
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Failed to load script file.", Toast.LENGTH_LONG).show();
        }
    }
}
