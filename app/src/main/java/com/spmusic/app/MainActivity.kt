package com.spmusic.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : Activity() {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val tracks = mutableListOf<Track>()
    private val handler = Handler(Looper.getMainLooper())
    private var sleepTask: Runnable? = null

    private lateinit var list: ListView
    private lateinit var search: EditText
    private lateinit var cover: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var playBtn: Button
    private lateinit var sleepBtn: Button

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        buildUi()
        doSearch("")
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        future = f
        f.addListener({
            val c = f.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(m: MediaMetadata) = updateBar(m)
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playBtn.text = if (isPlaying) "⏸" else "▶"
                }
            })
            updateBar(c.mediaMetadata)
            playBtn.text = if (c.isPlaying) "⏸" else "▶"
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    private fun updateBar(m: MediaMetadata) {
        title.text = m.title ?: "SpMusic"
        artist.text = m.artist ?: ""
        if (m.artworkUri != null) cover.load(m.artworkUri)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(12), dp(32), dp(12), dp(8))
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        search = EditText(this).apply {
            hint = "Buscar música o artista"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> doSearch(text.toString()); true }
        }
        top.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Button(this).apply {
            text = "Buscar"
            setOnClickListener { doSearch(search.text.toString()) }
        })
        sleepBtn = Button(this).apply {
            text = "⏲"
            setOnClickListener { showSleepDialog() }
        }
        top.addView(sleepBtn)
        root.addView(top)

        list = ListView(this).apply {
            divider = null
            setOnItemClickListener { _, _, pos, _ -> play(pos) }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#282828"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        cover = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bar.addView(cover, LinearLayout.LayoutParams(dp(56), dp(56)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 15f; setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        artist = TextView(this).apply {
            setTextColor(Color.LTGRAY); textSize = 12f; setSingleLine()
        }
        texts.addView(title); texts.addView(artist)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(10)
        bar.addView(texts, lp)
        bar.addView(Button(this).apply {
            text = "⏮"; setOnClickListener { controller?.seekToPreviousMediaItem() }
        })
        playBtn = Button(this).apply {
            text = "▶"
            setOnClickListener {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
        bar.addView(playBtn)
        bar.addView(Button(this).apply {
            text = "⏭"; setOnClickListener { controller?.seekToNextMediaItem() }
        })
        root.addView(bar)
        setContentView(root)
    }

    private fun doSearch(q: String) {
        Thread {
            try {
                val res = Api.search(q)
                runOnUiThread {
                    tracks.clear(); tracks.addAll(res)
                    list.adapter = TrackAdapter()
                    if (res.isEmpty()) toast("Sin resultados")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Error de red o falta tu client_id de Jamendo") }
            }
        }.start()
    }

    private fun play(index: Int) {
        val c = controller ?: return
        val items = tracks.map {
            MediaItem.Builder()
                .setUri(it.audio)
                .setMediaId(it.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.name).setArtist(it.artist)
                        .setArtworkUri(Uri.parse(it.image)).build()
                ).build()
        }
        c.setMediaItems(items, index, 0)
        c.prepare()
        c.play()
    }

    private fun showSleepDialog() {
        val labels = arrayOf("15 minutos", "30 minutos", "1 hora", "2 horas", "4 horas", "6 horas", "Cancelar temporizador")
        val mins = intArrayOf(15, 30, 60, 120, 240, 360, 0)
        AlertDialog.Builder(this).setTitle("Apagar música en…")
            .setItems(labels) { _, i ->
                sleepTask?.let { handler.removeCallbacks(it) }
                sleepTask = null
                if (mins[i] == 0) {
                    sleepBtn.text = "⏲"; toast("Temporizador cancelado")
                } else {
                    val t = Runnable { controller?.pause(); sleepBtn.text = "⏲"; toast("Auto apagado") }
                    sleepTask = t
                    handler.postDelayed(t, mins[i] * 60_000L)
                    sleepBtn.text = "⏲ ${labels[i].substringBefore(' ')}"
                    toast("Se apagará en ${labels[i]}")
                }
            }.show()
    }

    private fun askDownload(t: Track) {
        if (!t.canDownload || t.download.isBlank()) { toast("Esta canción no permite descarga"); return }
        val fmts = arrayOf("MP3", "OGG", "FLAC")
        val keys = arrayOf("mp32", "ogg", "flac")
        val exts = arrayOf("mp3", "ogg", "flac")
        AlertDialog.Builder(this).setTitle("Descargar como…")
            .setItems(fmts) { _, i ->
                val url = t.download.replace("/mp32/", "/${keys[i]}/")
                val safe = "${t.artist} - ${t.name}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val req = DownloadManager.Request(Uri.parse(url))
                    .setTitle(t.name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MUSIC, "$safe.${exts[i]}")
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                toast("Descargando ${fmts[i]}…")
            }.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private inner class TrackAdapter : BaseAdapter() {
        override fun getCount() = tracks.size
        override fun getItem(p: Int) = tracks[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val t = tracks[p]
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val img = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(t.image)
            }
            row.addView(img, LinearLayout.LayoutParams(dp(56), dp(56)))
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this@MainActivity).apply {
                text = t.name; setTextColor(Color.WHITE); textSize = 15f; setSingleLine()
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            col.addView(TextView(this@MainActivity).apply {
                text = t.artist; setTextColor(Color.LTGRAY); textSize = 12f; setSingleLine()
            })
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.leftMargin = dp(10)
            row.addView(col, lp)
            row.addView(Button(this@MainActivity).apply {
                text = "⬇"
                isFocusable = false
                setOnClickListener { askDownload(t) }
            })
            return row
        }
    }
}
