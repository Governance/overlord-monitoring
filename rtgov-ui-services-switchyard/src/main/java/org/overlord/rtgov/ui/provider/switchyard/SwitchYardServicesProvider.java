/*
 * Copyright 2013-4 Red Hat Inc
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
package org.overlord.rtgov.ui.provider.switchyard;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.management.AttributeList;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;

import org.overlord.rtgov.common.util.RTGovProperties;
import org.overlord.rtgov.ui.client.model.MessageBean;
import org.overlord.rtgov.ui.client.model.QName;
import org.overlord.rtgov.ui.client.model.ReferenceBean;
import org.overlord.rtgov.ui.client.model.ReferenceSummaryBean;
import org.overlord.rtgov.ui.client.model.ServiceBean;
import org.overlord.rtgov.ui.client.model.ServiceSummaryBean;
import org.overlord.rtgov.ui.client.model.ServicesFilterBean;
import org.overlord.rtgov.ui.client.model.UiException;
import org.overlord.rtgov.ui.provider.ServicesProvider;
import org.switchyard.remote.RemoteInvoker;
import org.switchyard.remote.RemoteMessage;
import org.switchyard.remote.http.HttpInvoker;

/**
 * This class provides a SwitchYard implementation of the ServicesProvider
 * interface obtaining its information via JMX.
 *
 */
public class SwitchYardServicesProvider implements ServicesProvider {
	
    private static volatile Messages i18n = new Messages();

	private static final String PROVIDER_NAME = "switchyard";

	// Properties
	private static final String SWITCHYARD_RESUBMIT_HANDLER_SERVER_URLS = "SwitchYardServiceProvider.serverURLs";
	private static final String SWITCHYARD_JMX_URL = "SwitchYardServiceProvider.serverJMX";

	protected static final String DEFAULT_REMOTE_INVOKER_URL = "http://localhost:8080/switchyard-remote";
	
	private String _serverURLs=null;
    
	private java.util.List<String> _urlList=new java.util.ArrayList<String>();
	
	private MBeanServerConnection _mbeanServerConnection;
	private String _serverJMX=null;

    private static final char ESCAPE_CHAR = '\\';
    private static final char SEPARATOR_CHAR = ':';

