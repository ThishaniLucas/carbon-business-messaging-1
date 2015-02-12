/*
 * Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 */
package org.wso2.carbon.andes.ui.client;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.andes.kernel.AndesException;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.andes.ui.UIUtils;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.util.Enumeration;
import java.util.Properties;

/**
 * The client class which is used to create a QueueConnection in order to browse the content of a selected queue.
 */
public class QueueBrowserClient {

    private static final Log log = LogFactory.getLog(QueueBrowserClient.class);

    public static final String QPID_ICF = "org.wso2.andes.jndi.PropertiesFileInitialContextFactory";
    private static final String CF_NAME_PREFIX = "connectionfactory.";
    private static final String QUEUE_NAME_PREFIX = "queue.";
    private static final String CF_NAME = "qpidConnectionfactory";

    private String nameOfQueue;
    private Properties properties;
    private QueueConnection queueConnection;
    private QueueSession queueSession;
    private QueueBrowser queueBrowser;

    /***
     * Constructor - create a new browser instance for the given queue as given user.
     * @param nameOfQueue name of queue
     * @param userName User's username as per user store
     * @param accessKey as generated by andes authentication service for the user.
     * @throws FileNotFoundException
     * @throws XMLStreamException
     * @throws AndesException
     */
    public QueueBrowserClient(String nameOfQueue, String userName, String accessKey) throws FileNotFoundException,
            XMLStreamException, AndesException {
        this.nameOfQueue = nameOfQueue;
        this.properties = new Properties();
        properties.put(Context.INITIAL_CONTEXT_FACTORY, QPID_ICF);
        properties.put(CF_NAME_PREFIX + CF_NAME, UIUtils.getTCPConnectionURL(userName, accessKey));
        properties.put(QUEUE_NAME_PREFIX + nameOfQueue, nameOfQueue);
        properties.put(CarbonConstants.REQUEST_BASE_CONTEXT, "true");

    }

    /**
     * Return a lazy-loading iterator (java.util.Enumeration) of the messages of the given queue.
     * @return java.util.Enumeration
     */
    public Enumeration browseQueue() {

        Enumeration queueContentsEnu = null;
        try {
            InitialContext ctx = new InitialContext(properties);
            QueueConnectionFactory connFactory = (QueueConnectionFactory) ctx.lookup(CF_NAME);
            queueConnection = connFactory.createQueueConnection();
            Queue queue = (Queue) ctx.lookup(nameOfQueue);
            queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            queueBrowser = queueSession.createBrowser(queue);
            queueConnection.start();

            queueContentsEnu = queueBrowser.getEnumeration();

        } catch (NamingException e) {
            log.error("Error browsing queue.", e);
        } catch (JMSException e) {
            log.error("Error browsing queue.", e);
        }
        return queueContentsEnu;
    }

    /**
     * Close browser instance on this queue of this user.
     * @throws JMSException
     */
    public void closeBrowser() throws JMSException {
        queueConnection.close();
        queueSession.close();
        queueBrowser.close();
    }

    /**
     * Get message details of the input javax.jms.Message object.
     * @param queueMessage javax.jms.Message (ideally fetched through the Queue Browser Enumeration)
     * @return String representation of the message
     * @throws JMSException
     */
    public String getMsgProperties(Message queueMessage) throws JMSException {

        Enumeration propertiesEnu = queueMessage.getPropertyNames();
        StringBuilder sb = new StringBuilder("");
        if (propertiesEnu != null) {
            while (propertiesEnu.hasMoreElements()) {
                String propName = (String) propertiesEnu.nextElement();
                sb.append(propName).append(" = ").append(queueMessage.getStringProperty(propName));
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    /**
     * Determines the type of the JMS message
     *
     * @param queueMessage - input message
     * @return type of the message as a string
     */
    public String getMsgContentType(Message queueMessage) {

        String contentType = "";
        if (queueMessage instanceof TextMessage) {
            contentType = "Text";
        } else if (queueMessage instanceof ObjectMessage) {
            contentType = "Object";
        } else if (queueMessage instanceof MapMessage) {
            contentType = "Map";
        } else if (queueMessage instanceof StreamMessage) {
            contentType = "Stream";
        } else if (queueMessage instanceof BytesMessage) {
            contentType = "Byte";
        }

        return contentType;
    }


    /**
     * A stream message can have java primitives plus objects, as its content. This message it used to retrieve the
     *
     * @param queueMessage - input message
     * @param sb           - a string builder to build the whole message content
     * @return - complete message content inside the stream message
     * @throws JMSException
     */
    private String getContentFromStreamMessage(StreamMessage queueMessage, StringBuilder sb) throws JMSException {

        boolean eofReached = false;

        while (!eofReached) {

            try {
                Object obj = queueMessage.readObject();
                // obj could be null if the wire type is AbstractBytesTypedMessage.NULL_STRING_TYPE
                if (null != obj) {
                    sb.append(obj.toString()).append(", ");
                }
            } catch (MessageEOFException ex) {
                eofReached = true;
            }

        }

        return StringEscapeUtils.escapeHtml(sb.toString());
    }

}
