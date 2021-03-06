package com.glodanif.bluetoothchat.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import com.glodanif.bluetoothchat.R
import com.glodanif.bluetoothchat.di.ComponentsManager
import com.glodanif.bluetoothchat.extension.toReadableFileSize
import com.glodanif.bluetoothchat.ui.adapter.ChatAdapter
import com.glodanif.bluetoothchat.ui.presenter.ChatPresenter
import com.glodanif.bluetoothchat.ui.util.SimpleTextWatcher
import com.glodanif.bluetoothchat.ui.view.ChatView
import com.glodanif.bluetoothchat.ui.view.NotificationView
import com.glodanif.bluetoothchat.ui.viewmodel.ChatMessageViewModel
import com.glodanif.bluetoothchat.ui.widget.ActionView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import pl.aprilapps.easyphotopicker.DefaultCallback
import pl.aprilapps.easyphotopicker.EasyImage
import java.io.File
import java.lang.Exception
import java.util.*
import javax.inject.Inject

class ChatActivity : SkeletonActivity(), ChatView {

    @Inject
    lateinit var presenter: ChatPresenter

    private val layoutManager = LinearLayoutManager(this)
    private lateinit var actions: ActionView
    private lateinit var chatList: RecyclerView
    private lateinit var messageField: EditText
    private lateinit var sendButtonsSwitcher: ViewSwitcher
    private lateinit var transferringImagePreview: ImageView
    private lateinit var transferringImageSize: TextView
    private lateinit var transferringImageHeader: TextView
    private lateinit var transferringImageProgressLabel: TextView
    private lateinit var transferringImageProgressBar: ProgressBar

    private lateinit var presharingContainer: CardView
    private lateinit var presharingImage: ImageView

    private lateinit var textSendingHolder: ViewGroup
    private lateinit var imageSendingHolder: ViewGroup

    private lateinit var chatAdapter: ChatAdapter

    private var deviceAddress: String? = null

    private val showAnimation =
            lazy { AnimationUtils.loadAnimation(this, R.anim.anime_fade_slide_in) }
    private val hideAnimation =
            lazy { AnimationUtils.loadAnimation(this, R.anim.anime_fade_slide_out) }

