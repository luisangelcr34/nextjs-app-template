package com.timervegajuegos

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.*
import java.text.SimpleDateFormat
import java.util.*

@Entity
data class Child(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val presetTimeMinutes: Int,
    var timeLeftMillis: Long,
    var isRunning: Boolean = false
)

@Dao
interface ChildDao {
    @Query("SELECT * FROM Child")
    fun getAll(): List<Child>

    @Insert
    fun insert(child: Child): Long

    @Update
    fun update(child: Child)

    @Delete
    fun delete(child: Child)

    @Query("DELETE FROM Child")
    fun deleteAll()
}

@Database(entities = [Child::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
}

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var childDao: ChildDao
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChildAdapter
    private lateinit var addButton: Button
    private lateinit var nameInput: EditText
    private lateinit var timeInput: EditText
    private lateinit var totalChildrenText: TextView
    private lateinit var resetAllButton: Button
    private lateinit var sendReportButton: Button

    private val children = mutableListOf<Child>()
    private val timers = mutableMapOf<Int, CountDownTimer>()
    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "timer-vega-juegos-db"
        ).allowMainThreadQueries().build()
        childDao = db.childDao()

        recyclerView = findViewById(R.id.recyclerView)
        addButton = findViewById(R.id.addButton)
        nameInput = findViewById(R.id.nameInput)
        timeInput = findViewById(R.id.timeInput)
        totalChildrenText = findViewById(R.id.totalChildrenText)
        resetAllButton = findViewById(R.id.resetAllButton)
        sendReportButton = findViewById(R.id.sendReportButton)

        adapter = ChildAdapter(children,
            onStart = { child -> startTimer(child) },
            onPause = { child -> pauseTimer(child) },
            onReset = { child -> resetTimer(child) },
            onDelete = { child -> deleteChild(child) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadChildren()

        addButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val timeStr = timeInput.text.toString().trim()
            val timeMinutes = timeStr.toIntOrNull()
            if (name.isEmpty()) {
                Toast.makeText(this, "Por favor ingrese el nombre del niño/a", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (timeMinutes == null || timeMinutes !in 1..60) {
                Toast.makeText(this, "Por favor ingrese un tiempo entre 1 y 60 minutos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newChild = Child(name = name, presetTimeMinutes = timeMinutes, timeLeftMillis = timeMinutes * 60 * 1000L)
            val id = childDao.insert(newChild).toInt()
            val childWithId = newChild.copy(id = id)
            children.add(childWithId)
            adapter.notifyItemInserted(children.size - 1)
            updateTotalChildren()
            nameInput.text.clear()
            timeInput.text.clear()
        }

        resetAllButton.setOnClickListener {
            pauseAllTimers()
            childDao.deleteAll()
            children.clear()
            adapter.notifyDataSetChanged()
            updateTotalChildren()
        }

        sendReportButton.setOnClickListener {
            sendReport()
        }

        mediaPlayer = MediaPlayer.create(this, R.raw.timer_end_sound)
    }

    private fun loadChildren() {
        children.clear()
        children.addAll(childDao.getAll())
        adapter.notifyDataSetChanged()
        updateTotalChildren()
    }

    private fun updateTotalChildren() {
        totalChildrenText.text = "Niños agregados: ${children.size}"
    }

    private fun startTimer(child: Child) {
        if (timers.containsKey(child.id)) {
            timers[child.id]?.cancel()
        }
        val timer = object : CountDownTimer(child.timeLeftMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                child.timeLeftMillis = millisUntilFinished
                adapter.notifyItemChanged(children.indexOf(child))
            }

            override fun onFinish() {
                child.timeLeftMillis = 0
                adapter.notifyItemChanged(children.indexOf(child))
                mediaPlayer.start()
                Toast.makeText(this@MainActivity, "¡Tiempo terminado para ${child.name}!", Toast.LENGTH_SHORT).show()
            }
        }
        timers[child.id] = timer
        timer.start()
        child.isRunning = true
        childDao.update(child)
    }

    private fun pauseTimer(child: Child) {
        timers[child.id]?.cancel()
        timers.remove(child.id)
        child.isRunning = false
        childDao.update(child)
        adapter.notifyItemChanged(children.indexOf(child))
    }

    private fun resetTimer(child: Child) {
        pauseTimer(child)
        child.timeLeftMillis = child.presetTimeMinutes * 60 * 1000L
        childDao.update(child)
        adapter.notifyItemChanged(children.indexOf(child))
    }

    private fun deleteChild(child: Child) {
        pauseTimer(child)
        val index = children.indexOf(child)
        childDao.delete(child)
        children.removeAt(index)
        adapter.notifyItemRemoved(index)
        updateTotalChildren()
    }

    private fun pauseAllTimers() {
        for (timer in timers.values) {
            timer.cancel()
        }
        timers.clear()
        for (child in children) {
            child.isRunning = false
            child.timeLeftMillis = child.presetTimeMinutes * 60 * 1000L
            childDao.update(child)
        }
    }

    private fun sendReport() {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dateTime = dateFormat.format(Date())
        val message = "Reporte Final - TIMER VEGA JUEGOS\nFecha y Hora: $dateTime\nTotal de Niños agregados: ${children.size}"
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, message)
        intent.setPackage("com.whatsapp")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        pauseAllTimers()
    }

    inner class ChildAdapter(
        private val children: List<Child>,
        private val onStart: (Child) -> Unit,
        private val onPause: (Child) -> Unit,
        private val onReset: (Child) -> Unit,
        private val onDelete: (Child) -> Unit
    ) : RecyclerView.Adapter<ChildAdapter.ChildViewHolder>() {

        inner class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(R.id.childName)
            val timerText: TextView = itemView.findViewById(R.id.timerText)
            val startButton: Button = itemView.findViewById(R.id.startButton)
            val pauseButton: Button = itemView.findViewById(R.id.pauseButton)
            val resetButton: Button = itemView.findViewById(R.id.resetButton)
            val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
            val timeUpText: TextView = itemView.findViewById(R.id.timeUpText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_child, parent, false)
            return ChildViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
            val child = children[position]
            holder.nameText.text = child.name
            holder.timerText.text = formatTime(child.timeLeftMillis)
            holder.timeUpText.visibility = if (child.timeLeftMillis == 0L) View.VISIBLE else View.GONE
            holder.startButton.visibility = if (child.isRunning || child.timeLeftMillis == 0L) View.GONE else View.VISIBLE
            holder.pauseButton.visibility = if (child.isRunning) View.VISIBLE else View.GONE
            holder.resetButton.visibility = if (child.timeLeftMillis != child.presetTimeMinutes * 60 * 1000L) View.VISIBLE else View.GONE

            holder.startButton.setOnClickListener { onStart(child) }
            holder.pauseButton.setOnClickListener { onPause(child) }
            holder.resetButton.setOnClickListener { onReset(child) }
            holder.deleteButton.setOnClickListener { onDelete(child) }
        }

        override fun getItemCount(): Int = children.size

        private fun formatTime(millis: Long): String {
            val totalSeconds = millis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }
}
