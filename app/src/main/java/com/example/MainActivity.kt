package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random

// Theme colors
val NeonGreen = Color(0xFF39FF14)
val DarkSlate = Color(0xFF15171E)
val GridColor = Color(0xFF222530)
val BallColor = Color(0xFF00FFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PingPongScreen()
                }
            }
        }
    }
}

data class GameState(
    val ballPosition: Offset = Offset(0f, 0f),
    val ballVelocity: Offset = Offset(0f, 0f),
    val playerX: Float = 0f,
    val aiX: Float = 0f,
    val scorePlayer: Int = 0,
    val scoreAi: Int = 0,
    val isPlaying: Boolean = false,
    val gameOver: Boolean = false
)

class PingPongViewModel : ViewModel() {
    var state by mutableStateOf(GameState())
        private set
    
    var canvasSize by mutableStateOf(IntSize.Zero)
    
    private val paddleWidthDp = 100f
    private val paddleHeightDp = 20f
    private val ballRadiusDp = 12f
    
    private var paddleWidth = 0f
    private var paddleHeight = 0f
    private var ballRadius = 0f
    private var baseSpeed = 0f 
    
    private var lastTime = 0L
    
    fun resetTime() { lastTime = 0L }
    
    fun updateCanvasSize(size: IntSize, density: Float) {
        if (size.width == 0 || size.height == 0) return
        canvasSize = size
        paddleWidth = paddleWidthDp * density
        paddleHeight = paddleHeightDp * density
        ballRadius = ballRadiusDp * density
        baseSpeed = size.height * 0.7f 
        
        if (!state.isPlaying && !state.gameOver) {
            resetBall()
            state = state.copy(
                playerX = size.width / 2f,
                aiX = size.width / 2f,
                isPlaying = true
            )
        }
    }
    
    fun setPlayerX(x: Float) {
        val minX = paddleWidth / 2
        val maxX = canvasSize.width - paddleWidth / 2
        state = state.copy(playerX = x.coerceIn(minX, maxX))
    }
    
    fun resetBall() {
        val startX = canvasSize.width / 2f
        val startY = canvasSize.height / 2f
        val startDirectionX = if (Random.nextBoolean()) 1f else -1f
        val startDirectionY = if (state.scoreAi > state.scorePlayer) 1f else -1f 
        
        state = state.copy(
            ballPosition = Offset(startX, startY),
            ballVelocity = Offset(baseSpeed * 0.4f * startDirectionX, baseSpeed * 0.8f * startDirectionY),
            isPlaying = true
        )
    }
    
    fun resetGame() {
        state = GameState()
        resetTime()
    }
    
