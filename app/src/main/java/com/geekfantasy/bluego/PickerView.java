package com.geekfantasy.bluego;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;


public class PickerView extends View {
    public static final String TAG = "PickerView";
    //minTextSize和text之间间距之比
    public static final float MARGIN_ALPHA = 1.90f;
    //自动回滚到中间的速度
    public static final byte SPEED = 50;
    public static final byte PERIOD = 10;
    public static final byte CODE = 0X10;
    private List<Item> mItemList;
    /**
     * 选中的位置，这个位置是mDataList的中心位置，一直不变
     */
    private int mCurrentSelected;
    private Paint mPaint;
    private float mMaxItemSize = 400;
    private float mMinItemSize = 280;
    private float mMaxItemAlpha = 255;
    private float mMinItemAlpha = 20;
    private int mViewHeight;
    private int mViewWidth;
    private float mLastDownY;
    //滑动的距离
    private float mMoveLen = 0;
    private boolean isInit = false;
    private onSelectListener mSelectListener;
    private volatile boolean isInAnimate;
    @SuppressLint("HandlerLeak")
    private Handler updateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage() is called");
            if (Math.abs(mMoveLen) < SPEED) {
                mMoveLen = 0;
                performSelect();
            } else {
                // 这里mMoveLen / Math.abs(mMoveLen)是为了保有mMoveLen的正负号，以实现上滚或下滚
                mMoveLen = mMoveLen - mMoveLen / Math.abs(mMoveLen) * SPEED;
                sendEmptyMessageDelayed(CODE, PERIOD);
            }
            invalidate();
        }
    };

    public PickerView(Context context) {
        this(context, null);
    }

    public PickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setOnSelectListener(onSelectListener listener) {
        mSelectListener = listener;
    }

    private void performSelect() {
        if (mSelectListener != null)
            mSelectListener.onSelect(mItemList.get(mCurrentSelected));
    }

    public void setData(List<Item> items) {
        Log.d(TAG, "setData() is called");
        if (items != null) {
            mItemList.addAll(items);
        }
        mCurrentSelected = mItemList.size() / 2;
        performSelect();
        invalidate();
    }

    public void setImageBadge(String tag, Integer imageBadgeID)
    {
        for (Item item: mItemList) {
            if(tag.equals(item.getTag())){
                item.setImageBadge(imageBadgeID);
            }
            else {
                item.setImageBadge(null);
            }
        }
    }

    private void moveHeadToTail() {
        Item head = mItemList.get(0);
        mItemList.remove(0);
        mItemList.add(head);
    }

    private void moveTailToHead() {
        Item tail = mItemList.get(mItemList.size() - 1);
        mItemList.remove(mItemList.size() - 1);
        mItemList.add(0, tail);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mViewHeight = getMeasuredHeight();
        mViewWidth = getMeasuredWidth();
        mMaxItemSize = mViewWidth / 3.5F;
        mMinItemSize = mViewWidth / 5.0F;

        Log.d(TAG, "onMessure() is called, viewHeight:" + mViewHeight + "viewWidth:" + mViewWidth);

        isInit = true;
        invalidate();
    }

    private void init() {
        Log.d(TAG, "init() is called");
        mItemList = new ArrayList<>();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Style.FILL);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint.setColor(getResources().getColor(R.color.teal_200));
