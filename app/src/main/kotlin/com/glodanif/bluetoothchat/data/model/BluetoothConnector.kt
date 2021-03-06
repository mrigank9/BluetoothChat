package com.glodanif.bluetoothchat.data.model

import android.bluetooth.BluetoothDevice
import android.support.annotation.VisibleForTesting
import android.support.test.espresso.IdlingResource
import com.glodanif.bluetoothchat.data.entity.Conversation
import com.glodanif.bluetoothchat.data.entity.TransferringFile
import java.io.File

interface BluetoothConnector {

    fun prepare()
    fun release()
    fun stop()
    fun restart()
    fun setOnConnectListener(listener: OnConnectionListener?)
    fun setOnPrepareListener(listener: OnPrepareListener?)
    fun setOnMessageListener(listener: OnMessageListener?)
    fun setOnFileListener(listener: OnFileListener?)
    fun connect(device: BluetoothDevice)
    fun sendMessage(message: String)
    fun sendFile(file: File)
    fun cancelFileTransfer()
    fun isConnected(): Boolean
    fun isConnectedOrPending(): Boolean
    fun isPending(): Boolean
    fun isConnectionPrepared(): Boolean
    fun getCurrentConversation(): Conversation?
    fun getTransferringFile(): TransferringFile?
    fun acceptConnection()
    fun rejectConnection()
    fun sendDisconnectRequest()
}
