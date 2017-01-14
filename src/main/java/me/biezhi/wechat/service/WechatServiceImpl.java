package me.biezhi.wechat.service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blade.kit.DateKit;
import com.blade.kit.FileKit;
import com.blade.kit.StringKit;
import com.blade.kit.http.HttpRequest;
import com.blade.kit.json.JSONArray;
import com.blade.kit.json.JSONKit;
import com.blade.kit.json.JSONObject;
import com.blade.kit.json.JSONValue;

import me.biezhi.wechat.Constant;
import me.biezhi.wechat.exception.WechatException;
import me.biezhi.wechat.model.WechatContact;
import me.biezhi.wechat.model.WechatMeta;
import me.biezhi.wechat.robot.MoLiRobot;
import me.biezhi.wechat.robot.Robot;
import me.biezhi.wechat.util.Matchers;

public class WechatServiceImpl implements WechatService {

	private static final Logger LOGGER = LoggerFactory.getLogger(WechatService.class);
	
	// 茉莉机器人
	private Robot robot = new MoLiRobot();
	
	/**
	 * 获取联系人
	 */
	@Override
	public WechatContact getContact(WechatMeta wechatMeta) {
		String url = wechatMeta.getBase_uri() + "/webwxgetcontact?pass_ticket=" + wechatMeta.getPass_ticket() + "&skey="
				+ wechatMeta.getSkey() + "&r=" + DateKit.getCurrentUnixTime();

		JSONObject body = new JSONObject();
		body.put("BaseRequest", wechatMeta.getBaseRequest());

		HttpRequest request = HttpRequest.post(url).contentType("application/json;charset=utf-8")
				.header("Cookie", wechatMeta.getCookie()).send(body.toString());
		
		LOGGER.debug(request.toString());
		String res = request.body();
		request.disconnect();

		if (StringKit.isBlank(res)) {
			throw new WechatException("获取联系人失败");
		}
		
		LOGGER.debug(res);
		
		WechatContact wechatContact = new WechatContact();
		try {
			JSONObject jsonObject = JSONKit.parseObject(res);
			JSONObject BaseResponse = jsonObject.get("BaseResponse").asJSONObject();
			if (null != BaseResponse) {
				int ret = BaseResponse.getInt("Ret", -1);
				if (ret == 0) {
					JSONArray memberList = jsonObject.get("MemberList").asArray();
					JSONArray contactList = new JSONArray();
					
					if (null != memberList) {
						for (int i = 0, len = memberList.size(); i < len; i++) {
							JSONObject contact = memberList.get(i).asJSONObject();
							// 公众号/服务号
							if (contact.getInt("VerifyFlag", 0) == 8) {
								continue;
							}
							// 特殊联系人
							if (Constant.FILTER_USERS.contains(contact.getString("UserName"))) {
								continue;
							}
							// 群聊
							if (contact.getString("UserName").indexOf("@@") != -1) {
								continue;
							}
							// 自己
//							if (contact.getString("UserName").equals(wechatMeta.getUser().getString("UserName"))) {
//								continue;
//							}
							contactList.add(contact);
						}

						wechatContact.setContactList(contactList);
						wechatContact.setMemberList(memberList);
						
						this.getGroup(wechatMeta, wechatContact);
						
						return wechatContact;
					}
				}
			}
		} catch (Exception e) {
			throw new WechatException(e);
		}
		return null;
	}

