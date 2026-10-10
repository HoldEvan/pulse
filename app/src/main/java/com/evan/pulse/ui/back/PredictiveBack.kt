package com.evan.pulse.ui.back

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SLIDE_SCRIM = 0.5f
private const val COVER_PARALLAX = 0.25f
private const val COVER_ALPHA_FALLOFF = 0.1f
private const val SETTLE_SPRING_DAMPING = 1f
private const val SETTLE_SPRING_STIFFNESS = 146f
private const val PROGRAMMATIC_DURATION_MS = 500
private const val MIN_RELEASE_PROGRESS = 0.001f

/** 松手时判定提交的最小进度占比 */
private const val COMMIT_RATIO = 0.3f

/** 松手时判定提交的最小前向速度（progress/秒） */
private const val COMMIT_VELOCITY = 0.5f

private val ProgrammaticEasing: Easing = SettleEasing(response = 0.8f, damping = 0.95f)

/** miuix NavSettleEasing 等价实现：二阶临界阻尼系统的单位阶跃响应 */
private class SettleEasing(response: Float, damping: Float) : Easing {
    private val w: Float
    private val r: Float
    private val c2: Float

    init {
        val omega = 2.0 * PI / response
        val k = omega * omega
        val c = damping * 4.0 * PI / response
        w = (sqrt(4.0 * k - c * c) / 2.0).toFloat()
        r = (-c / 2.0).toFloat()
        c2 = r / w
    }

    override fun transform(fraction: Float): Float {
        val t = fraction.toDouble()
        val decay = exp(r * t)
        return (decay * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat()
    }
}

/** 顶层页面位移对齐像素，避免拖影 */
private fun snapPx(offsetPx: Float): Float = offsetPx.roundToInt().toFloat()

@Composable
private fun rememberNavSystemCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val insets = view.rootWindowInsets
    val radiusPx = remember(context, view, insets) {
        val fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.takeIf { it > 0 }
        } else {
            null
        }
        fromInsets ?: bottomCornerRadiusFromResources(context)
    }
    return (radiusPx / density).dp
}

private fun bottomCornerRadiusFromResources(context: Context): Int {
    val id = context.resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
    return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
}

private fun leadingClipShape(radius: Dp, leadingIsStart: Boolean): Shape =
    if (leadingIsStart) {
        RoundedCornerShape(
            topStart = radius,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
            bottomStart = radius,
        )
    } else {
        RoundedCornerShape(
            topStart = 0.dp,
            topEnd = radius,
            bottomEnd = radius,
            bottomStart = 0.dp,
        )
    }

/**
 * miuix 风格侧滑返回容器（无缩放，顶页向右滑出并露出下层页面）。
 *
 * 使用官方 [PredictiveBackHandler]：手势进行中实时更新 [backProgress]（跟手动画）；
 * 手势结束后弹簧收敛到 1 再执行 [onBack]，或回弹到 0。
 *
 * 提交判定不盲信系统信号：部分机型会把已提交的侧滑误判为取消，因此除系统提交外，
 * 末进度或前向释放速度达阈值同样判定为提交，避免“返回后仍停留在原页面”。
 */
@Composable
fun MiuixPredictiveBack(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onBack: () -> Unit,
    previous: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cornerRadius = rememberNavSystemCornerRadius()
    val clipShapeStart = remember(cornerRadius) { leadingClipShape(cornerRadius, leadingIsStart = true) }

    val backProgress = remember { mutableFloatStateOf(0f) }
    val widthPx = remember { mutableFloatStateOf(1f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // 注意：必须传函数本身（rememberUpdatedState(onBack)），不能写成
    // rememberUpdatedState { onBack }——后者 value 是“返回 onBack 的 lambda”，
    // 调用 value() 只会得到 onBack 引用而不会执行，导致返回无效。
    val currentOnBack by rememberUpdatedState(onBack)

    val commitSpec: AnimationSpec<Float> = remember {
        spring(dampingRatio = SETTLE_SPRING_DAMPING, stiffness = SETTLE_SPRING_STIFFNESS)
    }
    val programmaticSpec: AnimationSpec<Float> = remember {
        tween(durationMillis = PROGRAMMATIC_DURATION_MS, easing = ProgrammaticEasing)
    }

    PredictiveBackHandler(enabled = enabled) { progressFlow ->
        settleJob?.cancel()
        settleJob = null

        var velocity = 0f
        var lastProgress = 0f
        var lastFrame = 0L
        var completed = false

        try {
            progressFlow.collect { event ->
                backProgress.floatValue = event.progress.coerceIn(0f, 1f)
                val elapsed = (event.frameTimeMillis - lastFrame).toFloat()
                if (lastFrame != 0L && elapsed >= 1f && elapsed <= 500f) {
                    velocity = (event.progress - lastProgress) / elapsed * 1000f
                }
                lastProgress = event.progress
                lastFrame = event.frameTimeMillis
            }
            completed = true
        } catch (_: CancellationException) {
            completed = false
        }

        val endProgress = backProgress.floatValue
        val releaseVelocity = velocity
        val shouldCommit =
            completed || endProgress >= COMMIT_RATIO || releaseVelocity >= COMMIT_VELOCITY

        // 在独立的 composition scope 中收敛，避免手势协程结束/取消时打断返回动画
        settleJob = scope.launch {
            if (shouldCommit) {
                if (endProgress < MIN_RELEASE_PROGRESS && releaseVelocity <= 0f) {
                    animate(0f, 1f, 0f, programmaticSpec) { value, _ ->
                        backProgress.floatValue = value.coerceIn(0f, 1f)
                    }
                } else {
                    animate(
                        endProgress.coerceAtLeast(MIN_RELEASE_PROGRESS),
                        1f,
                        releaseVelocity,
                        commitSpec,
                    ) { value, _ ->
                        backProgress.floatValue = value.coerceIn(0f, 1f)
                    }
                }
                backProgress.floatValue = 1f
                currentOnBack()
            } else {
                animate(endProgress, 0f, -releaseVelocity, commitSpec) { value, _ ->
                    backProgress.floatValue = value.coerceIn(0f, 1f)
                }
            }
            settleJob = null
            backProgress.floatValue = 0f
        }

        if (!completed) throw CancellationException("预测性返回手势被取消")
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            settleJob?.cancel()
            settleJob = null
            backProgress.floatValue = 0f
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (enabled) {
            // 下层：向左轻微视差并淡出
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { widthPx.floatValue = it.width.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer {
                        val depth = (1f - backProgress.floatValue).coerceIn(0f, 1f)
                        translationX = snapPx(-depth * COVER_PARALLAX * widthPx.floatValue)
                        alpha = 1f - COVER_ALPHA_FALLOFF * depth
                    }
            ) {
                previous()
            }

            // 遮罩：随进度加深
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = SLIDE_SCRIM * (1f - backProgress.floatValue).coerceIn(0f, 1f)
                    }
                    .background(Color.Black)
            )
        }

        // 顶层内容：始终显式赋值，避免条件分支残留上一帧位移
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { widthPx.floatValue = it.width.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    val p = backProgress.floatValue
                    translationX = snapPx(p * widthPx.floatValue)
                    val shouldClip = p > 0.001f && p < 0.999f && cornerRadius > 0.dp
                    clip = shouldClip
                    if (shouldClip) {
                        shape = clipShapeStart
                    }
                }
        ) {
            content()
        }
    }
}
