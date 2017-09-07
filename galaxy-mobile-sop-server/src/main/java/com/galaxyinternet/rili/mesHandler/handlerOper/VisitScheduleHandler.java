package com.galaxyinternet.rili.mesHandler.handlerOper;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.galaxyinternet.rili.mesHandler.ScheduleMessageHandler;
import com.galaxyinternet.rili.model.ScheduleInfo;
import com.galaxyinternet.rili.model.ScheduleMessage;
import com.galaxyinternet.rili.model.ScheduleMessageUser;
import com.galaxyinternet.rili.util.UtilOper;


@Component
public class VisitScheduleHandler implements ScheduleMessageHandler
{
	private static final long serialVersionUID = 1L;
	final Logger logger = LoggerFactory.getLogger(VisitScheduleHandler.class);
	
	
	private String add_com_schedule  = "1.4.1";
	private String edit_com_schedule = "1.4.2";
	private String del_com_schedule  = "1.4.3";
	
	
	private Map<String,String> map = new HashMap<String,String>();
	public VisitScheduleHandler(){
		map.put(add_com_schedule,  "新增   拜访日程 （ 拜访日程）");
		map.put(edit_com_schedule, "修改   拜访日程 （ 拜访日程）");
		map.put(del_com_schedule,  "删除   拜访日程 （ 拜访日程）");
	}
	

	public boolean support(Object info) {
		ScheduleInfo message = (ScheduleInfo) info;
		return  message != null && (map.containsKey(message.getMessageType()) || message.getMessageType().startsWith("1.4"));
	}

	
	
	// 您有一个日程将于①明日（2017-12-12） ②3:00开始，日程名称“③XXXXX”。 
	public void handle(ScheduleMessage message,Object info) {
		
		ScheduleInfo model = (ScheduleInfo) info;
		
		String startTime = model.getStartTime().replace("/","-");
		if(startTime.indexOf(":00.0")!=-1){
			startTime = startTime.replace(":00.0", "");
		}
		byte isAllday = model.getIsAllday(); //是否全天 0:否 1:是
		Long dictId = model.getWakeupId();
		Long info_id = model.getId();
		
		//消息内容
		message.setCategory((byte) 0);  // 0:操作消息  1:系统消息
		message.setType("1.4");         // 消息类型  日程(1.1:会议  1.2:拜访  1.3:其它)
		message.setRemarkId(info_id);
		message.setCreatedUid(model.getCreatedId());
		message.setCreatedUname(model.getUserName());
		
		StringBuffer content = new StringBuffer();
		//if(model.getMessageType().equals(add_com_schedule)){
			content.append("您有一个拜访事项将于 ");
			content.append("<time>").append(startTime).append("</time>");
			//content.append("(拜访开始时间) 开始，");
			content.append(" 开始，");
			//content.append("拜访人\"").append("<name>").append(model.getSchedulePerson()).append("</name>(拜访对象姓名)\"。");
			content.append("拜访人\"").append("<name>").append(model.getSchedulePerson()).append("</name>\"。");
		//}
		message.setContent(content.toString());
		
		
		//消息推送时间
		try {
			long sendTime = UtilOper.getSendTimeBy(startTime, isAllday, dictId);
			if(sendTime<System.currentTimeMillis()){
				return;
			}
			message.setSendTime(sendTime);
		} catch (ParseException e) {
			message.setSendTime(null);
			logger.error("CommonScheduleHandler . handle sendtime 异常 ",e.getMessage());
		}
		
		
		//消息接收人
		List<ScheduleMessageUser> toUsers = new ArrayList<ScheduleMessageUser>();
		ScheduleMessageUser toU = new ScheduleMessageUser();
		toU.setUid(message.getCreatedUid());
		toU.setUname(message.getCreatedUname());
		toUsers.add(toU);
		
		message.setToUsers(toUsers);
	}
	
	

}
