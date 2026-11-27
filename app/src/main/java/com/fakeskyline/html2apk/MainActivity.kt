package com.fakeskyline.html2apk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private var htmlUri: Uri? = null
    private var iconData: ByteArray? = null
    private var htmlLabel = "No HTML file selected"
    private lateinit var nameInput: EditText
    private lateinit var packageInput: EditText
    private lateinit var minApi: Spinner
    private lateinit var status: TextView
    private lateinit var body: LinearLayout

    private val htmlPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { htmlUri = it; htmlLabel = displayName(it); if (::status.isInitialized) status.text = htmlLabel }
    }
    private val iconPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { contentResolver.openInputStream(it)?.use { stream -> iconData = stream.readBytes() }; if (::status.isInitialized) status.text = "Icon selected" }
    }
    private val zipPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { writeProject(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF4F4F6.toInt()) }
        frame.addView(TextView(this).apply { text = "HTML2APK"; textSize = 23f; setTextColor(0xFF171717.toInt()); setPadding(22,26,22,14) })
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,12,20,20) }
        frame.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(0,0,1f))
        val tabs = LinearLayout(this).apply { setBackgroundColor(-1); gravity = Gravity.CENTER; setPadding(6,6,6,8) }
        listOf("Project","App","Export").forEachIndexed { index, label ->
            tabs.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { render(index) } }, LinearLayout.LayoutParams(0,-2,1f))
        }
        frame.addView(tabs)
        setContentView(frame)
        render(0)
    }

    private fun render(tab: Int) {
        body.removeAllViews()
        when(tab) {
            0 -> {
                heading("HTML source")
                button("Choose HTML file") { htmlPicker.launch(arrayOf("text/html", "application/xhtml+xml", "*/*")) }
                status = label(htmlLabel); body.addView(status)
                heading("App details")
                nameInput = input("App name", "My HTML App"); body.addView(nameInput)
                packageInput = input("Package name", "com.example.webapp"); body.addView(packageInput)
            }
            1 -> {
                heading("App icon")
                button("Choose image") { iconPicker.launch("image/*") }
                heading("Minimum Android version")
                minApi = Spinner(this)
                val versions = (26..36).map { "API " + it + "  ·  Android " + androidVersion(it) }
                minApi.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, versions)
                body.addView(minApi)
                heading("Runtime")
                body.addView(label("WebView · local HTML · JavaScript enabled · no internet permission by default"))
            }
            else -> {
                heading("Export Android project")
                body.addView(label("Your HTML, app metadata, optional icon and WebView wrapper will be packaged into a Gradle project ZIP."))
                button("Create project ZIP") { if (htmlUri == null) toast("Choose an HTML file first") else zipPicker.launch("HTML2APK-project.zip") }
                heading("Status"); status = label("Ready"); body.addView(status)
            }
        }
    }

    private fun writeProject(dest: Uri) {
        val html = htmlUri?.let { contentResolver.openInputStream(it)?.use { s -> s.readBytes() } } ?: run { toast("Unable to read HTML file"); return }
        val appName = if (::nameInput.isInitialized) nameInput.text.toString().trim().ifEmpty { "HTML App" } else "HTML App"
        val pkg = if (::packageInput.isInitialized) packageInput.text.toString().trim() else "com.example.webapp"
        if (!pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { toast("Invalid package name"); return }
        val api = if (::minApi.isInitialized) 26 + minApi.selectedItemPosition else 26
        try {
            contentResolver.openOutputStream(dest)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    put(zip,"settings.gradle","pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\nrootProject.name = 'WebApp'\ninclude ':app'\n")
                    put(zip,"build.gradle","plugins { id 'com.android.application' version '8.7.3' apply false }\n")
                    put(zip,"gradle.properties","org.gradle.jvmargs=-Xmx2048m\nandroid.useAndroidX=true\n")
                    put(zip,"app/build.gradle","plugins { id 'com.android.application' }\nandroid { namespace '"+pkg+"'; compileSdk 35; defaultConfig { applicationId '"+pkg+"'; minSdk "+api+"; targetSdk 35; versionCode 1; versionName '1.0' } }\n")
                    put(zip,"app/src/main/AndroidManifest.xml",manifest(pkg,appName,iconData!=null))
                    put(zip,"app/src/main/java/"+pkg.replace('.','/')+"/MainActivity.java",source(pkg))
                    put(zip,"app/src/main/assets/index.html",html)
                    iconData?.let { put(zip,"app/src/main/res/drawable/launcher_icon.png",it) }
                    put(zip,"README.txt","Open in Android Studio or AndroidIDE and build. The app opens app/src/main/assets/index.html in WebView.\n")
                }
            }
            status.text = "Project ZIP saved"; toast("Project exported")
        } catch(e: Exception) { toast("Export error: " + e.message) }
    }

    private fun manifest(pkg:String, label:String, hasIcon:Boolean) = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application android:label=\""+escape(label)+"\" android:theme=\"@android:style/Theme.Material.Light.NoActionBar\" android:usesCleartextTraffic=\"true\""+(if(hasIcon) " android:icon=\"@drawable/launcher_icon\"" else "")+"><activity android:name=\""+pkg+".MainActivity\" android:exported=\"true\" android:configChanges=\"orientation|screenSize\"><intent-filter><action android:name=\"android.intent.action.MAIN\"/><category android:name=\"android.intent.category.LAUNCHER\"/></intent-filter></activity></application></manifest>"

    private fun source(pkg:String) = "package "+pkg+";\nimport android.app.Activity; import android.os.Bundle; import android.webkit.WebView; import android.webkit.WebSettings; import android.webkit.WebViewClient;\npublic class MainActivity extends Activity { public void onCreate(Bundle b){super.onCreate(b); WebView w=new WebView(this); WebSettings s=w.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); w.setWebViewClient(new WebViewClient()); w.loadUrl(\"file:///android_asset/index.html\"); setContentView(w);} }\n"
    private fun put(z:ZipOutputStream,path:String,text:String)=put(z,path,text.toByteArray(Charsets.UTF_8))
    private fun put(z:ZipOutputStream,path:String,data:ByteArray){z.putNextEntry(ZipEntry(path));z.write(data);z.closeEntry()}
    private fun displayName(uri:Uri):String = contentResolver.query(uri,null,null,null,null)?.use { c -> val i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(c.moveToFirst()&&i>=0)c.getString(i) else uri.lastPathSegment ?: "HTML file" } ?: "HTML file"
    private fun escape(s:String)=s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;")
    private fun androidVersion(api:Int)=when(api){26->"8.0";27->"8.1";28->"9";29->"10";30->"11";31->"12";32->"12L";33->"13";34->"14";35->"15";else->"16"}
    private fun heading(s:String){body.addView(TextView(this).apply{text=s;textSize=17f;setTextColor(0xFF222222.toInt());setPadding(0,18,0,8)})}
    private fun label(s:String)=TextView(this).apply{text=s;textSize=14f;setTextColor(0xFF606060.toInt());setPadding(0,8,0,10)}
    private fun input(h:String,v:String)=EditText(this).apply{hint=h;setText(v);textSize=16f}
    private fun button(s:String,f:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{f()}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
