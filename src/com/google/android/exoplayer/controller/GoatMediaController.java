package com.google.android.exoplayer.controller;

import java.util.Formatter;
import java.util.Locale;

import com.google.android.exoplayer.demo.R;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

/**
 * 
 * @author Goat Software
 * @since 20, January 2016
 */
public class GoatMediaController extends FrameLayout {
	private static final String TAG = GoatMediaController.class.getSimpleName();
	private MediaPlayerControl mPlayer;
    private Context mContext;
    
    private StringBuilder mFormatBuilder;
    private Formatter mFormatter;
    
    private ImageButton mTurnButton;
    private ImageButton mScaleButton;
   
    private ViewGroup loadingLayout;
    private ViewGroup errorLayout;
    
    private View mBackButton;
    private View mTitleLayout;
    private View mControlLayout;
    private View mCenterPlayButton;
    
    private ProgressBar mProgress;
    private TextView mEndTime, mCurrentTime;
    private TextView mTitle;
    
    private boolean mShowing = true;
    private boolean mDragging;
    private boolean handled = false; // Handle show/hide with touch event
    
    private static final int sDefaultTimeout = 3000;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SHOW_LOADING = 3;
    private static final int HIDE_LOADING = 4;
    private static final int SHOW_ERROR = 5;
    private static final int HIDE_ERROR = 6;
    private static final int SHOW_COMPLETE = 7;
    private static final int HIDE_COMPLETE = 8;
    
    private IGoatCallBack mGoatCallBack; 
    
	public GoatMediaController(Context context) {
		super(context);
		init(context);
	}
	
	public GoatMediaController(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.mContext = context;
		init(context);
	}
	
	private void init(Context context) {
		this.mContext = context;
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View viewRoot = inflater.inflate(R.layout.uvv_player_controller, this);
        viewRoot.setOnTouchListener(mTouchListener);
        initControllerView(viewRoot);
	}
	
	private void initControllerView(View v) {
		mTitleLayout = v.findViewById(R.id.title_part);
        mControlLayout = v.findViewById(R.id.control_layout);
        loadingLayout = (ViewGroup) v.findViewById(R.id.loading_layout);
        errorLayout = (ViewGroup) v.findViewById(R.id.error_layout);
        mTurnButton = (ImageButton) v.findViewById(R.id.turn_button);
        mScaleButton = (ImageButton) v.findViewById(R.id.scale_button);
        mCenterPlayButton = v.findViewById(R.id.center_play_btn);
        mBackButton = v.findViewById(R.id.back_btn);
        
        if (mTurnButton != null) {
        	mTurnButton.requestFocus();
        	mTurnButton.setOnClickListener(mPauseListener);
        }
        
        if (mScaleButton != null) {
        	mScaleButton.requestFocus();
        	mScaleButton.setOnClickListener(mScaleListener);
        }
        
        // Restart play-back
        if (mCenterPlayButton != null) {
        	mCenterPlayButton.requestFocus();
        	mCenterPlayButton.setOnClickListener(mCenterPlayListener);
        }
        
        if (mBackButton != null) {
        	mBackButton.setOnClickListener(mBackListener);
        }
        
        mProgress = (ProgressBar) v.findViewById(R.id.seekbar);
        if (mProgress != null) {
            if (mProgress instanceof SeekBar) {
                SeekBar seeker = (SeekBar) mProgress;
                seeker.setOnSeekBarChangeListener(mSeekListener);
            }
            mProgress.setMax(1000);
        }
        
        mEndTime = (TextView) v.findViewById(R.id.duration);
        mCurrentTime = (TextView) v.findViewById(R.id.has_played);
        mTitle = (TextView) v.findViewById(R.id.title);
        
        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
	}
	
	public void setMediaPlayer(MediaPlayerControl player) {
		mPlayer = player;
		updatePausePlay();
	}
	
	public interface IGoatCallBack {
		void onBack();
		void onFullScreen();
	}
	
