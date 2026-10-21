package com.novacamera.s24

import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.novacamera.s24.core.GalleryBadge
import com.novacamera.s24.processing.ProcessingQueue
import com.novacamera.s24.ui.Nova
import com.novacamera.s24.ui.NovaIconView
import java.util.concurrent.Executors

/**
 * Galeria do próprio app: fotos e vídeos da Nova (sem originais nem rajadas de diagnóstico), com o selo
 * do estado da IA em cada foto. Toque: comparação/ajuste quando a foto ainda tem sessão de revisão, senão o
 * visualizador do aparelho. Toque e segure: abrir fora, compartilhar, apagar. Sem permissão de leitura:
 * o Android mostra ao app só os arquivos que ele mesmo criou (após reinstalar, as fotos antigas somem daqui
 * mas continuam na galeria do aparelho).
 */
class GalleryActivity : androidx.activity.ComponentActivity() {
    private class Item(val uri: Uri, val video: Boolean, val mime: String)

    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val thumbs = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val badges = HashMap<String, GalleryBadge>()
    private var items = emptyList<Item>()
    private var sessions = emptyMap<String, String>()
    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private var cell = 0
    private var generation = 0

    private val deleteRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { load() }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val frame = (convertView as? FrameLayout) ?: FrameLayout(this@GalleryActivity).apply {
                layoutParams = AbsListView.LayoutParams(-1, cell)
                addView(ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Nova.SURFACE) }, -1, -1)
                addView(TextView(context).apply {
                    textSize = 10f; typeface = Nova.MEDIUM; setTextColor(Nova.BG); setPadding(dpi(6), dpi(2), dpi(6), dpi(2))
                }, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply { setMargins(dpi(4), 0, 0, dpi(4)) })
            }
            frame.layoutParams.height = cell
            val item = items[position]
            val key = item.uri.toString()
            val image = frame.getChildAt(0) as ImageView
            val label = frame.getChildAt(1) as TextView
            frame.tag = key
            image.setImageBitmap(thumbs.get(key))
            showBadge(label, badges[key])
            if (thumbs.get(key) == null || !badges.containsKey(key)) loadItem(item, frame)
            return frame
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val cols = if (resources.configuration.screenWidthDp > 600) 5 else 3
        cell = resources.displayMetrics.widthPixels / cols
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Nova.BG) }
        val header = FrameLayout(this)
        header.addView(NovaIconView(this, NovaIconView.Icon.CLOSE).apply {
            contentDescription = "Voltar"; setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dpi(48), dpi(48), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dpi(8) })
        header.addView(TextView(this).apply { text = "Galeria Nova"; setTextColor(Nova.TEXT); textSize = 18f; typeface = Nova.MEDIUM },
            FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        header.addView(TextView(this).apply {
            text = "Abrir galeria"; setTextColor(Nova.ACCENT); textSize = 13f; typeface = Nova.MEDIUM
            setPadding(dpi(8), dpi(12), dpi(8), dpi(12))
            setOnClickListener { openSystemGallery() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dpi(8) })
        root.addView(header, LinearLayout.LayoutParams(-1, dpi(56)))
        val body = FrameLayout(this)
        grid = GridView(this).apply {
            numColumns = cols; stretchMode = GridView.STRETCH_COLUMN_WIDTH; horizontalSpacing = dpi(1); verticalSpacing = dpi(1)
            adapter = this@GalleryActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> open(items[pos]) }
            setOnItemLongClickListener { _, _, pos, _ -> menu(items[pos]); true }
        }
        body.addView(grid, -1, -1)
        empty = TextView(this).apply {
            text = "Nenhuma foto da Nova ainda"; setTextColor(Nova.TEXT_MUTED); textSize = 15f; gravity = Gravity.CENTER; visibility = View.GONE
        }
        body.addView(empty, -1, -1)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "IA = ruído reduzido · IA… = aplicando · NOVA = ajustada sem IA · toque e segure para mais opções"
            setTextColor(Nova.TEXT_MUTED); textSize = 11f; gravity = Gravity.CENTER; setPadding(dpi(12), dpi(8), dpi(12), dpi(8))
        })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
    }

    override fun onResume() { super.onResume(); load() }

    override fun onPause() { main.removeCallbacks(refresh); super.onPause() }

    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }

    /** Enquanto alguma foto está na fila ou recebendo a IA, o selo é relido a cada 3 s. */
    private val refresh = Runnable {
        badges.entries.removeAll { it.value == GalleryBadge.AI_RUNNING || it.value == GalleryBadge.AI_WAITING }
        sessions = emptyMap(); load()
    }

    private fun load() {
        val gen = ++generation
        io.execute {
            val found = query()
            val map = runCatching { ProcessingQueue.sessionsByUri(this) }.getOrDefault(emptyMap())
            main.post {
                if (gen != generation || isDestroyed) return@post
                items = found; sessions = map
                empty.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun query(): List<Item> {
        val out = ArrayList<Pair<Long, Item>>()
        fun scan(collection: Uri, video: Boolean, folders: List<String>) {
            val sel = folders.joinToString(" OR ") { "${MediaStore.MediaColumns.RELATIVE_PATH} = ?" }
            runCatching {
                contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.IS_PENDING), sel, folders.toTypedArray(), null)?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getInt(3) != 0) continue
                        val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                        out += c.getLong(1) to Item(uri, video, c.getString(2) ?: if (video) "video/mp4" else "image/jpeg")
                    }
                }
            }.onFailure { Log.w(TAG, "consulta da galeria falhou", it) }
        }
        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, listOf("Pictures/Nova Camera/", "Pictures/Nova Camera/Documentos/"))
        scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, listOf("Movies/Nova Camera/", "Movies/Nova Camera/Acabamento/"))
        return out.sortedByDescending { it.first }.take(MAX_ITEMS).map { it.second }
    }

    private fun loadItem(item: Item, frame: FrameLayout) {
        val key = item.uri.toString()
        val sessionId = sessions[key]
        io.execute {
            val bmp = thumbs.get(key) ?: runCatching { contentResolver.loadThumbnail(item.uri, Size(cell, cell), null) }.getOrNull()
            val badge = badges[key] ?: run {
                val description = if (item.video) null else runCatching {
                    contentResolver.openInputStream(item.uri)?.use { androidx.exifinterface.media.ExifInterface(it)
                        .getAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION) }
                }.getOrNull()
                val (state, report) = sessionId?.let { runCatching { ProcessingQueue.galleryAiState(this, it) }.getOrNull() } ?: (null to "")
                GalleryBadge.of(item.video, description, state, report)
            }
            main.post {
                if (isDestroyed) return@post
                bmp?.let { thumbs.put(key, it) }
                badges[key] = badge
                if (frame.tag == key) {
                    (frame.getChildAt(0) as ImageView).setImageBitmap(bmp)
                    showBadge(frame.getChildAt(1) as TextView, badge)
                }
                if (badge == GalleryBadge.AI_RUNNING || badge == GalleryBadge.AI_WAITING) {
                    main.removeCallbacks(refresh); main.postDelayed(refresh, 3_000L)
                }
            }
        }
    }

    private fun showBadge(label: TextView, badge: GalleryBadge?) {
        if (badge == null || badge == GalleryBadge.NONE) { label.visibility = View.GONE; return }
        label.visibility = View.VISIBLE
        label.text = badge.label
        label.setBackgroundColor(when (badge) {
            GalleryBadge.AI -> Nova.ACCENT
            GalleryBadge.AI_RUNNING, GalleryBadge.AI_WAITING -> Nova.LOCK
            GalleryBadge.AI_REFUSED -> Nova.WARN
            else -> Nova.TEXT_MUTED
        })
    }

    private fun open(item: Item) {
        val id = sessions[item.uri.toString()]
        if (!item.video && id != null) {
            startActivity(Intent(this, ReviewActivity::class.java).putExtra(ReviewActivity.EXTRA_SESSION, id)); return
        }
        view(item)
    }

    private fun view(item: Item) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            .onFailure { android.widget.Toast.makeText(this, "Nenhum visualizador disponível", android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun menu(item: Item) {
        val key = item.uri.toString()
        val reason = sessions[key]?.let { ProcessingQueue.refineStatus(this, it).detail }?.takeIf { it.isNotBlank() }
        android.app.AlertDialog.Builder(this)
            .setTitle(badges[key]?.label?.takeIf { it.isNotEmpty() } ?: if (item.video) "Vídeo" else "Foto")
            .apply { reason?.let { setMessage(it) } }
            .setItems(arrayOf("Abrir no visualizador do aparelho", "Compartilhar", "Apagar")) { _, which ->
                when (which) {
                    0 -> view(item)
                    1 -> runCatching {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(item.mime)
                            .putExtra(Intent.EXTRA_STREAM, item.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartilhar"))
                    }
                    else -> confirmDelete(item)
                }
            }.show()
    }

    private fun confirmDelete(item: Item) {
        android.app.AlertDialog.Builder(this).setTitle("Apagar este arquivo?")
            .setMessage("Some também da galeria do aparelho. O original separado (se houver) fica em Originais.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar") { _, _ ->
                try {
                    contentResolver.delete(item.uri, null, null); load()
                } catch (e: SecurityException) {
                    // Arquivo de outra instalação: o Android pede a confirmação dele (Android 11+).
                    if (android.os.Build.VERSION.SDK_INT < 30) {
                        android.widget.Toast.makeText(this, "Apague pela galeria do aparelho", android.widget.Toast.LENGTH_SHORT).show()
                    } else runCatching {
                        val pending = MediaStore.createDeleteRequest(contentResolver, listOf(item.uri))
                        deleteRequest.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    }
                }
            }.show()
    }

    private fun openSystemGallery() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW).setType("image/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "NovaGallery"
        private const val MAX_ITEMS = 500
    }
}
