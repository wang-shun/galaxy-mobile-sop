package com.galaxyinternet.vo;

import com.galaxyinternet.framework.core.model.BaseEntity;
import com.galaxyinternet.framework.core.model.Page;
/**
 * 移动端项目列表综合信息视图Bean
 * @author LZJ
 * @ClassName  : GeneralProjectVO  
 * @Version  版本   
 * @ModifiedBy修改人  
 * @Copyright  Galaxyinternet  
 * @date  2016年6月17日 上午10:09:20
 */
public class GeneralProjectVO extends BaseEntity{
	   
	private static final long serialVersionUID = 5111689293108636485L;
   /** 跟进中状态的项目总数 */	    
   private Long gjzCount;
   /** 投后运营的项目总数 */	    
   private Long thyyCount;	 
   /** 已否决的项目总数  */	
   private Long yfjCount;
   /** 项目列表分页Bean */
   private Page<ProjectVO> pvPage;//
   
	public Long getGjzCount() {
		return gjzCount;
	}
	public void setGjzCount(Long gjzCount) {
		this.gjzCount = gjzCount;
	}
	public Long getThyyCount() {
		return thyyCount;
	}
	public void setThyyCount(Long thyyCount) {
		this.thyyCount = thyyCount;
	}
	public Long getYfjCount() {
		return yfjCount;
	}
	public void setYfjCount(Long yfjCount) {
		this.yfjCount = yfjCount;
	}
	public Page<ProjectVO> getPvPage() {
		return pvPage;
	}
	public void setPvPage(Page<ProjectVO> pvPage) {
		this.pvPage = pvPage;
	}
	
}
