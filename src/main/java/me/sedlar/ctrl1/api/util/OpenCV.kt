package me.sedlar.ctrl1.api.util

import me.sedlar.ctrl1.native.getResourceImage
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.PixelGrabber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object OpenCV {

    fun mat(imgPath: String, format: Int): Mat {
        val img = getResourceImage("/models/$imgPath")
        val mat = img.toMat(format)
        return mat
    }

    fun mat(imgPath: String) = mat(imgPath, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)

    fun modelRange(imgDir: String, range: IntRange, weight: Double, format: Int): List<Pair<Mat, Double>> {
        val list: MutableList<Pair<Mat, Double>> = ArrayList()
        for (i in range) {
            list.add(mat("$imgDir/$i.png", format) to weight)
        }
        return list
    }

    fun cannyModelRange(imgDir: String, range: IntRange, weight: Double): List<Pair<Mat, Double>> {
        val list: MutableList<Pair<Mat, Double>> = ArrayList()
        for (i in range) {
            list.add(mat("$imgDir/$i.png", Imgcodecs.IMREAD_GRAYSCALE).canny() to weight)
        }
        return list
    }

    fun bwModelRange(imgDir: String, range: IntRange, weight: Double): List<Pair<Mat, Double>> {
        return modelRange(imgDir, range, weight, Imgcodecs.IMREAD_GRAYSCALE)
    }

    fun normModelRange(imgDir: String, range: IntRange, weight: Double): List<Pair<Mat, Double>> {
        return modelRange(imgDir, range, weight, Imgcodecs.IMREAD_UNCHANGED)
    }
}

fun Mat.canny(threshold: Double, ratio: Double): Mat {
    val edges = Mat()
    Imgproc.Canny(this, edges, threshold, threshold * ratio)
    return edges
}

fun Mat.canny(): Mat = canny(60.0, 3.0)

fun Mat.toImage(): BufferedImage {
    val mob = MatOfByte()
    Imgcodecs.imencode(".png", this, mob)
    return ImageIO.read(ByteArrayInputStream(mob.toArray()))
}

fun Mat.pixels(): IntArray {
    PixelGrabber(toImage(), 0, 0, width(), height(), true).let {
        it.grabPixels()
        return it.pixels as IntArray
    }
}

fun BufferedImage.toMat(format: Int = Imgcodecs.CV_LOAD_IMAGE_UNCHANGED): Mat {
    val data = ByteArrayOutputStream()
    ImageIO.write(this, "png", data)
    data.flush()
    val mat = Imgcodecs.imdecode(MatOfByte(*data.toByteArray()), format)
    if (mat.channels() == 3) {
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2RGBA)
    }
    return mat
}

private fun Mat.findTemplates(breakFirst: Boolean, vararg templates: Pair<Mat, Double>): List<Rectangle> {
    val results: MutableList<Rectangle> = ArrayList()

    for (templatePair in templates) {
        val template = templatePair.first
        val threshold = templatePair.second

        if (template.width() > this.width() || template.height() > this.height()) {
            continue
        }

        val result = Mat()

        Imgproc.matchTemplate(this, template, result, Imgproc.TM_CCOEFF_NORMED)
        Imgproc.threshold(result, result, 0.1, 1.0, Imgproc.THRESH_TOZERO)

        while (true) {
            val mml = Core.minMaxLoc(result)
            val pos = mml.maxLoc
            if (mml.maxVal >= threshold) {
                Imgproc.rectangle(
                    this, pos, Point(pos.x + template.cols(), pos.y + template.rows()),
                    Scalar(0.0, 255.0, 0.0), 1
                )
                Imgproc.rectangle(
                    result, pos, Point(pos.x + template.cols(), pos.y + template.rows()),
                    Scalar(0.0, 255.0, 0.0), -1
                )
                results.add(Rectangle(pos.x.toInt(), pos.y.toInt(), template.width(), template.height()))
                if (breakFirst) {
                    return results
                }
            } else {
                break
            }
        }
    }

    return results
}

fun Mat.findAllTemplates(vararg templates: Pair<Mat, Double>): List<Rectangle> {
    return this.findTemplates(false, *templates)
}

fun Mat.findFirstTemplate(vararg templates: Pair<Mat, Double>): Rectangle? {
    val result = findTemplates(true, *templates)
    return if (result.isNotEmpty()) result[0] else null
}

fun Rectangle.toRect(): Rect {
    return Rect(this.x, this.y, this.width, this.height)
}