    fun logicStep(timeNanos: Long) {
        if (!state.isPlaying || canvasSize == IntSize.Zero) return
        if (lastTime == 0L) {
            lastTime = timeNanos
            return
        }
        val dt = ((timeNanos - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f) 
        lastTime = timeNanos
        
        var newX = state.ballPosition.x + state.ballVelocity.x * dt
        var newY = state.ballPosition.y + state.ballVelocity.y * dt
        var velX = state.ballVelocity.x
        var velY = state.ballVelocity.y
        
        if (newX - ballRadius < 0) {
            newX = ballRadius
            velX = -velX
        } else if (newX + ballRadius > canvasSize.width) {
            newX = canvasSize.width - ballRadius
            velX = -velX
        }
        
        val pyOffset = paddleHeight * 2
        val playerY = canvasSize.height - pyOffset
        val aiY = pyOffset
        
        if (state.ballVelocity.y > 0 && newY + ballRadius >= playerY - paddleHeight / 2) {
            if (newX > state.playerX - paddleWidth / 2 - ballRadius && newX < state.playerX + paddleWidth / 2 + ballRadius) {
                newY = playerY - paddleHeight / 2 - ballRadius
                velY = -velY * 1.05f 
                val hitFactor = (newX - state.playerX) / (paddleWidth / 2)
                velX += hitFactor * baseSpeed * 0.5f
            }
        }
        
        if (state.ballVelocity.y < 0 && newY - ballRadius <= aiY + paddleHeight / 2) {
            if (newX > state.aiX - paddleWidth / 2 - ballRadius && newX < state.aiX + paddleWidth / 2 + ballRadius) {
                newY = aiY + paddleHeight / 2 + ballRadius
                velY = -velY * 1.05f
                val hitFactor = (newX - state.aiX) / (paddleWidth / 2)
                velX += hitFactor * baseSpeed * 0.5f
            }
        }
        
        val maxSpeed = baseSpeed * 2.0f
        if (abs(velY) > maxSpeed) velY = maxSpeed * kotlin.math.sign(velY)
        if (abs(velX) > maxSpeed) velX = maxSpeed * kotlin.math.sign(velX)
        
        if (abs(velY) < baseSpeed * 0.2f) {
            velY = baseSpeed * 0.2f * kotlin.math.sign(velY.takeIf { it != 0f } ?: 1f)
        }
        
        var scoreP = state.scorePlayer
        var scoreAi = state.scoreAi
        var playing = true
        
        if (newY > canvasSize.height) {
            scoreAi++
            playing = false
        } else if (newY < 0) {
            scoreP++
            playing = false
        }
        
        var aiNewX = state.aiX
        if (velY < 0) { 
            val diff = newX - state.aiX
            val aiReactionSpeed = baseSpeed * 0.55f * dt
            if (abs(diff) > aiReactionSpeed) {
                aiNewX += aiReactionSpeed * kotlin.math.sign(diff)
            } else {
                aiNewX = newX
            }
        } else {
            val diff = (canvasSize.width / 2f) - state.aiX
            val returnSpeed = baseSpeed * 0.2f * dt
            if (abs(diff) > returnSpeed) {
                aiNewX += returnSpeed * kotlin.math.sign(diff)
            }
        }
        val minAiX = paddleWidth / 2
        val maxAiX = canvasSize.width - paddleWidth / 2
        aiNewX = aiNewX.coerceIn(minAiX, maxAiX)
        
        state = state.copy(
            ballPosition = Offset(newX, newY),
            ballVelocity = Offset(velX, velY),
            aiX = aiNewX,
            scorePlayer = scoreP,
            scoreAi = scoreAi,
            isPlaying = playing,
            gameOver = scoreP >= 5 || scoreAi >= 5
        )
    }
}

@Composable
fun PingPongScreen(viewModel: PingPongViewModel = viewModel()) {
    val state = viewModel.state
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    
    LaunchedEffect(state.isPlaying, state.gameOver) {
        if (state.isPlaying && !state.gameOver) {
            while (isActive) {
                withFrameNanos { time ->
                    viewModel.logicStep(time)
                }
            }
        } else if (!state.isPlaying && !state.gameOver) {
            viewModel.resetTime()
            delay(1000)
            viewModel.resetBall()
        }
    }
    
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkSlate)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 64.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${state.scoreAi}",
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.1f)
                )
                Text(
                    text = "${state.scorePlayer}",
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen.copy(alpha = 0.1f)
                )
            }
            
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        viewModel.updateCanvasSize(size, density)
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pt = event.changes.firstOrNull()
                                if (pt != null) {
                                    viewModel.setPlayerX(pt.position.x)
                                }
                            }
                        }
                    }
            ) {
                if (viewModel.canvasSize == IntSize.Zero) return@Canvas
                
                val pw = 100f * density
                val ph = 20f * density
                val br = 12f * density
                val pyOffset = ph * 2
                
                drawLine(
                    color = GridColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 40f), 0f)
                )
                
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(state.aiX - pw / 2, pyOffset - ph / 2),
                    size = Size(pw, ph),
                    cornerRadius = CornerRadius(ph / 2, ph / 2)
                )
                
                drawRoundRect(
                    color = NeonGreen,
                    topLeft = Offset(state.playerX - pw / 2, size.height - pyOffset - ph / 2),
                    size = Size(pw, ph),
                    cornerRadius = CornerRadius(ph / 2, ph / 2)
                )
                
                drawCircle(
                    color = BallColor,
                    radius = br,
                    center = state.ballPosition
                )
            }
            
            if (state.gameOver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.scorePlayer >= 5) "لقد فزت!" else "الذكاء الاصطناعي فاز!",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = if (state.scorePlayer >= 5) NeonGreen else Color.White
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                viewModel.resetGame()
                                viewModel.updateCanvasSize(viewModel.canvasSize, density)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) {
                            Text(
                                "إلعب مرة أخرى",
                                color = DarkSlate,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
