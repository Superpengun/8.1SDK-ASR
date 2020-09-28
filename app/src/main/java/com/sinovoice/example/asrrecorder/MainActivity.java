package com.sinovoice.example.asrrecorder;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sinovoice.example.AccountInfo;
import com.sinovoice.example.HciCloudAsrHelper;
import com.sinovoice.example.HciCloudHelper;
import com.sinovoice.example.HciCloudSysHelper;
import com.sinovoice.example.VoiceCollector;
import com.sinovoice.hcicloudsdk.android.asr.recorder.AndroidAsrRecorder;
import com.sinovoice.hcicloudsdk.api.HciCloudSys;
import com.sinovoice.hcicloudsdk.common.asr.AsrResult;
import com.sinovoice.hcicloudsdk.recorder.AsrRecorder;
import com.sinovoice.hcicloudsdk.recorder.AsrRecorderListener;

public class MainActivity extends Activity {
	private static final String TAG = MainActivity.class.getSimpleName();

	// 纵向滑动灵敏度，按下录音按钮后向上滑动距离大于改值后松后调用取消识别方法，其余调用开始识别方法
	private static final float MIN_OFFSET_Y = 120l;

	// 日志窗体最大记录的行数，避免溢出问题
	private static final int MAX_LOG_LINES = 5 * 1024;
	private static final int MIN_SCORE = 20;
	private static final String ASR_LOCAL_GRAMMAR_V4 = "asr.local.grammar.v4";
	private static final String ASR_LOCAL_FREETALK = "asr.local.freetalk";
	private static final String ASR_CLOUD_FREETALK = "asr.cloud.freetalk";

	// 日志输出窗体
	private TextView mTvLogView;

	// 取消识别提示图图标
	private ImageView mIvCancel;

	// ASR 录音机
	private AndroidAsrRecorder recorder = null;

	// ASR 能力配置（初始化 ASR 能力时使用）
	private String asrInitConfig;

	// ASR 识别配置 (ASR识别时使用)
	private String asrRecogConfig;
	
	// ASR 语法数据
	String grammarData;

	// ASR 语法配置 (语法加载时使用)
	String grammarConfig;

	protected boolean bContinueIfNoVoiceInput = true;

	protected boolean bContinueIfVoiceEnded = true;

	// 实时识别的参数取值
	private String realtimeValue = "yes";

	protected int lastAudioLevel = -1;

	protected boolean reportInProgress = false;
	 
	private AccountInfo mAccountInfo;
	private Button mPressBtn;
	private Button mClickBtn;
	private Button mClearBtn;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		HciCloudHelper.setContext(this);
		
		mAccountInfo = AccountInfo.getInstance();
		boolean loadResult = mAccountInfo.loadAccountInfo(this);
		if (loadResult) {
			// 加载信息成功进入主界面
			Toast.makeText(getApplicationContext(), "加载灵云账号成功",
					Toast.LENGTH_SHORT).show();
		} else {
			// 加载信息失败，显示失败界面
			Toast.makeText(
					getApplicationContext(),
					"加载灵云账号失败！请在assets/AccountInfo.txt文件中填写正确的灵云账户信息，账户需要从www.hcicloud.com开发者社区上注册申请。",
					Toast.LENGTH_SHORT).show();
			return;
		}

		// 初始化界面控件
		initView();

		// 为控件绑定响应事件
		initEvents();

