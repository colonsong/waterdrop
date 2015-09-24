package tw.waterdrop.waterdrop.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.util.Log;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tw.waterdrop.waterdrop.R;
import tw.waterdrop.waterdrop.adapter.UploadBaseAdapter;
import tw.waterdrop.waterdrop.fragment.UploadPicFragment;


/**
 * Created by colon on 15/9/23.
 */
public class ImageWorker {

    private Context mContext;
    private static final String TAG = "LoadPicTask";
    private ImageCache imageCache;

    private Bitmap mLoadingBitmap;
    private List pictureList;
    protected Resources mResources;


    //捲動時暫停
    protected  boolean mPauseWork = false;
    private  final Object mPauseWorkLock = new Object();
    private static final float columnWidthDp = 100f;
    private static int columnWidthPixel;
    //fade image
    private static final int FADE_IN_TIME = 200;
    private boolean mFadeInBitmap = true;

    public static void setColumnWidthPixel(int size)
    {
        columnWidthPixel = size;
    }

    public ImageWorker(Context mContext , List pictureList, ImageCache imageCache)
    {
        this.mContext = mContext;
        this.pictureList = pictureList;
        this.imageCache = imageCache;
        this.mResources = mContext.getResources();

        this.columnWidthPixel =(int) (columnWidthDp * Pixel_dp.getDensity(mContext));
    }


    private class LoadPicTask extends AsyncTask<Object, Void, BitmapDrawable> {
        private final WeakReference<ImageView> imageViewReference;

        private String path;
        private Object mData;
        private BitmapFactory.Options options;

        public LoadPicTask(Object data, ImageView imageView) {
            mData  = data;
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<ImageView>(imageView);

        }

