package dev.ticker.converter.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.OutputTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ticker.converter.AppContainer
import dev.ticker.converter.core.model.CameraMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.ticker.converter.ocr.ImagePreprocessor
import dev.ticker.converter.ocr.OcrEngine
import dev.ticker.converter.ocr.PriceFrameAnalyzer
import dev.ticker.converter.ui.components.CurrencyPicker
import dev.ticker.converter.ui.theme.TickerColors
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableIntStateOf
import android.graphics.Matrix
import dev.ticker.converter.ui.receipt.ReceiptScreen
import dev.ticker.converter.ui.receipt.ReceiptViewModel
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.glow
import dev.ticker.converter.ui.theme.tile
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private const val BOX_W = 0.6f
private const val BOX_H = 0.15f
private const val BOX_CY = 0.44f
private const val RECEIPT_X = 0.1f
private const val RECEIPT_TOP = 0.13f
private const val RECEIPT_BOTTOM = 0.67f

@Composable
fun CameraRoute(container: AppContainer, onOpenConvert: () -> Unit) {
    val vm: CameraViewModel = viewModel {
        CameraViewModel(container.rates, container.settings, container.catalog, container.formatter, container.handoff::send)
    }
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) CameraScreen(vm, container, onOpenConvert)
    else PermissionScreen(asked, retry = { launcher.launch(Manifest.permission.CAMERA) })
}

@Composable
private fun PermissionScreen(asked: Boolean, retry: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CAMERA", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Ticker uses the camera to read prices on tags and convert them. Images are processed on your phone and never leave it.",
            color = TickerColors.Dim, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        NeonPillButton("ALLOW CAMERA", retry, icon = TickerIcons.Camera)
        if (asked) {
            Spacer(Modifier.height(10.dp))
            NeonPillButton("OPEN SETTINGS", {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            })
        }
    }
}

private enum class Picking { FROM, TO }