	private void getGroup(WechatMeta wechatMeta, WechatContact wechatContact) {
		String url = wechatMeta.getBase_uri() + "/webwxbatchgetcontact?type=ex&pass_ticket=" + wechatMeta.getPass_ticket() + "&skey="
				+ wechatMeta.getSkey() + "&r=" + DateKit.getCurrentUnixTime();

		JSONObject body = new JSONObject();
		body.put("BaseRequest", wechatMeta.getBaseRequest());

		HttpRequest request = HttpRequest.post(url).contentType("application/json;charset=utf-8")
				.header("Cookie", wechatMeta.getCookie()).send(body.toString());
		
		LOGGER.debug(request.toString());
		String res = request.body();
		request.disconnect();

		if (StringKit.isBlank(res)) {
			throw new WechatException("获取群信息失败");
		}
		
		LOGGER.debug(res);
		
		try {
			JSONObject jsonObject = JSONKit.parseObject(res);
			JSONObject BaseResponse = jsonObject.get("BaseResponse").asJSONObject();
			if (null != BaseResponse) {
				int ret = BaseResponse.getInt("Ret", -1);
				if (ret == 0) {
					JSONArray memberList = jsonObject.get("MemberList").asArray();
					JSONArray contactList = new JSONArray();
					
					if (null != memberList) {
						for (int i = 0, len = memberList.size(); i < len; i++) {
							JSONObject contact = memberList.get(i).asJSONObject();
							// 公众号/服务号
							if (contact.getInt("VerifyFlag", 0) == 8) {
								continue;
							}
							// 特殊联系人
							if (Constant.FILTER_USERS.contains(contact.getString("UserName"))) {
								continue;
							}
							// 群聊
							if (contact.getString("UserName").indexOf("@@") != -1) {
								continue;
							}
							// 自己
							if (contact.getString("UserName").equals(wechatMeta.getUser().getString("UserName"))) {
								continue;
							}
							contactList.add(contact);
						}
						
						wechatContact.setContactList(contactList);
						wechatContact.setMemberList(memberList);
					}
				}
			}
		} catch (Exception e) {
			throw new WechatException(e);
		}
	}

	/**
	 * 获取UUID
	 */
	@Override
	public String getUUID() throws WechatException {
		HttpRequest request = HttpRequest.get(Constant.JS_LOGIN_URL, true, "appid", "wx782c26e4c19acffb", "fun", "new",
				"lang", "zh_CN", "_", DateKit.getCurrentUnixTime());
		
		LOGGER.debug(request.toString());
		
		String res = request.body();
		request.disconnect();

		if (StringKit.isNotBlank(res)) {
			String code = Matchers.match("window.QRLogin.code = (\\d+);", res);
			if (null != code) {
				if (code.equals("200")) {
					return Matchers.match("window.QRLogin.uuid = \"(.*)\";", res);
				} else {
					throw new WechatException("错误的状态码: " + code);
				}
			}
		}
		throw new WechatException("获取UUID失败");
	}

	/**
	 * 打开状态提醒
	 */
	@Override
	public void openStatusNotify(WechatMeta wechatMeta) throws WechatException {

		String url = wechatMeta.getBase_uri() + "/webwxstatusnotify?lang=zh_CN&pass_ticket=" + wechatMeta.getPass_ticket();

		JSONObject body = new JSONObject();
		body.put("BaseRequest", wechatMeta.getBaseRequest());
		body.put("Code", 3);
		body.put("FromUserName", wechatMeta.getUser().getString("UserName"));
		body.put("ToUserName", wechatMeta.getUser().getString("UserName"));
		body.put("ClientMsgId", DateKit.getCurrentUnixTime());

		HttpRequest request = HttpRequest.post(url).contentType("application/json;charset=utf-8")
				.header("Cookie", wechatMeta.getCookie()).send(body.toString());

		LOGGER.debug("" + request);
		String res = request.body();
		request.disconnect();

		if (StringKit.isBlank(res)) {
			throw new WechatException("状态通知开启失败");
		}

		try {
			JSONObject jsonObject = JSONKit.parseObject(res);
			JSONObject BaseResponse = jsonObject.get("BaseResponse").asJSONObject();
			if (null != BaseResponse) {
				int ret = BaseResponse.getInt("Ret", -1);
				if (ret != 0) {
					throw new WechatException("状态通知开启失败，ret：" + ret);
				}
			}
		} catch (Exception e) {
			throw new WechatException(e);
		}
	}

