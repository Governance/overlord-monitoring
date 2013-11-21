/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.overlord.monitoring.ui.server.services.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.overlord.monitoring.ui.client.shared.beans.SituationsFilterBean;
import org.overlord.monitoring.ui.server.i18n.Messages;
import org.overlord.rtgov.activity.model.ActivityType;
import org.overlord.rtgov.activity.model.ActivityTypeId;
import org.overlord.rtgov.activity.model.ActivityUnit;
import org.overlord.rtgov.analytics.situation.Situation;
import org.overlord.rtgov.analytics.situation.Situation.Severity;

/**
 * This class provides access to the RTGov db.
 *
 */
public class RTGovRepository {

    private static final int MILLISECONDS_PER_DAY = 86400000;
	private static final String OVERLORD_RTGOV_DB = "overlord-rtgov-situations"; //$NON-NLS-1$
    private static volatile Messages i18n = new Messages();

    private EntityManagerFactory _entityManagerFactory=null;

    private static final Logger LOG=Logger.getLogger(RTGovRepository.class.getName());

    /**
     * The situation repository constructor.
     */
    public RTGovRepository() {
    	init();
    }

    /**
     * Initialize the situation repository.
     */
    protected void init() {
        _entityManagerFactory = Persistence.createEntityManagerFactory(OVERLORD_RTGOV_DB);
    }

    /**
     * This method returns an entity manager.
     *
     * @return The entity manager
     */
    protected EntityManager getEntityManager() {
        return (_entityManagerFactory.createEntityManager());
    }

    /**
     * This method closes the supplied entity manager.
     *
     * @param em The entity manager
     */
    protected void closeEntityManager(EntityManager em) {
        if (em != null) {
            em.close();
        }
    }

    /**
     * This method returns the situation associated with the supplied id.
     *
     * @param id The id
     * @return The situation, or null if not found
     * @throws Exception Failed to get situation
     */
    public Situation getSituation(String id) throws Exception {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(i18n.format("RTGovRepository.GetSit", id)); //$NON-NLS-1$
        }

        EntityManager em=getEntityManager();

        Situation ret=null;

        try {
            ret=(Situation)em.createQuery("SELECT sit FROM Situation sit " //$NON-NLS-1$
                                +"WHERE sit.id = '"+id+"'") //$NON-NLS-1$ //$NON-NLS-2$
                                .getSingleResult();

            if (LOG.isLoggable(Level.FINEST)) {
                LOG.finest(i18n.format("RTGovRepository.Result", ret)); //$NON-NLS-1$
            }
        } finally {
            closeEntityManager(em);
        }

