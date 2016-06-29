package com.galaxyinternet.project.dao;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import com.galaxyinternet.bo.project.ProjectBo;
import com.galaxyinternet.dao.project.ProjectDao;
import com.galaxyinternet.framework.core.constants.SqlId;
import com.galaxyinternet.framework.core.dao.BaseDao;
import com.galaxyinternet.framework.core.dao.impl.BaseDaoImpl;
import com.galaxyinternet.framework.core.exception.DaoException;
import com.galaxyinternet.framework.core.model.Page;
import com.galaxyinternet.framework.core.utils.BeanUtils;
import com.galaxyinternet.model.project.Project;
import com.galaxyinternet.vo.ProjectVO;


@Repository("projectDao")
public class ProjectDaoImpl extends BaseDaoImpl<Project, Long> implements ProjectDao {
	
	
	@Override
	public List<Project> selectProjectByMap(ProjectBo query) {
		Assert.notNull(query);
		Map<String, Object> params = BeanUtils.toMap(query);
		try {
		return sqlSessionTemplate.selectList(getSqlName(SqlId.SQL_SELECT),params);
		} catch (Exception e) {
			throw new DaoException(String.format("查询对象列表出错：%s", getSqlName(SqlId.SQL_SELECT)), e);
		}
	}

	@Override
	public Project selectTotalSummary(ProjectBo query) {
		return sqlSessionTemplate.selectOne(getSqlName("selectTotalSummary"),query);
	}

	@Override
	public List<Project> selectStageSummary(ProjectBo query) {
		return sqlSessionTemplate.selectList(getSqlName("selectStageSummary"),query);
	}

	@Override
	public long insertProject(Project project) {
		Assert.notNull(project);
		try {
			/*ID id = entity.getId();
			if (null == id) {
				if (StringUtils.isBlank(stringId)) {
					entity.setId((ID) generateId());
				}
			}*/
			sqlSessionTemplate.insert(getSqlName(SqlId.SQL_INSERT), project);
			return project.getId();
		} catch (Exception e) {
			throw new DaoException(String.format("添加对象出错！语句：%s", getSqlName(SqlId.SQL_INSERT)), e);
		}
	}

	@Override
	public List<Project> selectListById(List<Long> idList) {
		return sqlSessionTemplate.selectList(getSqlName("selectListById"),idList);
	}

	@Override
	public List<Long> queryProjectByUserId(Project project) {
		return sqlSessionTemplate.selectList(getSqlName("selectByUserId"),project);
	}
	
	public long queryCountProject(ProjectBo query){
		Assert.notNull(query);
		try{
			long count = sqlSessionTemplate.selectOne(getSqlName("selectCountRecordByParam"), query);
		   return count;
		}catch(Exception e){
			throw new DaoException(String.format("根据项目状态统计数据量出错！语句:%s" ,  getSqlName("selectCountRecordByParam")), e);
		}
		
	}

	@Override
	public Page<ProjectVO> queryPagingList(ProjectBo query , Pageable pageable) {
		// TODO Auto-generated method stub
		Assert.notNull(query);		
		try {
			List<ProjectVO> contentList = sqlSessionTemplate.selectList( getSqlName("selectPagingProjectList"),getParams(query, pageable));
					
			return new  Page<ProjectVO>(contentList, pageable, this.selectCount(query));
		} catch (Exception e) {
			throw new DaoException(String.format("根据分页对象查询列表出错！语句:%s", getSqlName("selectPagingProjectList")), e);
		}
		
	}


}