	/**
	 * 微信初始化
	 */
	@Override
	public void wxInit(WechatMeta wechatMeta) throws WechatException {
		String url = wechatMeta.getBase_uri() + "/webwxinit?r=" + DateKit.getCurrentUnixTime() + "&pass_ticket="
				+ wechatMeta.getPass_ticket() + "&skey=" + wechatMeta.getSkey();

		JSONObject body = new JSONObject();
		body.put("BaseRequest", wechatMeta.getBaseRequest());

		HttpRequest request = HttpRequest.post(url).contentType("application/json;charset=utf-8")
				.header("Cookie", wechatMeta.getCookie()).send(body.toString());
		
		LOGGER.debug("" + request);
		String res = request.body();
		request.disconnect();

		if (StringKit.isBlank(res)) {
			throw new WechatException("微信初始化失败");
		}

		try {
			JSONObject jsonObject = JSONKit.parseObject(res);
			if (null != jsonObject) {
				JSONObject BaseResponse = jsonObject.get("BaseResponse").asJSONObject();
				if (null != BaseResponse) {
					int ret = BaseResponse.getInt("Ret", -1);
					if (ret == 0) {
						wechatMeta.setSyncKey(jsonObject.get("SyncKey").asJSONObject());
						wechatMeta.setUser(jsonObject.get("User").asJSONObject());

						StringBuffer synckey = new StringBuffer();
						JSONArray list = wechatMeta.getSyncKey().get("List").asArray();
						for (int i = 0, len = list.size(); i < len; i++) {
							JSONObject item = list.get(i).asJSONObject();
							synckey.append("|" + item.getInt("Key", 0) + "_" + item.getInt("Val", 0));
						}
						wechatMeta.setSynckey(synckey.substring(1));
					}
				}
			}
		} catch (Exception e) {
		}
	}
	
	/**
	 * 选择同步线路
	 */
	@Override
	public void choiceSyncLine(WechatMeta wechatMeta) throws WechatException {
		boolean enabled = false;
		for(String syncUrl : Constant.SYNC_HOST){
			int[] res = this.syncCheck(syncUrl, wechatMeta);
			if(res[0] == 0){
				String url = "https://" + syncUrl + "/cgi-bin/mmwebwx-bin";
				wechatMeta.setWebpush_url(url);
				LOGGER.info("选择线路：[{}]", syncUrl);
				enabled = true;
				break;
			}
		}
		if(!enabled){
			throw new WechatException("同步线路不通畅");
		}
	}
	
	/**
	 * 检测心跳
	 */
	@Override
	public int[] syncCheck(WechatMeta wechatMeta) throws WechatException{
		return this.syncCheck(null, wechatMeta);
	}
	
	/**
	 * 检测心跳
	 */
	private int[] syncCheck(String url, WechatMeta meta) throws WechatException{
		if(null == url){
			url = meta.getWebpush_url() + "/synccheck";
		} else{
			url = "https://" + url + "/cgi-bin/mmwebwx-bin/synccheck";
		}
		
		JSONObject body = new JSONObject();
		body.put("BaseRequest", meta.getBaseRequest());
		
		HttpRequest request = HttpRequest
				.get(url, true, "r", DateKit.getCurrentUnixTime() + StringKit.getRandomNumber(5), "skey",
						meta.getSkey(), "uin", meta.getWxuin(), "sid", meta.getWxsid(), "deviceid",
						meta.getDeviceId(), "synckey", meta.getSynckey(), "_", System.currentTimeMillis())
				.header("Cookie", meta.getCookie());

		LOGGER.debug(request.toString());
		
		String res = request.body();
		request.disconnect();

		int[] arr = new int[]{-1, -1};
		if (StringKit.isBlank(res)) {
			return arr;
		}
		
		String retcode = Matchers.match("retcode:\"(\\d+)\",", res);
		String selector = Matchers.match("selector:\"(\\d+)\"}", res);
		if (null != retcode && null != selector) {
			arr[0] = Integer.parseInt(retcode);
			arr[1] = Integer.parseInt(selector);
			return arr;
		}
		return arr;
	}
	
	boolean isStop = false;
	List<String> GroupONList = new ArrayList<String>();
	List<String> UserOFFList = new ArrayList<String>();
	String postfix = "(自动回复)";
	String unknown = "=unknown=";

