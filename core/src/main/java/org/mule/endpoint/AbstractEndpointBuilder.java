/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.endpoint;

import org.mule.MessageExchangePattern;
import org.mule.api.DefaultMuleException;
import org.mule.api.MuleContext;
import org.mule.api.MuleRuntimeException;
import org.mule.api.config.MuleProperties;
import org.mule.api.endpoint.EndpointBuilder;
import org.mule.api.endpoint.EndpointException;
import org.mule.api.endpoint.EndpointMessageProcessorChainFactory;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.endpoint.MalformedEndpointException;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.registry.ServiceException;
import org.mule.api.registry.ServiceType;
import org.mule.api.retry.RetryPolicyTemplate;
import org.mule.api.security.EndpointSecurityFilter;
import org.mule.api.transaction.TransactionConfig;
import org.mule.api.transformer.Transformer;
import org.mule.api.transport.Connector;
import org.mule.config.i18n.CoreMessages;
import org.mule.config.i18n.Message;
import org.mule.transaction.MuleTransactionConfig;
import org.mule.transformer.TransformerUtils;
import org.mule.transport.AbstractConnector;
import org.mule.transport.service.TransportFactory;
import org.mule.transport.service.TransportFactoryException;
import org.mule.transport.service.TransportServiceDescriptor;
import org.mule.transport.service.TransportServiceException;
import org.mule.util.ClassUtils;
import org.mule.util.CollectionUtils;
import org.mule.util.MapCombiner;
import org.mule.util.ObjectNameHelper;
import org.mule.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Abstract endpoint builder used for externalizing the complex creation logic of
 * endpoints out of the endpoint instance itself. <br/>
 * The use of a builder allows i) Endpoints to be configured once and created in a
 * repeatable fashion (global endpoints), ii) Allow for much more extensibility in
 * endpoint creation for transport specific endpoints, streaming endpoints etc.<br/>
 */
public abstract class AbstractEndpointBuilder implements EndpointBuilder
{

    public static final String PROPERTY_RESPONSE_TIMEOUT = "responseTimeout";
    public static final String PROPERTY_RESPONSE_PROPERTIES = "responseProperties";

    protected URIBuilder uriBuilder;
    protected Connector connector;
    protected String name;
    protected Map<Object, Object> properties = new HashMap<Object, Object>();
    protected TransactionConfig transactionConfig;
    protected Boolean deleteUnacceptedMessages;
    protected EndpointSecurityFilter securityFilter;
    protected Boolean synchronous;
    protected MessageExchangePattern messageExchangePattern;
    protected Integer responseTimeout;
    protected String initialState = ImmutableEndpoint.INITIAL_STATE_STARTED;
    protected String encoding;
    protected Integer createConnector;
    protected RetryPolicyTemplate retryPolicyTemplate;
    protected String responsePropertiesList;
    protected EndpointMessageProcessorChainFactory messageProcessorsFactory;
    protected List<MessageProcessor> messageProcessors = new LinkedList<MessageProcessor>();
    protected List<MessageProcessor> responseMessageProcessors = new LinkedList<MessageProcessor>();
    protected Boolean disableTransportTransformer;
    protected String mimeType;

    // not included in equality/hash
    protected String registryId = null;
    protected MuleContext muleContext;

    protected transient Log logger = LogFactory.getLog(getClass());

    public InboundEndpoint buildInboundEndpoint() throws EndpointException, InitialisationException
    {
        return doBuildInboundEndpoint();
    }

    public OutboundEndpoint buildOutboundEndpoint() throws EndpointException, InitialisationException
    {
        return doBuildOutboundEndpoint();
    }

    protected void setPropertiesFromProperties(Map<Object, Object> properties)
    {
        final Boolean tempSync = getBooleanProperty(properties, MuleProperties.SYNCHRONOUS_PROPERTY,
            synchronous);
        if (tempSync != null)
        {
            if (uriBuilder != null)
            {
                logger.warn(String.format(
                    "Deprecated 'synchronous' flag found on endpoint '%s', please replace with "
                                    + "e.g. 'exchangePattern=request-response", uriBuilder.getEndpoint()));
            }
            else
            {
                logger.warn("Deprecated 'synchronous' flag found on endpoint)");
            }
        }

        String mepString = (String) properties.get(MuleProperties.EXCHANGE_PATTERN_CAMEL_CASE);
        if (StringUtils.isNotEmpty(mepString))
        {
            setExchangePattern(MessageExchangePattern.fromString(mepString));
        }

        responseTimeout = getIntegerProperty(properties, PROPERTY_RESPONSE_TIMEOUT, responseTimeout);
        responsePropertiesList = (String)properties.get(PROPERTY_RESPONSE_PROPERTIES);
    }

