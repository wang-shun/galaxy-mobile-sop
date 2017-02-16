package com.galaxyinternet.project.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.galaxyinternet.bo.project.MeetingRecordBo;
import com.galaxyinternet.bo.project.ProjectBo;
import com.galaxyinternet.common.annotation.LogType;
import com.galaxyinternet.common.controller.BaseControllerImpl;
import com.galaxyinternet.common.enums.DictEnum;
import com.galaxyinternet.common.utils.ControllerUtils;
import com.galaxyinternet.framework.core.constants.Constants;
import com.galaxyinternet.framework.core.file.OSSHelper;
import com.galaxyinternet.framework.core.file.UploadFileResult;
import com.galaxyinternet.framework.core.id.IdGenerator;
import com.galaxyinternet.framework.core.model.ResponseData;
import com.galaxyinternet.framework.core.model.Result;
import com.galaxyinternet.framework.core.model.Result.Status;
import com.galaxyinternet.framework.core.oss.OSSFactory;
import com.galaxyinternet.framework.core.service.BaseService;
import com.galaxyinternet.framework.core.utils.GSONUtil;
import com.galaxyinternet.framework.core.utils.JSONUtils;
import com.galaxyinternet.model.operationLog.UrlNumber;
import com.galaxyinternet.model.project.InterviewRecord;
import com.galaxyinternet.model.project.MeetingRecord;
import com.galaxyinternet.model.project.MeetingScheduling;
import com.galaxyinternet.model.project.Project;
import com.galaxyinternet.model.sopfile.AppSopFile;
import com.galaxyinternet.model.sopfile.SopFile;
import com.galaxyinternet.model.user.User;
import com.galaxyinternet.service.AppProjectMeetingService;
import com.galaxyinternet.service.InterviewRecordService;
import com.galaxyinternet.service.MeetingRecordService;
import com.galaxyinternet.service.MeetingSchedulingService;
import com.galaxyinternet.service.ProjectService;
/**
 * App端会议添加<br>
 * 含添加文字，和录音两种
 * @author LZJ
 * @ClassName  : AppProjectMeetingController
 * @Version  版本   1.0
 * @ModifiedBy LZJ  
 * @Copyright  Galaxyinternet  
 * @date  2016年5月13日 下午1:39:57
 */
@Controller
@RequestMapping("/galaxy/projectmeeting/approgress")
public class AppProjectMeetingController extends BaseControllerImpl<Project, ProjectBo>{

	final Logger logger = LoggerFactory.getLogger(AppProjectMeetingController.class);		
	@Autowired
	private ProjectService projectService;	
	@Autowired
	private MeetingRecordService meetingRecordService;	
	@Autowired
	private MeetingSchedulingService meetingSchedulingService;
	@Autowired
	com.galaxyinternet.framework.cache.Cache cache;
	@Autowired
	private AppProjectMeetingService appPmService;
	
	@Autowired
	private InterviewRecordService interviewRecordService;
	
	
	private String tempfilePath;

	public String getTempfilePath() {
		return tempfilePath;
	}
	@Value("${sop.oss.tempfile.path}")
	public void setTempfilePath(String tempfilePath) {
		this.tempfilePath = tempfilePath;
	}
	
	@Override
	protected BaseService<Project> getBaseService() {
		return this.projectService;
	}	
	
