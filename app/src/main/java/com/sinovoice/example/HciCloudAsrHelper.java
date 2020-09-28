package com.sinovoice.example;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.RadioGroup;

import com.sinovoice.example.asrrecorder.R;
import com.sinovoice.hcicloudsdk.common.asr.AsrConfig;
import com.sinovoice.hcicloudsdk.common.asr.AsrGrammarId;
import com.sinovoice.hcicloudsdk.common.asr.AsrInitParam;

import java.io.File;

public class HciCloudAsrHelper {
	private static final String TAG = HciCloudAsrHelper.class.getSimpleName();

	//private static final String CAPKEY = "asr.cloud.freetalk";

	//private static final String CAPKEY = "asr.local.grammar.v4 ";
	
	private static final String VADTAIL = "500";
	private static  final String VADSEG="";
	private static final String VADHEAD = "2000"; // 识别前若静音长度超过此设定值，会触发
													// NoVoiceInput

	private static HciCloudAsrHelper mInstance;

	private HciCloudAsrHelper() {
	}


    public static String getAsrInitConfig(Context context) {
        // 构造本地资源路径 DATAPATH . 该参数用于指定本地识别时所需资源的路径
        String datapath = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator + "sinovoice"
                + File.separator + context.getPackageName() + File.separator + "data";

        // 将样例assets/data下的本地资源拷贝到DATAPATH下
		HciCloudHelper.copyAssetsFiles(context,datapath);
		final AsrInitParam asrInitParam = new AsrInitParam();
		asrInitParam.addParam(AsrInitParam.PARAM_KEY_DATA_PATH, datapath);
		Log.d(TAG, "asrInitParam " + asrInitParam.getStringConfig());
		return asrInitParam.getStringConfig();
	}


    public static String getAsrRecogConfigForCloudFreetalk() {
		AsrConfig recogConfig = new AsrConfig();
		recogConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_CAP_KEY, AccountInfo.getInstance().getCapKey());
		recogConfig.addParam(AsrConfig.VadConfig.PARAM_KEY_VAD_TAIL, VADTAIL);
		recogConfig.addParam(AsrConfig.VadConfig.PARAM_KEY_VAD_HEAD, VADHEAD);

		// 本样例中，云端识别的realtime取值依赖界面上的流式识别（yes）与实时反馈（rt）的选择
		//recogConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_REALTIME, "yes");
		return recogConfig.getStringConfig();
	}

	public static String getAsrRecogConfigForLocalFreetalk() {
		AsrConfig recogConfig = new AsrConfig();
		recogConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_CAP_KEY, AccountInfo.getInstance().getCapKey());
		recogConfig.addParam(AsrConfig.VadConfig.PARAM_KEY_VAD_TAIL, VADTAIL);
		recogConfig.addParam(AsrConfig.VadConfig.PARAM_KEY_VAD_HEAD, VADHEAD);

		//由于DATAPATH下有多种模型，需通过资源前缀参数指定具体的本地资源文件
		recogConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_RES_PREFIX, "freetalk_");

		return recogConfig.getStringConfig();
	}

	public static String getAsrRecogConfigForLocalGrammar() {
		AsrConfig recogConfig = new AsrConfig();
		recogConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_CAP_KEY, AccountInfo.getInstance().getCapKey());
		recogConfig.addParam(AsrConfig.VadConfig.PARAM_KEY_VAD_TAIL, VADTAIL);
		recogConfig.addParam(AsrConfig.VadConfig.PARAM_KEY_VAD_HEAD, VADHEAD);

		//由于DATAPATH下有多种模型，需通过资源前缀参数指定具体的本地资源文件
		recogConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_RES_PREFIX, "grammar_");
		return recogConfig.getStringConfig();
	}

	public static String getLoadGrammarConfig(){
        AsrConfig loadGrammarConfig = new AsrConfig();
        loadGrammarConfig.addParam(AsrConfig.GrammarConfig.PARAM_KEY_GRAMMAR_TYPE, AsrConfig.GrammarConfig.VALUE_OF_PARAM_GRAMMAR_TYPE_JSGF);
        loadGrammarConfig.addParam(AsrConfig.GrammarConfig.PARAM_KEY_IS_FILE, AsrConfig.VALUE_OF_NO);

        //由于DATAPATH下有多种模型，需通过资源前缀参数指定具体的本地资源文件
        loadGrammarConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_RES_PREFIX, "grammar_");

        // 必须传入capkey
        loadGrammarConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_CAP_KEY, "asr.local.grammar.v4");
        return loadGrammarConfig.getStringConfig();
    }

}