    private static Boolean getBooleanProperty(Map<Object, Object> properties, String name, Boolean dflt)
    {
        if (properties.containsKey(name))
        {
            return Boolean.valueOf((String)properties.get(name));
        }
        else
        {
            return dflt;
        }
    }

    private static Integer getIntegerProperty(Map<Object, Object> properties, String name, Integer dflt)
    {
        if (properties.containsKey(name))
        {
            return Integer.decode((String)properties.get(name));
        }
        else
        {
            return dflt;
        }
    }

    protected InboundEndpoint doBuildInboundEndpoint() throws InitialisationException, EndpointException
    {
        //It does not make sense to allow inbound dynamic endpoints
        String uri = uriBuilder.getConstructor();
        if(muleContext.getExpressionManager().isExpression(uri))
        {
            throw new MalformedEndpointException(CoreMessages.dynamicEndpointURIsCannotBeUsedOnInbound(), uri);
        }

        prepareToBuildEndpoint();

        EndpointURI endpointURI = uriBuilder.getEndpoint();
        endpointURI.initialise();

        addTransformersFromUri(endpointURI);
        addResponseTransformersFromUri(endpointURI);

        Connector connector = getConnector();
        if (connector != null && !connector.supportsProtocol(endpointURI.getFullScheme()))
        {
            throw new IllegalArgumentException(CoreMessages.connectorSchemeIncompatibleWithEndpointScheme(
                connector.getProtocol(), endpointURI).getMessage());
        }

        checkInboundExchangePattern();

        InboundEndpoint endpoint = new DefaultInboundEndpoint(connector, endpointURI,
                getName(endpointURI), getProperties(), getTransactionConfig(),
                getDefaultDeleteUnacceptedMessages(connector), getSecurityFilter(),
                messageExchangePattern, getResponseTimeout(connector), getInitialState(connector),
                getEndpointEncoding(connector), name, muleContext, getRetryPolicyTemplate(connector),
                getMessageProcessorsFactory(), messageProcessors, responseMessageProcessors,
                isDisableTransportTransformer(), mimeType);

        for (MessageProcessor mp : messageProcessors)
        {
        	// TODO There should ideally be an EndpointAware interface
        	if (mp instanceof Transformer)
        	{
        		((Transformer) mp).setEndpoint(endpoint);
        	}
        }
        for (MessageProcessor mp : responseMessageProcessors)
        {
        	if (mp instanceof Transformer)
        	{
        		((Transformer) mp).setEndpoint(endpoint);
        	}
        }
        return endpoint;
    }

    protected OutboundEndpoint doBuildOutboundEndpoint() throws InitialisationException, EndpointException
    {

        String uri = uriBuilder.getConstructor();
        if(muleContext.getExpressionManager().isExpression(uri))
        {
            if(muleContext.getExpressionManager().isValidExpression(uriBuilder.getConstructor()))
            {
                uriBuilder = new URIBuilder(DynamicOutboundEndpoint.DYNAMIC_URI_PLACEHOLDER, muleContext);
                return new DynamicOutboundEndpoint(muleContext, this, uri);
            }
            else
            {
                throw new MalformedEndpointException(uri);
            }
        }

        prepareToBuildEndpoint();

        EndpointURI endpointURI = uriBuilder.getEndpoint();
        endpointURI.initialise();

        addTransformersFromUri(endpointURI);
        addResponseTransformersFromUri(endpointURI);

        Connector connector = getConnector();
        if (connector != null && !connector.supportsProtocol(getScheme()))
        {
            throw new IllegalArgumentException(CoreMessages.connectorSchemeIncompatibleWithEndpointScheme(
                connector.getProtocol(), endpointURI).getMessage());
        }

        checkOutboundExchangePattern();

        OutboundEndpoint endpoint = new DefaultOutboundEndpoint(connector, endpointURI,
                getName(endpointURI), getProperties(), getTransactionConfig(),
                getDefaultDeleteUnacceptedMessages(connector), getSecurityFilter(),
                messageExchangePattern, getResponseTimeout(connector), getInitialState(connector),
                getEndpointEncoding(connector), name, muleContext, getRetryPolicyTemplate(connector),
                responsePropertiesList,  getMessageProcessorsFactory(), messageProcessors,
                responseMessageProcessors, isDisableTransportTransformer(), mimeType);

        for (MessageProcessor mp : messageProcessors)
        {
        	// TODO There should ideally be an EndpointAware interface
        	if (mp instanceof Transformer)
        	{
        		((Transformer) mp).setEndpoint(endpoint);
        	}
        }
        for (MessageProcessor mp : responseMessageProcessors)
        {
        	if (mp instanceof Transformer)
        	{
        		((Transformer) mp).setEndpoint(endpoint);
        	}
        }
        return endpoint;
    }