	/**
	 * 处理消息
	 */
	@Override
	public void handleMsg(WechatMeta wechatMeta, JSONObject data) {
		if (null == data) {
			return;
		}

		JSONArray AddMsgList = data.get("AddMsgList").asArray();
		
		for (int i = 0, len = AddMsgList.size(); i < len; i++) {
			LOGGER.info("\n------------");
			JSONObject msg = AddMsgList.get(i).asJSONObject();
			int msgType = msg.getInt("MsgType", 0);
			String fullName = msg.getString("FromUserName");
			String name = getUserRemarkName(fullName); //remarkName
			String content = msg.getString("Content");
			String to = msg.getString("ToUserName");
			String me = wechatMeta.getUser().getString("UserName");
			
			if (msgType == 51) {
//				LOGGER.info("获取微信初始化消息");
			} else if (msgType == 1) {
				LOGGER.info("\n" +fullName + "\n" + name + ":\n" + content);
				if (UserOFFList.contains(fullName)) {
					continue;
				} else if (fullName.equals(me)) {
					if(content.equals("stop")){
						webwxsendmsg(wechatMeta, "（关闭机器人）", to);
						isStop=true;
					} else if(content.equals("start")){
						isStop=false;
						webwxsendmsg(wechatMeta, "（开启机器人）", to);
					} else if(content.equals("add")){
						if(!GroupONList.contains(to))
							GroupONList.add(to);
					} else if(content.equals("remove")){
						GroupONList.remove(to);
					} else if(content.equals("off")){
						if(!to.equals(me))
							if(!UserOFFList.contains(to))
								UserOFFList.add(to);
					} else if(content.equals("on")){
						UserOFFList.remove(to);
					}else if(content.equals("list-u")){
						String list = "";
						for(String str : UserOFFList)
						list = list + getUserRemarkName(str) + ", ";
						LOGGER.info("\nUserOFF:\n"+list);
						webwxsendmsg(wechatMeta, "UserOFFList:\n"+list, to);
					} else if(content.equals("list-g")){
						String list = "";
						for(String str : GroupONList)
						list = list + getUserRemarkName(str) + ", ";
						LOGGER.info("\nGroupON:\n"+list);
						webwxsendmsg(wechatMeta, "GroupONList:\n"+list, to);
					} else if(content.equals("reset")||content.equals("clear-a")||content.equals("clear-all")){
						UserOFFList.clear();
						GroupONList.clear();
						webwxsendmsg(wechatMeta, "(clear all list)", to);
					} else if(content.equals("clear-g")){
						GroupONList.clear();
						webwxsendmsg(wechatMeta, "(clear GroupONList)", to);
					} else if(content.equals("clear-u")){
						UserOFFList.clear();
						webwxsendmsg(wechatMeta, "(clear UserOFFList)", to);
					} else if(content.startsWith("postfix-")){
						postfix = content.substring(content.indexOf('-')+1, content.length());
						webwxsendmsg(wechatMeta, "set postfix= ", to);
						LOGGER.info("\n set postfix = " + postfix);
					} else if(content.startsWith("unknown-")){
						unknown = content.substring(content.indexOf('-')+1, content.length());
						webwxsendmsg(wechatMeta, "set unknown answer= " + unknown + "\n(set =unknown= will no respond)", to);
						LOGGER.info("\n set unknown answer= " + unknown);
					} else if(content.equals("help")){
						String ans = robot.talk(content);
						webwxsendmsg(wechatMeta, ans+"\n发stop停止，start开启\n在群里发add把群加入自动回复，remove移除\n在私聊里发off关闭某人的自动回复，on开启\nhttps://github.com/xyn3106/wechat-robot", to);
						LOGGER.info("\n自动回复to:\n" + to + "\n" + name + ":\n" + ans);
					} else if(content.startsWith("d")){
						String ans = robot.talk(content.substring(1));
						webwxsendmsg(wechatMeta, ans, to);
						LOGGER.info("\n自动回复to:\n" + to + "\n" + name + ":\n" + ans);
					}
				} else if (fullName.indexOf("@@") != -1) {
					if(GroupONList.contains(fullName)){
						String ans = robot.talk(content);
						webwxsendmsg(wechatMeta, ans, fullName);
						LOGGER.info("\n自动回复to:\n" + fullName + "\n" + name + ":\n" + ans);
					}
//					String[] peopleContent = content.split(":<br/>");
//					LOGGER.info("|" + name + "| \n" + peopleContent[0] + ":\n" + peopleContent[1].replace("<br/>", "\n"));
				} else {
					String ans = robot.talk(content);
					webwxsendmsg(wechatMeta, ans, fullName);
					LOGGER.info("\n自动回复to:\n" + fullName + "\n" + name + ":\n" + ans);
				}
			} else if (msgType == 3) {
				String imgDir = Constant.config.get("app.img_path");
				String msgId = msg.getString("MsgId");
				FileKit.createDir(imgDir, false);
				String imgUrl = wechatMeta.getBase_uri() + "/webwxgetmsgimg?MsgID=" + msgId + "&skey=" + wechatMeta.getSkey() + "&type=slave";
				HttpRequest.get(imgUrl).header("Cookie", wechatMeta.getCookie()).receive(new File(imgDir + "/" + msgId+".jpg"));
				LOGGER.info("\n" +fullName + "\n" + name + ":\n" + " 发送了-图片");
//				webwxsendmsg(wechatMeta, "= =", msg.getString("FromUserName"));
			} else if (msgType == 34) {
				LOGGER.info("\n" +fullName + "\n" + name + ":\n" + " 发了-语音");
//				webwxsendmsg(wechatMeta, "= =", msg.getString("FromUserName"));
			} else if (msgType == 42) {
				LOGGER.info("\n" +fullName + "\n" + name + ":\n" + " 发送了一张名片");
			} else {
				LOGGER.info("\n unknown msgType: "+msgType);
			}
}
	}
	
