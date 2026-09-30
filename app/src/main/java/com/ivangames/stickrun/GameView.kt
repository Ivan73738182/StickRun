package com.ivangames.stickrun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Размеры экрана
    private var screenW = 0f
    private var screenH = 0f

    // Высота платформ и земли
    private var groundY = 0f
    private var platformHeight = 0f

    // Текущая платформа (левая)
    private var currentPlatform = RectF()

    // Следующая платформа (правая)
    private var nextPlatform = RectF()

    // Стикман
    private var stickX = 0f
    private var stickY = 0f
    private val stickWidth = 20f
    private val stickHeight = 60f

    // Палка
    private var stickLength = 0f           // текущая длина палки
    private var stickGrowing = false       // растёт ли палка
    private var stickFalling = false       // падает ли палка
    private var stickAngle = 90f           // угол наклона (90° = вертикально)
    private val stickGrowSpeed = 8f        // скорость роста
    private val stickFallSpeed = 8f        // скорость падения

    // Состояние игры
    private enum class State { WAITING, GROWING, FALLING, WALKING, GAME_OVER }
    private var state = State.WAITING

    // Счёт
    private var score = 0

    // Стикман идёт
    private var walkProgress = 0f
    private val walkSpeed = 0.05f

    // Краски
    private val bgPaint = Paint().apply { color = Color.parseColor("#1A1A2E") }
    private val groundPaint = Paint().apply { color = Color.parseColor("#0F3460") }
    private val platformPaint = Paint().apply { color = Color.parseColor("#E94560") }
    private val stickPaint = Paint().apply { color = Color.parseColor("#FFFFFF") }
    private val stickmanPaint = Paint().apply { color = Color.parseColor("#FFFFFF") }
    private val scorePaint = Paint().apply {
        color = Color.WHITE
        textSize = 80f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    init {
        setupGame()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat()
        screenH = h.toFloat()
        groundY = screenH * 0.75f
        platformHeight = screenH * 0.05f
        setupGame()
    }

    private fun setupGame() {
        if (screenW == 0f) return

        // Первая платформа — слева
        val firstWidth = screenW * 0.25f
        currentPlatform.set(
            0f, groundY,
            firstWidth, groundY + platformHeight
        )

        // Следующая платформа — справа
        spawnNextPlatform()

        // Стикман стоит на первой платформе
        stickX = currentPlatform.left + currentPlatform.width() / 2f - stickWidth / 2f
        stickY = groundY - stickHeight

        stickLength = 0f
        stickAngle = 90f
        state = State.WAITING
        score = 0
        walkProgress = 0f

        invalidate()
    }

    private fun spawnNextPlatform() {
        // Случайная ширина и позиция для следующей платформы
        val minWidth = screenW * 0.15f
        val maxWidth = screenW * 0.30f
        val width = Random.nextFloat() * (maxWidth - minWidth) + minWidth

        // Случайное расстояние между платформами
        val minGap = screenW * 0.15f
        val maxGap = screenW * 0.40f
        val gap = Random.nextFloat() * (maxGap - minGap) + minGap

        val nextLeft = currentPlatform.right + gap
        val nextRight = nextLeft + width

        nextPlatform.set(
            nextLeft, groundY,
            nextRight, groundY + platformHeight
        )
    }
    // ============ РИСОВАНИЕ ============

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Фон
        canvas.drawColor(Color.parseColor("#1A1A2E"))

        // Земля (нижняя часть)
        canvas.drawRect(
            0f, groundY + platformHeight,
            screenW, screenH,
            groundPaint
        )

        // Текущая платформа
        canvas.drawRect(currentPlatform, platformPaint)

        // Следующая платформа
        canvas.drawRect(nextPlatform, platformPaint)

        // Палка (если растёт или падает)
        if (state == State.GROWING || state == State.FALLING || state == State.WALKING) {
            drawStick(canvas)
        }

        // Стикман
        drawStickman(canvas)

        // Счёт
        canvas.drawText(score.toString(), screenW / 2f, 120f, scorePaint)

        // Обновляем физику и перерисовываем
        update()
        invalidate()
    }

    private fun drawStick(canvas: Canvas) {
        // Палка растёт от стикмана
        val pivotX = stickX + stickWidth / 2f
        val pivotY = groundY

        // Определяем конец палки
        val angleRad = Math.toRadians(stickAngle.toDouble())
        val endX = pivotX + (stickLength * Math.cos(angleRad)).toFloat()
        val endY = pivotY - (stickLength * Math.sin(angleRad)).toFloat()

        stickPaint.strokeWidth = 6f
        stickPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(pivotX, pivotY, endX, endY, stickPaint)
    }

    private fun drawStickman(canvas: Canvas) {
        // Тело
        val bodyLeft = stickX
        val bodyTop = stickY
        val bodyRight = stickX + stickWidth
        val bodyBottom = stickY + stickHeight
        canvas.drawRect(bodyLeft, bodyTop, bodyRight, bodyBottom, stickmanPaint)

        // Голова
        val headRadius = stickWidth * 0.9f
        val headCx = stickX + stickWidth / 2f
        val headCy = stickY - headRadius
        canvas.drawCircle(headCx, headCy, headRadius, stickmanPaint)

        // Глазки (две чёрные точки)
        val eyePaint = Paint().apply { color = Color.BLACK }
        val eyeY = headCy - headRadius * 0.2f
        canvas.drawCircle(headCx - headRadius * 0.3f, eyeY, 3f, eyePaint)
        canvas.drawCircle(headCx + headRadius * 0.3f, eyeY, 3f, eyePaint)
    }

    // ============ ФИЗИКА ============

    private fun update() {
        when (state) {
            State.GROWING -> {
                // Палка растёт
                stickLength += stickGrowSpeed
            }

            State.FALLING -> {
                // Палка падает (поворачивается вправо)
                stickAngle -= stickFallSpeed
                if (stickAngle <= 0f) {
                    stickAngle = 0f
                    stickFalling = false
                    checkLanding()
                }
            }

            State.WALKING -> {
                // Стикман идёт вправо
                walkProgress += walkSpeed
                if (walkProgress >= 1f) {
                    walkProgress = 1f
                    finishWalk()
                }
            }

            else -> {}
        }
    }

    private fun checkLanding() {
        // Куда попала палка
        val pivotX = stickX + stickWidth / 2f
        val landingX = pivotX + stickLength

        // Проверяем, попали ли на следующую платформу
        if (landingX >= nextPlatform.left && landingX <= nextPlatform.right) {
            // Успех — идём на следующую платформу
            state = State.WALKING
            walkProgress = 0f
        } else {
            // Мимо — падаем
            state = State.GAME_OVER
            gameOver()
        }
    }

    private fun finishWalk() {
        // Стикман оказался на новой платформе
        val oldPlatform = RectF(currentPlatform)
        val landingX = oldPlatform.left + oldPlatform.width() / 2f + stickLength

        // Перемещаем стикмана на новую платформу
        stickX = nextPlatform.left + (landingX - nextPlatform.left).coerceIn(
            nextPlatform.left - stickX,
            nextPlatform.right - nextPlatform.left - stickWidth
        )
        stickX = nextPlatform.left + 10f

        // Обновляем платформы
        val shiftAmount = nextPlatform.left - screenW * 0.15f

        currentPlatform = RectF(
            nextPlatform.left - shiftAmount,
            nextPlatform.top,
            nextPlatform.right - shiftAmount,
            nextPlatform.bottom
        )
        nextPlatform = RectF(
            nextPlatform.left - shiftAmount,
            nextPlatform.top,
            nextPlatform.right - shiftAmount,
            nextPlatform.bottom
        )

        // Сдвигаем, чтобы текущая платформа была слева
        val newShift = currentPlatform.left
        currentPlatform.offset(-newShift, 0f)
        nextPlatform.offset(-newShift, 0f)

        stickX = currentPlatform.left + 10f

        // Сбрасываем палку
        stickLength = 0f
        stickAngle = 90f
        walkProgress = 0f

        // Счёт
        score++

        // Создаём новую платформу справа
        spawnNextPlatform()

        state = State.WAITING
    }

    private fun gameOver() {
        // Стикман падает (можно анимировать падение)
        stickY += 5f
        if (stickY > screenH + 200f) {
            // Рестарт
            setupGame()
        }
    }

    // ============ ТАП ============

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.WAITING) {
                    // Начинаем растить палку
                    state = State.GROWING
                    stickLength = 0f
                    stickAngle = 90f
                }
            }
            MotionEvent.ACTION_UP -> {
                if (state == State.GROWING) {
                    // Отпустили — палка падает
                    state = State.FALLING
                    stickFalling = true
                }
            }
        }
        return true
    }
}