    protected void prepareToBuildEndpoint()
    {
        // use an explicit value here to avoid caching
        Map<Object, Object> props = getProperties();
        // this sets values used below, if they appear as properties
        setPropertiesFromProperties(props);

        if (uriBuilder == null)
        {
            throw new MuleRuntimeException(CoreMessages.objectIsNull("uriBuilder"));
        }
        uriBuilder.setMuleContext(muleContext);
    }

    protected void checkInboundExchangePattern() throws EndpointException
    {
        TransportServiceDescriptor serviceDescriptor = getConnectorServiceDescriptor();
        initExchangePatternFromConnectorDefault(serviceDescriptor);

        if (!serviceDescriptor.getInboundExchangePatterns().contains(messageExchangePattern))
        {
            throw new EndpointException(CoreMessages.exchangePatternForEndpointNotSupported(
                messageExchangePattern, "inbound", uriBuilder.getEndpoint()));
        }
    }

    private void checkOutboundExchangePattern() throws EndpointException
    {
        TransportServiceDescriptor serviceDescriptor = getConnectorServiceDescriptor();
        initExchangePatternFromConnectorDefault(serviceDescriptor);

        if (!serviceDescriptor.getOutboundExchangePatterns().contains(messageExchangePattern))
        {
            throw new EndpointException(CoreMessages.exchangePatternForEndpointNotSupported(
                messageExchangePattern, "outbound", uriBuilder.getEndpoint()));
        }
    }

    private TransportServiceDescriptor getConnectorServiceDescriptor() throws EndpointException
    {
        try
        {
            Connector conn = getConnector();
            return getNonNullServiceDescriptor(conn);
        }
        catch (ServiceException e)
        {
            throw new EndpointException(e);
        }
    }

    protected void initExchangePatternFromConnectorDefault(TransportServiceDescriptor serviceDescriptor)
        throws EndpointException
    {
        if (messageExchangePattern == null)
        {
            try
            {
                messageExchangePattern = serviceDescriptor.getDefaultExchangePattern();
            }
            catch (TransportServiceException e)
            {
                throw new EndpointException(e);
            }
        }
    }

    private Properties getOverrides(Connector connector)
    {
        // Get connector specific overrides to set on the descriptor
        Properties overrides = new Properties();
        if (connector instanceof AbstractConnector)
        {
            Map so = ((AbstractConnector)connector).getServiceOverrides();
            if (so != null)
            {
                overrides.putAll(so);
            }
        }
        return overrides;
    }

    private TransportServiceDescriptor getNonNullServiceDescriptor(Connector conn) throws ServiceException
    {
        String scheme = uriBuilder.getEndpoint().getSchemeMetaInfo();
        Properties overrides = getOverrides(conn);
        TransportServiceDescriptor sd = (TransportServiceDescriptor) muleContext.getRegistry()
            .lookupServiceDescriptor(ServiceType.TRANSPORT, scheme, overrides);
        if (null != sd)
        {
            return sd;
        }
        else
        {
            throw new ServiceException(CoreMessages.noServiceTransportDescriptor(scheme));
        }
    }

    protected RetryPolicyTemplate getRetryPolicyTemplate(Connector conn)
    {
        return retryPolicyTemplate != null ? retryPolicyTemplate : conn.getRetryPolicyTemplate();
    }

    protected TransactionConfig getTransactionConfig()
    {
        return transactionConfig != null ? transactionConfig : getDefaultTransactionConfig();
    }

    protected TransactionConfig getDefaultTransactionConfig()
    {
        return new MuleTransactionConfig();
    }

    protected EndpointSecurityFilter getSecurityFilter()
    {
        return securityFilter != null ? securityFilter : getDefaultSecurityFilter();
    }

    protected EndpointSecurityFilter getDefaultSecurityFilter()
    {
        return null;
    }