	 /**
		 * App端接口--添加会议(无录音文件)
		 * @param meetingRecord
		 * @param request
		 * @param response
		 * @return
		 */
		@com.galaxyinternet.common.annotation.Logger(operationScope = { LogType.LOG, LogType.MESSAGE })
		@ResponseBody
		@RequestMapping(value = "/addfilemeetByNofile", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
		public ResponseData<MeetingRecord> addIosFileMeetByNoFile(@RequestBody MeetingRecordBo meetingRecord,HttpServletRequest request,HttpServletResponse response  ) {
			
			ResponseData<MeetingRecord> responseBody = new ResponseData<MeetingRecord>();
			User user = (User) request.getSession().getAttribute(Constants.SESSION_USER_KEY);
			//参数是否空值检查
			if(meetingRecord.getProjectId() == null 
					|| meetingRecord.getMeetingDate() == null 
					|| StringUtils.isBlank(meetingRecord.getMeetingType())
					|| StringUtils.isBlank(meetingRecord.getMeetingResult())) {
				responseBody.setResult(new Result(Status.ERROR,null, "请完善会议信息，参数值不完整或缺失"));
				return responseBody;
			}
			//检查当前会议类型是否存在"已有通过的会议"，若有，则不能再添加会议纪要
			MeetingRecord mrQuery = new MeetingRecord();
			mrQuery.setProjectId(meetingRecord.getProjectId());
			mrQuery.setMeetingType(meetingRecord.getMeetingType());
			mrQuery.setMeetingResult(DictEnum.meetingResult.通过.getCode());
			Long mrCount = meetingRecordService.queryCount(mrQuery);
			if(mrCount != null && mrCount.longValue() > 0L)
			{
				responseBody.setResult(new Result(Status.ERROR, "","已有通过的会议，不能再添加会议纪要!"));
				return responseBody;
			}			
			//检查立项会、投决会、CEO评审是否存在待排期记录
			String currMeetingType = meetingRecord.getMeetingType().trim();
		if (currMeetingType.equals(DictEnum.meetingType.立项会.getCode())
				|| currMeetingType.equals(DictEnum.meetingType.投决会.getCode())
				|| currMeetingType.equals(DictEnum.meetingType.CEO评审.getCode())) {
				
				MeetingScheduling ms = new MeetingScheduling();
				ms.setProjectId(meetingRecord.getProjectId());
				ms.setMeetingType(meetingRecord.getMeetingType());
//				ms.setScheduleStatus(DictEnum.meetingSheduleResult.待排期.getCode());
				List<MeetingScheduling> mslist = meetingSchedulingService.queryList(ms);				
				if(mslist==null || mslist.isEmpty()){
					responseBody.setResult(new Result(Status.ERROR, "","未在排期池中，不能添加会议记录!"));
					return responseBody;
				}else{
					int scheduleStatus = mslist.get(0).getScheduleStatus().intValue();
//					String status = mslist.get(0).getStatus();
							
					if(scheduleStatus==DictEnum.meetingSheduleResult.已否决.getCode()){
						responseBody.setResult(new Result(Status.ERROR, "","项目排期结果己否决，不能添加会议记录!"));
						return responseBody;
					}
//					else if (scheduleStatus==DictEnum.meetingSheduleResult.已通过.getCode() && status .equals( DictEnum.meetingResult.通过.getCode() )){
//						responseBody.setResult(new Result(Status.ERROR, "","项目会议已通过，不能添加会议记录!"));
//						return responseBody;
//					}
				}
			}			
			try {
				String prograss = "";
				UrlNumber uNum = null;
				String messageType = null;
				if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.内评会.getCode())){       
					prograss = DictEnum.projectProgress.内部评审.getCode();                                 	
  				 uNum = UrlNumber.one;
					if(meetingRecord.getMeetingResult().equals(DictEnum.meetingResult.通过.getCode())){
						messageType = "6.3";
					}else{
						messageType = "4.1";
					}
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.CEO评审.getCode())){ 
					prograss = DictEnum.projectProgress.CEO评审.getCode(); 								
					uNum = UrlNumber.two;
					messageType = "4.2";
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.立项会.getCode())){	
					prograss = DictEnum.projectProgress.立项会.getCode(); 										
					uNum = UrlNumber.three;
					if(meetingRecord.getMeetingResult().equals(DictEnum.meetingResult.通过.getCode())){
						messageType = "6.5";
					}else{
						messageType = "4.3";
					}
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.投决会.getCode())){
					prograss = DictEnum.projectProgress.投资决策会.getCode(); 								
					uNum = UrlNumber.four;
					if(meetingRecord.getMeetingResult().equals(DictEnum.meetingResult.通过.getCode())){
						messageType = "6.8";
					}else{
						messageType = "4.4";
					}
				}				
				//检查project id 及Project 是否为空
				Project project = new Project();
				project = projectService.queryById(meetingRecord.getProjectId());
				String err = errMessage(project,user,prograss);
				if(err!=null && err.length()>0){
					responseBody.setResult(new Result(Status.ERROR,null, err));
					return responseBody;
				}			
				//保存
				Long id = null;			
				if(meetingRecord.getFkey()!=null){
					if( meetingRecord.getFileLength()==null||meetingRecord.getFname()==null){
						responseBody.setResult(new Result(Status.ERROR,null, "请完善附件信息"));
						return responseBody;
					}
					if(meetingRecord.getBucketName()==null){
						meetingRecord.setBucketName(OSSFactory.getDefaultBucketName());
					}		
							
					Map<String,String> nameMap = transFileNames(meetingRecord.getFname());
					SopFile sopFile = new SopFile();
					sopFile.setBucketName(meetingRecord.getBucketName());
					sopFile.setFileKey(meetingRecord.getFkey());
					sopFile.setFileLength(meetingRecord.getFileLength());
					sopFile.setFileName(nameMap.get("fileName"));
					sopFile.setFileSuffix(nameMap.get("fileSuffix"));
					
					sopFile.setProjectId(project.getId());
					sopFile.setProjectProgress(project.getProjectProgress());
					sopFile.setFileUid(user.getId());	 //上传人
					sopFile.setCareerLine(user.getDepartmentId());
					sopFile.setFileType(DictEnum.fileType.音频文件.getCode());   //存储类型
					sopFile.setFileSource(DictEnum.fileSource.内部.getCode());  //档案来源
					//sopFile.setFileWorktype(fileWorkType);    //业务分类
					sopFile.setFileStatus(DictEnum.fileStatus.已上传.getCode());  //档案状态					
//					id = meetingRecordService.insertMeet(meetingRecord,project,sopFile,equalNowPrograss);
					id = appPmService.addingMeeting(meetingRecord, project, sopFile);
				}else if(!ServletFileUpload.isMultipartContent(request)){
					SopFile file = new SopFile();
					file.setCareerLine(user.getDepartmentId());
					file.setFileUid(user.getId());
//					id = meetingRecordService.insertMeet(meetingRecord,project,file,equalNowPrograss);
					id = appPmService.addingMeeting(meetingRecord, project, file);
				}
				responseBody.setId(id);
				responseBody.setResult(new Result(Status.OK, ""));	
				
				ControllerUtils.setRequestParamsForMessageTip(request, null, project.getProjectName(), project.getId(), messageType, uNum);
			} catch (Exception e) {
				responseBody.setResult(new Result(Status.ERROR,null, "会议添加失败"));
				if(logger.isErrorEnabled()){
					logger.error("addfilemeet 会议添加失败 ",e);
				}
			}
			return responseBody;
		}	
		
		
		
		/**
		 * 2016/8/8  为会议添加消息提醒
		 * 
		 * App端接口--添加会议(无录音文件)新版本需要
		 * @param meetingRecord
		 * @param request
		 * @param response
		 * @return
		 */
		@com.galaxyinternet.common.annotation.Logger(operationScope = { LogType.LOG, LogType.MESSAGE })
		@ResponseBody
		@RequestMapping(value = "/addIosFileMeetByNoFileTwo", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
		public ResponseData<MeetingRecord> addIosFileMeetByNoFileTwo(@RequestBody MeetingRecordBo meetingRecord,HttpServletRequest request,HttpServletResponse response  ) {
			
			ResponseData<MeetingRecord> responseBody = new ResponseData<MeetingRecord>();
			User user = (User) request.getSession().getAttribute(Constants.SESSION_USER_KEY);
			//参数是否空值检查
			if(meetingRecord.getProjectId() == null 
					|| meetingRecord.getMeetingDate() == null 
					|| StringUtils.isBlank(meetingRecord.getMeetingType())
					|| StringUtils.isBlank(meetingRecord.getMeetingResult())) {
				responseBody.setResult(new Result(Status.ERROR,null, "请完善会议信息，参数值不完整或缺失"));
				return responseBody;
			}
			//检查当前会议类型是否存在"已有通过的会议"，若有，则不能再添加会议纪要
			MeetingRecord mrQuery = new MeetingRecord();
			mrQuery.setProjectId(meetingRecord.getProjectId());
			mrQuery.setMeetingType(meetingRecord.getMeetingType());
			mrQuery.setMeetingResult(DictEnum.meetingResult.通过.getCode());
			Long mrCount = meetingRecordService.queryCount(mrQuery);
			if(mrCount != null && mrCount.longValue() > 0L)
			{
				responseBody.setResult(new Result(Status.ERROR, "","已有通过的会议，不能再添加会议纪要!"));
				return responseBody;
			}			
			//检查立项会、投决会、CEO评审是否存在待排期记录
			String currMeetingType = meetingRecord.getMeetingType().trim();
		if (currMeetingType.equals(DictEnum.meetingType.立项会.getCode())
				|| currMeetingType.equals(DictEnum.meetingType.投决会.getCode())
				|| currMeetingType.equals(DictEnum.meetingType.CEO评审.getCode())) {
				
				MeetingScheduling ms = new MeetingScheduling();
				ms.setProjectId(meetingRecord.getProjectId());
				ms.setMeetingType(meetingRecord.getMeetingType());
//				ms.setScheduleStatus(DictEnum.meetingSheduleResult.待排期.getCode());
				List<MeetingScheduling> mslist = meetingSchedulingService.queryList(ms);				
				if(mslist==null || mslist.isEmpty()){
					responseBody.setResult(new Result(Status.ERROR, "","未在排期池中，不能添加会议记录!"));
					return responseBody;
				}else{
					int scheduleStatus = mslist.get(0).getScheduleStatus().intValue();
//					String status = mslist.get(0).getStatus();
							
					if(scheduleStatus==DictEnum.meetingSheduleResult.已否决.getCode()){
						responseBody.setResult(new Result(Status.ERROR, "","项目排期结果己否决，不能添加会议记录!"));
						return responseBody;
					}
//					else if (scheduleStatus==DictEnum.meetingSheduleResult.已通过.getCode() && status .equals( DictEnum.meetingResult.通过.getCode() )){
//						responseBody.setResult(new Result(Status.ERROR, "","项目会议已通过，不能添加会议记录!"));
//						return responseBody;
//					}
				}
			}			
			try {
				String prograss = "";
				UrlNumber uNum = null;
				String messageType = null;
				if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.内评会.getCode())){       
					prograss = DictEnum.projectProgress.内部评审.getCode();                                 	
  				 uNum = UrlNumber.one;
					if(meetingRecord.getMeetingResult().equals(DictEnum.meetingResult.通过.getCode())){
						messageType = "6.3";
					}else{
						messageType = "4.1";
					}
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.CEO评审.getCode())){ 
					prograss = DictEnum.projectProgress.CEO评审.getCode(); 								
					uNum = UrlNumber.two;
					messageType = "4.2";
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.立项会.getCode())){	
					prograss = DictEnum.projectProgress.立项会.getCode(); 										
					uNum = UrlNumber.three;
					if(meetingRecord.getMeetingResult().equals(DictEnum.meetingResult.通过.getCode())){
						messageType = "6.5";
					}else{
						messageType = "4.3";
					}
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.投决会.getCode())){
					prograss = DictEnum.projectProgress.投资决策会.getCode(); 								
					uNum = UrlNumber.four;
					if(meetingRecord.getMeetingResult().equals(DictEnum.meetingResult.通过.getCode())){
						messageType = "6.8";
					}else{
						messageType = "4.4";
					}
				}			
				//检查project id 及Project 是否为空
				Project project = new Project();
				project = projectService.queryById(meetingRecord.getProjectId());
				String err = errMessage(project,user,prograss);
				if(err!=null && err.length()>0){
					responseBody.setResult(new Result(Status.ERROR,null, err));
					return responseBody;
				}			
				//保存
				Long id = null;			
				if(meetingRecord.getFkey()!=null){
					if( meetingRecord.getFileLength()==null||meetingRecord.getFname()==null){
						responseBody.setResult(new Result(Status.ERROR,null, "请完善附件信息"));
						return responseBody;
					}
					if(meetingRecord.getBucketName()==null){
						meetingRecord.setBucketName(OSSFactory.getDefaultBucketName());
					}		
							
					Map<String,String> nameMap = transFileNames(meetingRecord.getFname());
					SopFile sopFile = new SopFile();
					sopFile.setBucketName(meetingRecord.getBucketName());
					sopFile.setFileKey(meetingRecord.getFkey());
					sopFile.setFileLength(meetingRecord.getFileLength());
					sopFile.setFileName(nameMap.get("fileName"));
					sopFile.setFileSuffix(nameMap.get("fileSuffix"));
					
					sopFile.setProjectId(project.getId());
					sopFile.setProjectProgress(project.getProjectProgress());
					sopFile.setFileUid(user.getId());	 //上传人
					sopFile.setCareerLine(user.getDepartmentId());
					sopFile.setFileType(DictEnum.fileType.音频文件.getCode());   //存储类型
					sopFile.setFileSource(DictEnum.fileSource.内部.getCode());  //档案来源
					//sopFile.setFileWorktype(fileWorkType);    //业务分类
					sopFile.setFileStatus(DictEnum.fileStatus.已上传.getCode());  //档案状态					
//					id = meetingRecordService.insertMeet(meetingRecord,project,sopFile,equalNowPrograss);
					id = appPmService.addMeeting(meetingRecord, project, sopFile);
				}else if(!ServletFileUpload.isMultipartContent(request)){
					SopFile file = new SopFile();
					file.setCareerLine(user.getDepartmentId());
					file.setFileUid(user.getId());
//					id = meetingRecordService.insertMeet(meetingRecord,project,file,equalNowPrograss);
					id = appPmService.addMeeting(meetingRecord, project, file);
				}
				responseBody.setId(id);
				responseBody.setResult(new Result(Status.OK, ""));	
				
				ControllerUtils.setRequestParamsForMessageTip(request, null, project.getProjectName(), project.getId(), messageType, uNum);
			} catch (Exception e) {
				responseBody.setResult(new Result(Status.ERROR,null, "会议添加失败"));
				if(logger.isErrorEnabled()){
					logger.error("addfilemeet 会议添加失败 ",e);
				}
			}
			return responseBody;
		}	
		//TODO
		/**
		 * App端接口--添加会议（有录音文件）
		 *  测试调用URL/galaxy/projectmeeting/approgress/addAudioFile
		 * @param sopFile
		 * @param request
		 * @return
		 */
		@ResponseBody
		@RequestMapping(value="/addAudioFile" , method=RequestMethod.POST , produces=MediaType.APPLICATION_JSON_VALUE)
		public ResponseData<AppSopFile> addAppAudioFile(@RequestBody AppSopFile sopFile, HttpServletRequest request){
			ResponseData<AppSopFile> responseBody = new ResponseData<AppSopFile>();			
			try {
				
				if(sopFile == null ){				
					responseBody.setResult(new Result(Status.ERROR, null, "请完善附件信息"));
					return responseBody;
				}
				//会议ID验证
				if(sopFile!=null && (sopFile.getMeetingId()==null||sopFile.getMeetingId()==0)){
					responseBody.setResult(new Result(Status.ERROR, null, "会议ID缺失"));
					return responseBody;
				}
				//会议验证
				MeetingRecord meeting = meetingRecordService.queryById(sopFile.getMeetingId());
				if(meeting == null || meeting.getId() == null){			
					responseBody.setResult(new Result(Status.ERROR, null, "会议记录为空"));
					return responseBody;
				}
				//project id 验证
				Project project = projectService.queryById(sopFile.getProjectId());
				if(project == null || project.getId() == null){
					responseBody.setResult(new Result(Status.ERROR, null, "关联所属项目记录为空"));
					return responseBody;
				}
				
				User user  = (User) request.getSession().getAttribute(Constants.SESSION_USER_KEY);
				Project  p1 = projectService.queryById(sopFile.getProjectId());
				
				appPmService.addToAudioFileByMeeting(p1, user.getDepartmentId(), user.getId(), sopFile);
				responseBody.setResult(new Result(Status.OK,null, "录音添加成功"));
			} catch (Exception e) {
				responseBody.setResult(new Result(Status.ERROR,null, "录音追加失败"));
				logger.error("App端录音追加失败",e);
			}
			return responseBody;
		}
		
		/**
		 * 
		 * 
		 * 以下的暂不使用
		 * 
		 * 
		 * 
		 * 
		 * App端接口--添加会议（有录音文件）
		 * @param meetingRecord
		 * @param request
		 * @param response
		 * @return
		 */
		@com.galaxyinternet.common.annotation.Logger(operationScope = { LogType.LOG, LogType.MESSAGE })
		@ResponseBody
		@RequestMapping(value = "/addfilemeetByFile", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
		public ResponseData<MeetingRecord> addIosFileMeet(MeetingRecordBo meetingRecord,HttpServletRequest request,HttpServletResponse response  ) {
			ResponseData<MeetingRecord> responseBody = new ResponseData<MeetingRecord>();
			User user = (User) request.getSession().getAttribute(Constants.SESSION_USER_KEY);
			if(meetingRecord.getProjectId() == null){
				String json = JSONUtils.getBodyString(request);
				meetingRecord = GSONUtil.fromJson(json, MeetingRecordBo.class);
			}
			if(meetingRecord.getProjectId() == null 
					|| meetingRecord.getMeetingDate() == null 
					|| meetingRecord.getMeetingType() == null 
					|| meetingRecord.getMeetingResult() == null ){
				responseBody.setResult(new Result(Status.ERROR,null, "请完善会议信息"));
				return responseBody;
			}
			//已有通过的会议，不能再添加会议纪要
			MeetingRecord mrQuery = new MeetingRecord();
			mrQuery.setProjectId(meetingRecord.getProjectId());
			mrQuery.setMeetingType(meetingRecord.getMeetingType());
			mrQuery.setMeetingResult(DictEnum.meetingResult.通过.getCode());
			Long mrCount = meetingRecordService.queryCount(mrQuery);
			if(mrCount != null && mrCount.longValue() > 0L)
			{
				responseBody.setResult(new Result(Status.ERROR, "","已有通过的会议，不能再添加会议纪要!"));
				return responseBody;
			}
			
			//排期池校验
			if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.立项会.getCode()) || meetingRecord.getMeetingType().equals(DictEnum.meetingType.投决会.getCode())){	
				MeetingScheduling ms = new MeetingScheduling();
				ms.setProjectId(meetingRecord.getProjectId());
				ms.setMeetingType(meetingRecord.getMeetingType());
				ms.setStatus(DictEnum.meetingResult.待定.getCode());
				List<MeetingScheduling> mslist = meetingSchedulingService.queryList(ms);
				if(mslist==null || mslist.isEmpty()){
					responseBody.setResult(new Result(Status.ERROR, "","未在排期池中，不能添加会议记录!"));
					return responseBody;
				}
			}
				
			try {
				String prograss = "";
//				UrlNumber uNum = null;
				if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.内评会.getCode())){       
					prograss = DictEnum.projectProgress.内部评审.getCode();                                 	
//					uNum = UrlNumber.one;
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.CEO评审.getCode())){ 
					prograss = DictEnum.projectProgress.CEO评审.getCode(); 								
//					uNum = UrlNumber.two;
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.立项会.getCode())){	
					prograss = DictEnum.projectProgress.立项会.getCode(); 										
//					uNum = UrlNumber.three;
				}else if(meetingRecord.getMeetingType().equals(DictEnum.meetingType.投决会.getCode())){
					prograss = DictEnum.projectProgress.投资决策会.getCode(); 								
//					uNum = UrlNumber.four;
				}
				
				//project id 验证
				Project project = new Project();
				project = projectService.queryById(meetingRecord.getProjectId());
				String err = errMessage(project,user,prograss);
				if(err!=null && err.length()>0){
					responseBody.setResult(new Result(Status.ERROR,null, err));
					return responseBody;
				}
				
				//保存
				Long id = null;
				boolean equalNowPrograss = true; //判断当前阶段、之后阶段
				int operationPro = Integer.parseInt(prograss.substring(prograss.length()-1)) ;//会议对应的阶段
				int projectPro = Integer.parseInt(project.getProjectProgress().substring(project.getProjectProgress().length()-1)) ; //项目阶段
				if(projectPro > operationPro){
					equalNowPrograss = false;
				}
				
				if(meetingRecord.getFkey()!=null){
					if( meetingRecord.getFileLength()==null||meetingRecord.getFname()==null){
						responseBody.setResult(new Result(Status.ERROR,null, "请完善附件信息"));
						return responseBody;
					}
					if(meetingRecord.getBucketName()==null){
						meetingRecord.setBucketName(OSSFactory.getDefaultBucketName());
					}		
							
					Map<String,String> nameMap = transFileNames(meetingRecord.getFname());
					SopFile sopFile = new SopFile();
					sopFile.setBucketName(meetingRecord.getBucketName());
					sopFile.setFileKey(meetingRecord.getFkey());
					sopFile.setFileLength(meetingRecord.getFileLength());
					sopFile.setFileName(nameMap.get("fileName"));
					sopFile.setFileSuffix(nameMap.get("fileSuffix"));
					
					sopFile.setProjectId(project.getId());
					sopFile.setProjectProgress(project.getProjectProgress());
					sopFile.setFileUid(user.getId());	 //上传人
					sopFile.setCareerLine(user.getDepartmentId());
					sopFile.setFileType(DictEnum.fileType.音频文件.getCode());   //存储类型
					sopFile.setFileSource(DictEnum.fileSource.内部.getCode());  //档案来源
					//sopFile.setFileWorktype(fileWorkType);    //业务分类
					sopFile.setFileStatus(DictEnum.fileStatus.已上传.getCode());  //档案状态
					
					id = meetingRecordService.insertMeet(meetingRecord,project,sopFile,equalNowPrograss);
				}else if(!ServletFileUpload.isMultipartContent(request)){
					SopFile file = new SopFile();
					file.setCareerLine(user.getDepartmentId());
					file.setFileUid(user.getId());
					id = meetingRecordService.insertMeet(meetingRecord,project,file,equalNowPrograss);
				}
				
				
				else if(ServletFileUpload.isMultipartContent(request)){
					//调接口上传
					String fileKey = String.valueOf(IdGenerator.generateId(OSSHelper.class));
					//Map<String,Object> map = sopFileService.aLiColoudUpload(request, fileKey);
					UploadFileResult result = uploadFileToOSS(request, fileKey, tempfilePath);
					//上传成功后
					if(result!=null){
						Map<String,String> nameMap = new HashMap<String,String>();
						//MultipartFile file = (MultipartFile) map.get("file");
						String fileName = "";
						if(meetingRecord.getFname()!=null && meetingRecord.getFname().trim().length()>0){
							fileName = meetingRecord.getFname().trim();
							nameMap = transFileNames(fileName);
						}else{
							//nameMap = (Map<String, String>) map.get("nameMap");
						    nameMap.put("fileName",result.getFileName());
						    nameMap.put("fileSuffix", result.getFileSuffix());
						}
						if(nameMap.get("fileName") == null || nameMap.get("fileName").trim().length()==0){
							responseBody.setResult(new Result(Status.ERROR,null, "文件名获取失败"));
							return responseBody;
						}//end get file name 
						
						SopFile sopFile = new SopFile();
						sopFile.setProjectId(project.getId());
						sopFile.setProjectProgress(project.getProjectProgress());
						sopFile.setBucketName(OSSFactory.getDefaultBucketName()); 
						sopFile.setFileKey(fileKey);  
						//sopFile.setFileLength(file.getSize());  //文件大小
						sopFile.setFileLength(result.getContentLength());  //文件大小
						sopFile.setFileName(nameMap.get("fileName"));
						sopFile.setFileSuffix(nameMap.get("fileSuffix"));
						sopFile.setFileUid(user.getId());	 //上传人
						sopFile.setCareerLine(user.getDepartmentId());
						sopFile.setFileType(DictEnum.fileType.音频文件.getCode());   //存储类型
						sopFile.setFileSource(DictEnum.fileSource.内部.getCode());  //档案来源
						//sopFile.setFileWorktype(fileWorkType);    //业务分类
						sopFile.setFileStatus(DictEnum.fileStatus.已上传.getCode());  //档案状态
						
						id = meetingRecordService.insertMeet(meetingRecord,project,sopFile,equalNowPrograss);
					}else{
						responseBody.setResult(new Result(Status.ERROR,null, "上传失败"));
						return responseBody;
					}
				}
			
				
				responseBody.setId(id);
				responseBody.setResult(new Result(Status.OK, ""));
				//responseBody.setEntity(meetingRecord);
				
				//ControllerUtils.setRequestParamsForMessageTip(request, project.getProjectName(), project.getId(),uNum);
			} catch (Exception e) {
				responseBody.setResult(new Result(Status.ERROR,null, "会议添加失败"));
				if(logger.isErrorEnabled()){
					logger.error("addfilemeet 会议添加失败 ",e);
				}
			}
			return responseBody;
		}
		
		public String errMessage(Project project,User user,String prograss){
			if(project == null){
				return "项目检索为空";
			}else if(project.getProjectStatus().equals(DictEnum.meetingResult.否决.getCode())){ //字典 项目状态 = 会议结论 关闭
				return "项目已经关闭";
			}
			if(user != null){
				if(project.getCreateUid()==null || user.getId().longValue()!=project.getCreateUid().longValue()){ 
					return "不允许操作他人项目";
				}
			}
			if(prograss != null){
				if(project.getProjectProgress()!=null){
					try {
						int operationPro = Integer.parseInt(prograss.substring(prograss.length()-1)) ;
						int projectPro = Integer.parseInt(project.getProjectProgress().substring(project.getProjectProgress().length()-1)) ;
						if(projectPro < operationPro){
							return "项目当前阶段不允许进行该操作";
						}
					} catch (Exception e) {
						return "项目阶段不和规范";
					}
				}else{
					return "项目阶段出错";
				}
			}
			
			return null;
		}
		
		private Map<String, String> transFileNames(String fileName) {
			Map<String, String> retMap = new HashMap<String, String>();
			int dotPos = fileName.lastIndexOf(".");
			if(dotPos == -1){
				retMap.put("fileName", fileName);
				retMap.put("fileSuffix", "");
			}else{
				retMap.put("fileName", fileName.substring(0, dotPos));
				retMap.put("fileSuffix", fileName.substring(dotPos+1));
			}
			return retMap;
		}
		

		
		/**
		 * 为满足app端修改访谈  2017/1/17
		 * @param interviewRecord
		 * @param request
		 * @return  2017/2/15修改 
		 */
		@com.galaxyinternet.common.annotation.Logger
		@ResponseBody
		@RequestMapping(value = "/updateInterview", produces = MediaType.APPLICATION_JSON_VALUE)
		public ResponseData<InterviewRecord> updateInterview(@RequestBody InterviewRecord interviewRecord, HttpServletRequest request ) {
			ResponseData<InterviewRecord> responseBody = new ResponseData<InterviewRecord>();
			User user = (User) getUserFromSession(request);
			// 项目创建者用户ID与当前登录人ID是否一样
			
			
				if(interviewRecord.getProjectId()!=null){
					Project p = projectService.queryById(interviewRecord.getProjectId());
					if (user.getId().longValue() != p.getCreateUid().longValue()) {					
						responseBody
								.setResult(new Result(Status.ERROR, null, "没有权限修改该项目!"));
						return responseBody;
					}
				}else{
					responseBody.setResult(new Result(Status.ERROR, "projectId参数缺失"));
					return responseBody;
				}
				
				if(interviewRecord.getId()==null){
					responseBody.setResult(new Result(Status.ERROR, "访谈id参数缺失"));
					return responseBody;
				//	responseBody.setId(interviewRecord.getId());
				}
				if(interviewRecord.getViewNotes()==null){
					interviewRecord.setViewNotes("");
				}
			try {								
				interviewRecordService.updateById(interviewRecord);
				responseBody.setResult(new Result(Status.OK, "修改访谈记录成功"));
				responseBody.setId(interviewRecord.getId());
							
			//	ControllerUtils.setRequestParamsForMessageTip(request, null, project.getProjectName(), project.getId(), "3", UrlNumber.one);
			} catch (Exception e) {
				responseBody.setResult(new Result(Status.ERROR,null, "修改访谈记录失败"));
				
				if(logger.isErrorEnabled()){
					logger.error("updateInterview 修改访谈记录失败",e);
				}
			}
			return responseBody;
		}
		/**
		 * 2017/1/17开发为了app端修改会议(包括创意也在这修改)
		 * @param meetingRecord
		 * @param request
		 * @return 2017/2/15修改 
		 */
		@com.galaxyinternet.common.annotation.Logger
		@ResponseBody
		@RequestMapping(value = "/updatemeet", produces = MediaType.APPLICATION_JSON_VALUE)
		public ResponseData<MeetingRecord> updatemeet(@RequestBody MeetingRecord meetingRecord, HttpServletRequest request ) {
			ResponseData<MeetingRecord> responseBody = new ResponseData<MeetingRecord>();
			
			User user = (User) getUserFromSession(request);
			// 项目创建者用户ID与当前登录人ID是否一样
			
			
				if(meetingRecord.getProjectId()!=null){
					
					Project p = projectService.queryById(meetingRecord.getProjectId());
					if (user.getId().longValue() != p.getCreateUid().longValue()) {
						responseBody
								.setResult(new Result(Status.ERROR, null, "没有权限修改该项目!"));
						return responseBody;
					}
				}else{
					responseBody.setResult(new Result(Status.ERROR, "projectId参数缺失"));
					return responseBody;
				}
			try {
				if(meetingRecord.getId()==null){
					responseBody.setResult(new Result(Status.ERROR,null, "主键缺失"));
					return responseBody;
				}
				if(meetingRecord.getMeetingNotes()==null){
					meetingRecord.setMeetingNotes("");
				}
				//RecordType { PROJECT((byte) 0, "项目"), IDEAS((byte) 1, "创意");
				meetingRecordService.updateById(meetingRecord);
			    responseBody.setResult(new Result(Status.OK, "修改会议纪要成功"));
				responseBody.setId(meetingRecord.getId());
				
				meetingRecord = meetingRecordService.queryById(meetingRecord.getId());
				//ControllerUtils.setRequestParamsForMessageTip(request, null, project.getProjectName(), project.getId(), messageType, UrlNumber.one);
			} catch (Exception e) {
				responseBody.setResult(new Result(Status.ERROR,null, "修改会议纪要失败"));
				logger.error("updatemeet 修改会议纪要失败",e);
			}
			return responseBody;
		}
		
		
		

}
