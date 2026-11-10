package com.imesh.streamlite

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicLong

/** Total bytes downloaded from the network in this app session. */
object NetStats {
    val bytes = AtomicLong(0)
}

/** Counts real network bytes and forwards everything to the bandwidth meter. */
class CountingTransferListener(private val delegate: TransferListener?) : TransferListener {

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        delegate?.onTransferInitializing(source, dataSpec, isNetwork)
    }

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        delegate?.onTransferStart(source, dataSpec, isNetwork)
    }

    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int
    ) {
        if (isNetwork) NetStats.bytes.addAndGet(bytesTransferred.toLong())
        delegate?.onBytesTransferred(source, dataSpec, isNetwork, bytesTransferred)
    }

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        delegate?.onTransferEnd(source, dataSpec, isNetwork)
    }
}