    protected Connector getConnector() throws EndpointException
    {
        return connector != null ? connector : getDefaultConnector();
    }

    protected Connector getDefaultConnector() throws EndpointException
    {
        return getConnector(uriBuilder.getEndpoint(), muleContext);
    }

    protected String getName(EndpointURI endpointURI)
    {
        return name != null ? name : new ObjectNameHelper(muleContext).getEndpointName(endpointURI);
    }

    protected Map<Object, Object> getProperties()
    {
        // Add properties from builder, endpointURI and then seal (make unmodifiable)
        LinkedList<Object> maps = new LinkedList<Object>();
        // properties from url come first
        if (null != uriBuilder)
        {
            uriBuilder.setMuleContext(muleContext);
            // properties from the URI itself
            maps.addLast(uriBuilder.getEndpoint().getParams());
        }
        // properties on builder may override url
        if (properties != null)
        {
            maps.addLast(properties);
        }
        MapCombiner combiner = new MapCombiner();
        combiner.setList(maps);
        return Collections.unmodifiableMap(combiner);
    }

    protected boolean getDeleteUnacceptedMessages(Connector connector)
    {
        return deleteUnacceptedMessages != null
                                               ? deleteUnacceptedMessages.booleanValue()
                                               : getDefaultDeleteUnacceptedMessages(connector);
    }

    @SuppressWarnings("unused")
    protected boolean getDefaultDeleteUnacceptedMessages(Connector connector)
    {
        return false;
    }

    protected String getEndpointEncoding(Connector connector)
    {
        return encoding != null ? encoding : getDefaultEndpointEncoding(connector);
    }

    @SuppressWarnings("unused")
    protected String getDefaultEndpointEncoding(Connector connector)
    {
        return muleContext.getConfiguration().getDefaultEncoding();
    }

    protected String getInitialState(Connector connector)
    {
        return initialState != null ? initialState : getDefaultInitialState(connector);
    }

    @SuppressWarnings("unused")
    protected String getDefaultInitialState(Connector connector)
    {
        return ImmutableEndpoint.INITIAL_STATE_STARTED;
    }

    protected int getResponseTimeout(Connector connector)
    {
        return responseTimeout != null ? responseTimeout.intValue() : getDefaultResponseTimeout(connector);

    }

    @SuppressWarnings("unused")
    protected int getDefaultResponseTimeout(Connector connector)
    {
        return muleContext.getConfiguration().getDefaultResponseTimeout();
    }

    protected void addTransformersFromUri(EndpointURI endpointURI) throws TransportFactoryException
    {
        if (endpointURI.getTransformers() != null)
        {
            if (!CollectionUtils.containsType(messageProcessors, Transformer.class))
            {
                messageProcessors.addAll(getTransformersFromString(endpointURI.getTransformers()));
            }
            else
            {
                logger.info("Endpoint with uri '"
                            + endpointURI.toString()
                            + "' has transformer(s) configured, transformers configured as uri paramaters will be ignored.");
            }
        }
    }

    protected void addResponseTransformersFromUri(EndpointURI endpointURI) throws TransportFactoryException
    {
        if (endpointURI.getResponseTransformers() != null)
        {
            if (!CollectionUtils.containsType(responseMessageProcessors, Transformer.class))
            {
                responseMessageProcessors.addAll(getTransformersFromString(endpointURI.getResponseTransformers()));
            }
            else
            {
                logger.info("Endpoint with uri '"
                            + endpointURI.toString()
                            + "' has response transformer(s) configured, response transformers configured as uri paramaters will be ignored.");
            }
        }
    }

    protected String getMimeType()
    {
        return mimeType;
    }

