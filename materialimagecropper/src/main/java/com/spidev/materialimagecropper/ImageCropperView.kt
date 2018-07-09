package com.spidev.materialimagecropper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.support.annotation.ColorInt
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast

/**
 * Created by Carlos Leonardo Camilo Vargas Huamán on 8/13/17.
 *
 */

class ImageCropperView : View {

    /**
     * For making a movement on drawable image
     */
    private lateinit var gestureDetector: GestureDetector

    /**
     * For making a zoom in or zoom out on the drawable image
     */
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    /**
     *  For drawing a grid on the drawable image
     */
    private var gridDrawable = GridDrawable()

    /**
     * The current bitmap drawable -> current image
     */
    private var bitmapDrawable: BitmapDrawable? = null

    /**
     * Dimensions of the drawable image
     */
    private var bitmapWidth = 0f
    private var bitmapHeight = 0f

    /**
     * The animator for moving the drawable image to its initial position
     * when the user ACTION UP
     */
    private var mAnimator = ValueAnimator().apply {
        duration = 400
        setFloatValues(0f, 1f)
        interpolator = DecelerateInterpolator(0.25f)
    }

    /**
     * Dimensions of the view
     */
    private var viewWidth = 0f
    private var viewHeight = 0f

    /**
     * The value for scaling the bitmap drawable
     * by default scale is 1 for an square image
     */
    private var bitmapScale = DEFAULT_IMAGE_SCALE

    /**
     * The rectangle for handling the bounds of the drawable image
     */
    private val rectF = RectF()

    /**
     * The displacement of the image
     * we only need the left and top because the others can be calculated from these
     */
    private var bitmapDisplacementInLeft = 0f
    private var bitmapDisplacementInTop = 0f

    /**
     * Variables to scale the image and to return to it's initial scale through an animator
     */
    private var mScaleFocusX = 0f
    private var mScaleFocusY = 0f

    private var gridRectF = RectF()

    private var DEFAULT_CENTER_SCALE_TYPE = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize(attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(attrs, defStyleAttr, 0)
    }

    private fun initialize(attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ImageCropperView, defStyleAttr, defStyleRes)
        val gridLineBorderColor = a.getColor(R.styleable.ImageCropperView_gridLineBorderColor, Color.GREEN)
        val gridLineColor = a.getColor(R.styleable.ImageCropperView_gridLineColor, Color.GRAY)
        val gridScaleType = a.getInteger(R.styleable.ImageCropperView_gridScaleType, DEFAULT_CENTER_SCALE_TYPE)
        val gridLineStrokeWidth = a.getInteger(R.styleable.ImageCropperView_gridLineStrokeWidth, 1)
        val gridLineBorderStrokeWidth = a.getInteger(R.styleable.ImageCropperView_gridLineBorderStrokeWidth, 1)
        a.recycle()

        //SettingUp GridDrawable
        setGridLineColor(gridLineColor)
        setGridBorderLineColor(gridLineBorderColor)
        setGridLineStrokeWidth(gridLineStrokeWidth)
        setGridLineBorderStrokeWidth(gridLineBorderStrokeWidth)