        return (ret);
    }

    /**
     * 
     */
    @SuppressWarnings("unchecked")
    public SituationsResult getSituations(SituationsFilterBean filter, int page, int pageSize,
    			String sortColumn, boolean ascending) throws Exception {
    	SituationsResult ret=null;

        EntityManager em=getEntityManager();

        try {
        	// Build the query string
        	StringBuffer queryString=new StringBuffer();

        	if (filter.getSeverity() != null && filter.getSeverity().trim().length() > 0) {
        		queryString.append("sit.severity = :severity "); //$NON-NLS-1$
        	}

        	if (filter.getType() != null && filter.getType().trim().length() > 0) {
        		if (queryString.length() > 0) {
        			queryString.append("AND "); //$NON-NLS-1$
        		}
        		queryString.append("sit.type = '"+filter.getType()+"' ");  //$NON-NLS-1$//$NON-NLS-2$
        	}

        	if (filter.getTimestampFrom() != null) {
        		if (queryString.length() > 0) {
        			queryString.append("AND "); //$NON-NLS-1$
        		}
        		queryString.append("sit.timestamp >= "+filter.getTimestampFrom().getTime()+" ");  //$NON-NLS-1$//$NON-NLS-2$
        	}

        	if (filter.getTimestampTo() != null) {
        		if (queryString.length() > 0) {
        			queryString.append("AND "); //$NON-NLS-1$
        		}
        		// NOTE: As only the day is returned currently, will need to add a day on, so that
        		// the 'to' time represents the end of the day.
        		queryString.append("sit.timestamp <= "+(filter.getTimestampTo().getTime()+MILLISECONDS_PER_DAY)+" ");  //$NON-NLS-1$//$NON-NLS-2$
        	}

        	if (queryString.length() > 0) {
        		queryString.insert(0, "WHERE "); //$NON-NLS-1$
        	}

        	queryString.insert(0, "SELECT sit from Situation sit "); //$NON-NLS-1$
        	
        	if (sortColumn == null) {
        		sortColumn = "timestamp";
        	}
        	
        	queryString.append(" ORDER BY sit."+sortColumn+(ascending?"":" DESC"));

        	Query query=em.createQuery(queryString.toString());

        	if (filter.getSeverity() != null && filter.getSeverity().trim().length() > 0) {
        		String severityName=Character.toUpperCase(filter.getSeverity().charAt(0))
        						+filter.getSeverity().substring(1);
        		Severity severity=Severity.valueOf(severityName);

        		query.setParameter("severity", severity); //$NON-NLS-1$
        	}

            java.util.List<Situation> situations = query.getResultList();
            
            java.util.List<Situation> subset=new java.util.ArrayList<Situation>();
            
            for (int i=0; i < pageSize; i++) {
            	int pos=((page-1)*pageSize)+i;
            	
            	if (pos < situations.size()) {
            		subset.add(situations.get(pos));
            	} else {
            		break;
            	}
            }
            
            ret = new SituationsResult(subset, situations.size());

            if (LOG.isLoggable(Level.FINEST)) {
                LOG.finest(i18n.format("RTGovRepository.SitResult", ret)); //$NON-NLS-1$
            }
        } finally {
            closeEntityManager(em);
        }

        return (ret);
    }

    /**
     * This method returns the situation associated with the supplied id.
     *
     * @param id The id
     * @return The situation, or null if not found
     * @throws Exception Failed to get situation
     */
    public ActivityUnit getActivityUnit(String id) throws Exception {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(i18n.format("RTGovRepository.GetAU", id)); //$NON-NLS-1$
        }

        EntityManager em=getEntityManager();

        ActivityUnit ret=null;

        try {
            ret=(ActivityUnit)em.createQuery("SELECT au FROM ActivityUnit au " //$NON-NLS-1$
                                +"WHERE au.id = '"+id+"'") //$NON-NLS-1$ //$NON-NLS-2$
                                .getSingleResult();

            if (LOG.isLoggable(Level.FINEST)) {
                LOG.finest(i18n.format("RTGovRepository.Result", ret)); //$NON-NLS-1$
            }
        } finally {
            closeEntityManager(em);
        }

        return (ret);
    }

    /**
     * This method returns the situation associated with the supplied id.
     *
     * @param id The id
     * @return The situation, or null if not found
     * @throws Exception Failed to get situation
     */
    public ActivityType getActivityType(ActivityTypeId id) throws Exception {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(i18n.format("RTGovRepository.GetAT", id)); //$NON-NLS-1$
        }

        ActivityType ret=null;
        
        ActivityUnit au=getActivityUnit(id.getUnitId());
        
        if (au != null && id.getUnitIndex() < au.getActivityTypes().size()) {
        	ret = au.getActivityTypes().get(id.getUnitIndex());
        }
        
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(i18n.format("RTGovRepository.Result", ret)); //$NON-NLS-1$
        }

        return (ret);
    }

    /**
     * This class provides the situation results.
     *
     */
    public static class SituationsResult {
    	
    	private java.util.List<Situation> _situations=null;
    	private int _totalCount=0;
    	
    	/**
    	 * This is the constructor for the situation results.
    	 * 
    	 * @param situations The situations relevant for the requested page
    	 * @param total The total number
    	 */
    	public SituationsResult(java.util.List<Situation> situations, int total) {
    		_situations = situations;
    		_totalCount = total;
    	}
    	
    	/**
    	 * This method returns the list of situations for the
    	 * selected page.
    	 * 
    	 * @return The situations
    	 */
    	public java.util.List<Situation> getSituations() {
    		return (_situations);
    	}
    	
    	/**
    	 * This method returns the total number of situations available.
    	 * 
    	 * @return The total number of situations
    	 */
    	public int getTotalCount() {
    		return (_totalCount);
    	}
    }
}