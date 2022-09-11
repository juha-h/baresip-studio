package com.tutpro.baresip

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tutpro.baresip.databinding.ActivityCodecsBinding
import java.util.*

class CodecsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodecsBinding
    private lateinit var acc: Account
    private lateinit var ua: UserAgent
    private lateinit var codecsAdapter: CodecsAdapter

    private var aor = ""
    private var newCodecs = ArrayList<Codec>()
    private var media = ""

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityCodecsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        aor = intent.getStringExtra("aor")!!
        media = intent.getStringExtra("media")!!

        Utils.addActivity("codecs,$aor,$media")

        ua = UserAgent.ofAor(aor)!!
        acc = ua.account

        val allCodecs: ArrayList<String>
        val accCodecs: ArrayList<String>

        val codecsTitle = binding.CodecsTitle
        val codecList = binding.CodecList

        itemTouchHelper.attachToRecyclerView(codecList)

        if (media == "audio") {
            codecsTitle.text = getString(R.string.audio_codecs)
            allCodecs = ArrayList(Api.audio_codecs().split(","))
            accCodecs = acc.audioCodec
        } else {
            codecsTitle.text = getString(R.string.video_codecs)
            allCodecs = ArrayList(Api.video_codecs().split(",").distinct())
            accCodecs = acc.videoCodec
        }

        for (codec in accCodecs)
            newCodecs.add(Codec(codec, true))
        for (codec in allCodecs)
            if (codec !in accCodecs)
                newCodecs.add(Codec(codec, false))

        codecsAdapter = CodecsAdapter(newCodecs)

        codecList.layoutManager = LinearLayoutManager(this)
        codecList.adapter = codecsAdapter

        itemTouchHelper.attachToRecyclerView(binding.CodecList)

        codecsTitle.setOnClickListener {
            if (media == "audio")
                Utils.alertView(this, getString(R.string.audio_codecs),
                        getString(R.string.audio_codecs_help))
            else
                Utils.alertView(this, getString(R.string.video_codecs),
                        getString(R.string.video_codecs_help))
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

    }

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(
                UP or DOWN or START or END, RIGHT) {

            override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                codecsAdapter.moveItem(fromPosition, toPosition)
                codecsAdapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (newCodecs[position].enabled) {
                    codecsAdapter.disableItem(position)
                    codecsAdapter.notifyItemRemoved(position)
                } else {
                    codecsAdapter.enableItem(position)
                    codecsAdapter.notifyDataSetChanged()
                }
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                recyclerView.post { codecsAdapter.notifyDataSetChanged() }
            }
        }

        ItemTouchHelper(simpleItemTouchCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)
        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("codecs,$aor,$media") == -1)
            return true

        when (item.itemId) {

            R.id.checkIcon -> {

                var save = false
                val codecs = ArrayList<String>()

                for (codec in newCodecs)
                    if (codec.enabled)
                        codecs.add(codec.name)

                val codecList = Utils.implode(codecs, ",")

                if (media == "audio")
                    if (codecs != acc.audioCodec) {
                        if (Api.account_set_audio_codecs(acc.accp, codecList) == 0) {
                            Log.d(TAG, "New audio codecs '$codecList'")
                            acc.audioCodec = codecs
                            save = true
                        } else {
                            Log.e(TAG, "Setting of audio codecs '$codecList' failed")
                        }
                    }

                if (media == "video")
                    if (codecs != acc.videoCodec) {
                        if (Api.account_set_video_codecs(acc.accp, codecList) == 0) {
                            Log.d(TAG, "New video codecs '$codecs'")
                            acc.videoCodec = codecs
                            save = true
                        } else {
                            Log.e(TAG, "Setting of video codecs '$codecs' failed")
                        }
                    }

                if (save)
                    AccountsActivity.saveAccounts()

                BaresipService.activities.remove("codecs,$aor,$media")
                finish()
                return true
            }

            android.R.id.home -> {
                goBack()
                return true
            }

        }

        return super.onOptionsItemSelected(item)

    }

    private fun goBack() {
        BaresipService.activities.remove("codecs,$aor,$media")
        finish()
    }

    override fun onPause() {
        MainActivity.activityAor = aor
        super.onPause()
    }

}