        //SettingUp Listeners, We make sure that listeners are initialized at this point.
        mAnimator.addUpdateListener(onSettlingAnimatorUpdateListener)
        gestureDetector = GestureDetector(context, onGestureListener)
        scaleGestureDetector = ScaleGestureDetector(context, onScaleGestureListener)
        gridDrawable.callback = drawableCallback
    }

    fun setGridLineColor(@ColorInt color: Int) {
        gridDrawable.linePaint.color = color
    }

    fun setGridBorderLineColor(@ColorInt color: Int) {
        gridDrawable.lineBorderPaint.color = color
    }

    fun setGridLineStrokeWidth(lineStrokeWidth: Int) {
        gridDrawable.linePaint.strokeWidth = lineStrokeWidth.toFloat()
    }

    fun setGridLineBorderStrokeWidth(lineStrokeBorderWidth: Int) {
        gridDrawable.lineBorderPaint.strokeWidth = lineStrokeBorderWidth.toFloat()
    }

    /**
     * This function is executed by the user at each moment
     * @param bitmap The bitmap to be showed
     */
    fun setImageBitmap(bitmap: Bitmap) {
        this.bitmapWidth = bitmap.width.toFloat()
        this.bitmapHeight = bitmap.height.toFloat()

        Log.e("VIEW-WIDTH", "$viewWidth")
        Log.e("VIEW-HEIGHT", "$viewHeight")
        Log.e("IMAGE-WIDTH", "$bitmapWidth")
        Log.e("IMAGE-HEIGHT", "$bitmapHeight")
        bitmapDrawable = BitmapDrawable(context.resources, bitmap)
        centerTheScaledDrawableImage()
        refreshDrawable()
    }

    private fun refreshDrawable() {
        invalidate()
    }

    /**
     * This method adjusts the bitmap drawable to the view, and center the bitmap drawable
     * bitmap drawable -> current image
     */
    private fun centerTheScaledDrawableImage() {
        when {
        //width is bigger than height
            getBitmapDrawableRatio() >= 1f -> {
                bitmapScale = getBitmapScale(viewHeight, bitmapHeight)
                val scaledBitmapWidth = getScaledBitmapWidth(bitmapWidth, bitmapScale)
                val expansion = (scaledBitmapWidth - viewWidth) / 2
                bitmapDisplacementInLeft = -expansion
                bitmapDisplacementInTop = 0f
            }
        //width and height are equal
            getBitmapDrawableRatio() == 1f -> {
                bitmapDisplacementInLeft = 0f
                bitmapDisplacementInTop = 0f
            }
        //height is bigger than width
            else -> {
                bitmapScale = getBitmapScale(viewWidth, bitmapWidth)
                val scaledBitmapHeight = getScaledBitmapHeight(bitmapHeight, bitmapScale)
                val expansion = (scaledBitmapHeight - viewHeight) / 2
                bitmapDisplacementInLeft = 0f
                bitmapDisplacementInTop = -expansion
            }
        }
        Log.e("IMAGE-SCALE", "$bitmapScale")
        Log.e("IMAGE-WIDTH SCALED", "${getScaledBitmapWidth(bitmapWidth, bitmapScale)}")
        Log.e("IMAGE-HEIGHT SCALED", "${getScaledBitmapHeight(bitmapHeight, bitmapScale)}")
    }

    /**
     * In general, resolutions start takes form of width x height,
     * for calculating the aspect ratio just simplify the fraction, for instance:
     * 1920 x 1080 -> 16:9(aspect ratio) -> k = 120 and the division is 1.77777...(ratio)
     * 1.777777 indicates that width is bigger than height
     */
    private fun getBitmapDrawableRatio() = bitmapWidth / bitmapHeight

    /**
     * This method place the drawable image inside the view
     */
    private fun placeDrawableImageInTheCenter() {
        //TODO probably, here we can make the opposite functionality
    }

    private fun getBitmapScale(smallestSideOfView: Float, smallestSideOfImage: Float) =
            smallestSideOfView / smallestSideOfImage

    /**
     * This method scales the drawable image width
     */
    private fun getScaledBitmapWidth(bitmapWidth: Float, bitmapScale: Float) =
            bitmapWidth * bitmapScale

    /**
     * This method scales the drawable image height
     */
    private fun getScaledBitmapHeight(bitmapHeight: Float, bitmapScale: Float) =
            bitmapHeight * bitmapScale

    private fun updateBitmapDrawable() {
        Log.e("rectf-top", "$bitmapDisplacementInTop")
        rectF.left = bitmapDisplacementInLeft
        rectF.top = bitmapDisplacementInTop
        rectF.right = rectF.left + getScaledBitmapWidth(bitmapWidth, bitmapScale)
        rectF.bottom = rectF.top + getScaledBitmapHeight(bitmapHeight, bitmapScale)
    }

    private fun displayBitmapDrawable() {
        bitmapDrawable?.setBounds(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt())
    }

    /**
     * (1)
     * This method is called before onLayout method
     * @param widthMeasureSpec horizontal space requirement imposed by the PARENT
     * @param heightMeasureSpec vertical space requirement imposed by the PARENT
     * widthMeasureSpec and heightMeasureSpec are values imposed by the PARENT
     * for example:
     * XXHDPI -> 200dp x 200dp -> widthMeasureSpec 600px, heightMeasureSpec 600px
     *
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        //View Width and Height sizes of the PARENT, but in Pixel something like 640x480, 720x200
        val parentWidthSize = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeightSize = MeasureSpec.getSize(heightMeasureSpec)

        //Long number used for the setMeasuredDimension(,) for the ImageCropperView
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        //values for
        var targetWidth = 1
        var targetHeight = 1

        when (widthMode) {
            MeasureSpec.EXACTLY -> {
                Log.e("x-WIDTH EXACTLY", "WIDTH EXACTLY")
                targetWidth = parentWidthSize

                when (heightMode) {
                    MeasureSpec.EXACTLY -> {
                        Log.e("x-HEIGHT EXACTLY", "HEIGHT EXACTLY")
                        targetHeight = parentHeightSize
                    }
                    MeasureSpec.AT_MOST -> {
                        Log.e("x-HEIGHT AT_MOST", "HEIGHT AT_MOST")
                        targetWidth = parentWidthSize
                        targetHeight = parentWidthSize
                    }
                    MeasureSpec.UNSPECIFIED -> {
                        Log.e("x-HEIGHT UNSPECIFIED", "HEIGHT UNSPECIFIED")
                    }
                }
            }

            MeasureSpec.AT_MOST -> {
                Log.e("x-WIDTH AT_MOST", "WIDTH AT_MOST")
                when (heightMode) {
                    MeasureSpec.EXACTLY -> {
                        Log.e("x-HEIGHT EXACTLY", "HEIGHT EXACTLY")
                        // if we have a vertical line, wrap_content-match_parent
                        // set the all the height of the parent
                        // and the minimum between the width of the parent or the height of the parent x ratio
                        targetHeight = parentWidthSize
                        targetWidth = parentWidthSize

                    }
                    MeasureSpec.AT_MOST -> {
                        Log.e("x-HEIGHT AT_MOST", "HEIGHT AT_MOST")

                        val specRatio = parentWidthSize.toFloat() / parentHeightSize.toFloat()
                        Log.e("x-DEFAULT_RATIO", "DEFAULT_RATIO $DEFAULT_IMAGE_RATIO")
                        Log.e("x-DEFAULT_RATIO", "DEFAULT_RATIO $specRatio")

                        if (specRatio == DEFAULT_IMAGE_RATIO) {
                            targetWidth = parentWidthSize
                            targetHeight = parentHeightSize
                        } else if (specRatio > DEFAULT_IMAGE_RATIO) {
                            targetWidth = (targetHeight * DEFAULT_IMAGE_RATIO).toInt()
                            targetHeight = parentHeightSize
                        } else {
                            targetWidth = parentWidthSize
                            targetHeight = (targetWidth / DEFAULT_IMAGE_RATIO).toInt()
                        }

                    }
                    MeasureSpec.UNSPECIFIED -> {
                        Log.e("x-HEIGHT UNSPECIFIED", "HEIGHT UNSPECIFIED")
                    }
                }
            }

            MeasureSpec.UNSPECIFIED -> {
                Toast.makeText(context, "UNSPECIFIED", Toast.LENGTH_SHORT).show()
            }
        }

        //esto es para los valores en el preview de android studio
        setMeasuredDimension(targetWidth, targetHeight)
    }

    /**
     * (2)
     * This method is called before onDraw method
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        Log.e("x-onLayout", "onLayout")

        //Calculating width and height of the view
        viewHeight = right.toFloat() - left.toFloat()
        viewWidth = bottom.toFloat() - top.toFloat()
    }

    /**
     * (3)
     * This method is called after onLayout method
     * First: we're going to convert our current bitmap to drawable if we want to add any paint over
     * Don't forget that the invalidate() method call this method
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateBitmapDrawable()
        displayBitmapDrawable()
        if (bitmapDrawable != null) {
            bitmapDrawable?.draw(canvas)
            gridDrawable.draw(canvas)
        }
    }

    /**
     * This method draw the grid as a intersection between the view rect and the image drawable rect.
     * We cannot call this method directly in the onDraw() method because {@link GridDrawable.setBounds}
     * call an animator and internally call the {@link GridDrawable.invalidateSelf()} which call onDraw() again.
     */
    private fun displayGridDrawable(rectF: RectF) {
        gridRectF.set(rectF.left, rectF.top, rectF.right, rectF.bottom)
        gridRectF.intersect(0f, 0f, viewWidth, viewHeight)
        gridDrawable.setBounds(gridRectF.left.toInt(), gridRectF.top.toInt(), gridRectF.right.toInt(), gridRectF.bottom.toInt())
    }

    companion object {
        //TODO PODEMOS REDUCIR A MITAD LA ESCALA O 3 VECES SU MISMO TAMAÑO
        //TODO POR EJEMPLO SI TENEMOS UNA IAMGEN 200X 200 -> 100X 100 , OR 1080X1080 -> 540X540
        const val MINIMUM_ALLOWED_SCALE = 0.2F
        const val MAXIMUM_ALLOWED_SCALE = 3.0F
        const val MAXIMUM_OVER_SCALE = 0.7F
        //RATIO VALUE BY DEFAULT TO SPECIFY A SQUARE(1:1)
        const val DEFAULT_IMAGE_RATIO = 1f
        const val DEFAULT_IMAGE_SCALE = 1f
    }

    /**
     * We override onTouchEvent for handling movements action on drawable image
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {

        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {

                Log.e("ANIMATOR", "ANIMATOR $mAnimator.sta")
                mAnimator!!.start()
            }
        }
        return true
    }

    /**
     * These function stops the movement little by little when the user scroll the drawable image
     * @param dAtEachNewPoint The displacement at each new point as the user moves the drawable image
     * on the x-axis or y-axis.
     * @param overScroll The displacement of the drawable image, negative or positive
     * @return The new displacement at each new point
     */
    private fun applyOverScrollFix(dAtEachNewPoint: Float, overScroll: Float): Float {
        var newDAtEachNewPoint = dAtEachNewPoint
        // We divide the absolute value of overScroll between view width
        // for example: When drawable image has the same width like the view
        // overScroll -> 0 to 540
        // viewWidth -> 1080
        // offRatio -> 0.0 to 0.5
        val offRatio = Math.abs(overScroll) / viewWidth
        //basically we subtract the square root at each new point to reduce the movement
        newDAtEachNewPoint -= newDAtEachNewPoint * Math.sqrt(offRatio.toDouble()).toFloat()
        return newDAtEachNewPoint
    }

    /**
     * This method is called when the use scroll the drawable image and when the drawable image
     * return to it's position through animator.
     * This method evaluate four mains scenarios for example:
     * (1) If drawable image width is equals or smaller than view width
     * (2) If drawable image width is bigger than view width
     * (3) If drawable image left side is more to the right than left side of the view
     * (4) If drawable image right side is more to the left than right side of the view
     */
    private fun measureOverScrollX(): Float {

        // If drawable image width is equals or smaller than view width
        // Then we have to return the 'distance between the CENTER POINTS of the drawable image and the view'
        // only in the X-axis
        if (rectF.width() <= viewWidth) {
            return rectF.centerX() - viewWidth / 2
        }

        // If drawable image width is bigger than view width
        // Then we don't have any internal difference of the x-axis that's why we return 0
        if (rectF.left <= 0 && rectF.right >= viewWidth) {
            return 0f
        }

        // If drawable image left side is more to the right than left side of the view
        // Then we have internal difference between view left side and drawable left side
        // and we returned that difference
        if (rectF.left > 0) {
            return rectF.left
        }

        // If drawable image right side is more to the left than right side of the view
        // Then we have internal difference between view right side and drawable right side
        // and we returned that difference
        if (rectF.right < viewWidth) {
            return rectF.right - viewWidth
        }

        return 0f
    }

    /**
     * This method is called when the use scroll the drawable image and when the drawable image
     * return to it's position through animator.
     * This method evaluate four mains scenarios for example:
     * (1) If drawable image height is equals or smaller than view height
     * (2) If drawable image height is bigger than view height
     * (3) If drawable image top side is more to the bottom than top side of the view
     * (4) If drawable image bottom side is more to the top than bottom side of the view
     */
    private fun measureOverScrollY(): Float {

        // If drawable image height is equals or smaller than view height
        // Then we have to return the 'distance between the CENTER POINTS of the drawable image and the view'
        // only in the Y-axis
        if (rectF.height() <= viewHeight) {
            return rectF.centerY() - viewHeight / 2
        }

        // If drawable image height is bigger than view height
        // Then we don't have any internal difference of the y-axis that's why we return 0
        if (rectF.top <= 0 && rectF.bottom >= viewHeight) {
            return 0f
        }

        // If drawable image top side is more to the bottom than top side of the view
        // Then we have internal difference between view top side and drawable top side
        // and we returned that difference
        if (rectF.top > 0) {
            return rectF.top
        }

        // If drawable image bottom side is more to the top than bottom side of the view
        // Then we have internal difference between view bottom side and drawable bottom side
        // and we returned that difference
        if (rectF.bottom < viewHeight) {
            return rectF.bottom - viewHeight
        }

        return 0f
    }

    /**
     * Listener to handle the movement of the user's finger on the drawable image.
     */
    private var onGestureListener = object : GestureDetector.OnGestureListener {

        override fun onShowPress(e: MotionEvent?) {
        }

        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            return false
        }

        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            return false
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
            var mDistanceX = -distanceX
            var mDistanceY = -distanceY

            mDistanceX = applyOverScrollFix(mDistanceX, measureOverScrollX())
            mDistanceY = applyOverScrollFix(mDistanceY, measureOverScrollY())

            bitmapDisplacementInLeft += mDistanceX
            bitmapDisplacementInTop += mDistanceY

            displayGridDrawable(rectF)

            invalidate()
            return true
        }

        override fun onLongPress(e: MotionEvent?) {
        }
    }

    /**
     * This listener reacts when the user touch the drawable image with 2 or more fingers
     * with this listener we can handle the zoom in and zoom out of the image
     */
    private var onScaleGestureListener = object : ScaleGestureDetector.OnScaleGestureListener {
        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector?) {
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            //tiende a crecer
            //overScale = bitmapScale / 0.8
            val overScale = measureOverScale()

            //detector.scaleFactor -> when expanding -> 1.000
            //detector.scaleFactor -> when collapsing -> 0.9
            val scale = applyOverScaleFix(detector.scaleFactor, overScale)

            //Don't forget that focusX and focusY changes
            //detector.focusX and detector.focusY -> the focal point between each of the pointer forming the gesture
            mScaleFocusX = detector.focusX
            mScaleFocusY = detector.focusY
            setScaleKeepingFocus(bitmapScale * scale, mScaleFocusX, mScaleFocusY)

            invalidate()
            return true
        }
    }

    /**
     * This method keep the focus of the drawable image while it's scaling
     */
    private fun setScaleKeepingFocus(scale: Float, focusX: Float, focusY: Float) {
        updateBitmapDrawable()

        val focusRatioX = (focusX - rectF.left) / rectF.width()
        val focusRatioY = (focusY - rectF.top) / rectF.height()

        bitmapScale = scale

        updateBitmapDrawable()

        val scaledFocusX = rectF.left + focusRatioX * rectF.width()
        val scaledFocusY = rectF.top + focusRatioY * rectF.height()

        bitmapDisplacementInLeft += focusX - scaledFocusX
        bitmapDisplacementInTop += focusY - scaledFocusY

        invalidate()
    }

    /**
     * Returning the scale
     */
    private fun applyOverScaleFix(scaleFactor: Float, oveScale: Float): Float {
        var mScaleFactor = scaleFactor
        var newOverScale = oveScale
        if (newOverScale == 1f) {
            return mScaleFactor
        }

        if (newOverScale > 1) {
            newOverScale = 1f / newOverScale
        }

        var wentOverScaleRatio = (newOverScale - MAXIMUM_OVER_SCALE) / (1 - MAXIMUM_OVER_SCALE)

        if (wentOverScaleRatio < 0f) {
            wentOverScaleRatio = 0f
        }

        // 1 -> scale , 0 -> 1
        // scale * f(1) = scale
        // scale * f(0) = 1

        // f(1) = 1
        // f(0) = 1/scale

        mScaleFactor *= wentOverScaleRatio + (1 - wentOverScaleRatio) / mScaleFactor

        return mScaleFactor
    }

    /**
     * Nuestra imagen puede sobreescalarse un valor entre 0.83f
     * for instance:
     * esto hace que si nuestra scala inicial es 1.5 aumente hasta 1.8
     * 1.5 / 0.83 = 1.8
     */
    private fun measureOverScale(): Float {
        return when {
            bitmapScale < MINIMUM_ALLOWED_SCALE -> bitmapScale / MINIMUM_ALLOWED_SCALE
            bitmapScale > MAXIMUM_ALLOWED_SCALE -> bitmapScale / MAXIMUM_ALLOWED_SCALE
            else -> 1f
        }
    }

    /**
     * Listener for settling drawable image after user ACTION UP
     * We manage two main behaviors
     * (1)Return to the initial position of the image
     * (2)Return to the initial scale of the image
     */
    private var onSettlingAnimatorUpdateListener = ValueAnimator.AnimatorUpdateListener { animation ->

        val animatedValue = animation.animatedValue as Float

        //(1)We return to the initial position of the drawable image*
        val overScrollX = measureOverScrollX()
        val overScrollY = measureOverScrollY()
        bitmapDisplacementInLeft -= overScrollX * animatedValue
        bitmapDisplacementInTop -= overScrollY * animatedValue

        // Log.e("mDisplayDrawableTop ","mDisplayDrawableTop " +mDisplayDrawableTop)
        //(2)We return to the initial scale of the drawable image*
        val overScale = measureOverScale()
        val targetScale = bitmapScale / overScale
        val newScale = (1 - animatedValue) * bitmapScale + animatedValue * targetScale

        setScaleKeepingFocus(newScale, mScaleFocusX, mScaleFocusY)
        displayGridDrawable(rectF)
        invalidate()
    }

    /**
     * This method is necessary if we want to create an animated drawable that extends {@Drawable}
     * Please read the official documentation
     * https://developer.android.com/reference/android/graphics/drawable/Drawable.Callback.html
     */
    private var drawableCallback = object : Drawable.Callback {
        override fun unscheduleDrawable(who: Drawable?, what: Runnable?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun scheduleDrawable(who: Drawable?, what: Runnable?, `when`: Long) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun invalidateDrawable(who: Drawable?) {
            invalidate()
        }
    }

    fun cropImageAndResize(croppedBitmapCallback: CroppedBitmapCallback) {

        val croppedImageWidth: Int
        val croppedImageHeight: Int

        val croppedBitmapDisplacementInLeft: Float
        val croppedBitmapDisplacementInTop: Float

        when {
        //width is bigger than height
            getBitmapDrawableRatio() >= 1f -> {
                croppedImageWidth = this.bitmapDrawable!!.bitmap.height
                croppedImageHeight = this.bitmapDrawable!!.bitmap.height
                croppedBitmapDisplacementInLeft = bitmapDisplacementInLeft / bitmapScale
                croppedBitmapDisplacementInTop = 0f
            }
        //width and height are equal
            getBitmapDrawableRatio() == 1f -> {
                croppedImageWidth = 0
                croppedImageHeight = 0
                croppedBitmapDisplacementInLeft = 0f
                croppedBitmapDisplacementInTop = 0f
            }
        //height is bigger than width
            else -> {
                croppedImageWidth = this.bitmapDrawable!!.bitmap.width
                croppedImageHeight = this.bitmapDrawable!!.bitmap.width
                croppedBitmapDisplacementInLeft = 0f
                croppedBitmapDisplacementInTop = bitmapDisplacementInTop / bitmapScale
            }
        }

        Log.e("CROP-y","${Math.abs(croppedBitmapDisplacementInTop.toInt())}")
        Log.e("CROP-HEIGHT","$croppedImageHeight")
        Log.e("CROP-BITMAP-HEIGHT","${this.bitmapDrawable!!.bitmap.height}")

        val bitmap = Bitmap.createBitmap(
                this.bitmapDrawable!!.bitmap,
                Math.abs(croppedBitmapDisplacementInLeft.toInt()),
                Math.abs(croppedBitmapDisplacementInTop.toInt()),
                croppedImageWidth,
                croppedImageHeight)
        val path = FileUtils.saveToFile(true, bitmap)
        croppedBitmapCallback.onCroppedBitmapReady()
    }
}