        // Decode image in background.
        @Override
        protected BitmapDrawable doInBackground(Object... params) {

            if(params == null)
            {
                return null;
            }
            synchronized (mPauseWorkLock) {
                while (mPauseWork && !isCancelled()) {
                    try {
                        mPauseWorkLock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }

            path = params[0].toString();
            String position = String.valueOf(params[1]);
            Bitmap bitmap = null;
            BitmapDrawable drawable = null;


            // If the image cache is available and this task has not been cancelled by another
            // thread and the ImageView that was originally bound to this task is still bound back
            // to this task and our "exit early" flag is not set then try and fetch the bitmap from
            // the cache
            // Wait here if work is paused and the task is not cancelled



            if (imageCache != null && !isCancelled() && getAttachedImageView() != null
                    ) {
                bitmap = imageCache.getBitmapFromDiskCache(position);
                if(bitmap == null)
                {

                }
                else
                {
                    drawable = new BitmapDrawable(mResources, bitmap);
                    Log.v(TAG,"DiskImage MyUploadImage"+ position);
                    imageCache.mMemoryCache.put(position,drawable);
                    return drawable;

                }
            }



            if (bitmap == null && !isCancelled() && getAttachedImageView() != null) {
                // bitmap = decodeSampledBitmapFromFile(path,360,360);

                options = new BitmapFactory.Options();
                //先取寬高
                options.inJustDecodeBounds = true;

                BitmapFactory.decodeFile(path, options);

                options.inScaled = true;
                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, columnWidthPixel, columnWidthPixel);
//options.inDither = true;
                //density size
                options.inDensity = options.outWidth;
                options.inTargetDensity = columnWidthPixel * options.inSampleSize;
                // Decode bitmap with inSampleSize set
                options.inJustDecodeBounds = false;


                bitmap = BitmapFactory.decodeFile(path, options);
                options.inBitmap = bitmap;

                if(bitmap != null)
                {

                    if (Utils.hasHoneycomb()) {
                        // Running on Honeycomb or newer, so wrap in a standard BitmapDrawable
                        drawable = new BitmapDrawable( bitmap);
                    } else {

                        // Running on Gingerbread or older, so wrap in a RecyclingBitmapDrawable
                        // which will recycle automagically
                        // drawable = new RecyclingBitmapDrawable(mResources, bitmap);
                    }

                    if (imageCache != null) {
                        imageCache.addBitmapToCache(position, drawable);
                    }

                }

            }




            return drawable;
        }


        /**
         * Once the image is processed, associates it to the imageView
         */
        @Override
        protected void onPostExecute(BitmapDrawable bitmap) {

            if (isCancelled()) {
                bitmap = null;

            }


            final ImageView imageView = getAttachedImageView();

            if (imageView != null && bitmap != null) {
                setImageDrawable(imageView, bitmap);

            }


        /*
        final ImageView imageView = getAttachedImageView();

        if (imageView != null && bitmap != null) {

           // final LoadPicTask bitmapWorkerTask =
             //       UploadBaseAdapter.getBitmapWorkerTask(imageView);

                imageView.setImageBitmap(bitmap);

        }


        if (imageViewReference != null && bitmap != null) {
            final ImageView imageView = imageViewReference.get();
            if (imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
        */

        }


        @Override
        protected void onCancelled(BitmapDrawable value) {
            super.onCancelled(value);
            synchronized (mPauseWorkLock) {
                mPauseWorkLock.notifyAll();
            }
        }


        /**
         * Called when the processing is complete and the final drawable should be
         * set on the ImageView.
         *
         * @param imageView
         * @param drawable
         */
        private void setImageDrawable(ImageView imageView, Drawable drawable) {
            if (mFadeInBitmap) {
                // Transition drawable with a transparent drawable and the final drawable
                final TransitionDrawable td =
                        new TransitionDrawable(new Drawable[] {
                                new ColorDrawable(android.R.color.transparent),
                                drawable
                        });
                // Set background to loading bitmap
                imageView.setBackgroundDrawable(
                        new BitmapDrawable(mResources, mLoadingBitmap));

                imageView.setImageDrawable(td);
                td.startTransition(FADE_IN_TIME);
            } else {
                imageView.setImageDrawable(drawable);
            }
        }




        /**
         * Returns the ImageView associated with this task as long as the ImageView's task still
         * points to this task as well. Returns null otherwise.
         * 沒用了
         */
        private ImageView getAttachedImageView() {
            final ImageView imageView = imageViewReference.get();
            final LoadPicTask bitmapWorkerTask = getLoadPicTask(imageView);

            if (this == bitmapWorkerTask) {
                return imageView;
            }

            return null;
        }













    }
    public void drawImage(int position, ImageView imageView) {

        BitmapDrawable bitmap = null;
        Log.v(TAG,"drawImage Memory "+ position );
        String sPosition = String.valueOf(position);

        if(imageCache != null)
        {
            bitmap = imageCache.getBitmapFromMemCache(sPosition);
        }

        if (bitmap != null) {

            Log.v(TAG,"drawImage disk cache hit" + position);
            // Bitmap found in memory cache
            imageView.setImageDrawable(bitmap);
            if (UploadBaseAdapter.selectedPicMap.get(position) != null) {

                //  ViewPropertyAnimator.alpha(0.8f).withLayer();
                UploadPicFragment.imageToAlpha(mContext, imageView, 80);

            }

        } else if (cancelPotentialWork(position, imageView)) {
            final LoadPicTask task = new LoadPicTask(mContext, imageView);
            final AsyncDrawable asyncDrawable =
                    new AsyncDrawable(mContext.getResources(), mLoadingBitmap, task);
            imageView.setImageDrawable(asyncDrawable);

            //task.execute(pictureList.get(position).toString(), position);
            // Log.v(TAG,Runtime.getRuntime().availableProcessors() + "");
          Executor exec = new ThreadPoolExecutor(4, 128, 10,
                   TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            task.executeOnExecutor(exec, pictureList.get(position).toString(), position);

            // task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR);
        }


        //final LoadPicTask task = new LoadPicTask(imgView);
        // task.execute(pictureList.get(i).toString());


        //LoadPicTask task = new LoadPicTask((ImageView) grid_view.findViewWithTag(i));
        //task.execute(pictureList.get(i).toString());


    }
    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Pause any ongoing background work. This can be used as a temporary
     * measure to improve performance. For example background work could
     * be paused when a ListView or GridView is being scrolled using a
     * {@link android.widget.AbsListView.OnScrollListener} to keep
     * scrolling smooth.
     * <p/>
     * If work is paused, be sure setPauseWork(false) is called again
     * before your fragment or activity is destroyed (for example during
     * {@link android.app.Activity#onPause()}), or there is a risk the
     * background thread will never finish.
     */
    public void setPauseWork(boolean pauseWork) {
        synchronized (mPauseWorkLock) {
            mPauseWork = pauseWork;
            if (!mPauseWork) {
                mPauseWorkLock.notifyAll();
            }
        }
    }

    /**
     * @param imageView Any imageView
     * @return Retrieve the currently active work task (if any) associated with this imageView.
     * null if there is no such task.
     * , 被用作检索任务是否已经被分配到指定的 ImageView:
     */
    public static LoadPicTask getLoadPicTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getLoadPicTask();
            }
        }
        return null;
    }

    //讀取VIEW時檢查自己有沒有TASK還再執行 如果有 停止 執行新的
    public class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<LoadPicTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, LoadPicTask bitmapWorkerTask) {
            super(res, bitmap);

            bitmapWorkerTaskReference =
                    new WeakReference<LoadPicTask>(bitmapWorkerTask);
        }

        public LoadPicTask getLoadPicTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    /**
     * Returns true if the current work has been canceled or if there was no work in
     * progress on this image view.
     * Returns false if the work in progress deals with the same data. The work is not
     * stopped in that case.
     */
    public static boolean cancelPotentialWork(Object data, ImageView imageView) {
        final LoadPicTask loadPicTask = getLoadPicTask(imageView);

        if (loadPicTask != null) {
            final Object bitmapData = loadPicTask.mData;
            if (bitmapData == null || !bitmapData.equals(data)) {
                Log.v(TAG, data + "");
                loadPicTask.cancel(true);

            } else {
                // The same work is already in progress.
                return false;
            }
        }
        return true;
    }

}