//        mPaint.setTypeface(Typeface.create("eurostile", Typeface.NORMAL));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isInit) drawData(canvas);
    }

    private void drawData(Canvas canvas) {
        if (mItemList.isEmpty()) return;
        float scale = parabola(mViewHeight / 4.0f, mMoveLen);
        float size = (mMaxItemSize - mMinItemSize) * scale + mMinItemSize;
        float textSize = size/3.5f;
        mPaint.setTextSize(textSize);

        mPaint.setAlpha((int) ((mMaxItemAlpha - mMinItemAlpha) * scale + mMinItemAlpha));
        float x = (float) (mViewWidth / 2.0);
        float y = (float) (mViewHeight / 2.0  + mMoveLen);

        Bitmap pic = BitmapFactory.decodeResource(getContext().getResources(), mItemList.get(mCurrentSelected).getImageResourceId());
        Log.i(TAG, "Scale:" + scale + ", size:" + size + ", View H:" + mViewHeight + ", W:" + mViewWidth + ", MoveLen:" + mMoveLen);

        Matrix matrix = getScaleMatrix(size, pic.getHeight());
        Bitmap picScaled = Bitmap.createBitmap(pic, 0, 0, pic.getWidth(), pic.getHeight(), matrix, false);
        float picX = x - picScaled.getWidth()/2;
        float picY = y - picScaled.getHeight()/2;
        canvas.drawText(mItemList.get(mCurrentSelected).getTextLabel(), mViewWidth / 5.0f, y + textSize/2.0f, mPaint);
        canvas.drawBitmap(picScaled, picX, picY, mPaint);

        Integer imageBadgeId = mItemList.get(mCurrentSelected).getImageBadge();
        if(imageBadgeId != null){
            Bitmap picBadge = BitmapFactory.decodeResource(getContext().getResources(), imageBadgeId);
            Matrix matrixBadge = getScaleMatrix(picScaled.getWidth() / 4.0f, picBadge.getWidth());
            Bitmap picBadgeScaled = Bitmap.createBitmap(picBadge, 0, 0, picBadge.getWidth(), picBadge.getHeight(), matrixBadge, false);
            canvas.drawBitmap(picBadgeScaled, picX + picScaled.getWidth() - picBadgeScaled.getWidth() / 2.0f, picY - picBadgeScaled.getHeight() / 2.0f, mPaint);
        }

        // 绘制上方data
        for (int i = 1; (mCurrentSelected - i) >= 0; i++) {
            drawOtherData(canvas, i, -1);
        }
        // 绘制下方data
        for (int i = 1; (mCurrentSelected + i) < mItemList.size(); i++) {
            drawOtherData(canvas, i, 1);
        }
    }

    /**
     * @param canvas
     * @param position 距离mCurrentSelected的差值
     * @param type     1表示向下绘制，-1表示向上绘制
     */
    private void drawOtherData(Canvas canvas, int position, int type) {
        float d = (float) (MARGIN_ALPHA * mMinItemSize * position + type * mMoveLen);
        float scale = parabola(mViewHeight / 4.0f, d);
        float size = (mMaxItemSize - mMinItemSize) * scale + mMinItemSize;
        mPaint.setAlpha((int) ((mMaxItemAlpha - mMinItemAlpha) * scale + mMinItemAlpha));

        Bitmap pic = BitmapFactory.decodeResource(getContext().getResources(), mItemList.get(mCurrentSelected + type * position).getImageResourceId());
        float x = (float) (mViewWidth / 2.0);
        float y = (float) (mViewHeight / 2.0 + type * d);

        Log.d(TAG, "Scale:" + scale + ", d:" + d + ", size:" + size + ", MoveLen:" + mMoveLen );

        // draw scaled bitmap
        Matrix matrix = getScaleMatrix(size, pic.getHeight());
        Bitmap picScaled = Bitmap.createBitmap(pic, 0, 0, pic.getWidth(), pic.getHeight(), matrix, false);
        float picX = x - picScaled.getWidth()/2;
        float picY = y - picScaled.getHeight()/2;
        canvas.drawBitmap(picScaled, picX , picY, mPaint);

        Integer imageBadgeId = mItemList.get(mCurrentSelected + type * position).getImageBadge();
        if(imageBadgeId != null){
            Bitmap picBadge = BitmapFactory.decodeResource(getContext().getResources(), imageBadgeId);
            Matrix matrixBadge = getScaleMatrix(picScaled.getWidth() / 4.0f, picBadge.getWidth());
            Bitmap picBadgeScaled = Bitmap.createBitmap(picBadge, 0, 0, picBadge.getWidth(), picBadge.getHeight(), matrixBadge, false);
            canvas.drawBitmap(picBadgeScaled, picX + picScaled.getWidth() - picBadgeScaled.getWidth() / 2.0f, picY - picBadgeScaled.getHeight() / 2.0f, mPaint);
        }
    }

    private Matrix getScaleMatrix(float targetSize, float originalSize)
    {
        float picScale = targetSize / originalSize;
        Matrix matrix = new Matrix();
        matrix.postScale(picScale, picScale);
        return matrix;
    }

    /**
     * 抛物线
     *
     * @param zero 零点坐标
     * @param x    偏移量
     * @return scale
     */
    private float parabola(float zero, float x) {
        float f = (float) (1 - Math.pow(x / zero, 2));
        return f < 0 ? 0 : f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                doDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                doMove(event);
                break;
            case MotionEvent.ACTION_UP:
                doUp(event);
                break;
        }
        return true;
    }

    private void doDown(MotionEvent event) {
        Log.d(TAG, "doDown() is called");
        updateHandler.removeMessages(CODE);
        mLastDownY = event.getY();
    }

    private void doMove(MotionEvent event) {
        Log.d(TAG, "doMove() is called, even Y:" + event.getY());
        mMoveLen += (event.getY() - mLastDownY);
        if (mMoveLen > MARGIN_ALPHA * mMinItemSize / 2) {
            // 往下滑超过离开距离
            moveTailToHead();
            mMoveLen = mMoveLen - MARGIN_ALPHA * mMinItemSize;
        } else if (mMoveLen < -MARGIN_ALPHA * mMinItemSize / 2) {
            // 往上滑超过离开距离
            moveHeadToTail();
            mMoveLen = mMoveLen + MARGIN_ALPHA * mMinItemSize;
        }
        mLastDownY = event.getY();
        invalidate();
    }

    private void doUp(MotionEvent event) {
        Log.d(TAG, "doUp() is called");
        // 抬起手后mCurrentSelected的位置由当前位置move到中间选中位置
        if (Math.abs(mMoveLen) < 0.0001) {
            mMoveLen = 0;
            return;
        }
        //这段是为了修正瞬滑导致回滚较多现象，屏蔽亦可
        if (Math.abs(mMoveLen) > MARGIN_ALPHA * mMinItemSize / 2) {
            int m = (int) (Math.abs(mMoveLen) / (MARGIN_ALPHA * mMinItemSize / 2));
            for (int i = 0; i < m; i++) {
                if (mMoveLen > 0) moveTailToHead();
                else moveHeadToTail();
            }
            if (mMoveLen > 0) mMoveLen -= MARGIN_ALPHA * mMinItemSize * m / 2;
            else mMoveLen += MARGIN_ALPHA * mMinItemSize * m / 2;
        }
        updateHandler.removeMessages(CODE);
        updateHandler.sendEmptyMessage(CODE);
    }

    /**
     * @param up 是否向上滑动
     */
    public void autoScroll(boolean up) {
        Log.d(TAG, "autoScroll() is called");
        if (isInAnimate) return;
        isInAnimate = true;
        final float[] values = up ? new float[]{MARGIN_ALPHA * mMinItemSize + 1, 0} : new float[]{0, MARGIN_ALPHA * mMinItemSize + 1};
        ValueAnimator va = ValueAnimator.ofFloat(values);
        va.setDuration(100);
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = (float) animation.getAnimatedValue();
                doMove(MotionEvent.obtain(System.currentTimeMillis(), 0, 0, 0f, v, 0));
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                doUp(null);
                isInAnimate = false;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                doDown(MotionEvent.obtain(System.currentTimeMillis(), 0, 0, 0f, values[0], 0));
            }
        });
        va.start();
    }

    public interface onSelectListener {
        void onSelect(Item item);
    }

    public static class Item{
        private Integer imageResourceId;
        private Integer imageBadge;
        private String textLabel;
        private String tag;

        public Item(Integer imageResourceId, String textLabel, String tag)
        {
            this.imageResourceId  = imageResourceId;
            this.textLabel = textLabel;
            this.tag = tag;
            imageBadge = null;
        }

        public Integer getImageResourceId()
        {
            return imageResourceId;
        }

        public String getTextLabel()
        {
            return textLabel;
        }

        public String getTag()
        {
            return tag;
        }

        public void setImageBadge(Integer badgeResourceId){
            this.imageBadge = badgeResourceId;
        }

        public Integer getImageBadge(){
            return imageBadge;
        }
    }
}