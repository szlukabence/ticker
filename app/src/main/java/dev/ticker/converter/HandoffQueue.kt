package dev.ticker.converter

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot hand-over of an amount to Convert (from Camera, receipts or the selection card).
 * Unlike a SharedFlow it keeps a value sent before anyone collects, so a cold start still
 * lands the amount, and each value is delivered to exactly one collector.
 */
class HandoffQueue {
    private val channel = Channel<CameraHandoff>(Channel.CONFLATED)
    val flow: Flow<CameraHandoff> = channel.receiveAsFlow()
    fun send(h: CameraHandoff): Boolean = channel.trySend(h).isSuccess
}
