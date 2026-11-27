package com.fakeskyline.html2apk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.fakeskyline.html2apk.core.ApkBuilder
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.math.BigInteger
import java.util.Date
import java.util.Calendar
import java.util.HashMap
import javax.security.auth.x500.X500Principal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class MainActivity : ComponentActivity() {
    private var htmlUri: Uri? = null
    private var htmlName = ""
    private var iconBytes: ByteArray? = null
    private var iconLabel = "Default icon"
    private var resultApk: File? = null
    private var appTitle = "My HTML App"
    private var appId = "com.example.myhtmlapp"
    private var minApiValue = 26
    private lateinit var nameField: EditText
    private lateinit var packageField: EditText
    private lateinit var apiSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var page: LinearLayout
    private lateinit var tabBar: LinearLayout

    private val htmlPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { htmlUri = it; htmlName = fileName(it); statusSafe("Selected: " + htmlName) }
    }
    private val iconPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { contentResolver.openInputStream(it)?.use { stream -> iconBytes = stream.readBytes() }; iconLabel = "Custom icon selected"; statusSafe(iconLabel) }
    }
    private val savePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        uri?.let { saveApkTo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF5F5F7.toInt()) }
        root.addView(TextView(this).apply { text="HTML2APK"; textSize=23f; setTextColor(0xFF151515.toInt()); setPadding(dp(20),dp(26),dp(20),dp(16)) })
        page = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(20)) }
        root.addView(ScrollView(this).apply { addView(page) },LinearLayout.LayoutParams(0,0,1f))
        tabBar = LinearLayout(this).apply { gravity=Gravity.CENTER; setBackgroundColor(-1); setPadding(dp(6),dp(6),dp(6),dp(8)) }
        listOf("HTML","App","Build").forEachIndexed { idx, text ->
            tabBar.addView(Button(this).apply { this.text=text; isAllCaps=false; setOnClickListener { showTab(idx) } },LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(tabBar)
        setContentView(root)
        showTab(0)
    }

    private fun showTab(which:Int) {
        page.removeAllViews()
        when(which) {
            0 -> {
                title("HTML file")
                button("Choose HTML file") { htmlPicker.launch(arrayOf("text/html","application/xhtml+xml","*/*")) }
                status=info(htmlName.ifEmpty { "No file selected" }); page.addView(status)
                title("Input")
                page.addView(info("This version takes one standalone HTML file. CSS, JavaScript and images should be embedded in that file."))
            }
            1 -> {
                title("Application")
                nameField=edit("App name",appTitle).also { it.addTextChangedListener(simpleWatcher { value -> appTitle=value }) }; page.addView(nameField)
                packageField=edit("Package name",appId).also { it.addTextChangedListener(simpleWatcher { value -> appId=value }) }; page.addView(packageField)
                title("Launcher icon")
                button(iconLabel) { iconPicker.launch("image/*") }
                button("Reset icon") { iconBytes=null; iconLabel="Default icon"; statusSafe(iconLabel) }
                title("Minimum Android version")
                apiSpinner=Spinner(this)
                val options=(26..36).map { "API "+it+" · Android "+androidVersion(it) }
                apiSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,options)
                apiSpinner.setSelection(minApiValue-26)
                apiSpinner.onItemSelectedListener=object: android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){minApiValue=26+position}
                }
                page.addView(apiSpinner)
            }
            else -> {
                title("Build")
                page.addView(info("Build a real, signed APK locally on this phone. Your HTML is packaged into the APK; no web server or cloud build is used."))
                button("Build APK") { buildApk() }
                title("Output")
                status=info(if(resultApk!=null) "APK ready: "+resultApk!!.name else "No APK built yet")
                page.addView(status)
                button("Save APK") { if(resultApk==null) toast("Build the APK first") else savePicker.launch(safeFileName(appTitle)+".apk") }
                button("Install APK") { installApk() }
                button("Share APK") { shareApk() }
            }
        }
    }

    private fun buildApk() {
        val uri=htmlUri ?: run { toast("Choose an HTML file first"); return }
        val name=appTitle.trim().ifEmpty { "HTML App" }
        val pkg=appId.trim()
        try { ApkBuilder.validatePackageName(pkg) } catch(e:Exception) { toast(e.message ?: "Invalid package name"); return }
        val html=try { contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("File read failed") } catch(e:Exception) { toast(e.message ?: "Can't read HTML"); return }
        if(html.isEmpty()){toast("The selected HTML file is empty");return}
        statusSafe("Building APK…")
        Thread {
            try {
                val template=assets.open("template.apk").use { it.readBytes() }
                val icons=HashMap<String,ByteArray>()
                iconBytes?.let { raw ->
                    val bmp=android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.size) ?: throw Exception("Unsupported image")
                    val square=android.graphics.Bitmap.createBitmap(bmp, minOf(bmp.width,bmp.height).let{(bmp.width-it)/2},minOf(bmp.width,bmp.height).let{(bmp.height-it)/2},minOf(bmp.width,bmp.height),minOf(bmp.width,bmp.height))
                    val scaled=android.graphics.Bitmap.createScaledBitmap(square,192,192,true)
                    val out=java.io.ByteArrayOutputStream();scaled.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)
                    icons["res/drawable-nodpi-v4/launcher_icon.png"]=out.toByteArray()
                }
                val (key,certs)=signingIdentity()
                val apk=ApkBuilder.build(template,html,name,pkg,icons,key,certs,cacheDir)
                val file=File(cacheDir,"output.apk");file.writeBytes(apk);resultApk=file
                runOnUiThread { statusSafe("Build complete · "+(apk.size/1024)+" KB"); showTab(2) }
            } catch(e:Exception) {
                runOnUiThread { statusSafe("Build failed: "+(e.message ?: e.javaClass.simpleName)); toast("Build failed") }
            }
        }.start()
    }

    private fun signingIdentity():Pair<PrivateKey,List<Certificate>> {
        val alias="html2apk-local-signing"
        val store=KeyStore.getInstance("AndroidKeyStore");store.load(null)
        if(!store.containsAlias(alias)){
            val generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore")
            val start=Calendar.getInstance();val end=Calendar.getInstance().apply{add(Calendar.YEAR,20)}
            generator.initialize(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=HTML2APK Local Builder"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(start.time).setCertificateNotAfter(end.time).build())
            generator.generateKeyPair()
        }
        val key=store.getKey(alias,null) as PrivateKey
        return Pair(key,store.getCertificateChain(alias).toList())
    }

    private fun saveApkTo(uri:Uri) {
        val file=resultApk ?: return
        try { contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }; toast("APK saved") }
        catch(e:Exception){toast("Save failed: "+e.message)}
    }
    private fun installApk() {
        val f=resultApk ?: run {toast("Build an APK first");return}
        try {
            val uri=FileProvider.getUriForFile(this,packageName+".files",f)
            val intent=Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch(e:Exception) { toast("Install error: "+e.message) }
    }
    private fun shareApk() {
        val f=resultApk ?: run{toast("Build an APK first");return}
        val uri=FileProvider.getUriForFile(this,packageName+".files",f)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/vnd.android.package-archive";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share APK"))
    }
    private fun simpleWatcher(onChange:(String)->Unit)=object:android.text.TextWatcher{
        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){onChange(s?.toString() ?: "")}
        override fun afterTextChanged(s:android.text.Editable?){}
    }
    private fun fileName(uri:Uri):String=contentResolver.query(uri,null,null,null,null)?.use { c->val i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(c.moveToFirst()&&i>=0)c.getString(i) else uri.lastPathSegment ?: "HTML"} ?: "HTML"
    private fun androidVersion(api:Int)=when(api){26->"8.0";27->"8.1";28->"9";29->"10";30->"11";31->"12";32->"12L";33->"13";34->"14";35->"15";else->"16"}
    private fun title(s:String){page.addView(TextView(this).apply{text=s;textSize=17f;setTextColor(0xFF222222.toInt());setPadding(0,dp(16),0,dp(6))})}
    private fun info(s:String)=TextView(this).apply{text=s;textSize=14f;setTextColor(0xFF666666.toInt());setPadding(0,dp(8),0,dp(10))}
    private fun edit(h:String,v:String)=EditText(this).apply{hint=h;setText(v);textSize=16f}
    private fun button(s:String,action:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{action()}}
    private fun statusSafe(s:String){if(::status.isInitialized)runOnUiThread{status.text=s}}
    private fun safeFileName(s:String)=s.replace(Regex("[^A-Za-z0-9._-]+"),"_").trim('_').ifEmpty{"app"}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