@OptIn(TransformExperimental::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CameraScreen(vm: CameraViewModel, container: AppContainer, onOpenConvert: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val engine = remember { OcrEngine(context.applicationContext) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val transformRef = remember { AtomicReference<OutputTransform?>(null) }
    val boxRef = remember { AtomicReference<RectF?>(null) }
    val frozenRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var picking by remember { mutableStateOf<Picking?>(null) }
    var receipt by remember { mutableStateOf<ReceiptJob?>(null) }
    var receiptCount by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    // ~8 MP: receipt text needs about 3000 px on the long edge (see RECEIPT_MAX_EDGE), and capping
    // it keeps a full-sensor 50–108 MP still from exhausting memory.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(3264, 2448), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                ).build(),
            )
            .build()
    }
    // One view model for every receipt; start() resets it, so a new receipt never shows the last one.
    val receiptVm: ReceiptViewModel = viewModel(key = "receipt") {
        ReceiptViewModel(container.rates, container.settings, container.catalog, container.formatter, container.handoff, "USD", "EUR")
    }
    // Live reading pauses while a receipt is open (both use the one OCR thread) and in RECEIPT mode.
    androidx.compose.runtime.SideEffect { frozenRef.set(state.frozen || receipt != null || state.mode == CameraMode.RECEIPT) }

    // The preview's transform must be read on the main thread; refresh it for the analyser.
    LaunchedEffect(previewView) {
        while (true) { transformRef.set(previewView.outputTransform); delay(500) }
    }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build(),
            )
            .build()
        analysis.setAnalyzer(executor, PriceFrameAnalyzer(
            engine, scope, transformRef::get, boxRef::get, frozenRef::get,
            debugDir = if (dev.ticker.converter.BuildConfig.DEBUG) java.io.File(context.cacheDir, "ocr-debug") else null,
        ) { tokens ->
            scope.launch(Dispatchers.Main) { vm.onTokens(tokens) }
        })
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
    }
    LaunchedEffect(state.torch, camera) { camera?.cameraControl?.enableTorch(state.torch) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            engine.close()
            executor.shutdown()
        }
    }

    // A photo from the gallery opens as a receipt (extras spec §5).
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) receipt = ReceiptJob(++receiptCount, ReceiptSource.Photo(uri))
    }
    val pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // The receipt screen opens only once the photo is taken, so it doesn't cover the viewfinder (and
    // invite lowering the phone) mid-shot.
    val snapReceipt = {
        if (!capturing) scope.launch {
            capturing = true
            val shot = runCatching { captureBitmap(imageCapture) }
            capturing = false
            receipt = ReceiptJob(++receiptCount, shot.fold({ ReceiptSource.Captured(it) }, { ReceiptSource.Failed }))
        }
    }
    val receiptMode = state.mode == CameraMode.RECEIPT

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val priceBox = RectF(wPx * (0.5f - BOX_W / 2), hPx * (BOX_CY - BOX_H / 2), wPx * (0.5f + BOX_W / 2), hPx * (BOX_CY + BOX_H / 2))
        boxRef.set(priceBox)
        // RECEIPT: a tall guide only; the whole photo is read and the paper is found in it.
        val box = if (receiptMode) RectF(wPx * RECEIPT_X, hPx * RECEIPT_TOP, wPx * (1 - RECEIPT_X), hPx * RECEIPT_BOTTOM) else priceBox

        AndroidView({ previewView }, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val scrim = if (receiptMode) Color(0x80000000) else Color(0xB3000000)
            drawRect(scrim, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, box.top))
            drawRect(scrim, Offset(0f, box.bottom), androidx.compose.ui.geometry.Size(size.width, size.height - box.bottom))
            drawRect(scrim, Offset(0f, box.top), androidx.compose.ui.geometry.Size(box.left, box.height()))
            drawRect(scrim, Offset(box.right, box.top), androidx.compose.ui.geometry.Size(size.width - box.right, box.height()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                drawIntoCanvas { c ->
                    val glow = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 6.dp.toPx()
                        color = TickerColors.Accent.copy(alpha = 0.6f).toArgb()
                        maskFilter = android.graphics.BlurMaskFilter(10.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                    c.nativeCanvas.drawRoundRect(box.left, box.top, box.right, box.bottom, 14.dp.toPx(), 14.dp.toPx(), glow)
                }
            }
            drawRoundRect(
                TickerColors.Accent, Offset(box.left, box.top), androidx.compose.ui.geometry.Size(box.width(), box.height()),
                CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()),
            )
        }

        // Top chips
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val chipShape = RoundedCornerShape(50)
            Row(
                Modifier.height(44.dp).clip(chipShape).background(Color(0xCC000000)).border(1.dp, Color(0xFF262626), chipShape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CodeChip(state.from.code, "Currency on the tag") { picking = Picking.FROM }
                Icon(TickerIcons.Arrow, contentDescription = "to", tint = TickerColors.Accent, modifier = Modifier.size(18.dp))
                CodeChip(state.to.code, "Convert into") { picking = Picking.TO }
            }
            NeonIconButton(TickerIcons.Convert, "Swap currencies", vm::onSwap, Modifier.padding(start = 6.dp), tint = TickerColors.Text, background = Color(0xCC000000))
            Spacer(Modifier.weight(1f))
            NeonIconButton(
                TickerIcons.Torch, if (state.torch) "Turn torch off" else "Turn torch on", vm::onToggleTorch,
                tint = if (state.torch) TickerColors.Accent else TickerColors.Text, glowing = state.torch, background = Color(0xCC000000),
            )
        }

        // Result card / hint under the box
        val cardTop = with(density) { (box.bottom + 16.dp.toPx()).toDp() }
        Box(Modifier.fillMaxWidth().offset(y = cardTop), contentAlignment = Alignment.TopCenter) {
            if (state.converted != null) {
                Column(
                    Modifier.glow(alpha = 0.3f).clip(Neon.TileShape).background(Color(0xE6061510))
                        .border(1.dp, Neon.ActiveBorder, Neon.TileShape)
                        .clickable(onClickLabel = "Open in Convert") { if (vm.onResultTapped()) onOpenConvert() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.converted ?: "", color = TickerColors.Accent, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge.merge(Neon.glowText()))
                    Text("${state.to.code} · ${state.readingText ?: ""}", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.labelSmall)
                    state.feeLabel?.let { Text("incl. $it fee", color = TickerColors.Dim, style = MaterialTheme.typography.labelSmall) }
                    if (state.frozen) Text("LOCKED · shutter reads again", color = TickerColors.Accent, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    when {
                        capturing -> "Hold still…"
                        receiptMode -> "Fit the whole receipt in the frame"
                        else -> "Point the box at a price"
                    },
                    Modifier.background(Color(0xCC000000), RoundedCornerShape(50)).border(1.dp, Color(0xFF262626), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = TickerColors.Text, style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Bottom controls: mode switch above the shutter, like a phone camera app.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModeSwitch(state.mode, vm::onSetMode)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                NeonIconButton(
                    TickerIcons.Gallery, "Open a receipt photo", pickPhoto,
                    tint = TickerColors.Text, size = 48.dp, background = Color(0xCC000000),
                )
                Shutter(
                    mode = state.mode,
                    locked = state.frozen,
                    ready = receiptMode || state.reading != null,
                    busy = capturing,
                    onClick = { if (vm.onShutter()) snapReceipt() },
                )
                // Keeps the shutter centred.
                Spacer(Modifier.size(48.dp))
            }
        }
    }

    picking?.let { which ->
        CurrencyPicker(
            catalog = container.catalog,
            exclude = emptySet(),
            onPick = { code -> if (which == Picking.FROM) vm.onPickFrom(code) else vm.onPickTo(code); picking = null },
            onDismiss = { picking = null },
        )
    }

    receipt?.let { job ->
        val rvm = receiptVm
        LaunchedEffect(job.id) {
            rvm.start(state.from.code, state.to.code)
            try {
                val bmp = when (val src = job.source) {
                    is ReceiptSource.Captured -> src.bitmap
                    is ReceiptSource.Photo -> withContext(Dispatchers.IO) { decodeScaled(context, src.uri, ImagePreprocessor.RECEIPT_MAX_EDGE) }
                    ReceiptSource.Failed -> error("capture failed")
                }
                val prepared = withContext(Dispatchers.Default) { ImagePreprocessor.prepareReceipt(bmp) }
                val lines = engine.recognizeLines(prepared).map { it.text }
                if (dev.ticker.converter.BuildConfig.DEBUG) android.util.Log.i("TickerReceipt", "${prepared.width}x${prepared.height} lines=$lines")
                rvm.onLines(lines)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                rvm.onFailed()
            }
        }
        ReceiptScreen(
            vm = rvm,
            catalog = container.catalog,
            onClose = { receipt = null },
            onOpenConvert = { receipt = null; onOpenConvert() },
            onTryAnother = { receipt = null; pickPhoto() },
            onRetake = { receipt = null; vm.onSetMode(CameraMode.RECEIPT) },
        )
    }
}

@Composable
private fun ModeSwitch(mode: CameraMode, onSet: (CameraMode) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(Color(0xCC000000)).border(1.dp, Color(0xFF262626), shape).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraMode.entries.forEach { m ->
            val active = m == mode
            Text(
                if (m == CameraMode.PRICE) "PRICE" else "RECEIPT",
                Modifier.clip(shape)
                    .then(if (active) Modifier.background(Color(0xFF0E2A1E)).border(1.dp, Neon.ActiveBorder, shape) else Modifier)
                    .semantics { selected = active }
                    .clickable(role = Role.Tab, onClickLabel = if (m == CameraMode.PRICE) "Read one price" else "Photograph a receipt") { onSet(m) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                color = if (active) TickerColors.Accent else TickerColors.Dim,
                style = MaterialTheme.typography.labelMedium.let { if (active) it.merge(Neon.glowText()) else it },
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

/**
 * PRICE: locks the live reading (dim until there is one); locked, it shows "read again".
 * RECEIPT: takes the photo.
 */
@Composable
private fun Shutter(mode: CameraMode, locked: Boolean, ready: Boolean, busy: Boolean, onClick: () -> Unit) {
    val label = when {
        mode == CameraMode.RECEIPT -> "Take receipt photo"
        locked -> "Read another price"
        else -> "Lock this price"
    }
    Box(
        Modifier.glow(alpha = 0.4f, blur = 16.dp, corner = 36.dp).size(72.dp).clip(CircleShape).background(Color(0xCC000000))
            .border(3.dp, TickerColors.Accent, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, enabled = !busy, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when {
            busy -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = TickerColors.Accent, strokeWidth = 3.dp)
            locked -> Icon(TickerIcons.Refresh, contentDescription = null, tint = TickerColors.Accent, modifier = Modifier.size(28.dp))
            else -> Box(Modifier.size(54.dp).clip(CircleShape).background(TickerColors.Accent.copy(alpha = if (ready) 0.95f else 0.3f)))
        }
    }
}

private sealed interface ReceiptSource {
    data class Captured(val bitmap: Bitmap) : ReceiptSource
    data class Photo(val uri: Uri) : ReceiptSource
    data object Failed : ReceiptSource
}

/** One receipt being read; the id keys its view model so each receipt starts fresh. */
private data class ReceiptJob(val id: Int, val source: ReceiptSource)

/** Full-resolution still of what the camera sees, upright. Decoded off the main thread. */
private suspend fun captureBitmap(capture: ImageCapture): Bitmap = suspendCancellableCoroutine { cont ->
    capture.takePicture(Dispatchers.Default.asExecutor(), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val result = runCatching {
                val raw = image.toBitmap()
                val deg = image.imageInfo.rotationDegrees
                if (deg == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
                    .also { raw.recycle() }
            }
            image.close()
            cont.resumeWith(result)
        }

        override fun onError(exception: ImageCaptureException) {
            cont.resumeWithException(exception)
        }
    })
}

@Composable
private fun CodeChip(code: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxHeight().clickable(onClickLabel = description, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Icon(TickerIcons.ChevronDown, contentDescription = null, tint = TickerColors.Dim, modifier = Modifier.padding(start = 2.dp).size(16.dp))
    }
}

private fun decodeScaled(context: Context, uri: Uri, maxEdge: Int): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val long = maxOf(info.size.width, info.size.height)
            if (long > maxEdge) {
                val f = maxEdge.toFloat() / long
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not decode image")
    }