	/**
	 * {@inheritDoc}
	 */
	public String getName() {
		return PROVIDER_NAME;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isServiceKnown(String service) {
		// TODO:
		return (true);
	}
    
	@Override
	public boolean isResubmitSupported(String service, String operation) {
	    // TODO: proper determination of isResubmitSupported for service/operation
	    boolean isResubmitSupported = true;
	    return isServiceKnown(service) && isResubmitSupported;
	}

	/**
	 * This method sets the comma separated list of SwitchYard server URLs.
	 * 
	 * @param urls The server URLs
	 */
	public void setServerURLs(String urls) {
		synchronized (_urlList) {
			_serverURLs = urls;
			
			_urlList.clear();
		}
	}
	
	/**
	 * This method returns the comma separated list of SwitchYard server URLs.
	 * 
	 * @return The server URLs
	 */
	public String getServerURLs() {
		if (_serverURLs == null) {
			_serverURLs = RTGovProperties.getProperties().getProperty(SWITCHYARD_RESUBMIT_HANDLER_SERVER_URLS);
		}
		return (_serverURLs);
	}
	
	/**
	 * This method returns a list of URLs to use for a particular invocation.
	 * If multiple URLs are available, the list will round robin to balance the
	 * load - however if one URL fails, then the next one in the list will be
	 * tried until successful or end of list reached.
	 * 
	 * @return The list of URLs
	 */
	protected java.util.List<String> getURLList() {
		java.util.List<String> ret=null;
		
		synchronized (_urlList) {
			if (_urlList.size() == 0) {
				
				if (getServerURLs() != null && getServerURLs().trim().length() > 0) {
					String[] urls=getServerURLs().split("[, ]");
					
					for (int i=0; i < urls.length; i++) {
						String url=urls[i].trim();
						
						if (url.length() > 0) {
							_urlList.add(url);
						}
					}
					
				} else {
					_urlList.add(DEFAULT_REMOTE_INVOKER_URL);
				}
			}
			
			if (_urlList.size() == 1) {
				// Only one entry in the list, so just return it
				ret = _urlList;
			} else {
				ret = new java.util.ArrayList<String>(_urlList);
				
				Collections.rotate(_urlList, -1);
			}
		}
		
		return (ret);
	}
	
	/**
	 * This method sets the JMX server URL.
	 * 
	 * @param url The JMX server URL
	 */
	public void setServerJMX(String url) {
		_serverJMX = url;
	}
	
	/**
	 * This method returns the JMX server URL.
	 * 
	 * @return The JMX server URL
	 */
	public String getServerJMX() {
		if (_serverJMX == null && RTGovProperties.getProperties() != null) {
			_serverJMX = RTGovProperties.getProperties().getProperty(SWITCHYARD_JMX_URL);
		}
		return (_serverJMX);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void resubmit(String service, String operation, MessageBean message) throws UiException {

		// Currently assumes message is xml
		org.w3c.dom.Document doc=null;
		
		try {
			DocumentBuilder builder=DocumentBuilderFactory.newInstance().newDocumentBuilder();
			
			java.io.InputStream is=new java.io.ByteArrayInputStream(message.getContent().getBytes());
			
			doc = builder.parse(is);
			
			is.close();
		} catch (Exception e) {
			throw new UiException(e);
		}

		Object content=new DOMSource(doc.getDocumentElement());

		java.util.List<String> urls=getURLList();
		Exception exc=null;
		
		for (int i=0; i < urls.size(); i++) {
			try {
				// Create a new remote client invoker
				RemoteInvoker invoker = new HttpInvoker(urls.get(i));
				
				// Create the request message
				RemoteMessage rm = new RemoteMessage();
				rm.setService(javax.xml.namespace.QName.valueOf(service)).setOperation(operation).setContent(content);
		
				// Invoke the service
				RemoteMessage reply = invoker.invoke(rm);
				if (reply.isFault()) {
					if (reply.getContent() instanceof Exception) {
						throw new UiException((Exception)reply.getContent());
					}
					throw new UiException("Fault response received: "+reply.getContent());
				}
				
				// Clear previous exceptions
				exc = null;
				
				continue;
			} catch (java.io.IOException e) {
				exc = e;
			}
		}
		
		if (exc != null) {
			// Report exception
			throw new UiException(exc);
		}
	}
	
	/**
	 * This method returns the mbean server connection.
	 * 
	 * @return The MBean server connection
	 */
	protected synchronized MBeanServerConnection getMBeanServerConnection() throws UiException {
		if (_mbeanServerConnection == null) {
			
			if (getServerJMX() == null) {
				_mbeanServerConnection = ManagementFactory.getPlatformMBeanServer();
			} else {
				try {
					JMXServiceURL url = 
						    new JMXServiceURL(getServerJMX());
					
					JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
					
					_mbeanServerConnection = jmxc.getMBeanServerConnection();
				} catch (Exception e) {
					throw new UiException(i18n.format("SwitchYardServicesProvider.JMXConnectionFailed"), e);
				}
			}
		}
		
		return (_mbeanServerConnection);
	}
	
	/**
	 * {@inheritDoc}
	 */
    public List<QName> getApplicationNames() throws UiException {
        final List<QName> apps = new ArrayList<QName>();

        try {
	        MBeanServerConnection con=getMBeanServerConnection();
	        
	        java.util.Set<ObjectInstance> results=
	        		con.queryMBeans(new ObjectName("org.switchyard.admin:type=Application,name=*"), null);
	        
	        for (ObjectInstance result : results) {
	        	java.util.Map<String,String> map=result.getObjectName().getKeyPropertyList();
	        			
	        	if (map.containsKey("name")) {
			        String name=result.getObjectName().getKeyProperty("name");
			        
		        	apps.add(parseQName(stripQuotes(name)));
	        	}
	        }
        } catch (Exception e) {
			throw new UiException(i18n.format("SwitchYardServicesProvider.AppNamesFailed"), e);
        }
        
        return apps;
    }

	/**
	 * {@inheritDoc}
	 */
    public java.util.List<ServiceSummaryBean> findServices(final ServicesFilterBean filters) throws UiException {
        final ArrayList<ServiceSummaryBean> services = new ArrayList<ServiceSummaryBean>();

        try {
	        MBeanServerConnection con=getMBeanServerConnection();
	        
	        java.util.Set<ObjectInstance> results=
	        		con.queryMBeans(new ObjectName("org.switchyard.admin:type=Service,name=*"), null);
	        
            // TODO: Request all attributes in one operation
            
	        for (ObjectInstance result : results) {
	        	java.util.Map<String,String> map=result.getObjectName().getKeyPropertyList();
	        			
	        	if (map.containsKey("name")) {
			        AttributeList attrs=con.getAttributes(result.getObjectName(),
			        				new String[] {"Name", "Application", "Interface"});
			        String name=(String)getAttributeValue(attrs.get(0));

			        if (!isSet(filters.getServiceName()) ||
		        			filters.getServiceName().equals(name)) {
				        ObjectName app=(ObjectName)getAttributeValue(attrs.get(1));
				        String appName=stripQuotes(app.getKeyProperty("name"));
				        
				        if (!isSet(filters.getApplicationName()) ||
				        			filters.getApplicationName().equals(appName)) {
					        ServiceSummaryBean ssb=new ServiceSummaryBean();
					        ssb.setName(name);				        
					        ssb.setApplication(appName);
				        	
					        ssb.setAverageDuration(0L);
					        ssb.setBindings("");
					        ssb.setIface((String)getAttributeValue(attrs.get(2)));
					        
					        ssb.setServiceId(generateId(appName, name));
					        
				        	services.add(ssb);
				        }
			        }
	        	}
	        }
        } catch (Exception e) {
			throw new UiException(i18n.format("SwitchYardServicesProvider.GetServicesFailed"), e);
        }
        
        return services;
    }
    
    protected Object getAttributeValue(Object attr) {
    	if (attr instanceof javax.management.Attribute) {
    		return (((javax.management.Attribute)attr).getValue());
    	}
    	return (attr);
    }
    
    protected String stripQuotes(String text) {
    	if (text.length() >= 2 && text.charAt(0) == '\"'
    				&& text.charAt(text.length()-1) == '\"') {
    		return (text.substring(1, text.length()-1));
    	}
    	return (text);
    }

	/**
	 * This method returns the list of references associated with the supplied application
	 * and service.
	 * 
	 * @param applicationName The application
	 * @param serviceName The service name
	 * @return The list of references
	 * @throws UiException Failed to get the references
	 */
    protected List<ReferenceSummaryBean> getReferences(final String applicationName,
    							final String serviceName) throws UiException {
        final List<ReferenceSummaryBean> references = new ArrayList<ReferenceSummaryBean>();

        try {
	        MBeanServerConnection con=getMBeanServerConnection();
	        
            // TODO: Request all attributes in one operation
            
	        java.util.Set<ObjectInstance> results=
	        		con.queryMBeans(new ObjectName("org.switchyard.admin:type=Reference,name=*"), null);
	        
	        for (ObjectInstance result : results) {
	        	AttributeList attrs=con.getAttributes(result.getObjectName(),
	        					new String[]{"Name", "Application", "Interface"});
		        String name=(String)getAttributeValue(attrs.get(0));

		        ReferenceSummaryBean rsb=new ReferenceSummaryBean();
		        rsb.setName(name);
		        
		        ObjectName app=(ObjectName)getAttributeValue(attrs.get(1));
		        String appName=stripQuotes(app.getKeyProperty("name"));
		        
		        if (isSet(applicationName) ||
		        					applicationName.equals(appName)) {
			        rsb.setApplication(appName);
		        	
			        rsb.setAverageDuration(0L);
			        rsb.setBindings("");
			        rsb.setIface((String)getAttributeValue(attrs.get(2)));
			        
			        rsb.setReferenceId(generateId(appName, name));
			        
		        	references.add(rsb);
		        }
	        }
        } catch (Exception e) {
			throw new UiException(i18n.format("SwitchYardServicesProvider.GetReferencesFailed",
								applicationName, serviceName), e);
        }

        return references;
    }

	/**
	 * {@inheritDoc}
	 */
    public ServiceBean getService(final String uuid) throws UiException {
        final ServiceBean serviceResult = new ServiceBean();

        final List<String> ids = parseId(uuid);
        if (ids.size() == 2) {
            final String applicationName = ids.get(0);
            final String serviceName = ids.get(1);

            // TODO: Request all attributes in one operation
            
            try {
    	        MBeanServerConnection con=getMBeanServerConnection();
    	        
    	        ObjectInstance instance=con.getObjectInstance(
    	        		new ObjectName("org.switchyard.admin:type=Service,name=\""+serviceName+"\""));
    	        
		        serviceResult.setName(parseQName(serviceName));
		        
		        serviceResult.setApplication(parseQName(applicationName));
		        
		        AttributeList attrs=con.getAttributes(instance.getObjectName(), new String[]{"Interface"});
		        
		        serviceResult.setServiceInterface((String)getAttributeValue(attrs.get(0)));
		        
		        serviceResult.setServiceId(uuid);
		        
		        serviceResult.setReferences(getReferences(applicationName, serviceName));
		        
		        //ObjectName app=(ObjectName)con.getAttribute(result.getObjectName(), "Application");
		        //String appName=stripQuotes(app.getKeyProperty("name"));
		        
            } catch (Exception e) {
    			throw new UiException(i18n.format("SwitchYardServicesProvider.GetServiceFailed",
    								applicationName, serviceName), e);
            }
            
        }
        return serviceResult;
    }

	/**
	 * {@inheritDoc}
	 */
    public ReferenceBean getReference(final String uuid) throws UiException {
        final ReferenceBean referenceResult = new ReferenceBean();

        final List<String> ids = parseId(uuid);
        if (ids.size() == 2) {
            final String applicationName = ids.get(0);
            final String referenceName = ids.get(1);

            // TODO: Request all attributes in one operation
            
            try {
    	        MBeanServerConnection con=getMBeanServerConnection();
    	        
    	        ObjectInstance instance=con.getObjectInstance(
    	        		new ObjectName("org.switchyard.admin:type=Reference,name=\""+referenceName+"\""));
    	        
    	        referenceResult.setName(parseQName(referenceName));
		        
    	        referenceResult.setApplication(parseQName(applicationName));
		        
		        AttributeList attrs=con.getAttributes(instance.getObjectName(), new String[]{"Interface"});
		        
		        referenceResult.setReferenceInterface((String)getAttributeValue(attrs.get(0)));
		        
    	        referenceResult.setReferenceId(uuid);
		        
            } catch (Exception e) {
    			throw new UiException(i18n.format("SwitchYardServicesProvider.GetReferenceFailed",
    					applicationName, referenceName), e);
            }
        }
        
        return referenceResult;
    }

    private static QName parseQName(final String value) {
        final javax.xml.namespace.QName qname = javax.xml.namespace.QName.valueOf(value);
        return new QName(qname.getNamespaceURI(), qname.getLocalPart());
    }

    private static boolean isSet(final String name) {
        return ((name != null) && (name.trim().length() > 0));
    }

    public static String generateId(final String application, final String name) {
        return escape(application) + ':' + escape(name);
    }

    private static List<String> parseId(final String id) {
        if (id == null) {
            return null;
        }
        final List<String> ids = new ArrayList<String>();
        final StringBuilder unescaped = new StringBuilder();

        final int length = id.length();
        for(int count = 0 ; count < length ; count++) {
            final char ch = id.charAt(count);
            switch (ch) {
            case ESCAPE_CHAR:
                count++;
                if (count < length) {
                    unescaped.append(id.charAt(count));
                }
                break;
            case SEPARATOR_CHAR:
                ids.add(unescaped.toString());
                unescaped.setLength(0);
                break;
            default:
                unescaped.append(ch);
            }
        }
        ids.add(unescaped.toString());
        return ids;
    }

    private static String escape(final String val) {
        if (val == null) {
            return null;
        }
        final StringBuilder escaped = new StringBuilder();
        final int length = val.length();
        for(int count = 0 ; count < length ; count++) {
            final char ch = val.charAt(count);
            switch (ch) {
            case ESCAPE_CHAR:
            case SEPARATOR_CHAR:
                escaped.append(ESCAPE_CHAR);
            default:
                escaped.append(ch);
            }
        }
        return escaped.toString();
    }

}