    public void setMimeType(String mimeType)
    {
        if (mimeType == null)
        {
            this.mimeType = null;
        }
        else
        {
            MimeType mt;
            try
            {
                mt = new MimeType(mimeType);
            }
            catch (MimeTypeParseException e)
            {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            this.mimeType = mt.getPrimaryType() + "/" + mt.getSubType();
        }
    }

    private List<Transformer> getTransformersFromString(String transformers) throws TransportFactoryException
    {
        try
        {
            return TransformerUtils.getTransformers(transformers, muleContext);
        }
        catch (DefaultMuleException e)
        {
            throw new TransportFactoryException(e);
        }
    }

    private Connector getConnector(EndpointURI endpointURI, MuleContext muleContext) throws EndpointException
    {
        String scheme = getScheme();
        TransportFactory factory = new TransportFactory(muleContext);

        Connector connector;
        try
        {
            if (uriBuilder.getEndpoint().getConnectorName() != null)
            {
                connector = muleContext.getRegistry().lookupConnector(
                    uriBuilder.getEndpoint().getConnectorName());
                if (connector == null)
                {
                    throw new TransportFactoryException(CoreMessages.objectNotRegistered("Connector",
                        uriBuilder.getEndpoint().getConnectorName()));
                }
            }
            else if (isAlwaysCreateConnector())
            {
                connector = factory.createConnector(endpointURI);
                muleContext.getRegistry().registerConnector(connector);
            }
            else
            {
                connector = factory.getConnectorByProtocol(scheme);
                if (connector == null)
                {
                    connector = factory.createConnector(endpointURI);
                    muleContext.getRegistry().registerConnector(connector);
                }
            }
        }
        catch (Exception e)
        {
            throw new TransportFactoryException(e);
        }

        if (connector == null)
        {
            Message m = CoreMessages.failedToCreateObjectWith("Endpoint", "endpointURI: " + endpointURI);
            m.setNextMessage(CoreMessages.objectIsNull("connector"));
            throw new TransportFactoryException(m);

        }
        return connector;
    }

    protected String getScheme()
    {
        return uriBuilder.getEndpoint().getFullScheme();
    }

    /**
     * Some endpoint may always require a new connector to be created for every
     * endpoint
     *
     * @return the default if false but cusotm endpoints can override
     * @since 3.0.0
     */
    protected boolean isAlwaysCreateConnector()
    {
        return false;
    }

    // Builder setters

    public void setConnector(Connector connector)
    {
        this.connector = connector;
    }

    /** @deprecated Use addMessageProcessor() */
    public void addTransformer(Transformer transformer)
    {
    	this.messageProcessors.add(transformer);
    }

    /** @deprecated Use setMessageProcessors() */
    public void setTransformers(List<Transformer> transformers)
    {
        if (transformers != null && !transformers.isEmpty())
        {
            this.messageProcessors.addAll(transformers);
        }
    }

    protected EndpointMessageProcessorChainFactory getMessageProcessorsFactory()
    {
        return messageProcessorsFactory != null
                                               ? messageProcessorsFactory
                                               : getDefaultMessageProcessorsFactory();
    }

    protected EndpointMessageProcessorChainFactory getDefaultMessageProcessorsFactory()
    {
        return new DefaultEndpointMessageProcessorChainFactory();
    }

    /** @deprecated Use addResponseMessageProcessor() */
    public void addResponseTransformer(Transformer transformer)
    {
    	this.responseMessageProcessors.add(transformer);
    }

    /** @deprecated Use setResponseMessageProcessors() */
    public void setResponseTransformers(List<Transformer> transformers)
    {
        if (transformers != null && !transformers.isEmpty())
        {
            this.responseMessageProcessors.addAll(transformers);
        }
    }

    public void addMessageProcessor(MessageProcessor messageProcessor)
    {
        messageProcessors.add(messageProcessor);
    }

    public void setMessageProcessors(List<MessageProcessor> messageProcessors)
    {
        this.messageProcessors = messageProcessors;
    }

    public List<MessageProcessor> getMessageProcessors()
    {
        return messageProcessors;
    }

    public void addResponseMessageProcessor(MessageProcessor messageProcessor)
    {
        responseMessageProcessors.add(messageProcessor);
    }

    public void setResponseMessageProcessors(List<MessageProcessor> messageProcessors)
    {
        this.responseMessageProcessors = messageProcessors;
    }

    public List<MessageProcessor> getResponseMessageProcessors()
    {
        return responseMessageProcessors;
    }

    protected boolean isDisableTransportTransformer()
    {
        return disableTransportTransformer != null
                ? disableTransportTransformer.booleanValue()
                : getDefaultDisableTransportTransformer();
    }

    protected boolean getDefaultDisableTransportTransformer()
    {
        return false;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * NOTE - this appends properties.
     */
    public void setProperties(Map<Object, Object> properties)
    {
        if (null == this.properties)
        {
            this.properties = new HashMap<Object, Object>();
        }
        this.properties.putAll(properties);
    }

    /**
     * Sets a property on the endpoint
     *
     * @param key the property key
     * @param value the value of the property
     */
    public void setProperty(Object key, Object value)
    {
        properties.put(key, value);
    }

    public void setTransactionConfig(TransactionConfig transactionConfig)
    {
        this.transactionConfig = transactionConfig;
    }

    public void setDeleteUnacceptedMessages(boolean deleteUnacceptedMessages)
    {
        this.deleteUnacceptedMessages = Boolean.valueOf(deleteUnacceptedMessages);
    }

    public void setSecurityFilter(EndpointSecurityFilter securityFilter)
    {
        this.securityFilter = securityFilter;
    }

    public void setExchangePattern(MessageExchangePattern mep)
    {
        messageExchangePattern = mep;
    }

    public void setResponseTimeout(int responseTimeout)
    {
        this.responseTimeout = new Integer(responseTimeout);
    }

    public void setInitialState(String initialState)
    {
        this.initialState = initialState;
    }

    public void setEncoding(String encoding)
    {
        this.encoding = encoding;
    }

    public void setCreateConnector(int createConnector)
    {
        this.createConnector = new Integer(createConnector);
    }

    public void setRegistryId(String registryId)
    {
        this.registryId = registryId;
    }

    public void setMuleContext(MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }

    public void setRetryPolicyTemplate(RetryPolicyTemplate retryPolicyTemplate)
    {
        this.retryPolicyTemplate = retryPolicyTemplate;
    }

    public void setDisableTransportTransformer(boolean disableTransportTransformer)
    {
        this.disableTransportTransformer = Boolean.valueOf(disableTransportTransformer);
    }

    public URIBuilder getEndpointBuilder()
    {
        return uriBuilder;
    }

    public void setURIBuilder(URIBuilder URIBuilder)
    {
        this.uriBuilder = URIBuilder;
    }

    @Override
    public int hashCode()
    {
        return ClassUtils.hash(new Object[]{retryPolicyTemplate, connector, createConnector, 
            deleteUnacceptedMessages, encoding, uriBuilder, initialState, name, properties,
            responseTimeout, responseMessageProcessors, securityFilter, synchronous, 
            messageExchangePattern, transactionConfig, messageProcessors, disableTransportTransformer, mimeType});
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || getClass() != obj.getClass())
        {
            return false;
        }

        final AbstractEndpointBuilder other = (AbstractEndpointBuilder)obj;
        return equal(retryPolicyTemplate, other.retryPolicyTemplate) && equal(connector, other.connector)
                && equal(createConnector, other.createConnector)
                && equal(deleteUnacceptedMessages, other.deleteUnacceptedMessages) && equal(encoding, other.encoding)
                && equal(uriBuilder, other.uriBuilder)
                && equal(initialState, other.initialState) && equal(name, other.name)
                && equal(properties, other.properties)
                && equal(responseTimeout, other.responseTimeout)
                && equal(messageProcessors, other.messageProcessors)
                && equal(securityFilter, other.securityFilter) && equal(synchronous, other.synchronous)
                && equal(messageExchangePattern, other.messageExchangePattern)
                && equal(transactionConfig, other.transactionConfig)
                && equal(responseMessageProcessors, other.responseMessageProcessors)
                && equal(disableTransportTransformer, other.disableTransportTransformer)
                && equal(mimeType, other.mimeType);
    }

    protected static boolean equal(Object a, Object b)
    {
        return ClassUtils.equal(a, b);
    }

    @Override
    public Object clone() throws CloneNotSupportedException
    {
        EndpointBuilder builder = (EndpointBuilder)super.clone();
        builder.setConnector(connector);
        builder.setURIBuilder(uriBuilder);
        builder.setMessageProcessors(messageProcessors);
        builder.setResponseMessageProcessors(responseMessageProcessors);
        builder.setName(name);
        builder.setProperties(properties);
        builder.setTransactionConfig(transactionConfig);
        builder.setSecurityFilter(securityFilter);
        builder.setInitialState(initialState);
        builder.setEncoding(encoding);
        builder.setRegistryId(registryId);
        builder.setMuleContext(muleContext);
        builder.setRetryPolicyTemplate(retryPolicyTemplate);

        if (deleteUnacceptedMessages != null)
        {
            builder.setDeleteUnacceptedMessages(deleteUnacceptedMessages.booleanValue());
        }
        if (messageExchangePattern != null)
        {
            builder.setExchangePattern(messageExchangePattern);
        }

        if (responseTimeout != null)
        {
            builder.setResponseTimeout(responseTimeout.intValue());
        }
        if (disableTransportTransformer != null)
        {
            builder.setDisableTransportTransformer(disableTransportTransformer.booleanValue());
        }

        return builder;
    }
}
