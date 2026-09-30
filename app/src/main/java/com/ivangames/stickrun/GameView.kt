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

    // Высота земли
    private var groundY = 0f
    private var platformHeight = 0f

    // Текущая платформа (на которой стоит стикман)
    private var currentPlatform = RectF()

    // Следующая платформа
    private var nextPlatform = RectF()

    // Стикман
    private var stickX = 0f
    private var stickY = 0f
    private val stickWidth = 24f
    private val stickHeight = 70f

    // Палка
    private var stickLength = 0f
    private var stickAngle = 90f
    private val stickGrowSpeed = 10f
    private val stickFallSpeed = 10f

    // Состояние
    private enum class State { WAITING, GROWING, FALLING, WALKING, FALLING_DOWN, GAME_OVER }
    private var state = State.WAITING

    // Счёт
    private var score = 0

    // Анимация ходьбы
    private var walkStartX = 0f
    private var walkEndX = 0f
    private var walkProgress = 0f
    private val walkSpeed = 0.06f

    // Анимация падения стикмана
    private var fallTimer = 0

    // Краски
    private val bgPaint = Paint().apply { color = Color.parseColor("#1A1A2E") }
    private val groundPaint = Paint().apply { color = Color.parseColor("#0F3460") }
    private val platformPaint = Paint().apply { color = Color.parseColor("#E94560") }
    private val stickPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val stickmanPaint = Paint().apply { color = Color.WHITE }
    private val eyePaint = Paint().apply { color = Color.BLACK }
    private val scorePaint = Paint().apply {
        color = Color.WHITE
        textSize = 90f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    init {
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat()
        screenH = h.toFloat()
        groundY = screenH * 0.72f
        platformHeight = screenH * 0.06f
        resetGame()
    }

    private fun resetGame() {
        if (screenW == 0f) return

        score = 0

        // Первая платформа — слева
        val firstWidth = screenW * 0.25f
        currentPlatform.set(0f, groundY, firstWidth, groundY + platformHeight)

        // Стикман на первой платформе
        stickX = currentPlatform.left + 20f
        stickY = groundY - stickHeight

        // Следующая платформа
        spawnNextPlatform()

        stickLength = 0f
        stickAngle = 90f
        state = State.WAITING
        walkProgress = 0f
        fallTimer = 0

        invalidate()
    }

    private fun spawnNextPlatform() {
        val minWidth = screenW * 0.15f
        val maxWidth = screenW * 0.30f
        val width = Random.nextFloat() * (maxWidth - minWidth) + minWidth

        val minGap = screenW * 0.20f
        val maxGap = screenW * 0.45f
        val gap = Random.nextFloat() * (maxGap - minGap) + minGap

        val nextLeft = currentPlatform.right + gap
        val nextRight = nextLeft + width

        nextPlatform.set(nextLeft, groundY, nextRight, groundY + platformHeight)
    }
    // ============ РИСОВАНИЕ ============

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Фон
        canvas.drawColor(Color.parseColor("#1A1A2E"))

        // Земля
        canvas.drawRect(0f, groundY + platformHeight, screenW, screenH, groundPaint)

        // Текущая платформа
        canvas.drawRect(currentPlatform, platformPaint)

        // Следующая платформа
        canvas.drawRect(nextPlatform, platformPaint)

        // Палка
        if (state == State.GROWING || state == State.FALLING || state == State.WALKING) {
            drawStick(canvas)
        }

        // Стикман
        drawStickman(canvas)

        // Счёт
        canvas.drawText(score.toString(), screenW / 2f, 140f, scorePaint)

        // Обновляем физику
        update()
        invalidate()
    }

    private fun drawStick(canvas: Canvas) {
        val pivotX = stickX + stickWidth / 2f
        val pivotY = groundY

        val angleRad = Math.toRadians(stickAngle.toDouble())
        val endX = pivotX + (stickLength * Math.cos(angleRad)).toFloat()
        val endY = pivotY - (stickLength * Math.sin(angleRad)).toFloat()

        canvas.drawLine(pivotX, pivotY, endX, endY, stickPaint)
    }

    private fun drawStickman(canvas: Canvas) {
        // Тело
        canvas.drawRect(stickX, stickY, stickX + stickWidth, stickY + stickHeight, stickmanPaint)

        // Голова
        val headRadius = stickWidth * 0.8f
        val headCx = stickX + stickWidth / 2f
        val headCy = stickY - headRadius
        canvas.drawCircle(headCx, headCy, headRadius, stickmanPaint)

        // Глазки
        val eyeY = headCy - 2f
        canvas.drawCircle(headCx - headRadius * 0.3f, eyeY, 3.5f, eyePaint)
        canvas.drawCircle(headCx + headRadius * 0.3f, eyeY, 3.5f, eyePaint)
    }

    // ============ ФИЗИКА ============

    private fun update() {
        when (state) {
            State.GROWING -> {
                stickLength += stickGrowSpeed
            }

            State.FALLING -> {
                stickAngle -= stickFallSpeed
                if (stickAngle <= 0f) {
                    stickAngle = 0f
                    checkLanding()
                }
            }

            State.WALKING -> {
                walkProgress += walkSpeed
                if (walkProgress >= 1f) {
                    walkProgress = 1f
                    finishWalk()
                } else {
                    // Плавное перемещение стикмана
                    stickX = walkStartX + (walkEndX - walkStartX) * walkProgress
                }
            }

            State.FALLING_DOWN -> {
                fallTimer++
                // Стикман падает вниз
                stickY += 15f
                if (fallTimer > 40) {
                    state = State.GAME_OVER
                    postDelayed({ resetGame() }, 500)
                }
            }

            else -> {}
        }
    }

    private fun checkLanding() {
        val pivotX = stickX + stickWidth / 2f
        val landingX = pivotX + stickLength

        // Проверяем, попала ли палка на следующую платформу
        if (landingX >= nextPlatform.left && landingX <= nextPlatform.right) {
            // Успех — идём на следующую платформу
            state = State.WALKING
            walkProgress = 0f
            walkStartX = stickX
            walkEndX = nextPlatform.left + 20f
            score++
        } else {
            // Мимо — падаем
            state = State.FALLING_DOWN
            fallTimer = 0
        }
    }

    private fun finishWalk() {
        // Стикман оказался на новой платформе
        // Сдвигаем обе платформы влево так, чтобы новая была на месте старой
        val shiftAmount = nextPlatform.left - currentPlatform.left

        currentPlatform.offset(-shiftAmount, 0f)
        nextPlatform.offset(-shiftAmount, 0f)

        // Стикман стоит на новой текущей платформе
        stickX = currentPlatform.left + 20f
        stickY = groundY - stickHeight

        // Сбрасываем палку
        stickLength = 0f
        stickAngle = 90f
        walkProgress = 0f

        // Создаём новую платформу справа
        spawnNextPlatform()

        state = State.WAITING
    }

    // ============ ТАП ============

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.WAITING) {
                    state = State.GROWING
                    stickLength = 0f
                    stickAngle = 90f
                }
            }
            MotionEvent.ACTION_UP -> {
                if (state == State.GROWING) {
                    state = State.FALLING
                }
            }
        }
        return true
    }
}