	/**
	 * 发送消息
	 */
	private void webwxsendmsg(WechatMeta meta, String content, String ToUserName) {
		if(isStop)
			return;
		if(content.contains("=unknown=")){
			if(unknown.equals("=unknown="))
				return;
			else
				content = unknown;
		}
		
		String url = meta.getBase_uri() + "/webwxsendmsg?lang=zh_CN&pass_ticket=" + meta.getPass_ticket();
		JSONObject body = new JSONObject();

		String clientMsgId = DateKit.getCurrentUnixTime() + StringKit.getRandomNumber(5);
		JSONObject Msg = new JSONObject();
		Msg.put("Type", 1);
		Msg.put("Content", content + postfix);
		Msg.put("FromUserName", meta.getUser().getString("UserName"));
		Msg.put("ToUserName", ToUserName);
		Msg.put("LocalID", clientMsgId);
		Msg.put("ClientMsgId", clientMsgId);
		body.put("BaseRequest", meta.getBaseRequest());
		body.put("Msg", Msg);
		
		HttpRequest request = HttpRequest.post(url).contentType("application/json;charset=utf-8")
				.header("Cookie", meta.getCookie()).send(body.toString());
		LOGGER.debug("" + request);
		request.body();
		request.disconnect();
	}
	
	private String getUserRemarkName(String id) {
		String name = "这个东西名字未知";
		for (int i = 0, len = Constant.CONTACT.getMemberList().size(); i < len; i++) {
			JSONObject member = Constant.CONTACT.getMemberList().get(i).asJSONObject();
			if (member.getString("UserName").equals(id)) {
				if (StringKit.isNotBlank(member.getString("RemarkName"))) {
					name = member.getString("RemarkName");
				} else {
					name = member.getString("NickName");
				}
				return name;
			}
		}
		return name;
	}
	
	@Override
	public JSONObject webwxsync(WechatMeta meta) throws WechatException{
		
		String url = meta.getBase_uri() + "/webwxsync?skey=" + meta.getSkey() + "&sid=" + meta.getWxsid();
		
		JSONObject body = new JSONObject();
		body.put("BaseRequest", meta.getBaseRequest());
		body.put("SyncKey", meta.getSyncKey());
		body.put("rr", DateKit.getCurrentUnixTime());

		HttpRequest request = HttpRequest.post(url).contentType("application/json;charset=utf-8")
				.header("Cookie", meta.getCookie()).send(body.toString());
		
		LOGGER.debug(request.toString());
		String res = request.body();
		request.disconnect();

		if (StringKit.isBlank(res)) {
			throw new WechatException("同步syncKey失败");
		}
		
		JSONObject jsonObject = JSONKit.parseObject(res);
		JSONObject BaseResponse = jsonObject.get("BaseResponse").asJSONObject();
		if (null != BaseResponse) {
			int ret = BaseResponse.getInt("Ret", -1);
			if (ret == 0) {
				meta.setSyncKey(jsonObject.get("SyncKey").asJSONObject());
				StringBuffer synckey = new StringBuffer();
				JSONArray list = meta.getSyncKey().get("List").asArray();
				for (int i = 0, len = list.size(); i < len; i++) {
					JSONObject item = list.get(i).asJSONObject();
					synckey.append("|" + item.getInt("Key", 0) + "_" + item.getInt("Val", 0));
				}
				meta.setSynckey(synckey.substring(1));
				return jsonObject;
			}
		}
		return null;
	}
	
}
