package me.sedlar.ctrl1.plugins.osrs

import me.sedlar.ctrl1.Ctrl1
import me.sedlar.ctrl1.api.plugin.OSRSPlugin
import me.sedlar.ctrl1.api.util.OpenCV
import me.sedlar.ctrl1.api.util.findFirstTemplate
import me.sedlar.ctrl1.api.util.toMat
import me.sedlar.ctrl1.api.util.toRect
import me.sedlar.ctrl1.native.getResourceImage
import org.opencv.core.Mat
import java.awt.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JFrame

class Sodoku : OSRSPlugin() {

    var board: SodokuBoard? = null

    override fun verify(): Boolean {
        this.board = null // reset data since this is a singleton instance

        val img = snapshot()

        SodokuBoard.create(img)?.let { board ->
            this.board = board
            println("Before:")
            board.print()
            board.solve()
            println("After:")
            board.print()
        }

        return board != null
    }

    override fun run() {
        board?.let {
            val solution = it.createImage()
            println("Created sodoku solution image, displaying...")
            showFrame(solution)
            println("Displayed solution")
        }
    }

    private fun showFrame(solution: BufferedImage) {
        val frame = JFrame("Sodoku Solution")
        val panel = object : Canvas() {
            override fun paint(g: Graphics) {
                super.paint(g)
                g.drawImage(solution, 0, 0, null)
            }
        }
        panel.preferredSize = Dimension(solution.width, solution.height)
        frame.add(panel)
        frame.pack()
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.isVisible = true
        frame.setLocationRelativeTo(null)
    }
}

fun modelArray(rune: String): Array<Mat> = arrayOf(
    OpenCV.mat("sodoku/${rune}_yellow.png"),
    OpenCV.mat("sodoku/${rune}_orange.png")
)

val sodokuNaming = arrayOf("Mind", "Fire", "Body", "Air", "Death", "Water", "Chaos", "Earth", "Law")

private val sodokuMapping by lazy {
    arrayOf(
        modelArray("mind"),
        modelArray("fire"),
        modelArray("body"),
        modelArray("air"),
        modelArray("death"),
        modelArray("water"),
        modelArray("chaos"),
        modelArray("earth"),
        modelArray("law")
    )
}

class SodokuBoard(
    mat: Mat,
    private var startX: Int,
    private var startY: Int,
    private var boardSize: Int,
    private var itemSize: Int,
    private var gapX: Int,
    private var gapY: Int
) {

    private val tiles = boardSize * boardSize

    val initialData: Array<IntArray>
    val data = Array(tiles) { IntArray(tiles) }

    init {
        var xOff = 0
        for (x in 0 until tiles) {
            for (y in 0 until tiles) {
                val gx = startX + xOff
                val gy = startY + (itemSize * y) + (gapY * y)
                val slot = Rectangle(gx, gy, itemSize, itemSize)
                val slotMat = mat.submat(slot.toRect())

                for (r in 0 until 9) {
                    var match = false
                    for (model in sodokuMapping[r]) {
                        if (slotMat.findFirstTemplate(model to 0.95) != null) {
                            match = true
                        }
                    }
                    if (match) {
                        data[x][y] = (r + 1) // 1-index
                        break
                    }
                }
            }
            xOff += itemSize + gapX
        }
        initialData = data.clone()
    }

    private fun check(num: Int, row: Int, col: Int): Boolean {
        val r: Int = row / boardSize * boardSize
        val c: Int = col / boardSize * boardSize
        for (i in data.indices) {
            if (data[row][i] == num || data[i][col] == num || data[r + i % boardSize][c + i / boardSize] == num) {
                return false
            }
        }
        return true
    }

    private fun guess(row: Int, col: Int): Boolean {
        val nextCol: Int = (col + 1) % data.size
        val nextRow = if (nextCol == 0) row + 1 else row
        try {
            if (data[row][col] != 0) return guess(nextRow, nextCol)
        } catch (e: ArrayIndexOutOfBoundsException) {
            return true
        }
        for (i in 1..data.size) {
            if (check(i, row, col)) {
                data[row][col] = i
                if (guess(nextRow, nextCol)) {
                    return true
                }
            }
        }
        data[row][col] = 0
        return false
    }

    fun solve() {
        for (x in 0 until tiles) {
            for (y in 0 until tiles) {
                if (data[x][y] == 0) {
                    guess(x, y)
                }
            }
        }
    }

    fun translate(x: Int, y: Int): Rectangle {
        return Rectangle(
            startX + (x * itemSize) + (x * gapX),
            startY + (y * itemSize) + (y * gapY),
            itemSize,
            itemSize
        )
    }

    fun assignLabel(value: Int): String {
        return if (value == 0) "" else sodokuNaming[value - 1] // 1-index
    }

    fun checkOddity(x: Int, y: Int): Boolean {
        return (x <= 2 && y <= 2) || (x in 3..5 && y in 3..5) || (x >= 6 && y <= 2) || (x <= 2 && y >= 6) || (x >= 6 && y >= 6)
    }

    fun assignImage(x: Int, y: Int): BufferedImage? {
        val label = assignLabel(data[x][y]).toLowerCase()
        return if (label.isEmpty()) {
            null
        } else {
            val suffix = if (checkOddity(x, y)) "orange" else "yellow"
            getResourceImage("/models/sodoku/${label}_${suffix}.png")
        }
    }

    fun createImage(): BufferedImage {
        val img = BufferedImage(
            (itemSize * tiles) + (gapX * tiles) - gapX,
            (itemSize * tiles) + (gapY * tiles) - gapY,
            BufferedImage.TYPE_INT_RGB
        )
        val g = img.createGraphics()

        val gray = Color(88, 88, 88)
        val orange = Color(253, 153, 7)
        val yellow = Color(233, 192, 1)

        g.color = gray
        g.fillRect(0, 0, img.width, img.height)

        for (x in 0 until tiles) {
            for (y in 0 until tiles) {
                val gx = (x * itemSize) + (x * gapX)
                val gy = (y * itemSize) + (y * gapY)

                val slotImg = assignImage(x, y)

                if (slotImg != null) {
                    g.drawImage(slotImg, gx, gy, null)
                } else {
                    g.color = if (checkOddity(x, y)) orange else yellow
                    g.fillRect(gx, gy, itemSize, itemSize)
                }
            }
        }

        return img
    }

    fun print() {
        for (row in data) {
            println(row.contentToString())
        }
    }

    companion object {
        fun create(img: BufferedImage): SodokuBoard? {
            val imgMat = img.toMat()

            var board: SodokuBoard? = null

            imgMat.findFirstTemplate(
                OpenCV.mat("sodoku/iface_start.png") to 0.95,
                OpenCV.mat("sodoku/iface_start2.png") to 0.95
            )?.let { startPos ->
                val gridStartX = startPos.x + 114
                val gridStartY = startPos.y + 4
                board = SodokuBoard(
                    imgMat,
                    gridStartX,
                    gridStartY,
                    3,
                    32,
                    5,
                    5
                )
            }

            return board
        }
    }
}