		// 初始化录音机模块
		initAsrRecorder();
	}

	@Override
	protected void onDestroy() {
		HciCloudSysHelper.getInstance().release();
		super.onDestroy();
	}

	private void initView() {
		mTvLogView = (TextView) findViewById(R.id.tv_logview);
		mIvCancel = (ImageView) findViewById(R.id.iv_cancel);

		CheckBox cb;
		// 无输入时停止 复选框
		cb = (CheckBox) findViewById(R.id.checkNoVoiceInput);
		cb.setChecked(!bContinueIfNoVoiceInput);

		// 语音结束后停止 复选框
		cb = (CheckBox) findViewById(R.id.checkVoiceEnded);
		cb.setChecked(!bContinueIfVoiceEnded);

		String capkey = AccountInfo.getInstance().getCapKey();
		printLog("本次使用的capkey: ["  + capkey +"]");
		RadioGroup rg = (RadioGroup) findViewById(R.id.radioGroupReadltime);
		rg.check(R.id.radioRealtimeYes);
		if(capkey.equalsIgnoreCase(ASR_LOCAL_GRAMMAR_V4) || capkey.equalsIgnoreCase(ASR_LOCAL_FREETALK)){
			// 本地语法识别与本地自由说不支持RT模式，故此yes与rt的切换置灰
			rg.getChildAt(1).setEnabled(false);
		}



	}

	@SuppressLint("ClickableViewAccessibility")
	private void initEvents() {


		// 点击一次开始录音机，再点击一次结束录音机,期间根据后端点判断一次说话是否结束，可连续录音识别
		// 对于这种识别，由于需要实时判断录音的后端点，因此asrRecogConfig中的realtime参数，必须为yes或rt
		mClickBtn = (Button) findViewById(R.id.bt_recorder_click);
		mClickBtn.setText("未录音状态，请单点启动录音");
		mClickBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				printLog("recorder.getState()"  + recorder.getState());
				if (recorder.getState() == AsrRecorder.STOPPING) {
					// 上次录音会话还在识别中的话，先取消。
					// 调用 cancel 后可立即启动下一轮录音会话
					recorder.cancel();
					mClickBtn.setText("未录音状态，请单点启动录音");
				} else if (recorder.getState() == AsrRecorder.STARTED) {
					// 上次的会话还在继续的话，则停止录音并取消识别
					recorder.stop(true);
					mClickBtn.setText("未录音状态，请单点启动录音");
				}else{
					String capkey = AccountInfo.getInstance().getCapKey();
					if (capkey.equalsIgnoreCase(ASR_LOCAL_GRAMMAR_V4)) {
						// 准备要加载的语法数据
						grammarData = loadGrammar("stock_10001.gram");
						asrRecogConfig = HciCloudAsrHelper.getAsrRecogConfigForLocalGrammar();
						grammarConfig = HciCloudAsrHelper.getLoadGrammarConfig();

						recorder.start(asrRecogConfig, asrInitConfig, grammarData, grammarConfig);
					}else if(capkey.equalsIgnoreCase(ASR_CLOUD_FREETALK)) {
						asrRecogConfig = HciCloudAsrHelper.getAsrRecogConfigForCloudFreetalk();
						recorder.start(asrRecogConfig+",realtime="+realtimeValue, asrInitConfig, null, null);
					}else if(capkey.equalsIgnoreCase(ASR_LOCAL_FREETALK)){
						asrRecogConfig = HciCloudAsrHelper.getAsrRecogConfigForLocalFreetalk();
						recorder.start(asrRecogConfig, asrInitConfig, null, null);
					}
					mClickBtn.setText("录音中，请单点结束录音");
				}

			}
		});

		// 按住录音识别按钮
		mPressBtn = (Button) findViewById(R.id.bt_recorder);
		mPressBtn.setOnTouchListener(new OnTouchListener() {
			private float downY;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN: // 按钮按下时开始录音

					if (recorder.getState() == AsrRecorder.STOPPING) {
						// 上次录音会话还在识别中的话，先取消。
						// 调用 cancel 后可立即启动下一轮录音会话
						recorder.cancel();
					} else if (recorder.getState() == AsrRecorder.STARTED) {
						// 上次的会话还在继续的话，则停止录音并取消识别
						recorder.stop(true);
					}

					String capkey = AccountInfo.getInstance().getCapKey();
					if (capkey.equalsIgnoreCase(ASR_LOCAL_GRAMMAR_V4)) {
						// 准备要加载的语法数据
						grammarData = loadGrammar("stock_10001.gram");
						asrRecogConfig = HciCloudAsrHelper.getAsrRecogConfigForLocalGrammar();
						grammarConfig = HciCloudAsrHelper.getLoadGrammarConfig();

						recorder.start(asrRecogConfig, asrInitConfig, grammarData, grammarConfig);
					}else if(capkey.equalsIgnoreCase(ASR_CLOUD_FREETALK)) {
						asrRecogConfig = HciCloudAsrHelper.getAsrRecogConfigForCloudFreetalk();
						recorder.start(asrRecogConfig+",realtime="+realtimeValue, asrInitConfig, null, null);
					}else if(capkey.equalsIgnoreCase(ASR_LOCAL_FREETALK)){
						asrRecogConfig = HciCloudAsrHelper.getAsrRecogConfigForLocalFreetalk();
						recorder.start(asrRecogConfig, asrInitConfig, null, null);
					}



					// 记录按下时的纵坐标，用于计算纵向偏移
					downY = event.getY();
					mIvCancel.setVisibility(View.INVISIBLE);
					break;
				case MotionEvent.ACTION_MOVE: // 实时计算纵向偏移量，根据条件决定是否显示取消提示窗体
					final boolean visib = downY - event.getY() > MIN_OFFSET_Y;
					if (visib && mIvCancel.getVisibility() != View.VISIBLE) {
						mIvCancel.setVisibility(View.VISIBLE);
					} else if (!visib && mIvCancel.getVisibility() == View.VISIBLE) {
						mIvCancel.setVisibility(View.INVISIBLE);
					}
					break;

				case MotionEvent.ACTION_UP: // 抬手时，根据纵坐标偏移，决定开始识别还是取消识别
					if (downY - event.getY() > MIN_OFFSET_Y) {
						recorder.stop(true); // 停止录音并取消识别
					} else {
						System.currentTimeMillis();
						recorder.stop(false); // 停止录音，继续识别
					}
					// 返回后录音已停止，设置 audioLevel 为 0
					ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar1);
					pb.setProgress(0);
					mIvCancel.setVisibility(View.INVISIBLE);
					break;
				}
				return false;
			}


		});

		// 清屏按钮
		mClearBtn = (Button) findViewById(R.id.bt_clear);
		mClearBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mTvLogView.setText("");
					}
				});
			}
		});

		CheckBox cb;
		// 无输入时停止 复选框
		cb = (CheckBox) findViewById(R.id.checkNoVoiceInput);
		cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton cb, boolean checked) {
				bContinueIfNoVoiceInput = !checked;
			}
		});
		// 语音结束后停止 筛选框
		cb = (CheckBox) findViewById(R.id.checkVoiceEnded);
		cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton cb, boolean checked) {
				bContinueIfVoiceEnded = !checked;
			}
		});
		// 流式识别，实时反馈 单选按钮组
		RadioGroup.OnCheckedChangeListener onChecked;
		onChecked = new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId == R.id.radioRealtimeYes) {
					realtimeValue = "yes";
				} else if (checkedId == R.id.radioRealtimeRT) {
					realtimeValue = "rt";
				} else {
					throw new RuntimeException("界面上增加新的 radiobutton 了？");
				}
			}

		};
		RadioGroup rg = (RadioGroup) findViewById(R.id.radioGroupReadltime);
		rg.setOnCheckedChangeListener(onChecked);
	}

	private void initAsrRecorder() {
		final ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar1);
		pb.setProgress(0);
		pb.setMax(10);

		// 初始化灵云SDK，与授权相关
		int errCode = HciCloudSysHelper.getInstance().init(this);
		printLog("系统初始化，结果 errorCode = ["+errCode + "] msg = ["+ HciCloudSys.hciGetErrorInfo(errCode)+"]");

		// 准备录音识别的初始化参数，这些配置在一个asrrecorder的生命周期中生效
        // Android平台下常用初始化参数：
        //     datapath: 指定资源文件所在的存储目录。本地语法识别或本地自由说识别时需要。
		asrInitConfig = HciCloudAsrHelper.getAsrInitConfig(this);

		// 初始化一个录音机，可在业务中重复使用
        // 其中AsrRecorderListener.Skeleton接口需要客户实现，其中包含了各个重要回调，
        // 这些回调均为子线程。请勿在其中修改UI
		recorder = new AndroidAsrRecorder(new AsrRecorderListener.Skeleton() {

			@Override
			public void onStart(AsrRecorder recorder) {
				String info = "录音机已启动";
				if (realtimeValue == "yes") {
					info += ",流式识别";
				} else if (realtimeValue == "rt") {
					info += ",实时反馈";
				} else {
					throw new RuntimeException("UI/代码不一致");
				}
				if (!bContinueIfNoVoiceInput) {
					info += ",无输入时停止";
				}
				if (!bContinueIfVoiceEnded) {
					info += ",语音结束后停止";
				}
				printLog(info);
			}

			@Override
			public void onStopping(AsrRecorder recorder) {
				printLog("录音机停止中...");
			}

			@Override
			public void onFinish(AsrRecorder recorder, int reason) {
			    // 若需要每次录音识别保存数据，可在此保存
                VoiceCollector.getInstance().savePCMData(MainActivity.this);
                VoiceCollector.getInstance().clear();

			    // 录音识别结束，结束原因通过reason分类
				String strReason;
				switch (reason) {
				case AsrRecorderListener.NO_VOICE_INPUT:
					strReason = "NoVoiceInput";
					break;
				case AsrRecorderListener.STOPPED:
					strReason = "Normal";
					break;
				case AsrRecorderListener.CANCELLED:
					strReason = "Cancelled";
					break;
				case AsrRecorderListener.RECOG_ERROR:
					strReason = "RecognizeError";
					break;
				case AsrRecorderListener.DEVICE_ERROR:
					strReason = "DeviceError";
					break;
				case AsrRecorderListener.VOICE_ENDED:
					strReason = "VoiceEnded";
					break;
				case AsrRecorderListener.EXCEPTION:
					strReason = "Exception";
					break;
				default:
					strReason = "Unknown";
					break;
				}

				strReason += "(" + reason + ")\n";
				printLog("录音机已停止，原因 - " + strReason);
				resetClickBtnText();
			}

			@Override
			public void onDeviceError(AsrRecorder recorder, String stage, int errorCode) {
				printLog("录音设备错误，场景 - " + stage + ", 错误码 - " + errorCode);
				resetClickBtnText();
			}

			@Override
			public void onRecogError(AsrRecorder recorder, String stage, int errorCode) {
				printLog("语音识别错误，场景 - " + stage + ", 错误码 - " + errorCode);
				resetClickBtnText();

			}

			@Override
			public void onException(AsrRecorder recorder, Exception e) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				printLog("发生异常：" + sw.toString());
				resetClickBtnText();
			}

			@Override
			public void onRecogResult(AsrRecorder recorder, AsrResult result) {
			    // 返回录音识别的结果
				if (result == null) {
					printLog("无识别结果");
				} else {
					if (realtimeValue == "rt") {
						// 实时反馈模式下，start-stop期间都会不断返回结果，结果中有时间戳信息
						printLog("录音识别结果: 置信度 = " + result.score + ", 文本 = " + result.text
								+ ", 开始时间 = " + result.startTime + " 结束时间 = " + result.endTime);

					} else {
					    if(result.score > MIN_SCORE){
                            printLog("录音识别结果: 置信度 = " + result.score + ", 文本 = " + result.text);
                        }else{
                            printLog("The Score is too low,don't show recog result on the screen");
                        }

					}
				}
			}

			@Override
			public boolean onNoVoiceInput(AsrRecorder recorder, int nth) {
				printLog("第 " + nth + " 次未检测到语音输入");
				// 返回 false 将中止录音会话，返回 true 将继续录音并识别
				return bContinueIfNoVoiceInput;
			}

			@Override
			public boolean onEndOfVoice(AsrRecorder recorder) {
				printLog("语音末端点(语音结束)完成识别");
				return bContinueIfVoiceEnded;
			}

			@Override
			public void onAudioRecorded(byte[] audioData, int audioLevel) {

			    // 如果有需要，收集start-stop过程中的录音数据。
                // 本示例中，保存录音VoiceCollector.getInstance().savePCMData()的方法在onFinish,即录音机stop或cancel之后调用
                VoiceCollector.getInstance().collect(audioData);

				// 20ms 会被调用一次(在录音线程上)，需要保证效率
				if (lastAudioLevel == audioLevel) {
					// 和上次相同无须更新界面，直接返回
					return;
				}
				lastAudioLevel = audioLevel; // 更新 lastAudioLevel
				if (reportInProgress) {
					// 上次的 UI 更新还在进行中，直接返回
					return;
				}
				reportInProgress = true;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						reportInProgress = false;
						pb.setProgress(lastAudioLevel);
					}
				});
			}

			@Override
			public void onVoiceBegin(AsrRecorder recorder) {
				// SDK 中还未实现，此为预留接口
				printLog("语音输入检测到前端点");
			}

			@Override
			public void onVoiceEnd(AsrRecorder recorder) {
				printLog("语音输入检测到末端点");
			}
		});
		recorder.setAudioReportMethod(AsrRecorder.REPORT_AUDIO_AND_LEVEL);
	}

	private void printLog(final String detail) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// 日志输出同时记录到日志文件中
				Log.d(TAG, detail);

				if (mTvLogView != null) {
					// 如日志行数大于上限，则清空日志内容
					if (mTvLogView.getLineCount() > MAX_LOG_LINES) {
						mTvLogView.setText("");
					}

					// 在当前基础上追加日志
					mTvLogView.append(detail + "\n");

					// 二次刷新确保父控件向下滚动能到达底部,解决一次出现多行日志时滚动不到底部的问题
					mTvLogView.post(new Runnable() {
						@Override
						public void run() {
							((ScrollView) mTvLogView.getParent())
									.fullScroll(ScrollView.FOCUS_DOWN);
						}
					});
				}
			}
		});
	}
	
	private String loadGrammar(String fileName) {
		String grammar = "";
		try {
			InputStream is = null;
			try {
				is = getAssets().open(fileName);
				byte[] data = new byte[is.available()];
				is.read(data);
				grammar = new String(data);
			} finally {
				is.close();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		return grammar;
	}

	private void resetClickBtnText(){
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mClickBtn.setText("未录音状态，请单点启动录音");
			}
		});

	}
	
}