	public void registerCallBack(IGoatCallBack callback) {
		this.mGoatCallBack = callback;
	}
	
	/**
	 * Touch listener, If controller view is displayed, making it hide
	 */
    private OnTouchListener mTouchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (mShowing) {
                    hide();
                    handled = true;
                    return true;
                }
            }
            return false;
        }
    };
    
    // START: OnClickListener
    private OnClickListener mPauseListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if (mPlayer != null) {
				doPauseResume();
				show(sDefaultTimeout);
			}
		}
	};
	
	private OnClickListener mScaleListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if (mGoatCallBack != null && mPlayer != null) {
				mGoatCallBack.onFullScreen();
				updateBackButton();
				updateScaleButton();
			}
		}
	};
	
	private OnClickListener mCenterPlayListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if (mPlayer != null) {
				hideCenterView();
				mPlayer.seekTo(0);
			}
		}
	};
	
	private OnClickListener mBackListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if (mGoatCallBack != null) {
				mGoatCallBack.onBack();
			}
		}
	};
	
	private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
		int newPosition = 0;
		boolean change = false;
		
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			if (mPlayer == null) {
				return;
			}
			
			if (change) {
				mPlayer.seekTo(newPosition);
				if (mCurrentTime != null) {
	                mCurrentTime.setText(stringForTime(newPosition));
				}
			}
			
			mDragging = false;
			setProgress();
			updatePausePlay();
			show(sDefaultTimeout);
			
			// Ensure that progress is properly updated in the future,
            // the call to show() does not guarantee this because it is a
            // no-op if we are already showing.
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
		}
		
		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			if (mPlayer == null) {
				return;
			}
			
			show(3600000);
			mDragging = true;
			
			// By removing these pending progress messages we make sure that
			// a) we won't update the progress while the user adjusts the
			// seekbar and
			// b) once the user is done dragging the thumb we will post one of
			// these messages to the queue again and this ensures that there
			// will be exactly one message queued up.
			mHandler.removeMessages(SHOW_PROGRESS);
		}
		
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (mPlayer == null || !fromUser) {
				// We're not interested in programmatically generated changes to
                // the progress bar's position.
				return;
			}
			long duration = mPlayer.getDuration();
            long newposition = (duration * progress) / 1000L;
            newPosition = (int) newposition;
            change = true;
		}
	};
	// END: OnClickListener
	
	// START: Update UI
	private void updatePausePlay() {
		if (mPlayer.isPlaying()) {
			mTurnButton.setImageResource(R.drawable.uvv_stop_btn);
		} else {
			mTurnButton.setImageResource(R.drawable.uvv_player_player_btn);
		}
	}
	
	private void updateBackButton() {
		mBackButton.setVisibility(isFullScreen() ? VISIBLE : INVISIBLE);
	}
	
	public void updateScaleButton() {
		if (isFullScreen()) {
			mScaleButton.setImageResource(R.drawable.uvv_star_zoom_in);
		} else {
			mScaleButton.setImageResource(R.drawable.uvv_player_scale_btn);
		}
	}
	
	/**
	 * Show the controller on screen. <br>
	 * It will go away automatically after 3 seconds of inactivity.
	 */
	public void show() {
		show(sDefaultTimeout);
	}
	
	/**
	 * Show the controller on screen.<br>
	 * It will go away automatically after 'timeout' milliseconds of inactivity.
	 * @param timeout The timeout in milliseconds. <br>
	 * Use 0 to show the controller until hide() is called.
	 */
	public void show(int timeout) {
		if (!mShowing) {
			setProgress();
			if (mTurnButton != null) {
				mTurnButton.requestFocus();
			}
			disableUnsupportedButtons();
			mShowing = true;
		}
		updatePausePlay();
		
		if (getVisibility() != VISIBLE) {
			setVisibility(VISIBLE);
		}
		
		if (mTitleLayout != null && mTitleLayout.getVisibility() != VISIBLE) {
			mTitleLayout.setVisibility(VISIBLE);
		}
		
		if (mControlLayout != null && mControlLayout.getVisibility() != VISIBLE) {
			mControlLayout.setVisibility(VISIBLE);
		}
		
		// cause the progress bar to be updated even if mShowing
        // was already true.  This happens, for example, if we're
        // paused with the progress bar showing the user hits play.
        mHandler.sendEmptyMessage(SHOW_PROGRESS);

        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            Message msg = mHandler.obtainMessage(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
	}
	
	/**
     * Remove the controller from the screen.
     */
    public void hide() {
        if (mShowing) {
            mHandler.removeMessages(SHOW_PROGRESS);
            if (mTitleLayout != null) {
            	mTitleLayout.setVisibility(GONE);
            }
            if (mControlLayout != null) {
            	mControlLayout.setVisibility(GONE);
            }
            mShowing = false;
        }
    }
    
    private void showCenterView(int resId) {
    	switch (resId) {
		case R.id.loading_layout:
			if (loadingLayout.getVisibility() != VISIBLE) {
    			loadingLayout.setVisibility(VISIBLE);
    		}
    		if (mCenterPlayButton.getVisibility() == VISIBLE) {
    			mCenterPlayButton.setVisibility(GONE);
    		}
    		if (errorLayout.getVisibility() == VISIBLE) {
    			errorLayout.setVisibility(GONE);
    		}
			break;
		case R.id.center_play_btn:
			if (mCenterPlayButton.getVisibility() != VISIBLE) {
				mCenterPlayButton.setVisibility(VISIBLE);
			}
			if (loadingLayout.getVisibility() == VISIBLE) {
				loadingLayout.setVisibility(GONE);
			}
			if (errorLayout.getVisibility() == VISIBLE) {
				errorLayout.setVisibility(GONE);
			}
			break;
		case R.id.error_layout:
			if (errorLayout.getVisibility() != VISIBLE) {
				errorLayout.setVisibility(VISIBLE);
			}
			if (mCenterPlayButton.getVisibility() == VISIBLE) {
				mCenterPlayButton.setVisibility(GONE);
			}
			if (loadingLayout.getVisibility() == VISIBLE) {
				loadingLayout.setVisibility(GONE);
			}
			break;
		default:
			break;
		}
    }
    
    private void hideCenterView() {
    	if (mCenterPlayButton.getVisibility() == VISIBLE) {
    		mCenterPlayButton.setVisibility(GONE);
    	}
    	if (errorLayout.getVisibility() == VISIBLE) {
    		errorLayout.setVisibility(GONE);
    	}
    	if (loadingLayout.getVisibility() == VISIBLE) {
    		loadingLayout.setVisibility(GONE);
    	}
    }
    
    public void showLoading() {
    	mHandler.sendEmptyMessage(SHOW_LOADING);
    }
    
    public void hideLoading() {
    	mHandler.sendEmptyMessage(HIDE_LOADING);
    }
    
    public void showError() {
    	mHandler.sendEmptyMessage(SHOW_ERROR);
    }
    
    public void hideError() {
    	mHandler.sendEmptyMessage(HIDE_ERROR);
    }
    
    public void showComplete() {
    	mHandler.sendEmptyMessage(SHOW_COMPLETE);
    }
    
    public void hideComplete() {
    	mHandler.sendEmptyMessage(HIDE_COMPLETE);
    }
    
    public void setTitle(String titile) {
        mTitle.setText(titile);
    }
	// END: Update UI
	
	private void doPauseResume() {
		if (mPlayer.isPlaying()) {
			mPlayer.pause();
		} else {
			mPlayer.start();
		}
		updatePausePlay();
	}
	
	private int setProgress() {
		if (mPlayer == null || mDragging) {
			return 0;
		}
		int position = mPlayer.getCurrentPosition();
		int duration = mPlayer.getDuration();
		if (mProgress != null) {
			if (duration > 0) {
				// use long to avoid overflow
				long pos = 1000L * position / duration;
				mProgress.setProgress((int) pos);
			}
			int percent = mPlayer.getBufferPercentage();
			mProgress.setSecondaryProgress(percent * 10);
		}
		
		if (mEndTime != null) {
			mEndTime.setText(stringForTime(duration));
		}
		
		if (mCurrentTime != null) {
			mCurrentTime.setText(stringForTime(position));
		}
		
		return position;
	}
	
	private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours   = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }
	
	/**
     * Disable pause or seek buttons if the stream cannot be paused or seeked.
     * This requires the control interface to be a MediaPlayerControlExt
     */
    private void disableUnsupportedButtons() {
        try {
            if (mTurnButton != null && !mPlayer.canPause()) {
            	mTurnButton.setEnabled(false);
            }
            // TODO What we really should do is add a canSeek to the MediaPlayerControl interface;
            // this scheme can break the case when applications want to allow seek through the
            // progress bar but disable forward/backward buttons.
            //
            // However, currently the flags SEEK_BACKWARD_AVAILABLE, SEEK_FORWARD_AVAILABLE,
            // and SEEK_AVAILABLE are all (un)set together; as such the aforementioned issue
            // shouldn't arise in existing applications.
            if (mProgress != null && !mPlayer.canSeekBackward() && !mPlayer.canSeekForward()) {
                mProgress.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError ex) {
            // We were given an old version of the interface, that doesn't have
            // the canPause/canSeekXYZ methods. This is OK, it just means we
            // assume the media can be paused and seeked, and so we don't disable
            // the buttons.
        }
    }
	
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int pos;
            switch (msg.what) {
                case FADE_OUT:
                    hide();
                    break;
                case SHOW_PROGRESS:
                    pos = setProgress();
                    if (!mDragging && mShowing && mPlayer.isPlaying()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case SHOW_LOADING:
                	show();
                	showCenterView(R.id.loading_layout);
                	break;
                case SHOW_COMPLETE:
                	showCenterView(R.id.center_play_btn);
                	break;
                case SHOW_ERROR:
                	show();
                	showCenterView(R.id.error_layout);
                	break;
                case HIDE_LOADING:
                case HIDE_ERROR:
                case HIDE_COMPLETE:
                	hide();
                	hideCenterView();
                	break;
            }
        }
    };
    
    @Override
    public void setEnabled(boolean enabled) {
    	if (mTurnButton != null) {
    		mTurnButton.setEnabled(enabled);
    	}
    	if (mProgress != null) {
    		mProgress.setEnabled(enabled);
    	}
    	if (mScaleButton != null) {
    		mScaleButton.setEnabled(enabled);
    	}
    	mBackButton.setEnabled(true);
    	disableUnsupportedButtons();
    	super.setEnabled(enabled);
    };
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
    	switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			Log.i(TAG, "> onTouch");
			show(0); // Show unti hide is called
			handled = false;
			break;
		case MotionEvent.ACTION_UP:
			if (!handled) {
				handled = false;
				show(sDefaultTimeout);
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			hide();
			break;
		default:
			break;
		}
    	return true;
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
    	int keyCode = event.getKeyCode();
		final boolean uniqueDown = event.getRepeatCount() == 0
				&& event.getAction() == KeyEvent.ACTION_DOWN;
		if (keyCode ==  KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();
                show(sDefaultTimeout);
                if (mTurnButton != null) {
                	mTurnButton.requestFocus();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayer.isPlaying()) {
                mPlayer.start();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer.isPlaying()) {
                mPlayer.pause();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_CAMERA) {
            // don't show the controls for volume adjustment
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }
            return true;
        }

        show(sDefaultTimeout);
        return super.dispatchKeyEvent(event);
    }
    
    public boolean isShowing() {
    	return mShowing;
    }
    
    private boolean isFullScreen() {
    	Activity activity = (Activity) mContext;
    	return activity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ? true : false;
    }
}