    private val textWatcher = object : SimpleTextWatcher() {

        private var previousText: String? = null

        override fun afterTextChanged(text: String) {

            if (previousText.isNullOrEmpty() && text.isNotEmpty()) {
                sendButtonsSwitcher.showNext()
            } else if (!previousText.isNullOrEmpty() && text.isEmpty()) {
                sendButtonsSwitcher.showPrevious()
            }
            previousText = text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat, ActivityType.CHILD_ACTIVITY)

        deviceAddress = intent.getStringExtra(EXTRA_ADDRESS)

        ComponentsManager.injectChat(this, deviceAddress.toString())

        title = if (deviceAddress.isNullOrEmpty()) getString(R.string.app_name) else deviceAddress
        toolbar?.let {
            it.subtitle = getString(R.string.chat__not_connected)
            it.setTitleTextAppearance(this, R.style.ActionBar_TitleTextStyle)
            it.setSubtitleTextAppearance(this, R.style.ActionBar_SubTitleTextStyle)
        }

        textSendingHolder = findViewById(R.id.ll_text_sending_holder)
        imageSendingHolder = findViewById(R.id.ll_image_sending_holder)
        sendButtonsSwitcher = findViewById(R.id.vs_send_buttons)

        transferringImagePreview = findViewById(R.id.iv_transferring_image)
        transferringImageSize = findViewById(R.id.tv_file_size)
        transferringImageHeader = findViewById(R.id.tv_sending_image_label)
        transferringImageProgressLabel = findViewById(R.id.tv_file_sending_percentage)
        transferringImageProgressBar = findViewById(R.id.pb_transferring_progress)

        presharingContainer = findViewById(R.id.cv_presharing_image_holder)
        presharingImage = findViewById(R.id.iv_presharing_image)

        actions = findViewById(R.id.av_actions)
        messageField = findViewById<EditText>(R.id.et_message).apply {
            addTextChangedListener(textWatcher)
        }

        findViewById<ImageButton>(R.id.ib_send).setOnClickListener {
            presenter.sendMessage(messageField.text.toString().trim())
        }

        findViewById<ImageButton>(R.id.ib_image).setOnClickListener {
            EasyImage.openChooserWithGallery(this, "chooserTitle", 0)
        }

        findViewById<ImageButton>(R.id.ib_cancel).setOnClickListener {
            presenter.cancelFileTransfer()
        }

        findViewById<Button>(R.id.btn_retry).setOnClickListener {
            hideAnimation.value.let {
                it.fillAfter = true
                presharingContainer.startAnimation(it)
                presenter.proceedPresharing()
            }
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            hideAnimation.value.let {
                it.fillAfter = true
                presharingContainer.startAnimation(it)
                presenter.cancelPresharing()
            }
        }

        chatAdapter = ChatAdapter(this).apply {
            imageClickListener = { view, message ->
                ImagePreviewActivity.start(this@ChatActivity, view, message)
            }
        }

        chatList = findViewById<RecyclerView>(R.id.rv_chat).apply {

            val manager = this@ChatActivity.layoutManager
            manager.reverseLayout = true
            layoutManager = manager
            adapter = chatAdapter

            addOnScrollListener(object : RecyclerView.OnScrollListener() {

                override fun onScrollStateChanged(recyclerView: RecyclerView?, scrollState: Int) {

                    val picasso = Picasso.with(this@ChatActivity)
                    if (scrollState == RecyclerView.SCROLL_STATE_IDLE || scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        picasso.resumeTag(chatAdapter.picassoTag)
                    } else {
                        picasso.pauseTag(chatAdapter.picassoTag)
                    }
                }
            })
        }

        if (Intent.ACTION_SEND == intent.action) {

            val textToShare = intent.getStringExtra(EXTRA_MESSAGE)
            val fileToShare = intent.getStringExtra(EXTRA_FILE_PATH)

            if (textToShare != null) {
                messageField.setText(textToShare)
            } else if (fileToShare != null) {
                Handler().postDelayed({
                    presenter.sendFile(File(fileToShare))
                }, 1000)
            }

            intent.action = Intent.ACTION_VIEW
        }
    }

    override fun onStart() {
        super.onStart()
        presenter.prepareConnection()
    }

    override fun onStop() {
        super.onStop()
        presenter.releaseConnection()
    }

    override fun dismissMessageNotification() {
        (getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NotificationView.NOTIFICATION_TAG_MESSAGE, NotificationView.NOTIFICATION_ID_MESSAGE)
    }

    override fun showPartnerName(name: String, device: String) {
        title = "$name ($device)"
    }

    override fun showStatusConnected() {
        toolbar?.subtitle = getString(R.string.chat__connected)
    }

    override fun showStatusNotConnected() {
        toolbar?.subtitle = getString(R.string.chat__not_connected)
    }

    override fun showStatusPending() {
        toolbar?.subtitle = getString(R.string.chat__pending)
    }

    override fun showNotConnectedToSend() =
            Toast.makeText(this, getString(R.string.chat__not_connected_to_send), Toast.LENGTH_LONG).show()

    override fun afterMessageSent() {
        messageField.text = null
    }

    override fun showNotConnectedToThisDevice(currentDevice: String) {

        actions.visibility = View.VISIBLE
        actions.setActions(getString(R.string.chat__connected_to_another, currentDevice),
                ActionView.Action(getString(R.string.chat__connect)) { presenter.connectToDevice() },
                null
        )
    }

    override fun showNotConnectedToAnyDevice() {

        actions.visibility = View.VISIBLE
        actions.setActions(getString(R.string.chat__not_connected_to_this_device),
                ActionView.Action(getString(R.string.chat__connect)) { presenter.connectToDevice() },
                null
        )
    }

