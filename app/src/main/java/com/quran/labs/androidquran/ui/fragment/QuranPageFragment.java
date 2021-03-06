package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ScrollView;
import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.util.AyahMenuUtils;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.ui.util.QueryAyahCoordsTask;
import com.quran.labs.androidquran.ui.util.QueryPageCoordsTask;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class QuranPageFragment extends SherlockFragment
   implements AyahTracker {

   private static final String TAG = "QuranPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private int mPageNumber;
   private AsyncTask mCurrentTask;
   private HighlightingImageView mImageView;
   private ScrollView mScrollView;
   private PaintDrawable mLeftGradient, mRightGradient = null;

   private AyahMenuUtils mAyahMenuUtils;

   private boolean mOverlayText;
   private Map<String, List<AyahBounds>> mCoordinateData;

   public static QuranPageFragment newInstance(int page){
      final QuranPageFragment f = new QuranPageFragment();
      final Bundle args = new Bundle();
      args.putInt(PAGE_NUMBER_EXTRA, page);
      f.setArguments(args);
      return f;
   }

   @Override
   public void onCreate(Bundle savedInstanceState){
      super.onCreate(savedInstanceState);
      mPageNumber = getArguments() != null?
              getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
      int width = getActivity().getWindowManager()
            .getDefaultDisplay().getWidth();
      mLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      mRightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      setHasOptionsMenu(true);
   }

   @Override
   public View onCreateView(LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState){
      final View view = inflater.inflate(R.layout.quran_page_layout,
              container, false);
      view.setBackgroundDrawable((mPageNumber % 2 == 0?
              mLeftGradient : mRightGradient));
      int lineImageId = R.drawable.dark_line;
      int leftBorderImageId = R.drawable.border_left;
      int rightBorderImageId = R.drawable.border_right;
      
      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(getActivity());

      Resources res = getResources();
      if (!prefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true)) {
    	  view.setBackgroundColor(res.getColor(R.color.page_background));
      }

      boolean nightMode = false;
      int nightModeTextBrightness = Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
      if (prefs.getBoolean(Constants.PREF_NIGHT_MODE, false)){
         leftBorderImageId = R.drawable.night_left_border;
         rightBorderImageId = R.drawable.night_right_border;
         lineImageId = R.drawable.light_line;
         view.setBackgroundColor(Color.BLACK);
         nightMode = true;
         nightModeTextBrightness = QuranSettings.getNightModeTextBrightness(getActivity());
      }

      ImageView leftBorder = (ImageView)view.findViewById(R.id.left_border);
      ImageView rightBorder = (ImageView)view.findViewById(R.id.right_border);
      if (mPageNumber % 2 == 0){
         rightBorder.setVisibility(View.GONE);
         leftBorder.setBackgroundResource(leftBorderImageId);
      }
      else {
         rightBorder.setVisibility(View.VISIBLE);
         rightBorder.setBackgroundResource(rightBorderImageId);
         leftBorder.setBackgroundResource(lineImageId);
      }

      mImageView = (HighlightingImageView)view.findViewById(R.id.page_image);
      mImageView.setNightMode(nightMode);
      if (nightMode) {
         mImageView.setNightModeTextBrightness(nightModeTextBrightness);
      }

      mScrollView = (ScrollView)view.findViewById(R.id.page_scroller);
      
      final GestureDetector gestureDetector = new GestureDetector(
            new PageGestureDetector());
      OnTouchListener gestureListener = new OnTouchListener() {
         @Override
         public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
         }
      };
      mImageView.setOnTouchListener(gestureListener);
      mImageView.setClickable(true);
      mImageView.setLongClickable(true);

      mOverlayText = prefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);

      if (mCoordinateData != null){
         mImageView.setCoordinateData(mCoordinateData);
      }

      return view;
   }

   @Override
   public void onActivityCreated(Bundle savedInstanceState){
      super.onActivityCreated(savedInstanceState);
      Activity activity = getActivity();
      if (PagerActivity.class.isInstance(activity)){
         QuranPageWorker worker =
                 ((PagerActivity)activity).getQuranPageWorker();
         worker.loadPage(mPageNumber, mImageView);

         new QueryPageCoordinatesTask(activity).execute(mPageNumber);
      }
   }

   @Override
   public void onDestroyView() {
      if (mAyahMenuUtils != null){
         mAyahMenuUtils.cleanup();
         mAyahMenuUtils = null;
      }

      if (mCurrentTask != null){ mCurrentTask.cancel(true); }
      mCurrentTask = null;
      super.onDestroyView();
   }

   public void cleanup(){
      android.util.Log.d(TAG, "cleaning up page " + mPageNumber);
      if (mImageView != null){
         mImageView.setImageDrawable(null);
         mImageView = null;
      }

      if (mAyahMenuUtils != null){
         mAyahMenuUtils.cleanup();
         mAyahMenuUtils = null;
      }
   }

   private class QueryPageCoordinatesTask extends QueryPageCoordsTask {
      public QueryPageCoordinatesTask(Context context){
         super(context);
      }

      @Override
      protected void onPostExecute(Rect[] rect) {
         if (rect != null && rect.length == 1){
            if (mImageView != null){
               mImageView.setPageBounds(rect[0]);
               if (mOverlayText){
                  mImageView.setOverlayText(mPageNumber, true);
               }
            }
         }
      }
   }

   private class GetAyahCoordsTask extends QueryAyahCoordsTask {

      public GetAyahCoordsTask(Context context, MotionEvent event){
         super(context, event, mPageNumber);
      }

      public GetAyahCoordsTask(Context context, int sura, int ayah){
         super(context, sura, ayah);
      }

      @Override
      protected void onPostExecute(List<Map<String, List<AyahBounds>>> maps){
         if (maps != null && maps.size() > 0){
            mCoordinateData = maps.get(0);

            if (mImageView != null){
               mImageView.setCoordinateData(mCoordinateData);
            }
         }

         if (mHighlightAyah){
            handleHighlightAyah(mSura,  mAyah);
         }
         else { handleLongPress(mEvent); }
         mCurrentTask = null;
      }
   }

   @Override
   public void highlightAyah(int sura, int ayah){
      if (mCoordinateData == null){
         if (mCurrentTask != null &&
                 !(mCurrentTask instanceof QueryAyahCoordsTask)){
            mCurrentTask.cancel(true);
            mCurrentTask = null;
         }

         if (mCurrentTask == null){
            mCurrentTask = new GetAyahCoordsTask(
                    getActivity(), sura, ayah).execute(mPageNumber);
         }
      }
      else { handleHighlightAyah(sura, ayah); }
   }

   private void handleHighlightAyah(int sura, int ayah){
      if (mImageView == null){ return; }
      mImageView.highlightAyah(sura, ayah);
      if (mScrollView != null){
         AyahBounds yBounds = mImageView.getYBoundsForCurrentHighlight();
         if (yBounds != null){
            int screenHeight = QuranScreenInfo.getInstance().getHeight();
            int y = yBounds.getMinY() - (int)(0.05 * screenHeight);
            mScrollView.smoothScrollTo(mScrollView.getScrollX(), y);
         }
      }
      mImageView.invalidate();
   }

   @Override
   public void unHighlightAyat(){
      mImageView.unhighlight();
   }

   private void handleLongPress(MotionEvent event){
      QuranAyah result = ImageAyahUtils.getAyahFromCoordinates(
              mCoordinateData, mImageView, event.getX(), event.getY());
      if (result != null) {
         mImageView.highlightAyah(result.getSura(), result.getAyah());
         mImageView.invalidate();
         mImageView.performHapticFeedback(
                 HapticFeedbackConstants.LONG_PRESS);

         if (mAyahMenuUtils == null){
            Activity activity = getActivity();
            if (activity != null){
               mAyahMenuUtils = new AyahMenuUtils(activity);
            }
         }

         if (mAyahMenuUtils != null){
            mAyahMenuUtils.showMenu(result.getSura(),
                    result.getAyah(), mPageNumber);
         }
      }
   }

   private class PageGestureDetector extends SimpleOnGestureListener {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent event) {
         PagerActivity pagerActivity = ((PagerActivity)getActivity());
         if (pagerActivity != null){
            pagerActivity.toggleActionBar();
            return true;
         }
         else { return false; }
      }

      @Override
      public boolean onDoubleTap(MotionEvent event) {
         unHighlightAyat();
         return true;
      }

      @Override
      public void onLongPress(MotionEvent event) {
         if (!QuranFileUtils.haveAyaPositionFile(getActivity()) ||
             !QuranFileUtils.hasArabicSearchDatabase(getActivity())){
            Activity activity = getActivity();
            if (activity != null){
               PagerActivity pagerActivity = (PagerActivity)activity;
               pagerActivity.showGetRequiredFilesDialog();
               return;
            }
         }

         if (mCoordinateData == null){
            mCurrentTask = new GetAyahCoordsTask(getActivity(),
                    event).execute(mPageNumber);
         }
         else { handleLongPress(event); }
      }
   }
}
