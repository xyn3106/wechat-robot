package me.biezhi.wechat.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blade.kit.json.JSONObject;

import me.biezhi.wechat.model.WechatMeta;
import me.biezhi.wechat.service.WechatService;

public class WechatListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(WechatListener.class);
	
	int playWeChat = 0;
	
	public void start(final WechatService wechatService, final WechatMeta wechatMeta){
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				LOGGER.info("进入消息监听模式 ...");
				wechatService.choiceSyncLine(wechatMeta);
				while(true){
					try {
					int[] arr = wechatService.syncCheck(wechatMeta);
					
					if(arr[0] == 1100){
						LOGGER.info("你在手机上登出了微信，债见");
						break;
					} else if(arr[0] == 1101){
						LOGGER.info("你在手机上登出了微信，债见");
						break;
					} else if(arr[0] == 0){
						if(arr[1] == 2 || arr[1] == 6){
							JSONObject data = wechatService.webwxsync(wechatMeta);
							wechatService.handleMsg(wechatMeta, data);
						} else if(arr[1] == 7){
							playWeChat += 1;
							LOGGER.info("\n你在手机上玩微信被我发现了 {} 次\n", playWeChat);
						} else if(arr[1] == 3){
							continue;
						} else if(arr[1] == 0){
							continue;
						}  else if(arr[1] == 4){
							continue;
						} else {
							LOGGER.info("retcode={}, selector={}", arr[0], arr[1]);
						}
					} else {
						LOGGER.info("retcode={}, selector={}", arr[0], arr[1]);
					}
					try {
//						LOGGER.info("Sleep 2s...");
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					} catch(Exception e){
						e.printStackTrace();
						try {
						Thread.sleep(10000);
						} catch (InterruptedException ee) {
							ee.printStackTrace();
						}
					}
				}
			}
		}, "wechat-listener-thread").start();
	}
	
}