    override fun showWainingForOpponent() {

        actions.visibility = View.VISIBLE
        actions.setActions(getString(R.string.chat__waiting_for_device),
                ActionView.Action(getString(R.string.general__cancel)) { presenter.resetConnection() },
                null
        )
    }

    override fun showConnectionRequest(displayName: String, deviceName: String) {

        actions.visibility = View.VISIBLE
        actions.setActions(getString(R.string.chat__connection_request, displayName, deviceName),
                ActionView.Action(getString(R.string.general__start_chat)) { presenter.acceptConnection() },
                ActionView.Action(getString(R.string.chat__disconnect)) { presenter.rejectConnection() }
        )
    }

    override fun showServiceDestroyed() {

        if (!isStarted()) return

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.general__service_lost))
                .setPositiveButton(getString(R.string.general__restart), { _, _ -> presenter.prepareConnection() })
                .setCancelable(false)
                .show()
    }

    override fun hideActions() {
        actions.visibility = View.GONE
    }

    override fun showMessagesHistory(messages: List<ChatMessageViewModel>) {
        chatAdapter.messages = LinkedList(messages)
        chatAdapter.notifyDataSetChanged()
    }

    override fun showReceivedMessage(message: ChatMessageViewModel) {
        chatAdapter.messages.addFirst(message)
        chatAdapter.notifyItemInserted(0)
        layoutManager.scrollToPosition(0)
    }

    override fun showSentMessage(message: ChatMessageViewModel) {
        chatAdapter.messages.addFirst(message)
        chatAdapter.notifyItemInserted(0)
        layoutManager.scrollToPosition(0)
    }

    override fun showRejectedConnection() {

        if (!isStarted()) return

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.chat__connection_rejected))
                .setPositiveButton(getString(R.string.general__ok), null)
                .setCancelable(false)
                .show()
    }

    override fun showBluetoothDisabled() {
        actions.visibility = View.VISIBLE
        actions.setActions(getString(R.string.chat__bluetooth_is_disabled),
                ActionView.Action(getString(R.string.chat__enable)) { presenter.enableBluetooth() },
                null
        )
    }

    override fun showLostConnection() {

        if (!isStarted()) return

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.chat__connection_lost))
                .setPositiveButton(getString(R.string.chat__reconnect), { _, _ -> presenter.reconnect() })
                .setNegativeButton(getString(R.string.general__cancel), null)
                .setCancelable(false)
                .show()
    }

    override fun showDisconnected() {

        if (!isStarted()) return

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.chat__partner_disconnected))
                .setPositiveButton(getString(R.string.chat__reconnect), { _, _ -> presenter.reconnect() })
                .setNegativeButton(getString(R.string.general__cancel), null)
                .setCancelable(false)
                .show()
    }

    override fun showFailedConnection() {

        if (!isStarted()) return

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.chat__unable_to_connect))
                .setPositiveButton(getString(R.string.general__try_again), { _, _ -> presenter.connectToDevice() })
                .setNegativeButton(getString(R.string.general__cancel), null)
                .setCancelable(false)
                .show()

    }

    override fun showNotValidMessage() {
        Toast.makeText(this, getString(R.string.chat__message_cannot_be_empty), Toast.LENGTH_SHORT).show()
    }

    override fun showDeviceIsNotAvailable() {

        if (!isStarted()) return

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.chat__device_is_not_available))
                .setPositiveButton(getString(R.string.chat__rescan), { _, _ -> ScanActivity.start(this) })
                .show()
    }

    override fun requestBluetoothEnabling() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
    }

    override fun showBluetoothEnablingFailed() {
        AlertDialog.Builder(this)
                .setMessage(getString(R.string.chat__bluetooth_required))
                .setPositiveButton(getString(R.string.general__ok), null)
                .show()
    }

    override fun showImageTooBig(maxSize: Long) {

        if (!isStarted()) return

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.chat__too_big_image, maxSize.toReadableFileSize()))
                .setPositiveButton(getString(R.string.general__ok), null)
                .show()
    }

    override fun showPresharingImage(path: String) {

        showAnimation.value.let {
            it.fillAfter = true
            presharingContainer.visibility = View.VISIBLE
            presharingContainer.startAnimation(it)
        }

        Picasso.with(this)
                .load("file://$path")
                .into(presharingImage)
    }

    override fun showImageTransferLayout(fileAddress: String?, fileSize: Long, transferType: ChatView.FileTransferType) {

        textSendingHolder.visibility = View.GONE
        imageSendingHolder.visibility = View.VISIBLE

        transferringImageHeader.text = getString(if (transferType == ChatView.FileTransferType.SENDING)
            R.string.chat__sending_image else R.string.chat__receiving_images)

        Picasso.with(this)
                .load("file://$fileAddress")
                .into(object : Target {

                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                        transferringImagePreview.setImageResource(R.drawable.ic_photo)
                    }

                    override fun onBitmapFailed(errorDrawable: Drawable?) {
                        transferringImagePreview.setImageResource(R.drawable.ic_photo)
                    }

                    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                        transferringImagePreview.setImageBitmap(bitmap)
                    }
                })

        transferringImageSize.text = fileSize.toReadableFileSize()
        transferringImageProgressLabel.text = "0%"
        //FIXME should work with Long
        transferringImageProgressBar.progress = 0
        transferringImageProgressBar.max = fileSize.toInt()
    }

    @SuppressLint("SetTextI18n")
    override fun updateImageTransferProgress(transferredBytes: Long, totalBytes: Long) {

        val percents = transferredBytes.toFloat() / totalBytes * 100
        transferringImageProgressLabel.text = "${Math.round(percents)}%"
        //FIXME should work with Long
        transferringImageProgressBar.progress = transferredBytes.toInt()

    }

    override fun hideImageTransferLayout() {
        textSendingHolder.visibility = View.VISIBLE
        imageSendingHolder.visibility = View.GONE
    }

    override fun showImageTransferCanceled() {
        Toast.makeText(this, R.string.chat__partner_canceled_image_transfer, Toast.LENGTH_LONG).show()
    }

    override fun showImageTransferFailure() {
        Toast.makeText(this, R.string.chat__problem_during_file_transfer, Toast.LENGTH_LONG).show()
    }

    override fun showReceiverUnableToReceiveImages() {

        if (!isStarted()) return

        AlertDialog.Builder(this)
                .setMessage(R.string.chat__partner_unable_to_receive_images)
                .setPositiveButton(R.string.general__ok, null)
                .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                presenter.onBluetoothEnabled()
            } else {
                presenter.onBluetoothEnablingFailed()
            }
        } else {

            EasyImage.handleActivityResult(requestCode, resultCode, data, this, object : DefaultCallback() {

                override fun onImagesPicked(imageFiles: MutableList<File>, source: EasyImage.ImageSource?, type: Int) {
                    if (imageFiles.isNotEmpty()) {
                        presenter.sendFile(imageFiles[0])
                    }
                }

                override fun onImagePickerError(e: Exception?, source: EasyImage.ImageSource?, type: Int) {
                    Toast.makeText(this@ChatActivity, "${e?.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_images -> {
                ReceivedImagesActivity.start(this, deviceAddress)
                true
            }
            R.id.action_disconnect -> {
                presenter.disconnect()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {

        private const val REQUEST_ENABLE_BLUETOOTH = 101

        const val EXTRA_ADDRESS = "extra.address"
        private const val EXTRA_MESSAGE = "extra.message"
        private const val EXTRA_FILE_PATH = "extra.file_path"

        fun start(context: Context, address: String) {
            val intent: Intent = Intent(context, ChatActivity::class.java)
                    .putExtra(EXTRA_ADDRESS, address)
            context.startActivity(intent)
        }

        fun start(context: Context, address: String, message: String?, filePath: String?) {
            val intent: Intent = Intent(context, ChatActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .putExtra(EXTRA_ADDRESS, address)
                    .putExtra(EXTRA_MESSAGE, message)
                    .putExtra(EXTRA_FILE_PATH, filePath)
            context.startActivity(intent)
        }
    }
}
